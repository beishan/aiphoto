"""MemoryVault AI Service - FastAPI application for AI inference."""

import logging
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import FastAPI, File, UploadFile, HTTPException
from pydantic import BaseModel

from models.clip_model import CLIPModel
from models.insightface_model import InsightFaceModel
from models.yolo_model import YOLOModel
from models.blip_model import BLIPModel

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Global model instances
clip_model: Optional[CLIPModel] = None
face_model: Optional[InsightFaceModel] = None
yolo_model: Optional[YOLOModel] = None
blip_model: Optional[BLIPModel] = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load models on startup."""
    global clip_model, face_model, yolo_model
    logger.info("Loading AI models...")
    clip_model = CLIPModel()
    face_model = InsightFaceModel()
    yolo_model = YOLOModel()
    logger.info("AI models loaded successfully")
    yield
    logger.info("Shutting down AI service")


app = FastAPI(
    title="MemoryVault AI Service",
    description="AI inference service for CLIP, InsightFace, YOLOv8, and BLIP-2",
    version="1.0.0",
    lifespan=lifespan,
)


# Response models
class EmbeddingResponse(BaseModel):
    embedding: list[float]


class FaceResult(BaseModel):
    bbox: dict
    embedding: list[float]
    confidence: float


class FaceDetectionResponse(BaseModel):
    faces: list[FaceResult]


class TagResult(BaseModel):
    name: str
    confidence: float
    category: str


class ClassifyResponse(BaseModel):
    tags: list[TagResult]


class CaptionResponse(BaseModel):
    caption: str


class EmbedTextRequest(BaseModel):
    text: str


class BatchEmbedResponse(BaseModel):
    embeddings: list[list[float]]


class HealthResponse(BaseModel):
    status: str
    models: dict


@app.get("/health", response_model=HealthResponse)
async def health_check():
    """Check service health and model status."""
    return HealthResponse(
        status="healthy",
        models={
            "clip": clip_model is not None,
            "insightface": face_model is not None,
            "yolo": yolo_model is not None,
            "blip": blip_model is not None,
        },
    )


@app.post("/ai/embed", response_model=EmbeddingResponse)
async def embed(file: UploadFile = File(...)):
    """Generate CLIP embedding for an image."""
    if clip_model is None:
        raise HTTPException(status_code=503, detail="CLIP model not loaded")

    try:
        data = await file.read()
        embedding = clip_model.encode_image(data)
        return EmbeddingResponse(embedding=embedding)
    except Exception as e:
        logger.error(f"Embedding failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ai/detect-faces", response_model=FaceDetectionResponse)
async def detect_faces(file: UploadFile = File(...)):
    """Detect faces in an image using InsightFace."""
    if face_model is None:
        raise HTTPException(status_code=503, detail="InsightFace model not loaded")

    try:
        data = await file.read()
        faces = face_model.detect(data)
        return FaceDetectionResponse(
            faces=[
                FaceResult(
                    bbox={"x": f.bbox[0], "y": f.bbox[1], "w": f.bbox[2], "h": f.bbox[3]},
                    embedding=f.embedding,
                    confidence=f.confidence,
                )
                for f in faces
            ]
        )
    except Exception as e:
        logger.error(f"Face detection failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ai/classify", response_model=ClassifyResponse)
async def classify(file: UploadFile = File(...)):
    """Classify image using YOLOv8."""
    if yolo_model is None:
        raise HTTPException(status_code=503, detail="YOLO model not loaded")

    try:
        data = await file.read()
        tags = yolo_model.detect(data)
        return ClassifyResponse(
            tags=[
                TagResult(name=t.name, confidence=t.confidence, category=t.category)
                for t in tags
            ]
        )
    except Exception as e:
        logger.error(f"Classification failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ai/caption", response_model=CaptionResponse)
async def caption(file: UploadFile = File(...)):
    """Generate image caption using BLIP-2."""
    global blip_model

    # Lazy load BLIP-2 (heavy model)
    if blip_model is None:
        logger.info("Loading BLIP-2 model on demand...")
        blip_model = BLIPModel()

    try:
        data = await file.read()
        text = blip_model.generate_caption(data)
        return CaptionResponse(caption=text)
    except Exception as e:
        logger.error(f"Caption generation failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ai/embed-text", response_model=EmbeddingResponse)
async def embed_text(request: EmbedTextRequest):
    """Generate CLIP embedding for text."""
    if clip_model is None:
        raise HTTPException(status_code=503, detail="CLIP model not loaded")

    try:
        embedding = clip_model.encode_text(request.text)
        return EmbeddingResponse(embedding=embedding)
    except Exception as e:
        logger.error(f"Text embedding failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ai/batch-embed", response_model=BatchEmbedResponse)
async def batch_embed(files: list[UploadFile] = File(...)):
    """Batch embed multiple images."""
    if clip_model is None:
        raise HTTPException(status_code=503, detail="CLIP model not loaded")

    try:
        embeddings = []
        for file in files:
            data = await file.read()
            embedding = clip_model.encode_image(data)
            embeddings.append(embedding)
        return BatchEmbedResponse(embeddings=embeddings)
    except Exception as e:
        logger.error(f"Batch embedding failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))
