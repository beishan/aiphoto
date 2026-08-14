# aiphoto Project Memory

## Architecture Decisions

- **Project Created**: 2025-05-27 from `docs/requires_v0.md` requirements document
- **Tech Stack**: Spring Boot 3.3 + Vue 3 + Python FastAPI + PostgreSQL 16 + pgvector
- **Deployment**: Docker Compose with 4 services (frontend, backend, ai-service, postgres)
- **AI Models**: CLIP (ViT-B-32), InsightFace (buffalo_l), YOLOv8n, BLIP-2 (on-demand)

## Discovered Durable Knowledge

### Database Migration
- `schema.sql` is mounted as init script (`docker-entrypoint-initdb.d/01-schema.sql`) - only runs on first DB init
- Use `CREATE TABLE IF NOT EXISTS` and `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` for incremental changes
- Embedding columns (photos, face_clusters) must be `vector(512)` type, not `text`
- When running multiple SQL statements via `psql -c`, errors cause rollback of entire batch - run statements separately

### AI Model Persistence
- CLIP model (~577MB) must be downloaded to host: `./ai-models/clip/open_clip_pytorch_model.bin`
- Container model cache is lost on rebuild - models must be in mounted volumes
- Environment variables for model caching:
  - `HF_HOME=/opt/models/clip`
  - `TRANSFORMERS_CACHE=/opt/models/clip`
  - `OPENCLIP_CACHE_DIR=/opt/models/clip`
- InsightFace models: `./models/insightface/models/buffalo_l/`
- YOLO model: downloads to `~/.ultralytics/` (mounted via `./ai-models/ultralytics`)

### Configuration
- Default credentials: `admin` / `admin123`
- Search similarity threshold: configurable via `ai_search_similarity_threshold` setting (default: 80 = 0.80 cosine distance)
- Face cluster threshold: configurable via `ai_face_cluster_threshold` setting

## Patterns

### Docker Service Startup Order
- Backend (Java) takes ~15s to start; check health via `POST /api/auth/login`
- AI service depends on model downloads - check logs if startup fails

### SQL Migration Pattern
```sql
-- Add column safely
DO $$ BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'table' AND column_name = 'column'
    ) THEN
        ALTER TABLE table ADD COLUMN column TYPE;
    END IF;
END $$;
```

## Gotchas

- `photos.file_path` VARCHAR(255) is too short - use VARCHAR(1024) for full paths
- Model files in container writable layer are lost on `docker compose up -d --build`
- HuggingFace download may fail inside Docker - use `HF_ENDPOINT=https://hf-mirror.com` for China
- AI service methods must match calling signatures (e.g., `detect(image_data)` vs `detect(image_data, filename)`)
