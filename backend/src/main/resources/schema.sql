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
    schedule_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    daily_scan_time TIME NOT NULL DEFAULT '03:00:00',
    last_scheduled_scan_date DATE,
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
    min_request_interval_millis BIGINT NOT NULL DEFAULT 1000,
    migrated_rule_id BIGINT UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE crawl_sites ADD COLUMN IF NOT EXISTS min_request_interval_millis BIGINT NOT NULL DEFAULT 1000;
ALTER TABLE crawl_sites ADD COLUMN IF NOT EXISTS schedule_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE crawl_sites ADD COLUMN IF NOT EXISTS daily_scan_time TIME NOT NULL DEFAULT '03:00:00';
ALTER TABLE crawl_sites ADD COLUMN IF NOT EXISTS last_scheduled_scan_date DATE;
-- Website inspection uses its own network configuration, independent of task executors.
ALTER TABLE crawl_sites ADD COLUMN IF NOT EXISTS inspection_proxy_mode VARCHAR(16) NOT NULL DEFAULT 'DEFAULT';
ALTER TABLE crawl_sites ADD COLUMN IF NOT EXISTS inspection_proxy_id BIGINT;
ALTER TABLE crawl_sites ADD COLUMN IF NOT EXISTS discovery_urls TEXT;

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
CREATE INDEX IF NOT EXISTS idx_crawl_sites_schedule ON crawl_sites(schedule_enabled, daily_scan_time);
CREATE INDEX IF NOT EXISTS idx_crawl_rules_site_updated ON crawl_rules(site_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS crawl_jobs (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    site_id BIGINT NOT NULL REFERENCES crawl_sites(id) ON DELETE RESTRICT,
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
ALTER TABLE crawl_jobs ADD COLUMN IF NOT EXISTS site_id BIGINT REFERENCES crawl_sites(id) ON DELETE RESTRICT;
UPDATE crawl_jobs j SET site_id = r.site_id
FROM crawl_rules r WHERE j.site_id IS NULL AND j.rule_id = r.id;
ALTER TABLE crawl_jobs ALTER COLUMN site_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_runnable ON crawl_jobs(status, lease_until, created_at);
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_site_created ON crawl_jobs(site_id, created_at DESC);

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

CREATE TABLE IF NOT EXISTS crawl_seen_pages (
    id BIGSERIAL PRIMARY KEY,
    site_id BIGINT NOT NULL REFERENCES crawl_sites(id) ON DELETE CASCADE,
    normalized_url VARCHAR(4096) NOT NULL,
    url_hash VARCHAR(64) NOT NULL,
    first_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(site_id, url_hash)
);
INSERT INTO crawl_seen_pages (site_id, normalized_url, url_hash, first_seen_at)
SELECT DISTINCT ON (j.site_id, p.url_hash)
    j.site_id, p.normalized_url, p.url_hash, p.created_at
FROM crawl_pages p JOIN crawl_jobs j ON j.id = p.job_id
ON CONFLICT (site_id, url_hash) DO NOTHING;
CREATE INDEX IF NOT EXISTS idx_crawl_seen_pages_site_seen
    ON crawl_seen_pages(site_id, first_seen_at DESC);

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

CREATE TABLE IF NOT EXISTS crawl_asset_sources (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL REFERENCES crawl_assets(id) ON DELETE CASCADE,
    page_id BIGINT NOT NULL REFERENCES crawl_pages(id) ON DELETE CASCADE,
    source_page_url VARCHAR(4096) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(asset_id, page_id)
);
INSERT INTO crawl_asset_sources (asset_id, page_id, source_page_url, created_at)
SELECT id, page_id, source_page_url, created_at FROM crawl_assets
ON CONFLICT (asset_id, page_id) DO NOTHING;
CREATE INDEX IF NOT EXISTS idx_crawl_asset_sources_page
    ON crawl_asset_sources(page_id, asset_id);

CREATE TABLE IF NOT EXISTS crawl_image_skips (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    image_url VARCHAR(4096) NOT NULL,
    normalized_url VARCHAR(4096) NOT NULL,
    url_hash VARCHAR(64) NOT NULL,
    source_page_url VARCHAR(4096),
    reason VARCHAR(500),
    skip_count BIGINT NOT NULL DEFAULT 0,
    last_skipped_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(owner_id, url_hash)
);
CREATE INDEX IF NOT EXISTS idx_crawl_image_skips_owner_created
    ON crawl_image_skips(owner_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_crawl_jobs_owner_created ON crawl_jobs(owner_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_crawl_pages_job_status ON crawl_pages(job_id, status);
CREATE INDEX IF NOT EXISTS idx_crawl_assets_job_status ON crawl_assets(job_id, status);
CREATE INDEX IF NOT EXISTS idx_crawl_assets_job_page ON crawl_assets(job_id, page_id, id DESC);
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
-- AI stage completion is saved atomically with results, so retries can resume safely.
ALTER TABLE photos ADD COLUMN IF NOT EXISTS faces_indexed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE photos ADD COLUMN IF NOT EXISTS tags_indexed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE photos ADD COLUMN IF NOT EXISTS caption_indexed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE photos ADD COLUMN IF NOT EXISTS ai_owner_id BIGINT;
ALTER TABLE scan_folders ADD COLUMN IF NOT EXISTS ai_owner_id BIGINT;
UPDATE photos p SET faces_indexed = TRUE
WHERE faces_indexed = FALSE AND EXISTS (SELECT 1 FROM face_clusters f WHERE f.photo_id = p.id);
UPDATE photos SET caption_indexed = TRUE
WHERE caption_indexed = FALSE AND ai_caption IS NOT NULL AND btrim(ai_caption) <> '';
-- Repair historical counts without deleting faces or changing manual assignments.
UPDATE people person SET photo_count = (
    SELECT COUNT(DISTINCT face.photo_id) FROM face_clusters face
    JOIN photos photo ON photo.id = face.photo_id
    WHERE face.person_id = person.id AND photo.deleted_at IS NULL
);

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

-- 0.11.0: Shared HTTP/Mihomo proxies and crawler executor configuration.
ALTER TABLE crawler_proxies ADD COLUMN IF NOT EXISTS proxy_type VARCHAR(16) NOT NULL DEFAULT 'HTTP';
ALTER TABLE crawler_proxies ADD COLUMN IF NOT EXISTS controller_url VARCHAR(500);
ALTER TABLE crawler_proxies ADD COLUMN IF NOT EXISTS secret TEXT;
CREATE TABLE IF NOT EXISTS crawler_executors (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    proxy_mode VARCHAR(16) NOT NULL DEFAULT 'DEFAULT' CHECK (proxy_mode IN ('DEFAULT','DIRECT','SELECTED','MIHOMO')),
    proxy_id BIGINT REFERENCES crawler_proxies(id),
    proxy_group VARCHAR(255),
    nodes_json TEXT NOT NULL DEFAULT '[]',
    switch_mode VARCHAR(16) NOT NULL DEFAULT 'MANUAL' CHECK (switch_mode IN ('MANUAL','FAILOVER','REQUESTS')),
    rotate_after_requests INTEGER NOT NULL DEFAULT 100,
    cooldown_seconds INTEGER NOT NULL DEFAULT 300
);
INSERT INTO crawler_executors (name) SELECT '默认执行器' WHERE NOT EXISTS (SELECT 1 FROM crawler_executors);

ALTER TABLE crawler_executors ADD COLUMN IF NOT EXISTS current_node VARCHAR(255);
ALTER TABLE crawler_executors ADD COLUMN IF NOT EXISTS completed_requests INTEGER NOT NULL DEFAULT 0;
ALTER TABLE crawler_executors ADD COLUMN IF NOT EXISTS cooldowns_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE crawler_executors ADD COLUMN IF NOT EXISTS last_error VARCHAR(500);

-- 0.12.0: Expanded executor policies, reusable node groups and execution diagnostics.
ALTER TABLE crawler_executors ADD COLUMN IF NOT EXISTS options_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE crawler_executors ADD COLUMN IF NOT EXISTS runtime_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE crawler_executors DROP CONSTRAINT IF EXISTS crawler_executors_switch_mode_check;
ALTER TABLE crawler_executors ADD CONSTRAINT crawler_executors_switch_mode_check
    CHECK (switch_mode IN ('MANUAL','FAILOVER','REQUESTS','TIME','PAGE','IMAGE','TASK'));
CREATE TABLE IF NOT EXISTS crawler_node_groups (
    id BIGSERIAL PRIMARY KEY,
    proxy_id BIGINT NOT NULL REFERENCES crawler_proxies(id),
    name VARCHAR(100) NOT NULL,
    control_group VARCHAR(255) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL CHECK (port BETWEEN 1 AND 65535),
    nodes_json TEXT NOT NULL DEFAULT '[]'
);
CREATE INDEX IF NOT EXISTS idx_crawler_node_groups_proxy ON crawler_node_groups(proxy_id);
CREATE TABLE IF NOT EXISTS crawler_execution_logs (
    id BIGSERIAL PRIMARY KEY,
    executor_id BIGINT REFERENCES crawler_executors(id) ON DELETE SET NULL,
    job_id BIGINT,
    executor_name VARCHAR(100),
    proxy_name VARCHAR(100),
    proxy_node VARCHAR(255),
    url TEXT,
    failed BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INTEGER NOT NULL DEFAULT 1,
    duration_millis BIGINT NOT NULL DEFAULT 0,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_crawler_execution_logs_executor ON crawler_execution_logs(executor_id, id DESC);

CREATE TABLE IF NOT EXISTS crawler_executor_receipts (
    id BIGSERIAL PRIMARY KEY,
    executor_id BIGINT NOT NULL REFERENCES crawler_executors(id) ON DELETE CASCADE,
    receipt_key VARCHAR(150) NOT NULL,
    UNIQUE (executor_id, receipt_key)
);

ALTER TABLE crawler_execution_logs ADD COLUMN IF NOT EXISTS job_name VARCHAR(200);
ALTER TABLE crawler_execution_logs ADD COLUMN IF NOT EXISTS site_name VARCHAR(200);
ALTER TABLE crawler_execution_logs ADD COLUMN IF NOT EXISTS phase VARCHAR(30);

-- Independent scan entries and one collection job per discovered page (0.17.0).
CREATE TABLE IF NOT EXISTS crawl_scan_pages (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    site_id BIGINT NOT NULL REFERENCES crawl_sites(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    auto_scan_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    scan_interval_minutes INTEGER NOT NULL DEFAULT 360 CHECK (scan_interval_minutes BETWEEN 5 AND 10080),
    max_pages INTEGER NOT NULL DEFAULT 100 CHECK (max_pages BETWEEN 1 AND 1000),
    last_scan_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE crawl_jobs ADD COLUMN IF NOT EXISTS scan_page_id BIGINT REFERENCES crawl_scan_pages(id) ON DELETE SET NULL;
ALTER TABLE crawl_jobs ADD COLUMN IF NOT EXISTS source_page_id BIGINT;
ALTER TABLE crawl_pages ADD COLUMN IF NOT EXISTS collection_job_id BIGINT REFERENCES crawl_jobs(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_crawl_jobs_scan_status ON crawl_jobs(scan_page_id, status);
CREATE INDEX IF NOT EXISTS idx_crawl_scan_pages_auto ON crawl_scan_pages(auto_scan_enabled, last_scan_at);
CREATE INDEX IF NOT EXISTS idx_crawl_pages_collection ON crawl_pages(collection_job_id);

-- Migrate each legacy entry once; deleted scan pages must not reappear on restart.
ALTER TABLE crawl_sites ADD COLUMN IF NOT EXISTS scan_pages_migrated BOOLEAN NOT NULL DEFAULT FALSE;
INSERT INTO crawl_scan_pages (owner_id, site_id, name, url, auto_scan_enabled,
    scan_interval_minutes, max_pages, last_scan_at)
SELECT s.owner_id, s.id, LEFT(s.name, 75) || ' · ' || entry.ordinality,
    TRIM(entry.url), s.schedule_enabled, 1440, s.max_list_pages,
    CASE WHEN s.schedule_enabled THEN CURRENT_DATE + s.daily_scan_time - INTERVAL '1 day' ELSE NULL END
FROM crawl_sites s CROSS JOIN LATERAL regexp_split_to_table(
    COALESCE(NULLIF(TRIM(s.discovery_urls), ''), s.start_url), E'\n')
    WITH ORDINALITY AS entry(url, ordinality)
WHERE s.scan_pages_migrated = FALSE AND TRIM(entry.url) <> '';
UPDATE crawl_sites SET schedule_enabled = FALSE, scan_pages_migrated = TRUE WHERE scan_pages_migrated = FALSE;
ALTER TABLE crawl_sites ALTER COLUMN scan_pages_migrated SET DEFAULT TRUE;

-- Domain policy changed from an allowlist to an empty-by-default denylist (0.18.0).
-- Retain the legacy column for older schema backfills; never turn allowed domains into blocked ones.
ALTER TABLE crawl_sites ADD COLUMN IF NOT EXISTS blocked_hosts VARCHAR(1000) NOT NULL DEFAULT '';
ALTER TABLE crawl_sites ALTER COLUMN allowed_hosts SET DEFAULT '';
