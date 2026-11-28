import importlib.util
import os
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


def model_doubles():
    torch = types.ModuleType("torch")
    torch.cuda = types.SimpleNamespace(is_available=lambda: False)
    doubles = {"torch": torch}
    for name, model_class in (("clip", "CLIPModel"), ("insightface", "InsightFaceModel"),
                              ("yolo", "YOLOModel"), ("blip", "BLIPModel")):
        module = types.ModuleType(f"models.{name}_model")
        setattr(module, model_class, Mock())
        doubles[module.__name__] = module
    return doubles


class ModelStorageTest(unittest.TestCase):
    def test_cpu_selection_persists_and_gpu_requires_cuda(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(
            os.environ, {"AI_MODEL_ROOT": directory}
        ):
            doubles = model_doubles()
            cuda = doubles["torch"].cuda
            cuda.empty_cache = Mock()
            with patch.dict(sys.modules, doubles):
                spec = importlib.util.spec_from_file_location(
                    "device_test_manager", Path(__file__).resolve().parents[1] / "model_manager.py"
                )
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)
                manager = module.ModelManager()
                path = manager.root / "clip.pt"
                path.write_bytes(b"checkpoint")
                with patch.object(cuda, "is_available", return_value=True):
                    state = manager.update("clip", path.name, True, "cpu")
                    self.assertEqual(state["device"], "cpu")
                    manager.update("clip", path.name, True)
                    module.CLIPModel.assert_called_with(str(path), device="cpu")
                self.assertEqual(module.ModelManager().model_status("clip")["device"], "cpu")
                state = manager.update("clip", path.name, True, "gpu")
                self.assertIn("CUDA GPU 不可用", state["error"])
                self.assertEqual(manager.model_status("clip")["device"], "cpu")

    def test_migrated_model_root_is_used_without_restarting_ai(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            original = base / "original"
            pool = base / "pool"
            target = pool / "weights"
            control = base / "control"
            target.mkdir(parents=True)
            control.mkdir()
            with patch.dict(sys.modules, model_doubles()), patch.dict(os.environ, {
                "AI_MODEL_ROOT": str(original), "STORAGE_POOL_DIRECTORY": str(pool),
                "STORAGE_CONTROL_DIRECTORY": str(control),
            }):
                spec = importlib.util.spec_from_file_location("storage_test_manager",
                    Path(__file__).resolve().parents[1] / "model_manager.py")
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)
                manager = module.ModelManager()
                self.assertEqual(manager.root, original.resolve())
                (control / "models.active").write_text(str(target.resolve()), encoding="utf-8")
                (target / "local.pt").write_bytes(b"weights")
                self.assertEqual(manager.resolve_path("local.pt"), target.resolve() / "local.pt")
                manager.update("clip", "local.pt", False)
                self.assertTrue((target / "model-config.json").is_file())
                self.assertFalse((original / "model-config.json").exists())
