"""Local-only AI model lifecycle and configuration management."""

from __future__ import annotations

import gc
import json
import logging
import os
from pathlib import Path
from threading import RLock
from typing import Any

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
        self.root = Path(os.environ.get("AI_MODEL_ROOT", "/models")).resolve()
        self.root.mkdir(parents=True, exist_ok=True)
        self.config_file = self.root / "model-config.json"
        self.lock = RLock()
        self.models: dict[str, Any | None] = {name: None for name in MODEL_TYPES}
        self.errors: dict[str, str | None] = {name: None for name in MODEL_TYPES}
        default_capacity = 3 if torch.cuda.is_available() else 1
        self.max_loaded_models = max(
            1, int(os.environ.get("AI_MAX_LOADED_MODELS", default_capacity))
        )
        self.load_order: list[str] = []
        self.config = self._load_config()

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
        candidate = (self.root / relative_path).resolve()
        if candidate != self.root and self.root not in candidate.parents:
            raise ValueError("模型路径必须位于外部模型根目录内")
        return candidate

    def load_enabled(self) -> None:
        """Validate configured paths; inference endpoints load models on demand."""
        for name in MODEL_TYPES:
            setting = self.config[name]
            if setting.get("enabled"):
                path = self.resolve_path(str(setting.get("path", "")))
                if not path.exists():
                    self.errors[name] = f"本地模型不存在: {path}"

    def reload(self, name: str) -> dict[str, Any]:
        if name not in MODEL_TYPES:
            raise ValueError(f"不支持的模型类型: {name}")
        with self.lock:
            self._unload(name)
            self.errors[name] = None

            setting = self.config[name]
            if not setting.get("enabled"):
                return self.model_status(name)

            path = self.resolve_path(str(setting.get("path", "")))
            try:
                if not path.exists():
                    raise FileNotFoundError(f"本地模型不存在: {path}")
                self._make_room(name)
                if name == "clip":
                    self.models[name] = CLIPModel(str(path))
                elif name == "insightface":
                    self.models[name] = InsightFaceModel(str(path))
                elif name == "yolo":
                    self.models[name] = YOLOModel(str(path))
                else:
                    self.models[name] = BLIPModel(str(path))
                self._touch(name)
            except Exception as exc:  # Keep the service available for configuration.
                self.errors[name] = str(exc)
                logger.exception("Failed to load local %s model", name)
            return self.model_status(name)

    def get(self, name: str, lazy: bool = True) -> Any | None:
        with self.lock:
            model = self.models.get(name)
            if model is None and lazy and self.config[name].get("enabled"):
                self.reload(name)
                model = self.models.get(name)
            if model is not None:
                self._touch(name)
            return model

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

    def update(self, name: str, path: str, enabled: bool) -> dict[str, Any]:
        if name not in MODEL_TYPES:
            raise ValueError(f"不支持的模型类型: {name}")
        self.resolve_path(path)
        with self.lock:
            self.config[name] = {"path": path, "enabled": enabled}
            self._save_config()
        return self.reload(name)

    def model_status(self, name: str) -> dict[str, Any]:
        setting = self.config[name]
        path = self.resolve_path(str(setting.get("path", "")))
        return {
            "name": name,
            "enabled": bool(setting.get("enabled")),
            "path": str(setting.get("path", "")),
            "exists": path.exists(),
            "loaded": self.models[name] is not None,
            "error": self.errors[name],
        }

    def status(self) -> dict[str, Any]:
        return {
            "root": str(self.root),
            "offline": True,
            "maxLoadedModels": self.max_loaded_models,
            "models": [self.model_status(name) for name in MODEL_TYPES],
        }
