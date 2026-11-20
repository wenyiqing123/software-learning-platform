"""Create and install an isolated Linux Python 3.11/CUDA training environment."""
import platform
import subprocess
import sys
import venv
from pathlib import Path

PACKAGE = Path(__file__).resolve().parents[1]
VENV = PACKAGE / ".venv"


def main():
    system = platform.system()
    if system not in {"Linux", "Windows"}:
        raise SystemExit("The external training environment targets Linux/Windows + NVIDIA CUDA.")
    if sys.version_info[:2] != (3, 11):
        raise SystemExit(f"Run with Python 3.11, got {platform.python_version()}.")
    if subprocess.run(["nvidia-smi"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
        raise SystemExit("NVIDIA driver/GPU not detected. Install a compatible driver before setup.")
    if not VENV.exists():
        venv.EnvBuilder(with_pip=True).create(VENV)
    python = VENV / ("Scripts/python.exe" if system == "Windows" else "bin/python")
    subprocess.run([str(python), "-m", "pip", "install", "--upgrade", "pip"], check=True)
    subprocess.run([str(python), "-m", "pip", "install", "torch==2.10.0", "--index-url",
                    "https://download.pytorch.org/whl/cu128"], check=True)
    subprocess.run([str(python), "-m", "pip", "install", "-r", str(PACKAGE / "requirements.txt")], check=True)
    check = "import torch,transformers,peft; assert torch.cuda.is_available(); print('torch',torch.__version__,'cuda',torch.version.cuda,'transformers',transformers.__version__,'peft',peft.__version__,'gpu',torch.cuda.get_device_name(0))"
    subprocess.run([str(python), "-c", check], check=True)
    print(f"Environment ready: {python}")


if __name__ == "__main__":
    main()
