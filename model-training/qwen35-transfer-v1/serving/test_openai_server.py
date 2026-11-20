import json
import sys
import threading
import unittest
from contextlib import contextmanager
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

sys.path.insert(0, str(Path(__file__).resolve().parent))
from openai_server import (InferenceHandler, InferenceHTTPServer, Settings,
                           PINNED_MODEL_REPO, PINNED_MODEL_REVISION, verify_model_layout)


class FakeRuntime:
    def __init__(self):
        self.calls = []

    def complete(self, messages, temperature, max_new_tokens, model_id=None):
        self.calls.append((messages, temperature, max_new_tokens, False, model_id))
        return {"text": '{"answer":"课程答案","usedIds":["T1"]}',
                "prompt_tokens": 8, "completion_tokens": 9}

    def stream(self, messages, temperature, max_new_tokens, model_id=None):
        self.calls.append((messages, temperature, max_new_tokens, True, model_id))
        yield "课程"
        yield "答案"

    def health(self):
        return {"status": "UP", "model": "qwen35-ranked-lora", "models": ["qwen35-base", "qwen35-ranked-lora"], "device": "cuda:0"}

    def count_prompt_tokens(self, messages):
        return sum(len(message["content"]) for message in messages)

    def count_text_tokens(self, text):
        return len(text)


class InferenceHttpContractTests(unittest.TestCase):
    def setUp(self):
        self.settings = Settings(
            api_key="test-secret", model_id="qwen35-ranked-lora", host="127.0.0.1", port=8998,
            base_model_id="qwen35-base",
            max_request_bytes=4096, max_messages=8, max_input_tokens=128, max_new_tokens=32,
        )
        self.runtime = FakeRuntime()
        self.server = InferenceHTTPServer(("127.0.0.1", 0), InferenceHandler,
                                          settings=self.settings, runtime=self.runtime)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base_url = f"http://127.0.0.1:{self.server.server_port}"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)

    def request(self, path, payload=None, key="test-secret"):
        data = None if payload is None else json.dumps(payload).encode("utf-8")
        headers = {}
        if key is not None:
            headers["Authorization"] = f"Bearer {key}"
        if data is not None:
            headers["Content-Type"] = "application/json"
        request = Request(self.base_url + path, data=data, headers=headers)
        return urlopen(request, timeout=3)

    def payload(self, **updates):
        value = {"model": "qwen35-ranked-lora", "messages": [
            {"role": "system", "content": "只输出 JSON"},
            {"role": "user", "content": '{"question":"问题"}'},
        ], "temperature": 0, "max_tokens": 16}
        value.update(updates)
        return value

    def test_health_exposes_status_without_paths_or_credentials(self):
        result = json.loads(self.request("/health", key=None).read())
        self.assertEqual(result, {"status": "UP", "model": "qwen35-ranked-lora",
                                  "models": ["qwen35-base", "qwen35-ranked-lora"], "device": "cuda:0"})
        self.assertNotIn("test-secret", json.dumps(result))

    def test_models_endpoint_requires_bearer_key(self):
        with self.assertRaises(HTTPError) as raised:
            self.request("/v1/models", key=None)
        self.assertEqual(raised.exception.code, 401)
        result = json.loads(self.request("/v1/models").read())
        self.assertEqual([item["id"] for item in result["data"]], ["qwen35-base", "qwen35-ranked-lora"])

    def test_chat_completion_returns_openai_shape_and_usage(self):
        response = json.loads(self.request("/v1/chat/completions", self.payload()).read())
        self.assertEqual(response["object"], "chat.completion")
        self.assertEqual(response["choices"][0]["message"]["content"],
                         '{"answer":"课程答案","usedIds":["T1"]}')
        self.assertEqual(response["model"], "qwen35-ranked-lora")
        self.assertEqual(response["usage"], {"prompt_tokens": 8, "completion_tokens": 9, "total_tokens": 17})
        self.assertEqual(self.runtime.calls[0][2], 16)
        self.assertEqual(self.runtime.calls[0][4], "qwen35-ranked-lora")

    def test_base_and_lora_model_ids_reach_the_same_runtime_as_distinct_variants(self):
        base = json.loads(self.request("/v1/chat/completions", self.payload(model="qwen35-base")).read())
        lora = json.loads(self.request("/v1/chat/completions", self.payload(model="qwen35-ranked-lora")).read())

        self.assertEqual(base["model"], "qwen35-base")
        self.assertEqual(lora["model"], "qwen35-ranked-lora")
        self.assertEqual([call[4] for call in self.runtime.calls], ["qwen35-base", "qwen35-ranked-lora"])

    def test_stream_uses_sse_chunks_and_done_sentinel(self):
        response = self.request("/v1/chat/completions", self.payload(stream=True))
        body = response.read().decode("utf-8")
        self.assertIn('"object":"chat.completion.chunk"', body)
        self.assertIn('"model":"qwen35-ranked-lora"', body)
        self.assertIn('"content":"课程"', body)
        self.assertIn('"content":"答案"', body)
        self.assertTrue(body.endswith("data: [DONE]\n\n"))
        self.assertEqual(self.runtime.calls[0][4], "qwen35-ranked-lora")

    def test_base_model_stream_reports_and_uses_base_model_id(self):
        response = self.request("/v1/chat/completions", self.payload(model="qwen35-base", stream=True))
        body = response.read().decode("utf-8")

        self.assertIn('"model":"qwen35-base"', body)
        self.assertEqual(self.runtime.calls[0][4], "qwen35-base")

    def test_unknown_model_is_rejected(self):
        with self.assertRaises(HTTPError) as raised:
            self.request("/v1/chat/completions", self.payload(model="other-model"))
        self.assertEqual(raised.exception.code, 404)

    def test_invalid_roles_and_generation_limits_are_rejected_before_inference(self):
        with self.assertRaises(HTTPError) as role_error:
            self.request("/v1/chat/completions", self.payload(messages=[{"role": "tool", "content": "x"}]))
        self.assertEqual(role_error.exception.code, 400)
        with self.assertRaises(HTTPError) as token_error:
            self.request("/v1/chat/completions", self.payload(max_tokens=33))
        self.assertEqual(token_error.exception.code, 400)
        self.assertEqual(self.runtime.calls, [])

    def test_bad_authorization_never_calls_model(self):
        with self.assertRaises(HTTPError) as raised:
            self.request("/v1/chat/completions", self.payload(), key="wrong")
        self.assertEqual(raised.exception.code, 401)
        self.assertEqual(self.runtime.calls, [])

    def test_tokenizer_input_limit_is_enforced_before_model_generation(self):
        payload = self.payload(messages=[{"role": "user", "content": "x" * 129}])
        with self.assertRaises(HTTPError) as raised:
            self.request("/v1/chat/completions", payload)
        self.assertEqual(raised.exception.code, 413)
        self.assertEqual(self.runtime.calls, [])


class SettingsTests(unittest.TestCase):
    def test_config_file_is_loaded_then_process_environment_overrides_it(self):
        import tempfile

        with tempfile.TemporaryDirectory() as temporary:
            config = Path(temporary) / "qwen35.env"
            config.write_text("QWEN35_API_KEY=file-key\nQWEN35_PORT=8998\n", encoding="ascii")
            settings = Settings.from_env({"QWEN35_CONFIG_FILE": str(config), "QWEN35_PORT": "8999"})
        self.assertEqual(settings.api_key, "file-key")
        self.assertEqual(settings.port, 8999)
        self.assertEqual(settings.model_ids, ("qwen35-base", "qwen35-ranked-lora"))

    def test_base_and_adapter_model_names_must_differ(self):
        with self.assertRaisesRegex(ValueError, "differ"):
            Settings(api_key="test-key", model_id="same", base_model_id="same")


class RuntimeAdapterSelectionTests(unittest.TestCase):
    class AdapterSpy:
        def __init__(self):
            self.states = []

        @contextmanager
        def disable_adapter(self):
            self.states.append("base-enter")
            try:
                yield
            finally:
                self.states.append("base-exit")

    def test_context_disables_adapter_only_for_base_model(self):
        model = self.AdapterSpy()
        from openai_server import TransformersRuntime

        runtime = TransformersRuntime(model, None, None, "cpu", "qwen35-ranked-lora", "qwen35-base")
        with runtime._adapter_context("qwen35-base"):
            self.assertEqual(model.states, ["base-enter"])
        self.assertEqual(model.states, ["base-enter", "base-exit"])

        with runtime._adapter_context("qwen35-ranked-lora"):
            self.assertEqual(model.states, ["base-enter", "base-exit"])

        with self.assertRaisesRegex(ValueError, "not loaded"):
            runtime._adapter_context("unapproved-model")

    def test_default_workspace_config_is_discovered_without_env_inheritance(self):
        from unittest.mock import patch

        with patch("openai_server.Path.with_name", return_value=Path("C:/workspace/qwen35.env")), \
             patch("openai_server.Path.is_file", return_value=True), \
             patch("openai_server.read_env_file", return_value={"QWEN35_API_KEY": "workspace-key"}):
            settings = Settings.from_env({})
        self.assertEqual(settings.api_key, "workspace-key")


class PinnedModelValidationTests(unittest.TestCase):
    def make_layout(self, temporary):
        import hashlib
        import tempfile

        package = Path(temporary) / "package"
        base = package / "base-model"
        adapter = Path(temporary) / "adapter"
        provenance = package / "provenance"
        base.mkdir(parents=True)
        adapter.mkdir()
        provenance.mkdir()
        base_files = {
            "config.json": json.dumps({
                "model_type": "qwen3_5",
                "architectures": ["Qwen3_5ForConditionalGeneration"],
            }).encode(),
            "model.safetensors": b"pinned base weights",
            "tokenizer.json": b"pinned tokenizer",
        }
        file_manifest = {}
        for name, data in base_files.items():
            (base / name).write_bytes(data)
            file_manifest[name] = {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
        (provenance / "base-model-verification.json").write_text(json.dumps({
            "repoId": PINNED_MODEL_REPO,
            "revision": PINNED_MODEL_REVISION,
            "files": file_manifest,
        }), encoding="utf-8")
        (adapter / "adapter_config.json").write_text(json.dumps({
            "base_model_name_or_path": PINNED_MODEL_REPO,
            "revision": PINNED_MODEL_REVISION,
        }), encoding="utf-8")
        (adapter / "adapter_model.safetensors").write_bytes(b"trained adapter")
        return base, adapter

    def test_accepts_only_hash_verified_pinned_base_and_adapter(self):
        import tempfile

        with tempfile.TemporaryDirectory() as temporary:
            base, adapter = self.make_layout(temporary)
            verify_model_layout(base, adapter)

    def test_rejects_adapter_from_a_different_base_revision(self):
        import tempfile

        with tempfile.TemporaryDirectory() as temporary:
            base, adapter = self.make_layout(temporary)
            config = json.loads((adapter / "adapter_config.json").read_text(encoding="utf-8"))
            config["revision"] = "different-revision"
            (adapter / "adapter_config.json").write_text(json.dumps(config), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "pinned base-model revision"):
                verify_model_layout(base, adapter)

    def test_rejects_base_weight_checksum_mismatch(self):
        import tempfile

        with tempfile.TemporaryDirectory() as temporary:
            base, adapter = self.make_layout(temporary)
            (base / "model.safetensors").write_bytes(b"changed base weights")
            with self.assertRaisesRegex(RuntimeError, "checksum mismatch"):
                verify_model_layout(base, adapter)

    def test_rejects_mismatched_provenance_revision(self):
        import tempfile

        with tempfile.TemporaryDirectory() as temporary:
            base, adapter = self.make_layout(temporary)
            manifest_path = base.parent / "provenance" / "base-model-verification.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["revision"] = "different-revision"
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "pinned official model"):
                verify_model_layout(base, adapter)


if __name__ == "__main__":
    unittest.main()
