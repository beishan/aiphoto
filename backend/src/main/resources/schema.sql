-- MemoryVault Database Schema
-- PostgreSQL 16 + pgvector

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    avatar VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Photos table
CREATE TABLE IF NOT EXISTS photos (
    id BIGSERIAL PRIMARY KEY,
    file_path VARCHAR(1024) NOT NULL,
    file_hash_md5 VARCHAR(64),
    file_hash_phash VARCHAR(64),
    exif_date TIMESTAMP,
    gps_lat DOUBLE PRECISION,
    gps_lng DOUBLE PRECISION,
    rating INTEGER CHECK (rating >= 1 AND rating <= 5),
    note TEXT,
    ai_caption TEXT,
    embedding vector(512),
    width INTEGER,
    height INTEGER,
    file_size BIGINT,
    media_type VARCHAR(20) NOT NULL DEFAULT 'PHOTO',
    favorite BOOLEAN DEFAULT FALSE,
    original_filename VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Albums table
CREATE TABLE IF NOT EXISTS albums (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'VIRTUAL',
    cover_photo_id BIGINT REFERENCES photos(id),
    owner_id BIGINT REFERENCES users(id),
    shared BOOLEAN DEFAULT FALSE,
    birth_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Album-Photo many-to-many
CREATE TABLE IF NOT EXISTS album_photos (
    album_id BIGINT REFERENCES albums(id) ON DELETE CASCADE,
    photo_id BIGINT REFERENCES photos(id) ON DELETE CASCADE,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(20) DEFAULT 'manual',
    PRIMARY KEY (album_id, photo_id)
);

-- People table
CREATE TABLE IF NOT EXISTS people (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    cover_face_id BIGINT,
    photo_count INTEGER DEFAULT 0,
    first_seen TIMESTAMP,
    last_seen TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Face clusters table
CREATE TABLE IF NOT EXISTS face_clusters (
    id BIGSERIAL PRIMARY KEY,
    photo_id BIGINT NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
    bbox_json TEXT NOT NULL,
    embedding vector(512),
    person_id BIGINT REFERENCES people(id),
    confidence DOUBLE PRECISION
);

-- Add foreign key for people.cover_face_id
ALTER TABLE people ADD CONSTRAINT fk_people_cover_face
    FOREIGN KEY (cover_face_id) REFERENCES face_clusters(id);

-- Tags table
CREATE TABLE IF NOT EXISTS tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    color VARCHAR(7),
    type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Photo-Tag many-to-many
CREATE TABLE IF NOT EXISTS photo_tags (
    photo_id BIGINT REFERENCES photos(id) ON DELETE CASCADE,
    tag_id BIGINT REFERENCES tags(id) ON DELETE CASCADE,
    confidence DOUBLE PRECISION,
    source VARCHAR(20) DEFAULT 'manual',
    PRIMARY KEY (photo_id, tag_id)
);

-- AI Tasks table
CREATE TABLE IF NOT EXISTS ai_tasks (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    progress INTEGER DEFAULT 0,
    photo_ids_json TEXT,
    result_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP
);

-- Training sets table
CREATE TABLE IF NOT EXISTS training_sets (
    id BIGSERIAL PRIMARY KEY,
    album_id BIGINT NOT NULL UNIQUE REFERENCES albums(id) ON DELETE CASCADE,
    prototype_vector vector(512),
    threshold DOUBLE PRECISION NOT NULL DEFAULT 0.75,
    negative_count INTEGER DEFAULT 0,
    trained_at TIMESTAMP
);

-- User settings table
CREATE TABLE IF NOT EXISTS user_settings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    setting_key VARCHAR(50) NOT NULL,
    setting_value TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, setting_key)
);

-- Indexes
-- Embedding indexes (create after AI service populates embeddings with native SQL)
-- CREATE INDEX IF NOT EXISTS idx_photos_embedding ON photos USING hnsw (embedding vector_cosine_ops);
-- CREATE INDEX IF NOT EXISTS idx_face_clusters_embedding ON face_clusters USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_photos_fts ON photos USING gin(to_tsvector('simple', coalesce(note, '') || ' ' || coalesce(ai_caption, '')));
CREATE INDEX IF NOT EXISTS idx_photos_exif_date ON photos (exif_date DESC);
CREATE INDEX IF NOT EXISTS idx_photos_md5 ON photos (file_hash_md5);
CREATE INDEX IF NOT EXISTS idx_photos_phash ON photos (file_hash_phash);
CREATE INDEX IF NOT EXISTS idx_face_clusters_person ON face_clusters (person_id);
CREATE INDEX IF NOT EXISTS idx_album_photos_album ON album_photos (album_id);
CREATE INDEX IF NOT EXISTS idx_photo_tags_photo ON photo_tags (photo_id);
CREATE INDEX IF NOT EXISTS idx_user_settings_user ON user_settings (user_id);

-- Default admin user (password: admin123)
INSERT INTO users (username, password_hash, role) VALUES
    ('admin', '$2a$10$ZIEvrdNQ8X8Nr88UCEypDOVaKM5KIt.0w.UPJaQqpiwwhIB5UtqzW', 'ADMIN')
ON CONFLICT (username) DO NOTHING;
