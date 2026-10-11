"""Regression tests: real API/media code, model weights replaced by lightweight doubles."""

import importlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import threading
import types
import unittest
from unittest.mock import Mock, patch
import importlib.util

import cv2
import numpy as np
from PIL import Image
from fastapi.testclient import TestClient

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from media import decode_media
from model_validation import validate_model_path


class MediaTests(unittest.TestCase):
    def test_clip_uses_exact_local_checkpoint_and_never_downloads_or_rewrites(self):
        spec = importlib.util.spec_from_file_location(
            "test_clip_wrapper", Path(__file__).resolve().parents[1] / "models/clip_model.py")
        module = importlib.util.module_from_spec(spec)
        torch = types.SimpleNamespace(cuda=types.SimpleNamespace(is_available=lambda: False),
                                      load=Mock(return_value={"state_dict": {
                                          "visual.class_embedding": object(), "bert.embeddings": object()}}))
        model = Mock()
        model.float.return_value = model
        clip = types.SimpleNamespace(tokenize=Mock(), load_from_name=Mock())
        utils = types.SimpleNamespace(create_model=Mock(return_value=model), image_transform=Mock())
        with tempfile.TemporaryDirectory() as directory, patch.dict(sys.modules, {
                "torch": torch, "cn_clip": types.ModuleType("cn_clip"),
                "cn_clip.clip": clip, "cn_clip.clip.utils": utils}):
            spec.loader.exec_module(module)
            path = Path(directory) / "custom-name.pt"
            path.write_bytes(b"unchanged checkpoint")
            module.CLIPModel(str(path))
            torch.load.assert_called_once_with(str(path), map_location="cpu", weights_only=True)
            clip.load_from_name.assert_not_called()
            self.assertEqual(path.read_bytes(), b"unchanged checkpoint")
            torch.load.return_value = {"visual.conv1.weight": object()}
            with self.assertRaisesRegex(ValueError, "OpenCLIP"):
                module.CLIPModel(str(path))

    def test_yolo_categories_match_backend_categories(self):
        path = Path(__file__).resolve().parents[1] / "models/yolo_model.py"
        spec = importlib.util.spec_from_file_location("test_yolo_wrapper", path)
        module = importlib.util.module_from_spec(spec)
        with patch.dict(sys.modules, {"ultralytics": types.SimpleNamespace(YOLO=Mock()),
                                     "test_yolo_wrapper": module}):
            spec.loader.exec_module(module)
            for name, expected in {"person": "person", "cat": "animal", "dog": "animal",
                                   "banana": "food", "potted plant": "plant", "cup": "object"}.items():
                self.assertEqual(module.category_for(name), expected)

    def test_photo_and_empty_input(self):
        output = io.BytesIO()
        Image.new("RGB", (20, 10), "red").save(output, format="PNG")
        self.assertEqual(decode_media(output.getvalue(), "photo.png").size, (20, 10))
        with self.assertRaises(ValueError):
            decode_media(b"", "photo.jpg")

    def test_video_uses_midpoint_and_returns_rgb(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "video.avi"
            writer = cv2.VideoWriter(str(path), cv2.VideoWriter_fourcc(*"MJPG"), 5, (32, 32))
            self.assertTrue(writer.isOpened())
            for green in (0, 50, 100, 150, 200):
                frame = np.zeros((32, 32, 3), dtype=np.uint8)
                frame[:, :] = (0, green, 250)  # BGR
                writer.write(frame)
            writer.release()
            pixel = decode_media(path.read_bytes(), "VIDEO.AVI").getpixel((16, 16))
            self.assertLess(abs(pixel[0] - 250), 5)
            self.assertLess(abs(pixel[1] - 100), 5)
            self.assertLess(pixel[2], 5)

    def test_incomplete_model_directories_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            with self.assertRaisesRegex(ValueError, "config.json"):
                validate_model_path("blip", path)
            with self.assertRaisesRegex(ValueError, "Chinese-CLIP"):
                validate_model_path("clip", path / "missing.pt")


class ModelAndApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = tempfile.TemporaryDirectory()
        torch = types.ModuleType("torch")
        torch.cuda = types.SimpleNamespace(is_available=lambda: False, empty_cache=lambda: None)
        doubles = {"torch": torch}
        for name, model_class in (("clip", "CLIPModel"), ("insightface", "InsightFaceModel"),
                                  ("yolo", "YOLOModel"), ("blip", "BLIPModel")):
            module = types.ModuleType(f"models.{name}_model")
            setattr(module, model_class, Mock(return_value=Mock()))
            doubles[module.__name__] = module
        cls.modules = patch.dict(sys.modules, doubles)
        cls.environment = patch.dict(os.environ, {"AI_MODEL_ROOT": cls.directory.name})
        cls.modules.start()
        cls.environment.start()
        cls.manager_module = importlib.import_module("model_manager")
        cls.api = importlib.import_module("main")

    @classmethod
    def tearDownClass(cls):
        cls.modules.stop()
        cls.environment.stop()
        sys.modules.pop("main", None)
        sys.modules.pop("model_manager", None)
        cls.directory.cleanup()

    def setUp(self):
        self.manager = self.manager_module.ModelManager()
        self.manager.config = {name: {"enabled": False, "path": name}
                               for name in ("clip", "insightface", "yolo", "blip")}
        self.api.model_manager = self.manager

    def test_failed_activation_keeps_previous_configuration(self):
        root = Path(self.directory.name)
        (root / "good.pt").write_bytes(b"good")
        (root / "bad.pt").write_bytes(b"bad")
        factory = self.manager_module.CLIPModel
        factory.side_effect = lambda path: (_ for _ in ()).throw(ValueError("bad checkpoint")) \
            if path.endswith("bad.pt") else Mock()
        self.manager.update("clip", "good.pt", True)
        result = self.manager.update("clip", "bad.pt", True)
        self.assertEqual(result["error"], "bad checkpoint")
        self.assertEqual(self.manager.config["clip"]["path"], "good.pt")
        saved = json.loads((root / "model-config.json").read_text())
        self.assertEqual(saved["clip"]["path"], "good.pt")
        self.assertIsNotNone(self.manager.models["clip"])
        factory.side_effect = None

    def test_lease_prevents_reload_during_inference(self):
        finished = threading.Event()
        started = threading.Event()
        self.manager.models["clip"] = Mock()

        def reload():
            started.set()
            self.manager.reload("clip")
            finished.set()

        with self.manager.use("clip"):
            worker = threading.Thread(target=reload)
            worker.start()
            self.assertTrue(started.wait(1))
            self.assertFalse(finished.wait(0.05))
        worker.join(timeout=1)
        self.assertTrue(finished.is_set())

    def test_api_reports_missing_models_and_rejects_blank_queries(self):
        with TestClient(self.api.app) as client:
            result = client.post("/ai/embed", files={"file": ("photo.png", b"image")})
            self.assertEqual(result.status_code, 503)
            self.assertIn("未启用", result.json()["detail"])
            self.assertEqual(client.post("/ai/embed-text", json={"text": "   "}).status_code, 422)

    def test_batch_and_caption_forward_the_video_filename(self):
        model = Mock()
        model.encode_image.return_value = [1.0, 0.0]
        model.generate_caption.return_value = "a video frame"
        self.manager.models["clip"] = model
        self.manager.models["blip"] = model
        with TestClient(self.api.app) as client:
            result = client.post("/ai/batch-embed", files=[("files", ("clip.mp4", b"video"))])
            self.assertEqual(result.status_code, 200)
            model.encode_image.assert_called_once_with(b"video", "clip.mp4")
            result = client.post("/ai/caption", files={"file": ("clip.mp4", b"video")})
            self.assertEqual(result.status_code, 200)
            model.generate_caption.assert_called_once_with(b"video", filename="clip.mp4")


if __name__ == "__main__":
    unittest.main()
