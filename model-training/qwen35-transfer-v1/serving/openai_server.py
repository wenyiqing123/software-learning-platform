"""Private, bounded OpenAI-compatible inference endpoint for the Qwen3.5 LoRA adapter."""
from __future__ import annotations

import hmac
import hashlib
import json
import logging
import os
import threading
import time
import uuid
from contextlib import nullcontext
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Iterator

LOG = logging.getLogger("qwen35-inference")
PLACEHOLDER_KEYS = {"", "change-me", "your-api-key", "local-development-key"}
MAX_BODY_HARD_LIMIT = 1_048_576
MAX_MESSAGES_HARD_LIMIT = 32
MAX_INPUT_HARD_LIMIT = 8192
MAX_OUTPUT_HARD_LIMIT = 1024
ALLOWED_ROLES = {"system", "developer", "user", "assistant"}
PINNED_MODEL_REPO = "Qwen/Qwen3.5-0.8B"
PINNED_MODEL_REVISION = "2fc06364715b967f1860aea9cf38778875588b17"


def read_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.is_file():
        return values
    for line in path.read_text(encoding="utf-8-sig").splitlines():
        value = line.strip()
        if not value or value.startswith("#") or "=" not in value:
            continue
        key, raw = value.split("=", 1)
        key = key.strip()
        raw = raw.strip()
        if raw[:1] in {"'", '"'} and raw[-1:] == raw[:1] and len(raw) >= 2:
            raw = raw[1:-1]
        if key:
            values[key] = raw
    return values


@dataclass(frozen=True)
class Settings:
    api_key: str
    model_id: str = "qwen35-ranked-lora"
    base_model_id: str = "qwen35-base"
    host: str = "0.0.0.0"
    port: int = 8998
    max_request_bytes: int = 1_048_576
    max_messages: int = 32
    max_input_tokens: int = 4096
    max_new_tokens: int = 512
    min_free_vram_mib: int = 3072
    base_model_dir: Path | None = None
    adapter_dir: Path | None = None

    def __post_init__(self):
        if not self.api_key or self.api_key.strip().lower() in PLACEHOLDER_KEYS:
            raise ValueError("QWEN35_API_KEY must contain a non-placeholder secret")
        if not self.model_id.strip() or len(self.model_id) > 128:
            raise ValueError("QWEN35_MODEL_ID is invalid")
        if (not self.base_model_id.strip() or len(self.base_model_id) > 128
                or self.base_model_id == self.model_id):
            raise ValueError("QWEN35_BASE_MODEL_ID must be valid and differ from QWEN35_MODEL_ID")
        if not 1 <= self.port <= 65535:
            raise ValueError("QWEN35_PORT must be between 1 and 65535")
        if not 1024 <= self.max_request_bytes <= MAX_BODY_HARD_LIMIT:
            raise ValueError("QWEN35_MAX_REQUEST_BYTES is outside the supported range")
        if not 1 <= self.max_messages <= MAX_MESSAGES_HARD_LIMIT:
            raise ValueError("QWEN35_MAX_MESSAGES is outside the supported range")
        if not 128 <= self.max_input_tokens <= MAX_INPUT_HARD_LIMIT:
            raise ValueError("QWEN35_MAX_INPUT_TOKENS is outside the supported range")
        if not 1 <= self.max_new_tokens <= MAX_OUTPUT_HARD_LIMIT:
            raise ValueError("QWEN35_MAX_NEW_TOKENS is outside the supported range")
        if not 0 <= self.min_free_vram_mib <= 8192:
            raise ValueError("QWEN35_MIN_FREE_VRAM_MIB is outside the supported range")

    @classmethod
    def from_env(cls, environ: dict[str, str] | None = None) -> "Settings":
        process_env = dict(os.environ if environ is None else environ)
        config_path = process_env.get("QWEN35_CONFIG_FILE", "").strip()
        if not config_path and (environ is None or not process_env):
            default_path = Path(__file__).resolve().with_name("qwen35.env")
            if default_path.is_file():
                config_path = str(default_path)
        env = read_env_file(Path(config_path)) if config_path else {}
        env.update(process_env)

        def integer(name: str, default: int) -> int:
            raw = env.get(name)
            return default if raw is None or not raw.strip() else int(raw)

        base_model = env.get("QWEN35_BASE_MODEL_DIR", "").strip()
        adapter = env.get("QWEN35_ADAPTER_DIR", "").strip()
        return cls(
            api_key=env.get("QWEN35_API_KEY", "").strip(),
            model_id=env.get("QWEN35_MODEL_ID", "qwen35-ranked-lora").strip(),
            base_model_id=env.get("QWEN35_BASE_MODEL_ID", "qwen35-base").strip(),
            host=env.get("QWEN35_HOST", "0.0.0.0").strip(),
            port=integer("QWEN35_PORT", 8998),
            max_request_bytes=integer("QWEN35_MAX_REQUEST_BYTES", 1_048_576),
            max_messages=integer("QWEN35_MAX_MESSAGES", 32),
            max_input_tokens=integer("QWEN35_MAX_INPUT_TOKENS", 4096),
            max_new_tokens=integer("QWEN35_MAX_NEW_TOKENS", 512),
            min_free_vram_mib=integer("QWEN35_MIN_FREE_VRAM_MIB", 3072),
            base_model_dir=Path(base_model).resolve() if base_model else None,
            adapter_dir=Path(adapter).resolve() if adapter else None,
        )

    @property
    def model_ids(self) -> tuple[str, str]:
        return self.base_model_id, self.model_id


def normalize_messages(raw: Any, max_messages: int) -> list[dict[str, str]]:
    if not isinstance(raw, list) or not 1 <= len(raw) <= max_messages:
        raise ValueError("messages must contain between 1 and the configured maximum entries")
    messages = []
    for item in raw:
        if not isinstance(item, dict) or item.get("role") not in ALLOWED_ROLES:
            raise ValueError("message role is unsupported")
        content = item.get("content")
        if isinstance(content, str):
            text = content
        elif isinstance(content, list):
            parts = []
            for part in content:
                if not isinstance(part, dict) or part.get("type") != "text" or not isinstance(part.get("text"), str):
                    raise ValueError("only text message content is supported")
                parts.append(part["text"])
            text = "".join(parts)
        else:
            raise ValueError("message content must be text")
        if not text.strip() or len(text) > 65_536:
            raise ValueError("message content is empty or exceeds the per-message limit")
        messages.append({"role": item["role"], "content": text})
    if not any(message["role"] == "user" for message in messages):
        raise ValueError("at least one user message is required")
    return messages


def normalize_completion_request(payload: Any, settings: Settings) -> dict[str, Any]:
    if not isinstance(payload, dict):
        raise ValueError("request must be a JSON object")
    model_id = payload.get("model")
    if model_id not in settings.model_ids:
        raise LookupError("model not found")
    if payload.get("n", 1) != 1 or payload.get("tools") or payload.get("tool_choice"):
        raise ValueError("only one text completion without tools is supported")
    temperature = payload.get("temperature", 0.0)
    if isinstance(temperature, bool) or not isinstance(temperature, (int, float)) or not 0 <= temperature <= 2:
        raise ValueError("temperature must be between 0 and 2")
    max_new_tokens = payload.get("max_completion_tokens", payload.get("max_tokens", settings.max_new_tokens))
    if isinstance(max_new_tokens, bool) or not isinstance(max_new_tokens, int) or not 1 <= max_new_tokens <= settings.max_new_tokens:
        raise ValueError("max_tokens is outside the configured generation limit")
    stream = payload.get("stream", False)
    if not isinstance(stream, bool):
        raise ValueError("stream must be a boolean")
    stream_options = payload.get("stream_options", {}) or {}
    if not isinstance(stream_options, dict):
        raise ValueError("stream_options must be an object")
    messages = normalize_messages(payload.get("messages"), settings.max_messages)
    return {"model": model_id, "messages": messages, "temperature": float(temperature), "max_new_tokens": max_new_tokens,
            "stream": stream, "include_usage": bool(stream_options.get("include_usage", False))}


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_model_layout(base_model_dir: Path, adapter_dir: Path) -> None:
    base = base_model_dir.resolve()
    adapter = adapter_dir.resolve()
    base_config_path = base / "config.json"
    adapter_config_path = adapter / "adapter_config.json"
    adapter_weights_path = adapter / "adapter_model.safetensors"
    manifest_path = base.parent / "provenance" / "base-model-verification.json"

    if not base_config_path.is_file():
        raise RuntimeError("base model config is missing")
    if not adapter_config_path.is_file() or not adapter_weights_path.is_file():
        raise RuntimeError("LoRA adapter files are missing")

    base_config = json.loads(base_config_path.read_text(encoding="utf-8"))
    if (base_config.get("model_type") != "qwen3_5"
            or "Qwen3_5ForConditionalGeneration" not in base_config.get("architectures", [])):
        raise RuntimeError("base model architecture does not match pinned Qwen3.5-0.8B")

    adapter_config = json.loads(adapter_config_path.read_text(encoding="utf-8"))
    if (adapter_config.get("base_model_name_or_path") != PINNED_MODEL_REPO
            or adapter_config.get("revision") != PINNED_MODEL_REVISION):
        raise RuntimeError("LoRA adapter does not match the pinned base-model revision")

    if not manifest_path.is_file():
        raise RuntimeError("pinned base-model verification manifest is missing")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if (manifest.get("repoId") != PINNED_MODEL_REPO
            or manifest.get("revision") != PINNED_MODEL_REVISION):
        raise RuntimeError("base model manifest does not match the pinned official model")

    files = manifest.get("files")
    if not isinstance(files, dict) or not files:
        raise RuntimeError("pinned base-model manifest has no files")
    for relative_name, expected in files.items():
        candidate = (base / relative_name).resolve()
        if not candidate.is_relative_to(base) or not candidate.is_file():
            raise RuntimeError(f"pinned base-model file is missing or invalid: {relative_name}")
        if (not isinstance(expected, dict)
                or candidate.stat().st_size != expected.get("bytes")
                or _sha256_file(candidate) != expected.get("sha256")):
            raise RuntimeError(f"pinned base-model checksum mismatch: {relative_name}")


class TransformersRuntime:
    def __init__(self, model, tokenizer, torch, device: str, model_id: str, base_model_id: str):
        self.model = model
        self.tokenizer = tokenizer
        self.torch = torch
        self.device = device
        self.model_id = model_id
        self.base_model_id = base_model_id
        self.gpu_name = torch.cuda.get_device_name(0) if device == "cuda:0" else "CPU"

    @classmethod
    def load(cls, settings: Settings) -> "TransformersRuntime":
        if settings.base_model_dir is None or settings.adapter_dir is None:
            raise RuntimeError("QWEN35_BASE_MODEL_DIR and QWEN35_ADAPTER_DIR are required")
        verify_model_layout(settings.base_model_dir, settings.adapter_dir)

        import torch
        from peft import PeftModel
        from transformers import AutoModelForMultimodalLM, AutoTokenizer

        if not torch.cuda.is_available():
            raise RuntimeError("CUDA GPU is required for the configured 16 GB inference host")
        free_bytes, total_bytes = torch.cuda.mem_get_info(0)
        free_mib = free_bytes // (1024 * 1024)
        total_mib = total_bytes // (1024 * 1024)
        if free_mib < settings.min_free_vram_mib:
            raise RuntimeError(f"insufficient free GPU memory: {free_mib} MiB")
        device = "cuda:0"
        tokenizer = AutoTokenizer.from_pretrained(settings.base_model_dir, use_fast=True)
        if tokenizer.pad_token_id is None:
            tokenizer.pad_token = tokenizer.eos_token
        dtype = torch.bfloat16 if torch.cuda.get_device_capability(0) >= (8, 0) and torch.cuda.is_bf16_supported() else torch.float16
        model = AutoModelForMultimodalLM.from_pretrained(
            settings.base_model_dir, dtype=dtype, attn_implementation="sdpa", low_cpu_mem_usage=True)
        model = PeftModel.from_pretrained(model, settings.adapter_dir, is_trainable=False)
        model.to(device).eval()
        return cls(model, tokenizer, torch, device, settings.model_id, settings.base_model_id)

    @property
    def model_ids(self) -> tuple[str, str]:
        return self.base_model_id, self.model_id

    def _adapter_context(self, model_id: str):
        if model_id == self.base_model_id:
            return self.model.disable_adapter()
        if model_id == self.model_id:
            return nullcontext()
        raise ValueError("requested model is not loaded")

    def _encode(self, messages: list[dict[str, str]]):
        encoded = self.tokenizer.apply_chat_template(
            messages, tokenize=True, add_generation_prompt=True, enable_thinking=False,
            return_tensors="pt", return_dict=True)
        encoded = {key: value.to(self.device) for key, value in encoded.items()
                   if isinstance(value, self.torch.Tensor)}
        prompt_tokens = int(encoded["input_ids"].shape[1])
        return encoded, prompt_tokens

    def _generation_args(self, encoded, temperature: float, max_new_tokens: int) -> dict[str, Any]:
        args: dict[str, Any] = {**encoded, "max_new_tokens": max_new_tokens, "do_sample": temperature > 0,
                                "use_cache": True, "pad_token_id": self.tokenizer.pad_token_id,
                                "eos_token_id": self.tokenizer.eos_token_id}
        if temperature > 0:
            args["temperature"] = temperature
        return args

    def complete(self, messages, temperature, max_new_tokens, model_id=None):
        model_id = model_id or self.model_id
        encoded, prompt_tokens = self._encode(messages)
        with self._adapter_context(model_id):
            with self.torch.inference_mode():
                with self.torch.autocast(device_type="cuda", dtype=next(self.model.parameters()).dtype):
                    sequences = self.model.generate(**self._generation_args(encoded, temperature, max_new_tokens))
        prompt_length = encoded["input_ids"].shape[1]
        generated = sequences[0, prompt_length:]
        text = self.tokenizer.decode(generated, skip_special_tokens=True).strip()
        completion_tokens = int(generated.numel())
        return {"text": text, "prompt_tokens": prompt_tokens, "completion_tokens": completion_tokens}

    def count_prompt_tokens(self, messages) -> int:
        _, prompt_tokens = self._encode(messages)
        return prompt_tokens

    def count_text_tokens(self, text: str) -> int:
        return int(len(self.tokenizer.encode(text, add_special_tokens=False)))

    def stream(self, messages, temperature, max_new_tokens, model_id=None) -> Iterator[str]:
        from transformers import StoppingCriteria, StoppingCriteriaList, TextIteratorStreamer

        model_id = model_id or self.model_id
        encoded, _ = self._encode(messages)
        streamer = TextIteratorStreamer(self.tokenizer, skip_prompt=True, skip_special_tokens=True,
                                        clean_up_tokenization_spaces=False, timeout=180)
        cancelled = threading.Event()
        failure: list[BaseException] = []

        class StopOnDisconnect(StoppingCriteria):
            def __call__(self, input_ids, scores, **kwargs):
                return cancelled.is_set()

        args = self._generation_args(encoded, temperature, max_new_tokens)
        args["streamer"] = streamer
        args["stopping_criteria"] = StoppingCriteriaList([StopOnDisconnect()])

        def generate():
            try:
                with self._adapter_context(model_id):
                    with self.torch.inference_mode():
                        with self.torch.autocast(device_type="cuda", dtype=next(self.model.parameters()).dtype):
                            self.model.generate(**args)
            except BaseException as exc:
                failure.append(exc)
                streamer.on_finalized_text("", stream_end=True)

        worker = threading.Thread(target=generate, name="qwen35-generation", daemon=True)
        worker.start()
        try:
            for piece in streamer:
                if piece:
                    yield piece
            worker.join(timeout=5)
            if failure:
                raise RuntimeError("model generation failed") from failure[0]
        finally:
            if worker.is_alive():
                cancelled.set()
                worker.join(timeout=5)

    def health(self) -> dict[str, str]:
        return {"status": "UP", "model": self.model_id, "models": list(self.model_ids),
                "device": self.device, "gpu": self.gpu_name}


class InferenceHTTPServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address, handler, *, settings: Settings, runtime):
        self.settings = settings
        self.runtime = runtime
        self.generation_lock = threading.Lock()
        super().__init__(address, handler)


class InferenceHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        # Never let the base handler print headers, bodies or caller-supplied error text.
        LOG.info("http request from %s", self.client_address[0])

    def _json(self, status: int, payload: dict[str, Any]):
        body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)
        self.close_connection = True

    def _error(self, status: int, message: str, error_type: str = "invalid_request_error"):
        self._json(status, {"error": {"message": message, "type": error_type}})

    def _authorized(self) -> bool:
        header = self.headers.get("Authorization", "")
        if not header.startswith("Bearer "):
            return False
        return hmac.compare_digest(header[7:].strip().encode(), self.server.settings.api_key.encode())

    def do_GET(self):
        if self.path == "/health":
            self._json(200, self.server.runtime.health())
            return
        if self.path != "/v1/models":
            self._error(404, "endpoint not found", "not_found_error")
            return
        if not self._authorized():
            self._error(401, "authentication required", "authentication_error")
            return
        models = [{"id": model, "object": "model", "owned_by": "local"}
                  for model in self.server.settings.model_ids]
        self._json(200, {"object": "list", "data": models})

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self._error(404, "endpoint not found", "not_found_error")
            return
        if not self._authorized():
            self._error(401, "authentication required", "authentication_error")
            return
        try:
            length = int(self.headers.get("Content-Length", "-1"))
        except ValueError:
            self._error(400, "invalid content length")
            return
        if length < 1 or length > self.server.settings.max_request_bytes:
            self._error(413, "request body exceeds the configured limit", "request_too_large")
            return
        try:
            raw = self.rfile.read(length)
            payload = json.loads(raw.decode("utf-8"))
            request = normalize_completion_request(payload, self.server.settings)
        except LookupError:
            self._error(404, "model not found", "not_found_error")
            return
        except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as exc:
            self._error(400, str(exc))
            return
        if not self.server.generation_lock.acquire(blocking=False):
            self._error(503, "inference service is busy", "server_error")
            return
        try:
            try:
                prompt_tokens = self.server.runtime.count_prompt_tokens(request["messages"])
            except Exception as exc:
                LOG.error("prompt tokenization failed (%s)", type(exc).__name__)
                self._error(503, "model tokenizer is unavailable", "server_error")
                return
            if prompt_tokens < 1 or prompt_tokens > self.server.settings.max_input_tokens:
                self._error(413, "prompt exceeds the configured token limit", "request_too_large")
                return
            request["prompt_tokens"] = prompt_tokens
            if request["stream"]:
                self._stream(request)
            else:
                self._complete(request)
        except (BrokenPipeError, ConnectionResetError):
            LOG.info("client disconnected during generation")
        except Exception as exc:
            LOG.error("inference failed (%s)", type(exc).__name__)
            if not self.headers.get("Connection", "").lower() == "close":
                try:
                    self._error(503, "model inference failed", "server_error")
                except (BrokenPipeError, ConnectionResetError):
                    pass
        finally:
            self.server.generation_lock.release()

    def _complete(self, request):
        result = self.server.runtime.complete(request["messages"], request["temperature"],
                                             request["max_new_tokens"], request["model"])
        created = int(time.time())
        self._json(200, {"id": "chatcmpl-" + uuid.uuid4().hex, "object": "chat.completion", "created": created,
                         "model": request["model"],
                         "choices": [{"index": 0, "message": {"role": "assistant", "content": result["text"]},
                                      "finish_reason": "stop"}],
                         "usage": {"prompt_tokens": result["prompt_tokens"],
                                   "completion_tokens": result["completion_tokens"],
                                   "total_tokens": result["prompt_tokens"] + result["completion_tokens"]}})

    def _stream(self, request):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache, no-transform")
        self.send_header("Connection", "close")
        self.send_header("X-Accel-Buffering", "no")
        self.end_headers()
        self.close_connection = True
        completion_id = "chatcmpl-" + uuid.uuid4().hex
        created = int(time.time())
        completion_text = []
        try:
            for text in self.server.runtime.stream(request["messages"], request["temperature"],
                                                   request["max_new_tokens"], request["model"]):
                completion_text.append(text)
                self._sse({"id": completion_id, "object": "chat.completion.chunk", "created": created,
                           "model": request["model"],
                           "choices": [{"index": 0, "delta": {"content": text}, "finish_reason": None}]})
        except Exception as exc:
            LOG.error("stream inference failed (%s)", type(exc).__name__)
            self._sse({"error": {"message": "model inference failed", "type": "server_error"}})
            self._sse_done()
            return
        self._sse({"id": completion_id, "object": "chat.completion.chunk", "created": created,
                   "model": request["model"],
                   "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}]})
        if request["include_usage"]:
            completion_tokens = self.server.runtime.count_text_tokens("".join(completion_text))
            self._sse({"id": completion_id, "object": "chat.completion.chunk", "created": created,
                       "model": request["model"], "choices": [],
                       "usage": {"prompt_tokens": request["prompt_tokens"], "completion_tokens": completion_tokens,
                                 "total_tokens": request["prompt_tokens"] + completion_tokens}})
        self._sse_done()

    def _sse(self, payload):
        self.wfile.write(b"data: " + json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8") + b"\n\n")
        self.wfile.flush()

    def _sse_done(self):
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()


def main():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    settings = Settings.from_env()
    runtime = TransformersRuntime.load(settings)
    server = InferenceHTTPServer((settings.host, settings.port), InferenceHandler,
                                 settings=settings, runtime=runtime)
    LOG.info("Qwen3.5 LoRA inference ready on port %d", settings.port)
    try:
        server.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
