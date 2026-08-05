"""Chinese-CLIP model wrapper for image/text embedding with Chinese support."""

import logging
import os

import numpy as np
import torch
from PIL import Image
import io

logger = logging.getLogger(__name__)


class CLIPModel:
    """Chinese-CLIP model for generating image and text embeddings with Chinese support."""

    def __init__(self, model_path: str):
        logger.info(f"Loading Chinese-CLIP model: {model_path}")
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        local_weights = os.path.abspath(model_path)
        cache_dir = os.path.dirname(local_weights)
        if os.path.basename(local_weights) != "clip_cn_vit-b-16.pt":
            raise ValueError("Chinese-CLIP 文件名必须为 clip_cn_vit-b-16.pt")

        import cn_clip.clip as clip
        from cn_clip.clip import tokenize
        self.clip = clip
        self.tokenize = tokenize

        if os.path.isfile(local_weights):
            logger.info(f"Loading Chinese-CLIP from local file: {local_weights}")
            # Verify and fix checkpoint format if needed
            checkpoint = torch.load(local_weights, map_location="cpu")
            if isinstance(checkpoint, dict) and "state_dict" in checkpoint:
                sd = checkpoint["state_dict"]
            elif isinstance(checkpoint, dict) and any(k.startswith("vision_model.") or k.startswith("text_model.") for k in checkpoint.keys()):
                # Raw HuggingFace format without state_dict wrapper
                sd = checkpoint
            else:
                sd = None

            if sd is not None:
                needs_fix = "visual.class_embedding" not in sd
                if needs_fix:
                    logger.info("Checkpoint needs key conversion, fixing...")
                    sd = self._convert_keys(sd)
                    checkpoint = {"state_dict": sd}
                    torch.save(checkpoint, local_weights)
                    logger.info("Saved converted checkpoint")

        else:
            raise FileNotFoundError(f"Chinese-CLIP weights not found: {local_weights}")

        # The file is verified above, so cn_clip never needs to download it.
        self.model, self.preprocess = clip.load_from_name(
            "ViT-B-16", device=self.device, download_root=cache_dir
        )
        self.model.eval()
        logger.info(f"Chinese-CLIP model loaded on {self.device}")

    def _convert_keys(self, state_dict):
        """Convert HuggingFace key names to cn_clip key names."""
        new_sd = {}
        for key, value in state_dict.items():
            new_key = key

            if key.startswith("vision_model."):
                new_key = key.replace("vision_model.", "visual.")
                new_key = new_key.replace("embeddings.class_embedding", "class_embedding")
                new_key = new_key.replace("embeddings.position_embedding.weight", "positional_embedding")
                new_key = new_key.replace("embeddings.patch_embedding.weight", "conv1.weight")
                if "position_ids" in new_key:
                    continue
                new_key = new_key.replace("pre_layrnorm.", "ln_pre.")
                if "encoder.layers." in new_key:
                    new_key = new_key.replace("encoder.layers.", "transformer.resblocks.")
                    new_key = new_key.replace("self_attn.k_proj.", "attn.k_proj.")
                    new_key = new_key.replace("self_attn.v_proj.", "attn.v_proj.")
                    new_key = new_key.replace("self_attn.q_proj.", "attn.q_proj.")
                    new_key = new_key.replace("self_attn.out_proj.", "attn.out_proj.")
                    new_key = new_key.replace("layer_norm1.", "ln_1.")
                    new_key = new_key.replace("layer_norm2.", "ln_2.")
                    new_key = new_key.replace("mlp.fc1.", "mlp.c_fc.")
                    new_key = new_key.replace("mlp.fc2.", "mlp.c_proj.")
                new_key = new_key.replace("post_layernorm.", "ln_post.")
                new_key = new_key.replace("visual_projection.weight", "proj")

            elif key.startswith("text_model."):
                new_key = key.replace("text_model.", "bert.")
                if "position_ids" in new_key:
                    continue

            elif key == "text_projection.weight":
                new_key = "text_projection"

            new_sd[new_key] = value

        # Combine Q, K, V into in_proj
        for i in range(12):
            prefix = f"visual.transformer.resblocks.{i}.attn"
            q, k, v = f"{prefix}.q_proj.weight", f"{prefix}.k_proj.weight", f"{prefix}.v_proj.weight"
            if q in new_sd:
                new_sd[f"{prefix}.in_proj_weight"] = torch.cat([new_sd.pop(q), new_sd.pop(k), new_sd.pop(v)], dim=0)
                qb, kb, vb = f"{prefix}.q_proj.bias", f"{prefix}.k_proj.bias", f"{prefix}.v_proj.bias"
                if qb in new_sd:
                    new_sd[f"{prefix}.in_proj_bias"] = torch.cat([new_sd.pop(qb), new_sd.pop(kb), new_sd.pop(vb)], dim=0)

        # Fix projection matrix shapes (transpose if needed)
        for key in ["visual.proj", "text_projection"]:
            if key in new_sd and len(new_sd[key].shape) == 2:
                if new_sd[key].shape[0] < new_sd[key].shape[1]:
                    new_sd[key] = new_sd[key].T.contiguous()

        return new_sd

    def encode_image(self, image_data: bytes, filename: str = "") -> list[float]:
        """Generate embedding for an image."""
        image = Image.open(io.BytesIO(image_data)).convert("RGB")
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
