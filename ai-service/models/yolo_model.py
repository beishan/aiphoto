"""YOLOv8 model wrapper for object detection and classification."""

import logging
import os
from dataclasses import dataclass

import numpy as np
from ultralytics import YOLO
from PIL import Image
import io

logger = logging.getLogger(__name__)


@dataclass
class Detection:
    name: str
    confidence: float
    category: str  # "object" or "scene"


# Simple category mapping
SCENE_KEYWORDS = {
    "outdoor", "indoor", "beach", "mountain", "forest", "city",
    "street", "park", "garden", "kitchen", "bedroom", "living room",
}


class YOLOModel:
    """YOLOv8 model for object detection."""

    def __init__(self, model_path: str):
        if not os.path.isfile(model_path):
            raise FileNotFoundError(f"YOLO model not found: {model_path}")
        logger.info(f"Loading YOLOv8 model: {model_path}")
        self.model = YOLO(model_path)
        logger.info("YOLOv8 model loaded")

    def detect(self, image_data: bytes, confidence_threshold: float = 0.5, filename: str = "") -> list[Detection]:
        """Detect objects in an image."""
        image = Image.open(io.BytesIO(image_data)).convert("RGB")
        results = self.model(image, conf=confidence_threshold, verbose=False)

        detections = []
        seen_names = set()

        for result in results:
            for box in result.boxes:
                class_id = int(box.cls[0])
                name = result.names[class_id]
                conf = float(box.conf[0])

                if name not in seen_names:
                    seen_names.add(name)
                    category = "scene" if name.lower() in SCENE_KEYWORDS else "object"
                    detections.append(
                        Detection(name=name, confidence=conf, category=category)
                    )

        return sorted(detections, key=lambda d: d.confidence, reverse=True)
