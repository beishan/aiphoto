"""BLIP-2 model wrapper for image captioning."""

import logging

import torch
from PIL import Image
from transformers import Blip2Processor, Blip2ForConditionalGeneration
import io

logger = logging.getLogger(__name__)


class BLIPModel:
    """BLIP-2 model for generating image captions."""

    def __init__(self, model_path: str):
        logger.info(f"Loading local BLIP-2 model: {model_path}")
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.processor = Blip2Processor.from_pretrained(model_path, local_files_only=True)
        self.model = Blip2ForConditionalGeneration.from_pretrained(
            model_path,
            local_files_only=True,
            torch_dtype=torch.float16 if self.device == "cuda" else torch.float32,
        ).to(self.device)
        self.model.eval()
        logger.info(f"BLIP-2 model loaded on {self.device}")

    def generate_caption(self, image_data: bytes, max_length: int = 100) -> str:
        """Generate a caption for an image."""
        image = Image.open(io.BytesIO(image_data)).convert("RGB")

        inputs = self.processor(images=image, return_tensors="pt").to(self.device)

        with torch.no_grad():
            generated_ids = self.model.generate(
                **inputs,
                max_length=max_length,
                num_beams=5,
                repetition_penalty=1.5,
            )

        caption = self.processor.batch_decode(generated_ids, skip_special_tokens=True)[0]
        return caption.strip()

    def generate_caption_zh(self, image_data: bytes) -> str:
        """Generate a Chinese caption (prompt-based)."""
        image = Image.open(io.BytesIO(image_data)).convert("RGB")

        inputs = self.processor(
            images=image,
            text="这张图片描述的是",
            return_tensors="pt",
        ).to(self.device)

        with torch.no_grad():
            generated_ids = self.model.generate(
                **inputs,
                max_length=100,
                num_beams=5,
            )

        caption = self.processor.batch_decode(generated_ids, skip_special_tokens=True)[0]
        return caption.strip()
