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
    nickname VARCHAR(100),
    enabled BOOLEAN DEFAULT TRUE,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Migration: Add new columns to users table (idempotent)
ALTER TABLE users ADD COLUMN IF NOT EXISTS nickname VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;
ALTER TABLE users ADD COLUMN IF NOT EXISTS mood VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS birth_date DATE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS photo_preferences TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS profile_notes TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS theme VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS dock_opacity DOUBLE PRECISION;
ALTER TABLE users ADD COLUMN IF NOT EXISTS dock_blur_strength INTEGER;
ALTER TABLE users ADD COLUMN IF NOT EXISTS dock_icon_size INTEGER;
ALTER TABLE users ADD COLUMN IF NOT EXISTS dock_icon_padding INTEGER DEFAULT 11;
ALTER TABLE users ADD COLUMN IF NOT EXISTS dock_max_scale DOUBLE PRECISION;
ALTER TABLE users ADD COLUMN IF NOT EXISTS dock_animation_speed DOUBLE PRECISION;
ALTER TABLE users ADD COLUMN IF NOT EXISTS dock_icon_style VARCHAR(20);

-- Photos table
CREATE TABLE IF NOT EXISTS photos (
    id BIGSERIAL PRIMARY KEY,
    file_path VARCHAR(1024) NOT NULL,
    file_hash_md5 VARCHAR(64),
    file_hash_phash VARCHAR(64),
    exif_date TIMESTAMP,
    gps_lat DOUBLE PRECISION,
    gps_lng DOUBLE PRECISION,
    rating INTEGER CHECK (rating >= 0 AND rating <= 5),
    note TEXT,
    ai_caption TEXT,
    embedding vector(512),
    width INTEGER,
    height INTEGER,
    file_size BIGINT,
    media_type VARCHAR(20) NOT NULL DEFAULT 'PHOTO',
    favorite BOOLEAN DEFAULT FALSE,
    original_filename VARCHAR(255),
    source_folder_id BIGINT,
    in_timeline BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Migration: Add in_timeline column to photos (idempotent)
ALTER TABLE photos ADD COLUMN IF NOT EXISTS in_timeline BOOLEAN DEFAULT FALSE;
ALTER TABLE photos ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;
CREATE INDEX IF NOT EXISTS idx_photos_deleted_at ON photos (deleted_at);

-- Albums table
CREATE TABLE IF NOT EXISTS albums (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(1024) NOT NULL,
    description VARCHAR(1000),
    type VARCHAR(20) NOT NULL DEFAULT 'VIRTUAL',
    cover_photo_id BIGINT REFERENCES photos(id) ON DELETE SET NULL,
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

-- Add foreign key for people.cover_face_id (idempotent: drop then add)
ALTER TABLE people DROP CONSTRAINT IF EXISTS fk_people_cover_face;
ALTER TABLE people ADD CONSTRAINT fk_people_cover_face
    FOREIGN KEY (cover_face_id) REFERENCES face_clusters(id);

-- Tags table
CREATE TABLE IF NOT EXISTS tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    color VARCHAR(7),
    type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    category VARCHAR(50),
    description VARCHAR(500),
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Migration: Add new columns to tags table (idempotent)
ALTER TABLE tags ADD COLUMN IF NOT EXISTS description VARCHAR(500);
ALTER TABLE tags ADD COLUMN IF NOT EXISTS sort_order INTEGER DEFAULT 0;

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

-- Categories table
CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    icon VARCHAR(50),
    color VARCHAR(7),
    is_system BOOLEAN DEFAULT FALSE,
    cover_photo_id BIGINT REFERENCES photos(id) ON DELETE SET NULL,
    prototype_vector vector(512),
    threshold DOUBLE PRECISION NOT NULL DEFAULT 0.7,
    photo_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Photo-Category many-to-many
CREATE TABLE IF NOT EXISTS photo_categories (
    category_id BIGINT REFERENCES categories(id) ON DELETE CASCADE,
    photo_id BIGINT REFERENCES photos(id) ON DELETE CASCADE,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(20) DEFAULT 'auto',
    PRIMARY KEY (category_id, photo_id)
);

-- Scan Folders table
CREATE TABLE IF NOT EXISTS scan_folders (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    path VARCHAR(1024) NOT NULL UNIQUE,
    description TEXT,
    storage_mode VARCHAR(10) NOT NULL DEFAULT 'COPY',
    scan_status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    last_scan_at TIMESTAMP,
    photo_count INTEGER DEFAULT 0,
    video_count INTEGER DEFAULT 0,
    file_count INTEGER DEFAULT 0,
    scan_progress INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    hidden BOOLEAN DEFAULT FALSE,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Migration: Add new columns to scan_folders table (idempotent)
ALTER TABLE scan_folders ADD COLUMN IF NOT EXISTS video_count INTEGER DEFAULT 0;
ALTER TABLE scan_folders ADD COLUMN IF NOT EXISTS file_count INTEGER DEFAULT 0;
ALTER TABLE scan_folders ADD COLUMN IF NOT EXISTS scan_progress INTEGER DEFAULT 0;
ALTER TABLE scan_folders ADD COLUMN IF NOT EXISTS enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE scan_folders ADD COLUMN IF NOT EXISTS hidden BOOLEAN DEFAULT FALSE;
ALTER TABLE scan_folders ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE scan_folders ALTER COLUMN name TYPE VARCHAR(1024);

-- Foreign key: photos.source_folder_id -> scan_folders.id (idempotent: drop then add)
ALTER TABLE photos DROP CONSTRAINT IF EXISTS fk_photos_source_folder;
ALTER TABLE photos ADD CONSTRAINT fk_photos_source_folder
    FOREIGN KEY (source_folder_id) REFERENCES scan_folders(id) ON DELETE SET NULL;

-- Unique index for categories name (ensures ON CONFLICT works)
CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_name_unique ON categories (name);

-- Indexes
-- Embedding indexes
CREATE INDEX IF NOT EXISTS idx_photos_embedding ON photos USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_face_clusters_embedding ON face_clusters USING hnsw (embedding vector_cosine_ops);
CREATE INDEX IF NOT EXISTS idx_photos_fts ON photos USING gin(to_tsvector('simple', coalesce(note, '') || ' ' || coalesce(ai_caption, '')));
CREATE INDEX IF NOT EXISTS idx_photos_exif_date ON photos (exif_date DESC);
CREATE INDEX IF NOT EXISTS idx_photos_md5 ON photos (file_hash_md5);
CREATE INDEX IF NOT EXISTS idx_photos_phash ON photos (file_hash_phash);
CREATE INDEX IF NOT EXISTS idx_face_clusters_person ON face_clusters (person_id);
CREATE INDEX IF NOT EXISTS idx_album_photos_album ON album_photos (album_id);
CREATE INDEX IF NOT EXISTS idx_photo_tags_photo ON photo_tags (photo_id);
CREATE INDEX IF NOT EXISTS idx_user_settings_user ON user_settings (user_id);
CREATE INDEX IF NOT EXISTS idx_categories_is_system ON categories (is_system);
CREATE INDEX IF NOT EXISTS idx_photo_categories_category ON photo_categories (category_id);
CREATE INDEX IF NOT EXISTS idx_photo_categories_photo ON photo_categories (photo_id);
CREATE INDEX IF NOT EXISTS idx_photos_source_folder ON photos (source_folder_id);

-- Index for timeline photos
CREATE INDEX IF NOT EXISTS idx_photos_in_timeline ON photos (in_timeline) WHERE in_timeline = TRUE;
CREATE INDEX IF NOT EXISTS idx_photos_rating ON photos (rating);
CREATE INDEX IF NOT EXISTS idx_photos_source_folder_enabled ON scan_folders (enabled);
CREATE INDEX IF NOT EXISTS idx_tags_sort_order ON tags (sort_order);

-- Migration: Add category column to tags table (idempotent)
ALTER TABLE tags ADD COLUMN IF NOT EXISTS category VARCHAR(50);

-- Default admin user (password: admin123)
INSERT INTO users (username, password_hash, role) VALUES
    ('admin', '$2a$10$ZIEvrdNQ8X8Nr88UCEypDOVaKM5KIt.0w.UPJaQqpiwwhIB5UtqzW', 'ADMIN')
ON CONFLICT (username) DO NOTHING;

-- Predefined system categories
INSERT INTO categories (name, icon, color, is_system) VALUES
    ('风景', 'landscape', '#34c759', TRUE),
    ('人物', 'person', '#007aff', TRUE),
    ('美食', 'food', '#ff9500', TRUE),
    ('动物', 'animal', '#af52de', TRUE),
    ('建筑', 'building', '#5856d6', TRUE),
    ('植物', 'plant', '#30d158', TRUE),
    ('旅行', 'travel', '#ff2d55', TRUE),
    ('活动', 'event', '#ff9f0a', TRUE),
    ('截图', 'screenshot', '#8e8e93', TRUE),
    ('文档', 'document', '#636366', TRUE)
ON CONFLICT (name) DO NOTHING;
