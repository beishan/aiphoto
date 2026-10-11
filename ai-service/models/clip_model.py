"""Chinese-CLIP model wrapper for image/text embedding with Chinese support."""

import logging
import os

import numpy as np
import torch
from PIL import Image
from media import decode_media

logger = logging.getLogger(__name__)


class CLIPModel:
    """Chinese-CLIP model for generating image and text embeddings with Chinese support."""

    def __init__(self, model_path: str):
        logger.info(f"Loading Chinese-CLIP model: {model_path}")
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        local_weights = os.path.abspath(model_path)
        if not os.path.isfile(local_weights):
            raise FileNotFoundError(f"Chinese-CLIP 权重不存在: {local_weights}")
        import cn_clip.clip as clip
        from cn_clip.clip import tokenize
        from cn_clip.clip.utils import create_model, image_transform

        self.clip = clip
        self.tokenize = tokenize
        # Load the exact supplied file. Never rewrite it or implicitly download weights.
        checkpoint = torch.load(local_weights, map_location="cpu", weights_only=True)
        state_dict = checkpoint.get("state_dict") if isinstance(checkpoint, dict) else None
        if not isinstance(state_dict, dict) or not state_dict:
            raise ValueError("需要 Chinese-CLIP ViT-B/16 官方 checkpoint（含 state_dict）；不支持直接加载 OpenCLIP/HuggingFace .bin")
        keys = [key.removeprefix("module.") for key in state_dict]
        if "visual.class_embedding" not in keys or not any(key.startswith("bert.") for key in keys):
            raise ValueError("权重架构不兼容，请使用 Chinese-CLIP ViT-B/16 官方 checkpoint")
        self.model = create_model("ViT-B-16@RoBERTa-wwm-ext-base-chinese", checkpoint)
        self.model = self.model.float() if self.device == "cpu" else self.model.to(self.device)
        self.preprocess = image_transform(224)
        self.model.eval()
        logger.info(f"Chinese-CLIP model loaded on {self.device}")

    def encode_image(self, image_data: bytes, filename: str = "") -> list[float]:
        """Generate embedding for an image."""
        image = decode_media(image_data, filename)
        image_tensor = self.preprocess(image).unsqueeze(0).to(self.device)

        with torch.no_grad():
            features = self.model.encode_image(image_tensor)
            features = features / features.norm(dim=-1, keepdim=True)

        return features.cpu().numpy().flatten().tolist()

    def encode_text(self, text: str) -> list[float]:
        """Generate embedding for text (supports Chinese)."""
        tokens = self.tokenize([text]).to(self.device)

        with torch.no_grad():
            features = self.model.encode_text(tokens)
            features = features / features.norm(dim=-1, keepdim=True)

        return features.cpu().numpy().flatten().tolist()

    def compute_similarity(self, image_embedding: list[float], text_embedding: list[float]) -> float:
        """Compute cosine similarity between image and text embeddings."""
        img = np.array(image_embedding)
        txt = np.array(text_embedding)
        return float(np.dot(img, txt) / (np.linalg.norm(img) * np.linalg.norm(txt)))
