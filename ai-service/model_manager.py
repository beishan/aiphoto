"""Local-only AI model lifecycle and configuration management."""

from __future__ import annotations

import gc
import json
import logging
import os
from pathlib import Path
from threading import RLock
from typing import Any
from contextlib import contextmanager
from model_validation import validate_model_path

import torch

from models.blip_model import BLIPModel
from models.clip_model import CLIPModel
from models.insightface_model import InsightFaceModel
from models.yolo_model import YOLOModel

logger = logging.getLogger(__name__)

MODEL_TYPES = ("clip", "insightface", "yolo", "blip")


class ModelManager:
    """Loads models exclusively from the externally mounted model repository."""

    def __init__(self) -> None:
        self.default_root = Path(os.environ.get("AI_MODEL_ROOT", "/models")).resolve()
        self.default_root.mkdir(parents=True, exist_ok=True)
        control = os.environ.get("STORAGE_CONTROL_DIRECTORY")
        self.control = Path(control) if control else self.default_root / ".aiphoto-storage"
        self.lock = RLock()
        self.models: dict[str, Any | None] = {name: None for name in MODEL_TYPES}
        self.errors: dict[str, str | None] = {name: None for name in MODEL_TYPES}
        default_capacity = 3 if torch.cuda.is_available() else 1
        self.max_loaded_models = max(
            1, int(os.environ.get("AI_MAX_LOADED_MODELS", default_capacity))
        )
        self.load_order: list[str] = []
        self.config = self._load_config()
        self.loaded_root = self.root

    @property
    def root(self) -> Path:
        try:
            target = Path((self.control / "models.active").read_text(encoding="utf-8"))
        except FileNotFoundError:
            return self.default_root
        pool = os.environ.get("STORAGE_POOL_DIRECTORY")
        if not pool or not target.is_absolute() or not target.is_dir():
            raise ValueError("当前模型存储目录不可用")
        pool_root = Path(pool).resolve(strict=True)
        target = target.resolve(strict=True)
        if pool_root not in target.parents:
            raise ValueError("模型目录必须位于存储池内")
        return target

    @property
    def config_file(self) -> Path:
        return self.root / "model-config.json"

    @contextmanager
    def _configuration_writer(self):
        # The backend holds the same POSIX record lock while moving models.
        if os.name != "posix" or not os.environ.get("STORAGE_POOL_DIRECTORY"):
            yield
            return
        import fcntl

        self.control.mkdir(exist_ok=True)
        with (self.control / "models.lock").open("ab") as lock:
            fcntl.lockf(lock, fcntl.LOCK_EX)
            try:
                yield
            finally:
                fcntl.lockf(lock, fcntl.LOCK_UN)

    def _defaults(self) -> dict[str, dict[str, Any]]:
        return {
            "clip": {"enabled": True, "path": "chinese-clip/clip_cn_vit-b-16.pt"},
            "insightface": {"enabled": True, "path": "insightface/models/buffalo_l"},
            "yolo": {"enabled": True, "path": "ultralytics/yolov8n.pt"},
            "blip": {"enabled": False, "path": "blip2"},
        }

    def _load_config(self) -> dict[str, dict[str, Any]]:
        config = self._defaults()
        if self.config_file.is_file():
            try:
                saved = json.loads(self.config_file.read_text(encoding="utf-8"))
                for name in MODEL_TYPES:
                    if isinstance(saved.get(name), dict):
                        config[name].update(saved[name])
            except (OSError, ValueError) as exc:
                logger.error("Unable to read model configuration: %s", exc)
        return config

    def _save_config(self) -> None:
        temporary = self.config_file.with_suffix(".tmp")
        temporary.write_text(
            json.dumps(self.config, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        temporary.replace(self.config_file)

    def resolve_path(self, relative_path: str) -> Path:
        root = self.root
        candidate = (root / relative_path).resolve()
        if candidate != root and root not in candidate.parents:
            raise ValueError("模型路径必须位于外部模型根目录内")
        return candidate

    def load_enabled(self) -> None:
        """Validate configured paths; inference endpoints load models on demand."""
        for name in MODEL_TYPES:
            setting = self.config[name]
            if setting.get("enabled"):
                try:
                    path = self.resolve_path(str(setting.get("path", "")))
                    validate_model_path(name, path)
                    self.errors[name] = None
                except (ValueError, OSError) as exc:
                    self.errors[name] = str(exc)

    def reload(self, name: str) -> dict[str, Any]:
        if name not in MODEL_TYPES:
            raise ValueError(f"不支持的模型类型: {name}")
        with self.lock:
            self._refresh_root()
            self._unload(name)
            self.errors[name] = None

            setting = self.config[name]
            if not setting.get("enabled"):
                return self.model_status(name)

            try:
                path = self.resolve_path(str(setting.get("path", "")))
                validate_model_path(name, path)
                preference = setting.get("device", "auto")
                cuda_available = torch.cuda.is_available()
                if preference == "gpu" and not cuda_available:
                    raise ValueError("CUDA GPU 不可用，请检查 GPU 部署配置或选择 CPU")
                device = "cuda" if cuda_available and preference != "cpu" else "cpu"
                self._make_room(name)
                if name == "clip":
                    self.models[name] = CLIPModel(str(path), device=device)
                elif name == "insightface":
                    model = InsightFaceModel(str(path), device=device)
                    if preference == "gpu" and any(
                        "CUDAExecutionProvider" not in component.session.get_providers()
                        for component in model.app.models.values()
                    ):
                        raise ValueError("InsightFace 无法使用 CUDA 执行提供程序")
                    self.models[name] = model
                elif name == "yolo":
                    self.models[name] = YOLOModel(str(path), device=device)
                else:
                    self.models[name] = BLIPModel(str(path), device=device)
                self._touch(name)
            except Exception as exc:  # Keep the service available for configuration.
                self.errors[name] = str(exc)
                logger.exception("Failed to load local %s model", name)
            return self.model_status(name)

    def get(self, name: str, lazy: bool = True) -> Any | None:
        with self.lock:
            self._refresh_root()
            model = self.models.get(name)
            if model is None and lazy and self.config[name].get("enabled"):
                self.reload(name)
                model = self.models.get(name)
            if model is not None:
                self._touch(name)
            return model

    def _refresh_root(self) -> None:
        root = self.root
        if root != self.loaded_root:
            for name in list(self.load_order):
                self._unload(name)
            self.loaded_root = root

    @contextmanager
    def use(self, name: str):
        """Keep a model resident until inference ends, including concurrent reloads."""
        with self.lock:
            model = self.get(name)
            yield model

    def _make_room(self, requested_name: str) -> None:
        while sum(model is not None for model in self.models.values()) >= self.max_loaded_models:
            victim = next(
                (name for name in self.load_order if name != requested_name), None
            )
            if victim is None:
                break
            logger.info("Unloading AI model to free memory: %s", victim)
            self._unload(victim)

    def _touch(self, name: str) -> None:
        if name in self.load_order:
            self.load_order.remove(name)
        self.load_order.append(name)

    def _unload(self, name: str) -> None:
        self.models[name] = None
        if name in self.load_order:
            self.load_order.remove(name)
        gc.collect()
        if torch.cuda.is_available():
            torch.cuda.empty_cache()

    def update(self, name: str, path: str, enabled: bool, device: str | None = None) -> dict[str, Any]:
        if name not in MODEL_TYPES:
            raise ValueError(f"不支持的模型类型: {name}")
        with self.lock, self._configuration_writer():
            resolved = self.resolve_path(path)
            if enabled:
                validate_model_path(name, resolved)
            previous = self.config[name].copy()
            was_loaded = self.models[name] is not None
            self.config[name] = {
                "path": path,
                "enabled": enabled,
                "device": device if device is not None else previous.get("device", "auto"),
            }
            result = self.reload(name)
            if result["error"]:
                self.config[name] = previous
                self._unload(name)
                if was_loaded:
                    self.reload(name)
                else:
                    self.errors[name] = None
                    self.load_enabled()
                return result
            self._save_config()
            return result

    def model_status(self, name: str) -> dict[str, Any]:
        setting = self.config[name]
        try:
            path = self.resolve_path(str(setting.get("path", "")))
            exists = path.exists()
            error = self.errors[name]
        except (ValueError, OSError) as exc:
            exists = False
            error = str(exc)
        return {
            "name": name,
            "enabled": bool(setting.get("enabled")),
            "path": str(setting.get("path", "")),
            "device": setting.get("device", "auto"),
            "exists": exists,
            "loaded": self.models[name] is not None,
            "error": error,
        }

    def status(self) -> dict[str, Any]:
        with self.lock:
            self._refresh_root()
            return {
                "root": str(self.root),
                "offline": True,
                "maxLoadedModels": self.max_loaded_models,
                "models": [self.model_status(name) for name in MODEL_TYPES],
            }
