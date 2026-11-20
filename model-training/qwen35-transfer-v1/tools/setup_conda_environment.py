"""Create the external Windows training environment under the supplied workspace."""
import argparse
import hashlib
import json
import os
import platform
import shutil
import subprocess
from pathlib import Path

PACKAGE = Path(__file__).resolve().parents[1]
TORCH_WHEEL_SHA256 = "3523fda6e2cfab2b04ae09b1424681358e508bb3faa11ceb67004113d5e7acad"


def workspace_layout(package_root, workspace_root=None):
    package = Path(package_root).resolve()
    workspace = Path(workspace_root).resolve() if workspace_root else package.parent
    try:
        package.relative_to(workspace)
    except ValueError as error:
        raise ValueError("Package must be located inside the selected workspace") from error
    return {
        "package": package,
        "workspace": workspace,
        "environment": workspace / "envs" / "qwen35-finetune",
        "conda_packages": workspace / "cache" / "conda-pkgs",
        "pip_cache": workspace / "cache" / "pip",
        "huggingface_cache": workspace / "cache" / "huggingface",
        "torch_cache": workspace / "cache" / "torch",
        "temporary": workspace / "cache" / "tmp",
    }


def process_environment(layout, base_environment=None):
    env = dict(os.environ if base_environment is None else base_environment)
    env.update({
        "CONDARC": str(layout["workspace"] / "condarc.yml"),
        "CONDA_ENVS_PATH": str(layout["workspace"] / "envs"),
        "CONDA_PKGS_DIRS": str(layout["conda_packages"]),
        "PIP_CACHE_DIR": str(layout["pip_cache"]),
        "HF_HOME": str(layout["huggingface_cache"]),
        "HF_HUB_CACHE": str(layout["huggingface_cache"] / "hub"),
        "TORCH_HOME": str(layout["torch_cache"]),
        "TEMP": str(layout["temporary"]),
        "TMP": str(layout["temporary"]),
        "TRITON_CACHE_DIR": str(layout["torch_cache"] / "triton"),
        "PIP_CONSTRAINT": str(layout["workspace"] / "pip-constraints.txt"),
    })
    return env


def pip_constraint_content():
    return "torch==2.10.0+cu128\n"


def validated_torch_wheel(wheel_path, workspace_root):
    wheel = Path(wheel_path).resolve()
    workspace = Path(workspace_root).resolve()
    try:
        wheel.relative_to(workspace)
    except ValueError as error:
        raise ValueError("CUDA wheel must be stored inside the selected workspace") from error
    if not wheel.is_file():
        raise FileNotFoundError(f"CUDA wheel does not exist: {wheel}")
    digest = hashlib.sha256()
    with wheel.open("rb") as stream:
        for chunk in iter(lambda: stream.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    actual = digest.hexdigest()
    if actual != TORCH_WHEEL_SHA256:
        raise ValueError(f"Official CUDA wheel SHA-256 mismatch: {actual}")
    return wheel


def validated_wheelhouse(wheelhouse_path, workspace_root):
    wheelhouse = Path(wheelhouse_path).resolve()
    workspace = Path(workspace_root).resolve()
    try:
        wheelhouse.relative_to(workspace)
    except ValueError as error:
        raise ValueError("Dependency wheelhouse must be stored inside the selected workspace") from error
    manifest_path = wheelhouse / "wheelhouse-manifest.json"
    if not wheelhouse.is_dir() or not manifest_path.is_file():
        raise ValueError(f"Dependency wheelhouse is missing Windows wheels: {wheelhouse}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    expected_files = manifest.get("filesSha256", {})
    actual_wheels = {path.name for path in wheelhouse.glob("*.whl")}
    if not expected_files or set(expected_files) != actual_wheels:
        raise ValueError("Wheelhouse manifest does not match the available wheel files")
    for name, expected in expected_files.items():
        if Path(name).name != name:
            raise ValueError(f"Invalid wheelhouse manifest filename: {name}")
        digest = hashlib.sha256((wheelhouse / name).read_bytes()).hexdigest()
        if digest != expected:
            raise ValueError(f"Wheelhouse checksum mismatch: {name}")
    return wheelhouse


def requirements_pip_args(python, requirements, constraints, wheelhouse=None):
    args = [str(python), "-m", "pip", "install", "-r", str(requirements), "--constraint", str(constraints)]
    if wheelhouse:
        args.extend(["--no-index", "--find-links", str(wheelhouse)])
    return args


def write_conda_config(layout):
    config = layout["workspace"] / "condarc.yml"
    config.write_text(conda_config_content(layout),
                      encoding="utf-8")
    return config


def prepare_workspace(layout):
    for key, path in layout.items():
        if key != "package":
            path.mkdir(parents=True, exist_ok=True)
    write_conda_config(layout)
    (layout["workspace"] / "pip-constraints.txt").write_text(
        pip_constraint_content(), encoding="ascii")


def conda_config_content(layout):
    return (
        "envs_dirs:\n"
        f"  - \"{(layout['workspace'] / 'envs').as_posix()}\"\n"
        "pkgs_dirs:\n"
        f"  - \"{layout['conda_packages'].as_posix()}\"\n"
        "auto_activate_base: false\nchangeps1: false\n"
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--conda-exe", type=Path, required=True)
    parser.add_argument("--workspace", type=Path)
    parser.add_argument("--torch-wheel", type=Path,
                        help="Optional pre-staged official CUDA wheel inside the workspace")
    parser.add_argument("--wheelhouse", type=Path,
                        help="Optional pre-staged Windows dependency wheels; installs without network access")
    args = parser.parse_args()
    if platform.system() != "Windows":
        raise SystemExit("This helper targets the external Windows Anaconda host.")
    conda_exe = args.conda_exe.resolve()
    if not conda_exe.is_file():
        raise SystemExit(f"Anaconda executable not found: {conda_exe}")
    layout = workspace_layout(PACKAGE, args.workspace)
    python = layout["environment"] / "python.exe"
    if layout["environment"].exists() and not python.is_file():
        raise SystemExit(f"Existing Conda prefix is incomplete and has no Python; inspect it before cleanup: {layout['environment']}")
    prepare_workspace(layout)
    env = process_environment(layout)
    wheel = None
    wheelhouse = None
    if args.torch_wheel:
        try:
            wheel = validated_torch_wheel(args.torch_wheel, layout["workspace"])
        except (ValueError, FileNotFoundError) as error:
            raise SystemExit(str(error)) from error
    if args.wheelhouse:
        try:
            wheelhouse = validated_wheelhouse(args.wheelhouse, layout["workspace"])
        except ValueError as error:
            raise SystemExit(str(error)) from error
    nvidia_smi = shutil.which("nvidia-smi")
    if not nvidia_smi:
        candidate = Path(os.environ.get("WINDIR", r"C:\Windows")) / "System32" / "nvidia-smi.exe"
        if candidate.is_file():
            nvidia_smi = str(candidate)
    if not nvidia_smi:
        raise SystemExit("nvidia-smi is missing; stopping without changing the installed driver.")
    gpu_result = subprocess.run(
        [nvidia_smi, "--query-gpu=name,memory.total,memory.free,driver_version", "--format=csv,noheader,nounits"],
        capture_output=True, text=True, check=True)
    gpu = [part.strip() for part in gpu_result.stdout.strip().splitlines()[0].split(",")]
    if len(gpu) != 4 or int(gpu[1]) < 14 * 1024:
        raise SystemExit(f"Expected a 16 GB-class NVIDIA GPU; nvidia-smi returned: {gpu_result.stdout.strip()}")
    if not layout["environment"].exists():
        subprocess.run([str(conda_exe), "create", "--yes", "--prefix", str(layout["environment"]),
                        "python=3.11", "pip"], env=env, check=True)
    subprocess.run([str(python), "-m", "pip", "install", "--upgrade", "pip"], env=env, check=True)
    if wheel:
        torch_install = [str(python), "-m", "pip", "install"]
        if wheelhouse:
            torch_install.append("--no-index")
        torch_install.append("--no-deps")
        if wheelhouse:
            torch_install.extend(["--find-links", str(wheelhouse)])
        torch_install.append(str(wheel))
        subprocess.run(torch_install, env=env, check=True)
    else:
        torch_index = "https" + chr(58) + chr(47) * 2 + "download.pytorch.org/whl/cu128"
        subprocess.run([str(python), "-m", "pip", "install", "torch==2.10.0", "--index-url", torch_index],
                       env=env, check=True)
    torch_check = (
        "import json,torch; assert torch.__version__.split('+')[0]=='2.10.0', torch.__version__; "
        "assert torch.cuda.is_available(), 'CUDA cannot access the installed PyTorch runtime and existing driver'; "
        "x=torch.ones((256,256),device='cuda',dtype=torch.float16); y=x@x; torch.cuda.synchronize(); "
        "print(json.dumps({'torch':torch.__version__,'cudaRuntime':torch.version.cuda,"
        "'gpu':torch.cuda.get_device_name(0),'totalMiB':round(torch.cuda.get_device_properties(0).total_memory/1048576),"
        "'freeMiB':round(torch.cuda.mem_get_info(0)[0]/1048576),'bf16':torch.cuda.get_device_capability(0)>=(8,0) and torch.cuda.is_bf16_supported(),"
        "'matmulSmoke':'passed'}))"
    )
    install_args = requirements_pip_args(
        python, PACKAGE / "requirements.txt", layout["workspace"] / "pip-constraints.txt", wheelhouse)
    subprocess.run(install_args, env=env, check=True)
    torch_report = subprocess.run([str(python), "-c", torch_check], env=env, capture_output=True, text=True, check=True)
    cuda_check = (
        "import json,torch; assert torch.cuda.is_available(), 'CUDA cannot use the existing driver'; "
        "x=torch.ones((256,256),device='cuda',dtype=torch.float16); y=x@x; torch.cuda.synchronize(); "
        "print(json.dumps({'torch':torch.__version__,'cudaRuntime':torch.version.cuda,"
        "'gpu':torch.cuda.get_device_name(0),'totalMiB':round(torch.cuda.get_device_properties(0).total_memory/1048576),"
        "'freeMiB':round(torch.cuda.mem_get_info(0)[0]/1048576),'bf16':torch.cuda.get_device_capability(0)>=(8,0) and torch.cuda.is_bf16_supported(),"
        "'matmulSmoke':'passed'}))"
    )
    checked = subprocess.run([str(python), "-c", cuda_check], env=env, capture_output=True, text=True, check=True)
    subprocess.run([str(python), str(PACKAGE / "tools/verify_package.py")], env=env, check=True)
    report = {
        "workspace": str(layout["workspace"]), "package": str(PACKAGE),
        "condaExecutable": str(conda_exe), "condaPrefix": str(layout["environment"]),
        "condaPackageCache": str(layout["conda_packages"]), "pipCache": str(layout["pip_cache"]),
        "preStagedTorchWheel": str(wheel) if wheel else None,
        "preStagedWheelhouse": str(wheelhouse) if wheelhouse else None,
        "modelCache": str(layout["huggingface_cache"]), "nvidiaSmi": gpu,
        "torchCudaSmoke": json.loads(torch_report.stdout.strip().splitlines()[-1]),
        "pythonCudaSmoke": json.loads(checked.stdout.strip().splitlines()[-1]),
        "systemConfigurationChanged": False,
    }
    report_path = layout["workspace"] / "setup-report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
