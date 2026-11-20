import sys
import tempfile
import unittest
import hashlib
import json
from pathlib import Path

PACKAGE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(PACKAGE / "tools"))

from training_profile import (
    DEFAULT_BATCH_SIZE,
    DEFAULT_BATCH_CANDIDATES,
    DEFAULT_EFFECTIVE_BATCH_SIZE,
    DEFAULT_GRADIENT_ACCUMULATION,
    DEFAULT_MAX_SEQ_LEN,
    MIN_FREE_VRAM_MIB,
    MIN_PROBE_RESERVE_MIB,
    MIN_TOTAL_VRAM_MIB,
    accumulation_for_batch,
    batch_candidates,
    probe_memory_reserve,
    require_complete_split,
    supports_native_bf16,
    validate_vram,
)
from setup_conda_environment import (conda_config_content, pip_constraint_content,
                                     prepare_workspace, process_environment, requirements_pip_args,
                                     validated_torch_wheel, validated_wheelhouse, workspace_layout)


class TrainingProfileTests(unittest.TestCase):
    def test_16gb_profile_prefers_largest_working_batch_and_full_training_length(self):
        self.assertEqual(DEFAULT_BATCH_SIZE, 0)
        self.assertEqual(DEFAULT_BATCH_CANDIDATES, (8, 4, 2, 1))
        self.assertEqual(DEFAULT_EFFECTIVE_BATCH_SIZE, 8)
        self.assertEqual(DEFAULT_GRADIENT_ACCUMULATION, None)
        self.assertEqual(DEFAULT_MAX_SEQ_LEN, 1024)
        self.assertEqual(MIN_TOTAL_VRAM_MIB, 14 * 1024)
        self.assertEqual(MIN_FREE_VRAM_MIB, 6 * 1024)
        self.assertEqual(MIN_PROBE_RESERVE_MIB, 1024)

    def test_accumulation_keeps_effective_batch_near_eight_for_speed_profile(self):
        self.assertEqual(accumulation_for_batch(8), 1)
        self.assertEqual(accumulation_for_batch(4), 2)
        self.assertEqual(accumulation_for_batch(2), 4)
        self.assertEqual(accumulation_for_batch(1), 8)

    def test_bfloat16_is_used_only_when_gpu_has_native_support(self):
        self.assertFalse(supports_native_bf16((7, 5), True))
        self.assertFalse(supports_native_bf16((8, 0), False))
        self.assertTrue(supports_native_bf16((8, 0), True))

    def test_batch_probe_tries_largest_microbatch_first_and_respects_split_size(self):
        self.assertEqual(batch_candidates(0, 1248), [8, 4, 2, 1])
        self.assertEqual(batch_candidates(0, 4), [4, 2, 1])
        self.assertEqual(batch_candidates(4, 1248), [4])
        with self.assertRaisesRegex(ValueError, "exceeds"):
            batch_candidates(8, 4)

    def test_microbatch_probe_reserves_memory_for_optimizer_and_runtime(self):
        self.assertEqual(probe_memory_reserve(12000, 1800, 9000), (True, 4800))
        self.assertEqual(probe_memory_reserve(12000, 1800, 13000), (False, 800))

    def test_16gb_device_with_enough_free_memory_passes_preflight(self):
        checked = validate_vram(total_mib=15360, free_mib=8000)
        self.assertEqual(checked, {"totalMiB": 15360, "freeMiB": 8000})

    def test_preflight_rejects_small_or_busy_device_with_actionable_error(self):
        with self.assertRaisesRegex(RuntimeError, "total VRAM"):
            validate_vram(total_mib=4096, free_mib=3500)
        with self.assertRaisesRegex(RuntimeError, "free VRAM"):
            validate_vram(total_mib=16384, free_mib=6143)

    def test_conda_environment_and_caches_stay_under_workspace(self):
        layout = workspace_layout(Path("D:/SXJ/qwen35-finetune/package"))
        self.assertEqual(layout["workspace"], Path("D:/SXJ/qwen35-finetune"))
        self.assertEqual(layout["environment"], Path("D:/SXJ/qwen35-finetune/envs/qwen35-finetune"))
        self.assertTrue(all(path.is_relative_to(layout["workspace"]) for key, path in layout.items()
                            if key not in {"workspace", "package"}))
        env = process_environment(layout, {"PATH": "existing", "CONDARC": "old-config"})
        self.assertEqual(env["PATH"], "existing")
        self.assertEqual(env["CONDARC"], str(layout["workspace"] / "condarc.yml"))
        for key in ("CONDA_ENVS_PATH", "CONDA_PKGS_DIRS", "PIP_CACHE_DIR", "HF_HOME", "TORCH_HOME", "TEMP", "TMP"):
            self.assertTrue(Path(env[key]).is_relative_to(layout["workspace"]))
        self.assertEqual(Path(env["PIP_CONSTRAINT"]), layout["workspace"] / "pip-constraints.txt")

    def test_condarc_and_torch_constraints_are_workspace_scoped(self):
        layout = workspace_layout(Path("D:/SXJ/qwen35-finetune/package"))
        config = conda_config_content(layout)
        self.assertIn("D:/SXJ/qwen35-finetune/envs", config)
        self.assertIn("D:/SXJ/qwen35-finetune/cache/conda-pkgs", config)
        self.assertEqual(pip_constraint_content(), "torch==2.10.0+cu128\n")

    def test_workspace_setup_materializes_condarc_and_cuda_torch_constraint(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "qwen35-finetune"
            package = root / "package"
            package.mkdir(parents=True)
            layout = workspace_layout(package, root)
            prepare_workspace(layout)
            self.assertEqual((root / "condarc.yml").read_text(encoding="utf-8"), conda_config_content(layout))
            self.assertEqual((root / "pip-constraints.txt").read_text(encoding="ascii"), pip_constraint_content())
            self.assertTrue(layout["conda_packages"].is_dir())

    def test_workspace_layout_rejects_package_outside_workspace(self):
        with self.assertRaisesRegex(ValueError, "inside the selected workspace"):
            workspace_layout(Path("C:/other/package"), Path("D:/SXJ"))

    def test_pre_staged_cuda_wheel_must_be_inside_workspace_and_hash_verified(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "workspace"
            root.mkdir()
            wrong = root / "wrong.whl"
            wrong.write_bytes(b"not-the-pytorch-wheel")
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                validated_torch_wheel(wrong, root)
            outside = Path(temp) / "outside.whl"
            outside.write_bytes(b"wheel")
            with self.assertRaisesRegex(ValueError, "inside the selected workspace"):
                validated_torch_wheel(outside, root)

    def test_offline_wheelhouse_pip_args_disable_index_and_stay_in_workspace(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "workspace"
            package = root / "package"
            wheelhouse = root / "cache" / "wheelhouse"
            wheelhouse.mkdir(parents=True)
            wheel = wheelhouse / "example-1.0-py3-none-any.whl"
            wheel.write_bytes(b"wheel")
            (wheelhouse / "wheelhouse-manifest.json").write_text(json.dumps({
                "filesSha256": {wheel.name: hashlib.sha256(wheel.read_bytes()).hexdigest()}
            }), encoding="utf-8")
            verified = validated_wheelhouse(wheelhouse, root)
            args = requirements_pip_args("python", package / "requirements.txt", root / "pip-constraints.txt", verified)
            self.assertIn("--no-index", args)
            self.assertIn(str(verified), args)
            with self.assertRaisesRegex(ValueError, "inside the selected workspace"):
                validated_wheelhouse(Path(temp) / "outside", root)
            wheel.write_bytes(b"tampered")
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                validated_wheelhouse(wheelhouse, root)

    def test_full_dataset_guard_rejects_any_silently_dropped_sample(self):
        self.assertEqual(require_complete_split(1248, 1248, []), 1248)
        with self.assertRaisesRegex(ValueError, "Full-split training"):
            require_complete_split(1248, 1247, ["case-over-limit"])


if __name__ == "__main__":
    unittest.main()
