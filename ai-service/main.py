"""aiphoto AI Service - FastAPI application for AI inference."""

import logging
import os
from contextlib import asynccontextmanager
from fastapi import FastAPI, File, UploadFile, HTTPException
from pydantic import BaseModel, Field

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
    version=os.getenv("APP_VERSION", "0.19.23"),
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
    text: str = Field(min_length=1, max_length=2000)


class ModelConfigRequest(BaseModel):
    path: str
    enabled: bool = True


class BatchEmbedResponse(BaseModel):
    embeddings: list[list[float]]


class HealthResponse(BaseModel):
    status: str
    models: dict


@app.get("/health", response_model=HealthResponse)
def health_check():
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
def model_status():
    """Return local model repository configuration and load status."""
    return model_manager.status()


@app.put("/models/{model_name}")
def configure_model(model_name: str, request: ModelConfigRequest):
    """Persist and load a model from a path below AI_MODEL_ROOT."""
    try:
        return model_manager.update(model_name, request.path, request.enabled)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/models/{model_name}/reload")
def reload_model(model_name: str):
    """Reload one model without restarting the service."""
    try:
        return model_manager.reload(model_name)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


def infer(name, operation):
    # Synchronous endpoints run in FastAPI's worker pool; the lease serializes GPU
    # inference and prevents a concurrent configure/reload from evicting the model.
    with model_manager.use(name) as model:
        if model is None:
            state = model_manager.model_status(name)
            detail = state["error"] or f"{name} 模型未启用，请在设置中启用并配置本地模型"
            raise HTTPException(status_code=503, detail=detail)
        try:
            return operation(model)
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        except Exception as exc:
            logger.exception("%s inference failed", name)
            raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.post("/ai/embed", response_model=EmbeddingResponse)
def embed(file: UploadFile = File(...)):
    data = file.file.read()
    return EmbeddingResponse(embedding=infer("clip", lambda model: model.encode_image(data, file.filename)))


@app.post("/ai/detect-faces", response_model=FaceDetectionResponse)
def detect_faces(file: UploadFile = File(...)):
    data = file.file.read()
    faces = infer("insightface", lambda model: model.detect(data, file.filename))
    return FaceDetectionResponse(faces=[FaceResult(
        bbox=dict(zip(("x", "y", "w", "h"), map(float, face.bbox))),
        embedding=list(map(float, face.embedding)), confidence=float(face.confidence)
    ) for face in faces])


@app.post("/ai/classify", response_model=ClassifyResponse)
def classify(file: UploadFile = File(...)):
    data = file.file.read()
    tags = infer("yolo", lambda model: model.detect(data, filename=file.filename))
    return ClassifyResponse(tags=[TagResult(name=tag.name, confidence=tag.confidence,
                                          category=tag.category) for tag in tags])


@app.post("/ai/caption", response_model=CaptionResponse)
def caption(file: UploadFile = File(...)):
    data = file.file.read()
    text = infer("blip", lambda model: model.generate_caption(data, filename=file.filename))
    return CaptionResponse(caption=text)


@app.post("/ai/embed-text", response_model=EmbeddingResponse)
def embed_text(request: EmbedTextRequest):
    text = request.text.strip()
    if not text:
        raise HTTPException(status_code=422, detail="搜索文字不能为空")
    return EmbeddingResponse(embedding=infer("clip", lambda model: model.encode_text(text)))


@app.post("/ai/batch-embed", response_model=BatchEmbedResponse)
def batch_embed(files: list[UploadFile] = File(...)):
    def encode(model):
        return [model.encode_image(file.file.read(), file.filename) for file in files]
    return BatchEmbedResponse(embeddings=infer("clip", encode))
