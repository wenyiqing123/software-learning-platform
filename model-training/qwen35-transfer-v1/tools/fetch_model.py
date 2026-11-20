"""Fetch a revision-pinned Qwen3.5 Transformers snapshot and verify model blobs."""
import hashlib
import json
from pathlib import Path

from huggingface_hub import snapshot_download

PACKAGE = Path(__file__).resolve().parents[1]
MODEL_DIR = PACKAGE / "base-model"
REPO_ID = "Qwen/Qwen3.5-0.8B"
REVISION = "2fc06364715b967f1860aea9cf38778875588b17"
EXPECTED = {
    "model.safetensors-00001-of-00001.safetensors": (1746942600, "04b1c301231dd422b8860db31311ab2721511346a32cb1e079c4c4e5f1fe4696"),
    "tokenizer.json": (12807982, "5f9e4d4901a92b997e463c1f46055088b6cca5ca61a6522d1b9f64c4bb81cb42"),
}
ALLOW_PATTERNS = ["LICENSE", "README.md", "chat_template.jinja", "config.json", "merges.txt",
                  "model.safetensors-00001-of-00001.safetensors", "model.safetensors.index.json",
                  "preprocessor_config.json", "tokenizer.json", "tokenizer_config.json",
                  "video_preprocessor_config.json", "vocab.json"]


def digest(path):
    hasher = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(4 * 1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def main():
    marker = MODEL_DIR / ".qwen35-transfer-download"
    if MODEL_DIR.exists() and any(MODEL_DIR.iterdir()) and not marker.is_file():
        raise SystemExit(f"Refusing to overwrite non-empty model directory without this downloader's marker: {MODEL_DIR}")
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    marker.write_text(REVISION + "\n", encoding="ascii")
    snapshot_download(repo_id=REPO_ID, revision=REVISION, local_dir=MODEL_DIR,
                      allow_patterns=ALLOW_PATTERNS, max_workers=2)
    files = {}
    for name in ALLOW_PATTERNS:
        path = MODEL_DIR / name
        if not path.is_file():
            raise RuntimeError(f"Missing model file: {name}")
        record = {"bytes": path.stat().st_size, "sha256": digest(path)}
        if name in EXPECTED and record != {"bytes": EXPECTED[name][0], "sha256": EXPECTED[name][1]}:
            raise RuntimeError(f"Model file integrity mismatch: {name}: {record}")
        files[name] = record
    verification = PACKAGE / "provenance" / "base-model-verification.json"
    verification.parent.mkdir(parents=True, exist_ok=True)
    verification.write_text(json.dumps({"repoId": REPO_ID, "revision": REVISION, "files": files},
                                       sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"modelDir": str(MODEL_DIR), "revision": REVISION, "verifiedFiles": len(files),
                      "safetensorsBytes": files["model.safetensors-00001-of-00001.safetensors"]["bytes"]}, indent=2))
    marker.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
