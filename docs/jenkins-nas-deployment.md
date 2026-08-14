# 飞牛 NAS Jenkins 部署指南

本方案仿照 `ai-book` 的发布流程，将 `git@github.com:beishan/aiphoto.git`
的 `main` 分支部署到家庭网络内飞牛 NAS 的 Docker。Jenkins 默认也运行在
该 NAS 的 Docker 中，通过 Docker Socket 管理宿主机容器。

## 1. 部署内容

Pipeline 会自动执行：检出 `main`、后端测试、构建前端/后端/AI 版本
镜像和内置 schema 的 pgvector 镜像、备份 PostgreSQL、Compose 滚动更新、
容器与局域网健康检查。发布
失败时自动恢复上一版的三个应用镜像，不回滚数据。
发布成功后，前端、后端和 AI 服务各保留当前版与上一版两个镜像，
并清理本项目重建产生的悬空镜像。

默认局域网端口：

| 服务 | 端口 |
|---|---:|
| aiphoto Web | 8391 |
| Spring Boot API | 8392 |

PostgreSQL 和 AI API 仅在 Docker 内部网络开放。

## 2. NAS 与 Jenkins 前置条件

Jenkins 容器需要具备 `git`、`docker`、`docker compose`和 `curl`，并挂载：

```text
/var/run/docker.sock:/var/run/docker.sock
```

这个 Socket 等同于 NAS Docker 管理权限，只应交给可信的 Jenkins 管理员。
GPU 加速为可选功能。NAS 已安装 NVIDIA 驱动、NVIDIA Container Toolkit，
且以下命令成功时，可在 Jenkins 中勾选 `ENABLE_GPU`：

```bash
docker run --rm --gpus all nvidia/cuda:12.1.0-base-ubuntu22.04 nvidia-smi
```

## 3. 准备 NAS 目录

例如创建：

```text
/vol1/photos
/vol1/docker/aiphoto/ai-models/insightface/models
/vol1/docker/aiphoto/ai-models/ultralytics
/vol1/docker/aiphoto/ai-models/clip
/vol1/docker/aiphoto/ai-models/chinese-clip
/vol1/docker/aiphoto/storage
```

将现有 `ai-models` 目录的子目录内容放到对应位置。至少确保存在：

```text
ultralytics/yolov8n.pt
chinese-clip/clip_cn_vit-b-16.pt
insightface/models/buffalo_l/
```

照片库以只读方式挂载到后端 `/photos`，在 aiphoto 的扫描目录中应填
`/photos` 或其子目录。用于上传的照片和缩略图存在
`STORAGE_DATA_PATH` 指定的 NAS 目录，不依赖 Jenkins 工作区。

## 4. Jenkins 生产凭据

1. 复制 `docker/.env.production.example` 到本地临时文件。
2. 替换全部密码。JWT 密钥可用
   `openssl rand -base64 48` 生成。
3. Jenkins 中新建 `Secret file` 凭据，ID 必须是
   `memoryvault-production-env`（兼容既有 Jenkins 凭据，无需重新上传密钥）。

照片库、AI 模型根目录和本地存储目录可保留示例文件中的默认值，部署时会由
Jenkins 构建参数中的同名值覆盖。

真实生产环境文件不要提交到 Git。

## 5. 创建 Pipeline

新建 **Pipeline script from SCM** 任务：

- Repository URL：`git@github.com:beishan/aiphoto.git`
- Branch Specifier：`*/main`
- Script Path：`Jenkinsfile`
- SSH Credentials：选择已可读取该 GitHub 仓库的凭据

Pipeline 中可在每次“Build with Parameters”时填写：

| 参数 | 用途 |
|---|---|
| `NAS_HOST` | 飞牛 NAS 局域网 IP 或域名 |
| `PHOTO_LIBRARY_PATH` | NAS 宿主机照片库绝对路径 |
| `AI_MODELS_PATH` | NAS 宿主机 AI 模型根目录绝对路径 |
| `STORAGE_DATA_PATH` | NAS 宿主机上传照片和缩略图存储目录 |
| `FRONTEND_PORT` | 前端对外端口 |
| `BACKEND_PORT` | 后端对外端口 |
| `ENABLE_GPU` | 是否申请 NVIDIA GPU；未安装 Container Toolkit 时不要勾选 |

这些参数会覆盖凭据文件中的同名值。三个目录必须是 Docker 宿主机上
已存在的安全绝对路径；模型的 `chinese-clip`、`insightface/models`、
`ultralytics` 等子目录仍位于 `AI_MODELS_PATH` 之下。

如果 Jenkins 只在家庭局域网中，GitHub 无法主动访问 Webhook，可在任务中
使用 `Poll SCM`，例如每五分钟：`H/5 * * * *`。

## 6. 首次部署和访问

首次部署会下载 CUDA/PyTorch 基础镜像与 Maven/npm 依赖，时间较长；AI 模型
不会自动下载，请提前放入 `AI_MODELS_PATH`，或部署后在“设置 → 本地模型管理”
中上传并配置。没有旧
PostgreSQL 时跳过备份，没有上一版镜像时不能回滚，都属于正常现象。

```text
http://192.168.31.155:8391/
http://192.168.31.155:8392/actuator/health
```

## 7. 运维命令

在仓库中指定生产环境文件：

```bash
./scripts/deploy.sh health /path/to/aiphoto-production.env
./scripts/deploy.sh logs /path/to/aiphoto-production.env
./scripts/rollback.sh /path/to/aiphoto-production.env
```

每次发布前的 PostgreSQL custom-format 备份保存在
`aiphoto-backups` Docker Volume，默认保留 10 份。上一版镜像信息保存在
`aiphoto-deploy-state` Volume。手动回滚只替换前端、后端和 AI 镜像，
不会删除 PostgreSQL、本地存储和照片数据。

查看备份：

```bash
docker run --rm -v aiphoto-backups:/backups alpine:3.20 ls -lh /backups
```

故障排查优先使用：

```bash
docker ps --filter name=aiphoto
docker logs --tail=200 aiphoto-ai
docker logs --tail=200 aiphoto-backend
docker exec aiphoto-ai nvidia-smi
```

如 AI 容器一直 `unhealthy`，重点检查 NVIDIA Container Toolkit、模型目录结构和
NAS 文件权限。如 Compose 报挂载源不存在，检查的是 NAS 宿主机路径，
而不是 Jenkins 容器内路径。
