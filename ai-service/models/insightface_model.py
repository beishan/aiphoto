"""InsightFace model wrapper for face detection and recognition."""

import logging
from pathlib import Path
from dataclasses import dataclass

import cv2
import numpy as np
import insightface
import torch
from insightface.app import FaceAnalysis
from media import decode_media

logger = logging.getLogger(__name__)


@dataclass
class FaceDetection:
    bbox: list[float]  # [x, y, w, h]
    embedding: list[float]
    confidence: float


class InsightFaceModel:
    """InsightFace model for face detection and embedding extraction."""

    def __init__(self, model_path: str):
        path = Path(model_path).resolve()
        if not path.is_dir():
            raise FileNotFoundError(f"InsightFace model directory not found: {path}")
        if not any(path.glob("*.onnx")):
            raise FileNotFoundError(f"InsightFace directory contains no ONNX models: {path}")
        if path.parent.name != "models":
            raise ValueError("InsightFace 目录必须采用 <root>/models/<模型名> 结构")
        model_name = path.name
        logger.info(f"Loading InsightFace model: {path}")
        cuda_available = torch.cuda.is_available()
        providers = (
            ["CUDAExecutionProvider", "CPUExecutionProvider"]
            if cuda_available
            else ["CPUExecutionProvider"]
        )
        self.app = FaceAnalysis(
            name=model_name,
            root=str(path.parent.parent),
            providers=providers,
        )
        self.app.prepare(ctx_id=0 if cuda_available else -1, det_size=(640, 640))
        logger.info("InsightFace model loaded with providers: %s", providers)

    def detect(self, image_data: bytes, filename: str = "") -> list[FaceDetection]:
        """Detect faces and extract embeddings."""
        image = decode_media(image_data, filename)
        img = cv2.cvtColor(np.asarray(image), cv2.COLOR_RGB2BGR)

        faces = self.app.get(img)

        results = []
        for face in faces:
            bbox = face.bbox.tolist()  # [x1, y1, x2, y2]
            # Convert to [x, y, w, h]
            x, y, x2, y2 = bbox
            w, h = x2 - x, y2 - y

            results.append(
                FaceDetection(
                    bbox=[x, y, w, h],
                    embedding=face.embedding.tolist(),
                    confidence=float(face.det_score),
                )
            )

        return results
