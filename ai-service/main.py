"""aiphoto AI Service - FastAPI application for AI inference."""

import logging
import os
from contextlib import asynccontextmanager
from fastapi import FastAPI, File, UploadFile, HTTPException
from pydantic import BaseModel

from model_manager import ModelManager

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

model_manager = ModelManager()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Validate local models; inference endpoints load them on demand."""
    logger.info("Validating configured local AI models from %s", model_manager.root)
    model_manager.load_enabled()
    logger.info(
        "Local AI model validation complete; at most %s model(s) will stay loaded",
        model_manager.max_loaded_models,
    )
    yield
    logger.info("Shutting down AI service")


app = FastAPI(
    title="aiphoto AI Service",
    description="AI inference service for Chinese-CLIP, InsightFace, YOLOv8, and BLIP-2",
    version=os.getenv("APP_VERSION", "0.2.0"),
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


class ModelConfigRequest(BaseModel):
    path: str
    enabled: bool = True


class BatchEmbedResponse(BaseModel):
    embeddings: list[list[float]]


class HealthResponse(BaseModel):
    status: str
    models: dict


@app.get("/health", response_model=HealthResponse)
async def health_check():
    """Check service health and model status."""
    model_states = model_manager.status()["models"]
    enabled_states = [item for item in model_states if item["enabled"]]
    available_count = sum(
        1 for item in enabled_states if item["exists"] and not item["error"]
    )
    if enabled_states and available_count == len(enabled_states):
        status = "healthy"
    elif available_count > 0:
        status = "degraded"
    else:
        status = "unhealthy"
    return HealthResponse(
        status=status,
        models={item["name"]: item["loaded"] for item in model_states},
    )


@app.get("/models")
async def model_status():
    """Return local model repository configuration and load status."""
    return model_manager.status()


@app.put("/models/{model_name}")
async def configure_model(model_name: str, request: ModelConfigRequest):
    """Persist and load a model from a path below AI_MODEL_ROOT."""
    try:
        return model_manager.update(model_name, request.path, request.enabled)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/models/{model_name}/reload")
async def reload_model(model_name: str):
    """Reload one model without restarting the service."""
    try:
        return model_manager.reload(model_name)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/ai/embed", response_model=EmbeddingResponse)
async def embed(file: UploadFile = File(...)):
    """Generate CLIP embedding for an image/video."""
    clip_model = model_manager.get("clip")
    if clip_model is None:
        raise HTTPException(status_code=503, detail="CLIP model not loaded")

    try:
        data = await file.read()
        filename = file.filename
        embedding = clip_model.encode_image(data, filename)
        return EmbeddingResponse(embedding=embedding)
    except Exception as e:
        logger.error(f"Embedding failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ai/detect-faces", response_model=FaceDetectionResponse)
async def detect_faces(file: UploadFile = File(...)):
    """Detect faces in an image/video using InsightFace."""
    face_model = model_manager.get("insightface")
    if face_model is None:
        raise HTTPException(status_code=503, detail="InsightFace model not loaded")

    try:
        data = await file.read()
        filename = file.filename
        faces = face_model.detect(data, filename)
        return FaceDetectionResponse(
            faces=[
                FaceResult(
                    bbox={"x": float(f.bbox[0]), "y": float(f.bbox[1]), "w": float(f.bbox[2]), "h": float(f.bbox[3])},
                    embedding=[float(x) for x in f.embedding],
                    confidence=float(f.confidence),
                )
                for f in faces
            ]
        )
    except Exception as e:
        logger.error(f"Face detection failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/ai/classify", response_model=ClassifyResponse)
async def classify(file: UploadFile = File(...)):
    """Classify image/video using YOLOv8."""
    yolo_model = model_manager.get("yolo")
    if yolo_model is None:
        raise HTTPException(status_code=503, detail="YOLO model not loaded")

    try:
        data = await file.read()
        filename = file.filename
        tags = yolo_model.detect(data, filename=filename)
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
    # BLIP-2 remains lazy because it requires substantially more GPU memory.
    blip_model = model_manager.get("blip", lazy=True)
    if blip_model is None:
        raise HTTPException(status_code=503, detail="BLIP-2 local model not loaded")

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
    clip_model = model_manager.get("clip")
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
    clip_model = model_manager.get("clip")
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
