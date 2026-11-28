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
