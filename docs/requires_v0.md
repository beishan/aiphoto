# MemoryVault — AI 智能相册应用完整实现方案

> Java Spring Boot 3 + Vue 3 + Python AI 微服务 | NAS Docker 部署 | NVIDIA RTX 2060 GPU 加速

---

## 目录

1. [项目定位与命名](#1-项目定位与命名)
2. [整体架构设计](#2-整体架构设计)
3. [技术选型详情](#3-技术选型详情)
   - 3.1 [前端：Vue 3 技术栈](#31-前端vue-3-技术栈)
   - 3.2 [后端：Java Spring Boot 3 技术栈](#32-后端java-spring-boot-3-技术栈)
   - 3.3 [AI 推理服务：Python FastAPI](#33-ai-推理服务python-fastapi)
   - 3.4 [数据库与存储](#34-数据库与存储)
4. [页面风格设计](#4-页面风格设计)
5. [功能模块详细设计](#5-功能模块详细设计)
6. [核心数据库设计](#6-核心数据库设计)
7. [Docker 部署配置](#7-docker-部署配置)
8. [项目目录结构](#8-项目目录结构)
9. [开发规划与里程碑](#9-开发规划与里程碑)
10. [硬件资源评估](#10-硬件资源评估)

---

## 1. 项目定位与命名

**项目名：MemoryVault**（记忆保险库）

面向家庭 / 个人的私有化部署 AI 相册系统，运行于 NAS Docker 环境，支持 NVIDIA GPU 加速。核心价值主张：

- **数据不出门**：所有照片、视频、人脸数据存储在自家 NAS，不依赖任何云服务
- **AI 全本地**：CLIP、InsightFace、YOLOv8 全部在本地 GPU 运行
- **家庭级功能**：宝宝相册、成长时间线、人物履历
- **技术选型主流**：Java + Vue + Python AI 微服务混合架构

---

## 2. 整体架构设计

### 2.1 架构分层

| 层次 | 技术组件 | 职责 |
|------|---------|------|
| 用户层 | 浏览器 / PWA | 访问入口，支持安装到手机 |
| 前端层 | Vue 3 + Vite + Naive UI | SPA 应用，Nginx 静态服务 |
| 网关层 | Nginx 反代理 | 路由分发：`/api` → Java，`/ai` → Python |
| 业务层 | Spring Boot 3 (Java 21) | REST API、WebSocket、业务逻辑 |
| AI 层 | FastAPI (Python 3.11) | CLIP / InsightFace / YOLO 推理 |
| 任务层 | Spring Async + RabbitMQ | 异步任务队列、进度推送 |
| 数据层 | PostgreSQL 16 + pgvector | 主数据库 + 向量存储 |
| 缓存层 | Redis 7 | 会话、缓存、消息队列 |
| 存储层 | MinIO + NAS 目录挂载 | 原图、缩略图、视频对象存储 |

### 2.2 服务间调用关系

Java Spring Boot 层负责所有业务逻辑、用户认证、数据存储。当需要 AI 能力时，通过内部 HTTP 调用 Python AI 服务，接口设计如下：

| API 接口 | 输入 | 输出 | 调用方 |
|---------|------|------|--------|
| `POST /ai/embed` | 图片文件或 URL | 512 维向量 | Spring 调用 |
| `POST /ai/detect-faces` | 图片文件 | 人脸框 + embedding | Spring 调用 |
| `POST /ai/classify` | 图片文件 | YOLOv8 标签列表 | Spring 调用 |
| `POST /ai/caption` | 图片文件 | BLIP-2 描述文本 | Spring 调用 |
| `POST /ai/batch-embed` | 图片列表 | 批量向量结果 | Spring Async 调用 |

> Python 服务作为独立容器运行，仅负责模型封装，不包含任何业务逻辑。两个容器都在同一个 docker-compose 里，对外只暴露 Java 的端口。

---

## 3. 技术选型详情

### 3.1 前端：Vue 3 技术栈

| 分类 | 技术 / 库 | 版本 | 用途 |
|------|---------|------|------|
| 核心框架 | Vue 3 + Vite 5 | 3.x / 5.x | Composition API，快速构建 |
| UI 组件库 | Naive UI | 2.x | 暗色模式支持，组件丰富 |
| 状态管理 | Pinia | 2.x | Vue 3 官方推荐 |
| 路由 | Vue Router 4 | 4.x | 懒加载路由 |
| 瀑布流布局 | vue-waterfall-plugin-next | 最新 | 照片墙标准方案 |
| HTTP 客户端 | Axios | 1.x | 配合拦截器处理 Token |
| WebSocket | @stomp/stompjs | 最新 | 任务进度实时推送 |
| 视频播放 | Plyr.js | 3.x | 支持 H264/WebM/字幕/倍速 |
| 地图 | Leaflet.js | 1.x | GPS 聚类地图时间线 |
| 图片预览 | PhotoSwipe 5 | 5.x | 全屏查看器，手势支持 |
| 虚拟滚动 | vueuse/virtual-list | 最新 | 万张照片性能保障 |
| PWA | vite-plugin-pwa | 最新 | 支持安装到手机 |

### 3.2 后端：Java Spring Boot 3 技术栈

| 分类 | 技术 / 库 | 版本 | 用途 |
|------|---------|------|------|
| 核心框架 | Spring Boot 3 | 3.3.x | Web / 安全 / JPA / WebSocket |
| Java 版本 | Java 21 (LTS) | 21 | 虚拟线程 (Virtual Threads) 支持 |
| ORM | Spring Data JPA + Hibernate | 6.x | 配合 PostgreSQL |
| 数据库连接池 | HikariCP | 内置 | 高性能连接池 |
| 认证 | Spring Security + JJWT | 最新 | JWT + HTTPOnly Cookie |
| 异步任务 | Spring @Async + RabbitMQ | 3.x | AI 推理 / 转码异步处理 |
| WebSocket | Spring WebSocket (STOMP) | 内置 | 任务进度推送 |
| 文件存储 | MinIO Java SDK | 8.x | S3 兼容 API |
| 图片处理 | Thumbnailator + Imgscalr | 最新 | 缩略图生成、旋转 |
| 视频处理 | FFmpeg-cli-wrapper (Java) | 最新 | 调用 FFmpeg 进行转码截帧 |
| EXIF 解析 | metadata-extractor | 2.x | 读取拍摄时间、GPS 等信息 |
| 对象存储 | AWS S3 SDK (MinIO 兼容) | 2.x | 文件上传 / 下载 / 预签名 URL |
| 关系映射 | MapStruct | 1.x | DTO / Entity 转换 |
| API 文档 | SpringDoc OpenAPI 3 | 2.x | 自动生成 Swagger UI |
| 对象缓存 | Redis (Spring Cache) | 内置 | 缩略图 URL、用户信息缓存 |

### 3.3 AI 推理服务：Python FastAPI

AI 服务作为独立容器运行，仅负责模型封装，不包含任何业务逻辑。

| 功能 | 模型 / 库 | 显存占用 | 处理速度 |
|------|---------|---------|---------|
| 语义向量化 | CLIP (ViT-B/32) via OpenCLIP | ~1.2 GB | ~50 张/秒 |
| 人脸识别 | InsightFace (buffalo_l) | ~0.8 GB | ~20 张/秒 |
| 目标检测/分类 | YOLOv8n | ~0.5 GB | ~60 张/秒 |
| 图片描述生成 | BLIP-2（按需加载） | ~4.5 GB | ~3 张/秒 |
| 三模型并行 (no BLIP) | 流水线处理 | ~2.5 GB | 应对日常增量 |

**Python 服务依赖：**
`FastAPI`、`Uvicorn`、`PyTorch 2.x (CUDA 12.1)`、`OpenCLIP`、`InsightFace`、`Ultralytics (YOLOv8)`、`transformers (BLIP-2)`

### 3.4 数据库与存储

| 组件 | 版本 | 用途 | 备注 |
|------|------|------|------|
| PostgreSQL | 16 | 主数据库 | 配合 pgvector 扩展存储向量 |
| pgvector | 0.7+ | 向量存储 & 检索 | 替代独立 Milvus，减少容器数量 |
| Redis | 7 | 缓存 / 消息队列 | Spring Cache + RabbitMQ Broker |
| RabbitMQ | 3.x | 异步任务队列 | AI 推理、转码、批量操作 |
| MinIO | 最新稳定版 | 对象存储 | S3 兼容，挂载 NAS 目录 |

---

## 4. 页面风格设计

### 4.1 设计语言：深空暗色 + 极简信息密度

| 设计要素 | 规格 |
|---------|------|
| 主背景色 | `#0E0F11`（深炭黑） |
| 卡片背景 | `#1A1B1F` |
| 主强调色 | `#6C8EFF`（冷紫蓝） |
| 成功色 | `#4ADE80` |
| 警告色 | `#F59E0B` |
| 危险色 | `#EF4444` |
| 标题字体 | DM Sans |
| 正文字体 | Inter |
| 等宽字体 | JetBrains Mono |
| 圆角 | 12px（卡片）、8px（按钮）、50%（头像） |
| 边框 | 无多余边框，仅用微妙中性色 |
| 动效 | 悬停浮层展示元信息、平滑过渡 |

### 4.2 主要页面布局

- **左侧边栏**：可折叠，展开 240px 显示导航文字，收起显示图标
- **主内容区**：动态瀑布流照片网格，支持网格 / 时间线 / 地图三种视图切换
- **顶部工具栏**：搜索框 + 快速过滤 + 任务进度指示器，固定不随滚动消失
- **全屏查看器**：PhotoSwipe，左右翻页、手势缩放、底部元信息面板
- **移动端**：全屏铺展流式布局，PWA 支持安装到手机桌面

---

## 5. 功能模块详细设计

### 5.1 文件夹管理

- 支持映射 NAS 真实目录结构，也可创建虚拟相册
- 文件夹树形侧边栏，支持拖拽排序、重命名、嵌套
- 批量导入：指定 NAS 路径后台自动扫描入库，WebSocket 推送进度
- 权限管理：每个文件夹可设置为私密 / 家庭共享

---

### 5.2 照片浏览

- 动态瀑布流 + 按年 / 月分组的网格视图切换
- 图片懒加载，优先显示 WebP 缩略图（400px 宽）
- 全屏查看器：左右翻页、手势缩放、旋转、EXIF 信息面板
- 支持 RAW 格式（转缩略图展示）、GIF 动图原样播放
- 视频：内嵌 Plyr 播放器，支持字幕、倍速、画质切换

---

### 5.3 时间线

- 以拍摄时间（EXIF `DateTimeOriginal`）为准，非上传时间
- 年 / 月 / 日三级折叠展示
- 顶部切换「地图模式」：按 GPS 坐标聚类，Leaflet 热力点
- 无 EXIF 的文件按修改时间归类，标注「时间未知」

---

### 5.4 收藏 & 打分

- 5 星评分（存入 `photos.rating` 字段），支持批量打分
- 收藏心形按钮（快捷键 `F`），收藏夹作为虚拟相册
- 支持按评分过滤，默认智能排序（评分 × 新鲜度）

---

### 5.5 AI 智能搜索

- **全文搜索**：备注、文件名、AI 生成描述（PostgreSQL FTS）
- **语义搜索**：输入自然语言（「海边日落」「全家吃饭」），CLIP 编码后向量检索 pgvector
- **组合过滤**：时间范围 + 地点 + 人物 + 标签 + 评分
- **搜索结果高亮来源**（「为什么匹配」小徽章）

---

### 5.6 AI 训练分类（核心功能）

**实现原理**：基于 CLIP 原型向量匹配，用户提供少量样本即可迭代匹配全库照片。

**详细步骤：**

1. 用户创建「训练集相册」，拖入 20~100 张正样本图片
2. RabbitMQ 异步任务：CLIP 提取所有图片特征向量，计算质心向量作为该类别「原型」
3. 可选负样本（反例图片）进一步提升精度
4. 训练完成通知（WebSocket），展示匹配效果预览
5. 自动扫描：全库图片与原型向量做余弦相似度比较，超过阈值（默认 0.75，用户可调）自动加入目标相册
6. 支持重新训练、调整阈值后重新匹配

> **典型用例**：给宝宝相册训练，上传 50 张宝宝照片 → 自动把 3000 张家庭照里的宝宝照全找出来

---

### 5.7 照片备注

- 手动备注（Markdown 支持，显示时渲染）
- AI 自动备注：BLIP-2 生成描述，支持中英文，可手动修改并锁定
- 备注参与全文搜索索引（PostgreSQL FTS）
- 批量导出备注为 JSON / CSV

---

### 5.8 人物列表 & 人脸识别

- 首次入库时 InsightFace 检测人脸，提取 512d embedding 存入 pgvector
- 自动聚类（DBSCAN）归组，展示「未命名人物 1/2/3...」
- 用户点击确认 / 合并 / 命名，形成命名人物档案
- 人物主页：该人物所有照片、首次出现、最近出现
- 支持手动框选未被识别的人脸补充训练

---

### 5.9 宝宝相册（专属模块）

- 内置时间线：以宝宝出生日期为起点，显示「第 X 天 / 第 X 个月 / X 岁 X 个月」
- 成长里程碑标记（第一次走路、第一次笑等，手动打标）
- 自动生成成长相册 PDF / 视频幻灯片（FFmpeg 合成）

---

### 5.10 去重 & 相似度检测

- **精确去重**：MD5 哈希，100% 相同文件
- **感知去重**：pHash 汉明距离 ≤ 8，视为重复（同一张图不同压缩率 / 分辨率）
- **相似图片组**：展示可视化对比，用户手动选择保留哪张（默认保留分辨率更高的）
- 去重扫描作为独立后台任务，不影响浏览

---

### 5.11 分类 & 标签

- AI 自动标签：YOLOv8 检测场景（户外 / 室内 / 海边 / 山地）和物体
- 手动标签（拖拽添加，标签云展示）
- 智能相册：按规则自动归集（如「所有有猫的照片」）
- 标签同步：写入图片 EXIF `XPKeywords` 字段（可选）

---

## 6. 核心数据库设计

### 6.1 主要表结构

| 表名 | 主要字段 | 说明 |
|------|---------|------|
| `photos` | id, file_path, file_hash_md5, file_hash_phash, exif_date, gps_lat, gps_lng, rating, note, ai_caption, embedding(512), width, height, file_size, media_type | 主表：存储所有媒体文件元数据 |
| `albums` | id, name, type(virtual/directory/training/baby), cover_photo_id, owner_id, shared, birth_date | 相册表：虚拟 / 目录 / 训练集 / 宝宝 |
| `album_photos` | album_id, photo_id, added_at, source(manual/ai/training) | 相册与照片多对多关联 |
| `people` | id, name, cover_face_id, photo_count, first_seen, last_seen | 人物档案 |
| `face_clusters` | id, photo_id, bbox_json, embedding(512), person_id, confidence | 人脸聚类，关联到人物 |
| `tags` | id, name, color, type(manual/ai/scene/object) | 标签表 |
| `photo_tags` | photo_id, tag_id, confidence, source | 照片标签关联 |
| `ai_tasks` | id, type, status, progress, photo_ids_json, result_json, created_at, finished_at | 异步任务状态跟踪 |
| `training_sets` | id, album_id, prototype_vector(512), threshold, negative_count, trained_at | AI 训练集原型向量 |
| `users` | id, username, password_hash, role, avatar, created_at | 用户表，支持家庭多用户 |

### 6.2 关键索引

```sql
-- 向量相似度搜索索引（HNSW 算法）
CREATE INDEX ON photos USING hnsw (embedding vector_cosine_ops);
CREATE INDEX ON face_clusters USING hnsw (embedding vector_cosine_ops);

-- 全文搜索索引
CREATE INDEX ON photos USING gin(to_tsvector('simple', coalesce(note, '') || ' ' || coalesce(ai_caption, '')));

-- 时间线查询索引
CREATE INDEX ON photos (exif_date DESC);

-- 去重查询索引
CREATE INDEX ON photos (file_hash_md5);
CREATE INDEX ON photos (file_hash_phash);
```

---

## 7. Docker 部署配置

### 7.1 服务清单

| 服务名 | 镜像基础 | 端口 | 资源限制 |
|--------|---------|------|---------|
| `frontend` | nginx:alpine | 80, 443 | 无 GPU |
| `backend` | eclipse-temurin:21-jre-alpine | 8080 | 无 GPU |
| `ai-service` | pytorch/pytorch:2.3.0-cuda12.1-cudnn8-runtime | 8000 | GPU: 1 (RTX 2060) |
| `postgres` | pgvector/pgvector:pg16 | 5432 | SHM: 256m |
| `redis` | redis:7-alpine | 6379 | 无 GPU |
| `rabbitmq` | rabbitmq:3-management-alpine | 5672, 15672 | 无 GPU |
| `minio` | minio/minio:latest | 9000, 9001 | 无 GPU |

### 7.2 NAS 目录挂载

| NAS 目录 | 容器路径 | 说明 |
|---------|---------|------|
| `/volume1/photos` | `/data/photos` | 包含已有照片和视频的目录，只读 |
| `/volume1/memoryvault/thumbs` | `/data/thumbs` | 缩略图缓存，读写 |
| `/volume1/memoryvault/models` | `/data/models` | AI 模型文件，只读 |
| `/volume1/memoryvault/postgres` | `/var/lib/postgresql/data` | PG 数据目录，读写 |
| `/volume1/memoryvault/minio` | `/data/minio` | MinIO 数据目录，读写 |

### 7.3 docker-compose.yml 关键片段

```yaml
version: '3.9'

services:
  frontend:
    image: nginx:alpine
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./frontend/dist:/usr/share/nginx/html:ro

  backend:
    build: ./backend
    image: memoryvault-backend:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/memoryvault
      - SPRING_REDIS_HOST=redis
      - SPRING_RABBITMQ_HOST=rabbitmq
      - MINIO_ENDPOINT=http://minio:9000
      - AI_SERVICE_URL=http://ai-service:8000
    depends_on:
      - postgres
      - redis
      - rabbitmq
      - minio

  ai-service:
    build: ./ai-service
    image: memoryvault-ai:latest
    ports:
      - "8000:8000"
    volumes:
      - /volume1/memoryvault/models:/data/models:ro
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]
    environment:
      - MODEL_PATH=/data/models
      - CUDA_VISIBLE_DEVICES=0

  postgres:
    image: pgvector/pgvector:pg16
    environment:
      - POSTGRES_DB=memoryvault
      - POSTGRES_USER=memoryvault
      - POSTGRES_PASSWORD=${DB_PASSWORD}
    volumes:
      - /volume1/memoryvault/postgres:/var/lib/postgresql/data
    shm_size: 256m

  redis:
    image: redis:7-alpine
    command: redis-server --requirepass ${REDIS_PASSWORD}

  rabbitmq:
    image: rabbitmq:3-management-alpine
    environment:
      - RABBITMQ_DEFAULT_USER=memoryvault
      - RABBITMQ_DEFAULT_PASS=${RABBITMQ_PASSWORD}

  minio:
    image: minio/minio:latest
    command: server /data/minio --console-address ":9001"
    volumes:
      - /volume1/memoryvault/minio:/data/minio
    environment:
      - MINIO_ROOT_USER=memoryvault
      - MINIO_ROOT_PASSWORD=${MINIO_PASSWORD}
```

> NAS 需要提前安装 **NVIDIA Container Toolkit**，并确保宿主机驱动版本 ≥ 525。

### 7.4 Nginx 反代理配置关键片段

```nginx
server {
    listen 80;

    # 前端静态资源
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }

    # Java 业务 API
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # WebSocket
    location /ws/ {
        proxy_pass http://backend:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }

    # 媒体文件（缩略图走 MinIO 预签名 URL，原图走此路径）
    location /media/ {
        proxy_pass http://minio:9000/;
    }
}
```

---

## 8. 项目目录结构

```
memoryvault/
├── docker-compose.yml          # Docker 服务编排配置
├── .env                        # 环境变量（密码、端口等）
├── nginx/
│   └── nginx.conf              # Nginx 反代理配置
│
├── backend/                    # Java Spring Boot 项目
│   ├── pom.xml
│   └── src/main/java/com/memoryvault/
│       ├── MemoryVaultApplication.java
│       ├── controller/         # REST API 控制器
│       │   ├── PhotoController.java
│       │   ├── AlbumController.java
│       │   ├── PeopleController.java
│       │   ├── SearchController.java
│       │   ├── TimelineController.java
│       │   └── TaskController.java
│       ├── service/            # 业务逻辑层
│       │   ├── PhotoService.java
│       │   ├── AlbumService.java
│       │   ├── FaceService.java
│       │   ├── SearchService.java
│       │   ├── DedupService.java
│       │   └── BabyAlbumService.java
│       ├── repository/         # JPA Repository 接口
│       ├── entity/             # JPA 实体类
│       ├── dto/                # DTO 请求/响应对象
│       ├── ai/                 # AI 服务调用封装
│       │   └── AiServiceClient.java
│       ├── async/              # RabbitMQ 消费者和异步处理器
│       │   ├── PhotoIndexingConsumer.java
│       │   └── TrainingTaskConsumer.java
│       ├── websocket/          # WebSocket / STOMP 配置
│       │   └── ProgressWebSocketHandler.java
│       ├── storage/            # MinIO 文件存储封装
│       │   └── MinioStorageService.java
│       └── config/             # Spring 配置类
│           ├── SecurityConfig.java
│           ├── RedisConfig.java
│           └── RabbitMQConfig.java
│
├── ai-service/                 # Python AI 推理服务
│   ├── main.py                 # FastAPI 入口
│   ├── requirements.txt        # Python 依赖
│   ├── Dockerfile
│   └── models/
│       ├── clip.py             # CLIP 封装
│       ├── insightface.py      # InsightFace 封装
│       ├── yolo.py             # YOLOv8 封装
│       └── blip.py             # BLIP-2 封装（按需加载）
│
└── frontend/                   # Vue 3 + Vite 项目
    ├── package.json
    ├── vite.config.ts
    └── src/
        ├── main.ts
        ├── App.vue
        ├── views/              # 页面组件
        │   ├── GalleryView.vue     # 主相册页
        │   ├── TimelineView.vue    # 时间线页
        │   ├── PeopleView.vue      # 人物列表页
        │   ├── AlbumsView.vue      # 相册管理页
        │   ├── SearchView.vue      # 搜索结果页
        │   └── BabyAlbumView.vue   # 宝宝相册页
        ├── components/         # 通用组件
        │   ├── PhotoCard.vue       # 照片卡片
        │   ├── PhotoViewer.vue     # 全屏查看器
        │   ├── Uploader.vue        # 上传组件
        │   ├── TaskProgress.vue    # 任务进度弹窗
        │   └── SearchBar.vue       # 智能搜索框
        ├── stores/             # Pinia 状态存储
        │   ├── photoStore.ts
        │   ├── albumStore.ts
        │   └── taskStore.ts
        └── api/                # Axios API 封装
            ├── photoApi.ts
            ├── albumApi.ts
            └── searchApi.ts
```

---

## 9. 开发规划与里程碑

### 9.1 阶段规划

#### Phase 1 — 基础框架（4 周）

- Docker Compose 基础服务搭通（PostgreSQL、Redis、RabbitMQ、MinIO、Spring Boot、Vue）
- 用户认证（JWT 登录 / 注册 / 多用户）
- 文件夹管理、照片上传入库
- EXIF 解析、缩略图生成（WebP）
- 基础瀑布流浏览、时间线视图

**交付物**：可浏览的基础相册系统

---

#### Phase 2 — 媒体完善（2 周）

- 视频支持（FFmpeg 转码 + Plyr 播放器）
- GIF / RAW 格式支持
- 照片全屏查看器完善（手势、翻页、EXIF 信息面板）
- 收藏、评分、备注功能
- 去重检测模块（MD5 + pHash）

**交付物**：完整媒体浏览体验

---

#### Phase 3 — AI 核心（4 周）

- InsightFace 人脸识别 + DBSCAN 聚类 + 人物列表
- CLIP 语义向量化 + pgvector 搜索
- YOLOv8 自动标签
- AI 训练分类功能（原型向量 + 全库匹配 + 阈值调节）
- RabbitMQ 异步任务队列 + WebSocket 进度推送

**交付物**：AI 功能全面上线

---

#### Phase 4 — 高级功能（3 周）

- 宝宝相册专属模块（成长时间线 + 里程碑）
- BLIP-2 自动图片描述
- Leaflet 地图时间线（GPS 聚类）
- 成长相册 PDF / 视频幻灯片生成（FFmpeg 合成）
- 移动端 PWA 优化

**交付物**：宝宝相册、地图、PWA

---

#### Phase 5 — 性能 & 运维（2 周）

- 全量照片向量化性能优化（批处理，显存管理）
- 自动备份方案（pg_dump + MinIO 定时备份）
- 多语言支持（中 / 英）
- 用户手册文档

**交付物**：生产就绪系统

---

### 9.2 REST API 主要端点（Spring Boot）

| HTTP 方法 | 路径 | 功能 |
|---------|------|------|
| `GET` | `/api/photos` | 照片列表，支持分页、排序、多条件过滤 |
| `POST` | `/api/photos/upload` | 上传照片/视频，返回任务 ID |
| `GET` | `/api/photos/{id}` | 照片详情 + EXIF + AI 标签 |
| `PUT` | `/api/photos/{id}` | 更新评分 / 备注 / 收藏状态 |
| `DELETE` | `/api/photos/{id}` | 删除照片（移入回收站） |
| `GET` | `/api/albums` | 相册列表 |
| `POST` | `/api/albums` | 创建相册（包括训练集相册） |
| `POST` | `/api/albums/{id}/train` | 触发 AI 训练分类任务 |
| `GET` | `/api/search?q={query}&type={semantic\|text}` | 语义搜索 + 全文搜索 |
| `GET` | `/api/people` | 人物列表 |
| `PUT` | `/api/people/{id}` | 修改人物名称和封面 |
| `POST` | `/api/people/merge` | 合并两个人物档案 |
| `GET` | `/api/timeline` | 时间线数据，按年月分组 |
| `GET` | `/api/tasks/{id}` | 查询异步任务状态 |
| `WS` | `/ws/progress` | WebSocket 连接，订阅任务进度 |
| `POST` | `/api/dedup/scan` | 触发去重扫描任务 |
| `GET` | `/api/dedup/groups` | 获取重复图片分组 |

---

## 10. 硬件资源评估

### 10.1 RTX 2060 各场景显存 / 速度

| 场景 | 显存占用 | 处理速度 | 备注 |
|------|---------|---------|------|
| CLIP 特征提取 | ~1.2 GB | ~50 张/秒 | 批量传入提升 2x |
| InsightFace 人脸检测 | ~0.8 GB | ~20 张/秒 | 每张可能含多张人脸 |
| YOLOv8n 目标检测 | ~0.5 GB | ~60 张/秒 | 轻量版型号已足够 |
| BLIP-2 描述生成 | ~4.5 GB | ~3 张/秒 | 按需加载，用后释放 |
| 三模型并行 (no BLIP) | ~2.5 GB | 流水线处理 | 2060 的 6GB 显存可应对 |
| 1 万张全量入库 | 6 GB 内全部利用 | 15~30 分钟 | CLIP + 人脸并行流水线 |
| 日常增量处理 | < 2 GB | 实时完成 | 单张图片入库 < 3s |

### 10.2 NAS 推荐配置

| 资源 | 推荐规格 | 说明 |
|------|---------|------|
| 内存 | ≥ 16 GB RAM | 容器浮现空间 |
| CPU | 8 核以上 | Java + Python 并行处理 |
| 系统盘 | 100 GB | 操作系统 + Docker 镜像 + 模型文件（约 15 GB） |
| 数据盘 | 按实际照片量 | 原图 + 缩略图（缩略图约为原图 10%） |
| GPU | NVIDIA RTX 2060（6GB VRAM） | 已满足所有 AI 功能需求 |
| 驱动版本 | ≥ 525 | NVIDIA Container Toolkit 要求 |

---

## 附录：AI 模型下载说明

首次部署时需要下载以下模型文件到 `/volume1/memoryvault/models/` 目录：

```bash
# CLIP 模型（约 600 MB）
# 容器启动时 OpenCLIP 自动下载，也可手动放置到 models/clip/

# InsightFace buffalo_l 模型包（约 300 MB）
# 下载地址：https://github.com/deepinsight/insightface/releases
# 放置到 models/insightface/buffalo_l/

# YOLOv8n 模型（约 6 MB）
# 下载地址：https://github.com/ultralytics/assets/releases
# 放置到 models/yolo/yolov8n.pt

# BLIP-2 模型（约 15 GB，按需启用）
# 通过 huggingface-cli 下载 Salesforce/blip2-opt-2.7b
# 放置到 models/blip2/
```

---

*MemoryVault — 记忆保险库 | 为家庭打造永久投影仪*
