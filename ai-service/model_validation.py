"""Validate local repository layout without downloading or loading weights."""

import json
from pathlib import Path


def validate_model_path(name: str, path: Path) -> None:
    if not path.exists():
        hint = ""
        if name == "clip":
            hint = "；请安装 Chinese-CLIP ViT-B/16 官方 .pt 权重，OpenCLIP 权重不能直接替代"
        if name == "blip":
            hint = "；请放置包含配置、处理器和全部权重的 BLIP-2 本地目录"
        raise ValueError(f"本地模型不存在: {path}{hint}")
    if name in {"clip", "yolo"}:
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError("请选择非空的模型权重文件")
        if name == "yolo" and path.suffix.lower() != ".pt":
            raise ValueError("YOLO 目标检测需要 .pt 权重")
    elif name == "insightface":
        if not path.is_dir() or path.parent.name != "models":
            raise ValueError("InsightFace 需要 <root>/models/<模型名> 目录")
        if len(list(path.glob("*.onnx"))) < 2:
            raise ValueError("InsightFace 至少需要检测和识别两个 ONNX 模型")
    elif name == "blip":
        for filename in ("config.json", "preprocessor_config.json", "tokenizer_config.json"):
            config = path / filename
            if not config.is_file():
                raise ValueError(f"BLIP-2 本地目录缺少 {filename}")
            json.loads(config.read_text(encoding="utf-8"))
        if not (list(path.glob("*.safetensors")) or list(path.glob("pytorch_model*.bin"))):
            raise ValueError("BLIP-2 本地目录缺少权重文件")
