"""Create a portable ZIP64 archive without environments, caches, or training outputs."""
import hashlib
import json
import os
import tempfile
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from verify_package import verify

PACKAGE = Path(__file__).resolve().parents[1]
ARCHIVE = PACKAGE.parent / "qwen35-transfer-v1-offline.zip"
SKIP_DIRS = {".venv", "__pycache__", ".cache", "output"}


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(4 * 1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--refresh-existing", action="store_true",
                        help="Replace only an earlier archive whose adjacent SHA-256 file still verifies")
    args = parser.parse_args()
    report = verify()
    checksum_path = ARCHIVE.with_suffix(ARCHIVE.suffix + ".sha256")
    if ARCHIVE.exists():
        if not args.refresh_existing or not checksum_path.is_file():
            raise SystemExit(f"Archive exists; pass --refresh-existing only for a previously verified package: {ARCHIVE}")
        expected = checksum_path.read_text(encoding="ascii").split()[0]
        if digest(ARCHIVE) != expected:
            raise SystemExit("Existing archive SHA-256 does not match its sidecar; preserving both files")
    files = []
    for path in PACKAGE.rglob("*"):
        relative = path.relative_to(PACKAGE)
        if any(part in SKIP_DIRS for part in relative.parts) or path.suffix == ".pyc" or not path.is_file():
            continue
        files.append(path)
    if not (PACKAGE / "base-model/model.safetensors-00001-of-00001.safetensors").is_file():
        raise SystemExit("Verified Safetensors base weights are missing")
    descriptor, pending_name = tempfile.mkstemp(prefix="qwen35-transfer-", suffix=".zip.pending", dir=ARCHIVE.parent)
    os.close(descriptor)
    pending = Path(pending_name)
    try:
        with zipfile.ZipFile(pending, "w", compression=zipfile.ZIP_STORED, allowZip64=True) as archive:
            for path in sorted(files):
                archive.write(path, path.relative_to(PACKAGE).as_posix())
        with zipfile.ZipFile(pending) as archive:
            if archive.testzip() is not None:
                raise RuntimeError("ZIP CRC verification failed")
        archive_hash = digest(pending)
        os.replace(pending, ARCHIVE)
        side_descriptor, side_name = tempfile.mkstemp(prefix="qwen35-transfer-", suffix=".sha256.pending", dir=ARCHIVE.parent)
        os.close(side_descriptor)
        side_pending = Path(side_name)
        try:
            side_pending.write_text(f"{archive_hash}  {ARCHIVE.name}\n", encoding="ascii")
            os.replace(side_pending, checksum_path)
        finally:
            side_pending.unlink(missing_ok=True)
    finally:
        pending.unlink(missing_ok=True)
    print(json.dumps({"archive": str(ARCHIVE), "bytes": ARCHIVE.stat().st_size,
                      "sha256": archive_hash, "files": len(files), "checksum": str(checksum_path),
                      "verifiedFiles": report["verifiedFiles"]}, indent=2))


if __name__ == "__main__":
    main()
