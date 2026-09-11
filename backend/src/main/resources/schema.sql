-- aiphoto Database Schema
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
ALTER TABLE users ADD COLUMN IF NOT EXISTS dock_icon_gap INTEGER DEFAULT 11;
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

-- Web crawler rules and staging area
CREATE TABLE IF NOT EXISTS crawler_settings (
    id BIGINT PRIMARY KEY,
    direct_fallback BOOLEAN NOT NULL DEFAULT TRUE,
    connect_timeout_seconds INTEGER NOT NULL DEFAULT 10,
    request_timeout_seconds INTEGER NOT NULL DEFAULT 30,
    min_request_interval_millis BIGINT NOT NULL DEFAULT 1000,
    max_retries INTEGER NOT NULL DEFAULT 2,
    retry_base_delay_millis BIGINT NOT NULL DEFAULT 1000,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE crawler_settings ADD COLUMN IF NOT EXISTS max_retries INTEGER NOT NULL DEFAULT 2;
ALTER TABLE crawler_settings ADD COLUMN IF NOT EXISTS retry_base_delay_millis BIGINT NOT NULL DEFAULT 1000;
INSERT INTO crawler_settings (
    id, direct_fallback, connect_timeout_seconds,
    request_timeout_seconds, min_request_interval_millis
) VALUES (1, TRUE, 10, 30, 1000)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS crawler_proxies (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL,
    username VARCHAR(255),
    password TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    priority INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_crawler_proxies_enabled_priority
    ON crawler_proxies(enabled, priority, id);

CREATE TABLE IF NOT EXISTS crawl_sites (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    start_url VARCHAR(2048) NOT NULL,
    allowed_hosts VARCHAR(1000) NOT NULL,
    max_list_pages INTEGER NOT NULL DEFAULT 100,
    max_detail_pages INTEGER NOT NULL DEFAULT 1000,
    max_images INTEGER NOT NULL DEFAULT 5000,
    max_file_bytes BIGINT NOT NULL DEFAULT 20971520,
    migrated_rule_id BIGINT UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS crawl_rules (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    site_id BIGINT REFERENCES crawl_sites(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    start_url VARCHAR(2048) NOT NULL,
    detail_selector VARCHAR(500) NOT NULL,
    detail_url_includes VARCHAR(1000),
    detail_url_excludes VARCHAR(1000),
    next_selector VARCHAR(500),
    image_selector VARCHAR(500) NOT NULL DEFAULT 'img',
    image_attributes VARCHAR(200) DEFAULT 'data-original,data-src,srcset,src',
    image_url_includes VARCHAR(1000),
    image_url_excludes VARCHAR(1000),
    detail_next_selector VARCHAR(500),
    max_pages_per_detail INTEGER NOT NULL DEFAULT 20,
    allowed_hosts VARCHAR(1000) NOT NULL,
    max_list_pages INTEGER NOT NULL DEFAULT 100,
    max_detail_pages INTEGER NOT NULL DEFAULT 1000,
    max_images INTEGER NOT NULL DEFAULT 5000,
    max_file_bytes BIGINT NOT NULL DEFAULT 20971520,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE crawl_rules ADD COLUMN IF NOT EXISTS detail_url_includes VARCHAR(1000);
ALTER TABLE crawl_rules ADD COLUMN IF NOT EXISTS detail_url_excludes VARCHAR(1000);
ALTER TABLE crawl_rules ADD COLUMN IF NOT EXISTS image_url_includes VARCHAR(1000);
ALTER TABLE crawl_rules ADD COLUMN IF NOT EXISTS image_url_excludes VARCHAR(1000);
ALTER TABLE crawl_rules ADD COLUMN IF NOT EXISTS site_id BIGINT REFERENCES crawl_sites(id) ON DELETE CASCADE;
ALTER TABLE crawl_rules ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT FALSE;
INSERT INTO crawl_sites (owner_id, name, start_url, allowed_hosts, max_list_pages,
    max_detail_pages, max_images, max_file_bytes, migrated_rule_id, created_at, updated_at)
SELECT owner_id, name, start_url, allowed_hosts, max_list_pages, max_detail_pages,
    max_images, max_file_bytes, id, created_at, updated_at
FROM crawl_rules WHERE site_id IS NULL
ON CONFLICT (migrated_rule_id) DO NOTHING;
UPDATE crawl_rules r SET site_id = s.id
FROM crawl_sites s WHERE r.site_id IS NULL AND s.migrated_rule_id = r.id;
UPDATE crawl_rules r SET enabled = TRUE
FROM crawl_sites s WHERE r.id = s.migrated_rule_id AND r.site_id = s.id;
UPDATE crawl_sites SET migrated_rule_id = NULL WHERE migrated_rule_id IS NOT NULL;
ALTER TABLE crawl_rules ALTER COLUMN site_id SET NOT NULL;
ALTER TABLE crawl_rules ALTER COLUMN start_url DROP NOT NULL;
ALTER TABLE crawl_rules ALTER COLUMN allowed_hosts DROP NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_crawl_rules_one_enabled_per_site
    ON crawl_rules(site_id) WHERE enabled = TRUE;
CREATE INDEX IF NOT EXISTS idx_crawl_sites_owner_updated ON crawl_sites(owner_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_crawl_rules_site_updated ON crawl_rules(site_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS crawl_jobs (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rule_id BIGINT NOT NULL REFERENCES crawl_rules(id) ON DELETE RESTRICT,
    rule_snapshot TEXT NOT NULL,
    name VARCHAR(200) NOT NULL,
    phase VARCHAR(30) NOT NULL DEFAULT 'DISCOVERY',
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    list_processed INTEGER NOT NULL DEFAULT 0,
    pages_found INTEGER NOT NULL DEFAULT 0,
    pages_processed INTEGER NOT NULL DEFAULT 0,
    images_downloaded INTEGER NOT NULL DEFAULT 0,
    fail_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    lease_owner VARCHAR(100),
    lease_until TIMESTAMP,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP
);
ALTER TABLE crawl_jobs ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(100);
ALTER TABLE crawl_jobs ADD COLUMN IF NOT EXISTS lease_until TIMESTAMP;
ALTER TABLE crawl_jobs ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_runnable ON crawl_jobs(status, lease_until, created_at);

CREATE TABLE IF NOT EXISTS crawl_pages (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES crawl_jobs(id) ON DELETE CASCADE,
    url VARCHAR(4096) NOT NULL,
    normalized_url VARCHAR(4096) NOT NULL,
    url_hash VARCHAR(64) NOT NULL,
    included BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(job_id, url_hash)
);
ALTER TABLE crawl_pages ADD COLUMN IF NOT EXISTS included BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS crawl_assets (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES crawl_jobs(id) ON DELETE CASCADE,
    page_id BIGINT NOT NULL REFERENCES crawl_pages(id) ON DELETE CASCADE,
    source_page_url VARCHAR(4096) NOT NULL,
    image_url VARCHAR(4096) NOT NULL,
    normalized_url VARCHAR(4096) NOT NULL,
    url_hash VARCHAR(64) NOT NULL,
    local_path VARCHAR(1024),
    thumbnail_path VARCHAR(1024),
    original_filename VARCHAR(255),
    content_type VARCHAR(100),
    file_hash_md5 VARCHAR(64),
    file_hash_phash VARCHAR(16),
    similarity_group_id BIGINT,
    similarity_count INTEGER NOT NULL DEFAULT 0,
    file_size BIGINT,
    width INTEGER,
    height INTEGER,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    imported_photo_id BIGINT REFERENCES photos(id) ON DELETE SET NULL,
    note TEXT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(job_id, url_hash)
);
ALTER TABLE crawl_assets ADD COLUMN IF NOT EXISTS file_hash_phash VARCHAR(16);
ALTER TABLE crawl_assets ADD COLUMN IF NOT EXISTS similarity_group_id BIGINT;
ALTER TABLE crawl_assets ADD COLUMN IF NOT EXISTS similarity_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE crawl_assets ADD COLUMN IF NOT EXISTS note TEXT;

CREATE INDEX IF NOT EXISTS idx_crawl_jobs_owner_created ON crawl_jobs(owner_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_crawl_pages_job_status ON crawl_pages(job_id, status);
CREATE INDEX IF NOT EXISTS idx_crawl_assets_job_status ON crawl_assets(job_id, status);
CREATE INDEX IF NOT EXISTS idx_crawl_assets_md5 ON crawl_assets(file_hash_md5);
CREATE INDEX IF NOT EXISTS idx_crawl_assets_similarity
    ON crawl_assets(job_id, similarity_group_id) WHERE similarity_group_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS crawl_import_batches (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id BIGINT NOT NULL REFERENCES crawl_jobs(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    requested_count INTEGER NOT NULL DEFAULT 0,
    success_count INTEGER NOT NULL DEFAULT 0,
    fail_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    album_id BIGINT REFERENCES albums(id) ON DELETE SET NULL,
    asset_ids_json TEXT NOT NULL,
    tag_ids_json TEXT,
    lease_owner VARCHAR(100),
    lease_until TIMESTAMP,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    UNIQUE(owner_id, idempotency_key)
);
ALTER TABLE crawl_import_batches ADD COLUMN IF NOT EXISTS lease_owner VARCHAR(100);
ALTER TABLE crawl_import_batches ADD COLUMN IF NOT EXISTS lease_until TIMESTAMP;
ALTER TABLE crawl_import_batches ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE crawl_import_batches ALTER COLUMN status SET DEFAULT 'QUEUED';
CREATE INDEX IF NOT EXISTS idx_crawl_import_batches_runnable
    ON crawl_import_batches(status, lease_until, created_at);

CREATE TABLE IF NOT EXISTS crawl_import_items (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES crawl_import_batches(id) ON DELETE CASCADE,
    asset_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    photo_id BIGINT REFERENCES photos(id) ON DELETE SET NULL,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(batch_id, asset_id)
);
CREATE INDEX IF NOT EXISTS idx_crawl_import_batches_job_created
    ON crawl_import_batches(job_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_crawl_import_items_batch_status
    ON crawl_import_items(batch_id, status, id);

CREATE TABLE IF NOT EXISTS photo_sources (
    id BIGSERIAL PRIMARY KEY,
    photo_id BIGINT NOT NULL REFERENCES photos(id) ON DELETE CASCADE,
    page_url VARCHAR(4096) NOT NULL,
    image_url VARCHAR(4096) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_photo_sources_photo ON photo_sources(photo_id);

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
