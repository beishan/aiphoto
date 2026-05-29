# MemoryVault - AI 智能相册

## 项目概述

MemoryVault 是一个面向家庭/个人的私有化部署 AI 相册系统，运行于 NAS Docker 环境，支持 NVIDIA GPU 加速。

**核心特性：**
- 数据不出门：所有照片、视频、人脸数据存储在自家 NAS
- AI 全本地：CLIP、InsightFace、YOLOv8、BLIP-2 全部在本地 GPU 运行
- 家庭级功能：宝宝相册、成长时间线、人物履历

## 技术栈

| 层次 | 技术 |
|------|------|
| 前端 | Vue 3 + Vite 5 + Naive UI + Pinia + Vue Router 4 |
| 后端 | Java 21 + Spring Boot 3.3 + Spring Data JPA + Spring Security |
| AI 服务 | Python 3.11 + FastAPI + PyTorch (CUDA 12.1) |
| 数据库 | PostgreSQL 16 + pgvector + Redis 7 |
| 消息队列 | RabbitMQ 3.x |
| 对象存储 | MinIO (S3 兼容) |
| 部署 | Docker Compose + Nginx 反代理 |

## 项目结构

```
ai-photo/
├── docker-compose.yml          # Docker 服务编排
├── .env                        # 环境变量
├── nginx/nginx.conf            # Nginx 反代理配置
│
├── backend/                    # Java Spring Boot 后端
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/memoryvault/
│       ├── controller/         # REST API 控制器
│       ├── service/            # 业务逻辑层
│       ├── repository/         # JPA Repository
│       ├── entity/             # JPA 实体类
│       ├── dto/                # DTO 对象
│       ├── ai/                 # AI 服务调用客户端
│       ├── async/              # RabbitMQ 消费者
│       ├── websocket/          # WebSocket 处理
│       ├── storage/            # MinIO 存储服务
│       ├── security/           # JWT 认证
│       └── config/             # Spring 配置
│
├── ai-service/                 # Python AI 推理服务
│   ├── main.py                 # FastAPI 入口
│   ├── requirements.txt
│   ├── Dockerfile
│   └── models/                 # AI 模型封装
│       ├── clip_model.py       # CLIP 语义向量化
│       ├── insightface_model.py # 人脸识别
│       ├── yolo_model.py       # YOLOv8 目标检测
│       └── blip_model.py       # BLIP-2 图片描述
│
└── frontend/                   # Vue 3 前端
    ├── package.json
    ├── vite.config.ts
    └── src/
        ├── views/              # 页面组件
        ├── components/         # 通用组件
        ├── stores/             # Pinia 状态管理
        ├── api/                # API 调用封装
        ├── router/             # 路由配置
        └── types/              # TypeScript 类型定义
```

## 常用命令

### Docker 部署
```bash
# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f backend
docker-compose logs -f ai-service

# 重新构建并启动
docker-compose up -d --build
```

### 后端开发
```bash
cd backend

# 编译
mvn clean package

# 运行（开发环境）
mvn spring-boot:run

# 运行测试
mvn test
```

### 前端开发
```bash
cd frontend

# 安装依赖
npm install

# 开发服务器
npm run dev

# 构建生产版本
npm run build

# 类型检查
npm run build  # 包含 vue-tsc
```

### AI 服务开发
```bash
cd ai-service

# 安装依赖
pip install -r requirements.txt

# 运行开发服务器
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

## 数据库

### 初始化
数据库 schema 定义在 `backend/src/main/resources/schema.sql`，Docker 启动时自动执行。

### 关键表
- `photos` - 照片主表，包含 CLIP embedding (pgvector)
- `albums` - 相册表（虚拟/目录/训练集/宝宝）
- `face_clusters` - 人脸聚类，包含 InsightFace embedding
- `people` - 人物档案
- `tags` / `photo_tags` - 标签系统
- `training_sets` - AI 训练集原型向量
- `ai_tasks` - 异步任务状态

## AI 服务接口

| 端点 | 功能 | 模型 |
|------|------|------|
| `POST /ai/embed` | 图片语义向量化 | CLIP ViT-B/32 |
| `POST /ai/detect-faces` | 人脸检测 + embedding | InsightFace buffalo_l |
| `POST /ai/classify` | 目标检测/分类 | YOLOv8n |
| `POST /ai/caption` | 图片描述生成 | BLIP-2 (按需加载) |
| `POST /ai/batch-embed` | 批量向量化 | CLIP |

## REST API 端点

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/auth/register` | 用户注册 |
| GET | `/api/photos` | 照片列表（分页） |
| POST | `/api/photos/upload` | 上传照片 |
| GET | `/api/photos/{id}` | 照片详情 |
| PUT | `/api/photos/{id}` | 更新评分/备注/收藏 |
| DELETE | `/api/photos/{id}` | 删除照片 |
| GET | `/api/albums` | 相册列表 |
| POST | `/api/albums` | 创建相册 |
| POST | `/api/albums/{id}/train` | 触发 AI 训练 |
| GET | `/api/search` | 搜索（全文/语义） |
| GET | `/api/people` | 人物列表 |
| GET | `/api/timeline` | 时间线数据 |
| WS | `/ws/progress` | WebSocket 任务进度 |

## 开发规范

### 代码风格
- **Java**: 遵循 Google Java Style Guide
- **Vue/TypeScript**: 使用 Composition API + `<script setup>`
- **Python**: 遵循 PEP 8

### Git 提交
- 使用中文提交信息
- 格式: `<类型>: <描述>`
- 类型: feat, fix, refactor, docs, style, test, chore

### 分支策略
- `main` - 生产分支
- `develop` - 开发分支
- `feature/*` - 功能分支
- `fix/*` - 修复分支

## 环境变量

关键环境变量在 `.env` 文件中配置：

```bash
# 数据库密码
DB_PASSWORD=memoryvault
REDIS_PASSWORD=memoryvault
RABBITMQ_PASSWORD=memoryvault
MINIO_PASSWORD=memoryvault

# JWT 密钥（生产环境必须修改）
JWT_SECRET=your-secret-key

# AI 模型路径
MODEL_PATH=./models
```

## 硬件要求

### 开发环境
- 内存: ≥ 16 GB RAM
- CPU: 8 核以上
- GPU: NVIDIA RTX 2060 (6GB VRAM) 或更高
- NVIDIA 驱动: ≥ 525
- NVIDIA Container Toolkit

### 模型显存占用
| 模型 | 显存 | 速度 |
|------|------|------|
| CLIP | ~1.2 GB | ~50 张/秒 |
| InsightFace | ~0.8 GB | ~20 张/秒 |
| YOLOv8n | ~0.5 GB | ~60 张/秒 |
| BLIP-2 | ~4.5 GB | ~3 张/秒 |

## 注意事项

1. **BLIP-2 按需加载**: 图片描述生成模型较大(4.5GB显存)，首次调用时加载
2. **pgvector 索引**: HNSW 索引在大数据量时提供高效的向量相似度搜索
3. **MinIO 预签名 URL**: 缩略图通过预签名 URL 访问，有效期 24 小时
4. **WebSocket**: 任务进度通过 STOMP over WebSocket 推送
5. **JWT 认证**: Token 存储在 localStorage，24 小时过期
