# AI 功能检查报告

> 后续处理：0.11.0 已修复本报告中的代码问题，包括独立阶段重试、训练匹配、分类、阈值、激活与后台下载，并增加视频代表帧支持。下文保留 0.10.0 检查快照。模型权重缺失仍需要安装兼容文件，真实部署验收步骤见 `docs/AI_SETUP.md`；本次回归结果记录于 `DEVELOPMENT_LOG.md`。

- 检查日期：2026-10-04（Asia/Shanghai）
- 应用版本：0.10.0
- 范围：AI 模型封装与生命周期、后端索引/人脸/分类/搜索/训练、模型管理前端入口、Docker 配置及本地模型文件。
- 本次仅检查并新增报告，没有修改应用行为，因此未递增版本。

## 结论

系统已经实现 Chinese-CLIP 图文向量、InsightFace 人脸检测与聚合、YOLO 目标标签、BLIP-2 描述，以及相关后端调用。但存在多处影响使用的逻辑问题，不能据此认定 AI 功能已完整可用。当前工作区默认模型目录缺少服务要求的 CLIP 文件和 BLIP-2 模型。

## 主要问题（按处理优先级）

### 1. P1：当前本地 CLIP 文件不符合默认加载要求

位置：`ai-service/model_manager.py:44`、`ai-service/models/clip_model.py:26`。

服务默认需要 `ai-models/chinese-clip/clip_cn_vit-b-16.pt`，封装还强制校验该文件名。当前实际文件是 `ai-models/clip/open_clip_pytorch_model.bin`，模型目录中没有 `model-config.json`。因此按当前工作区的默认配置部署，CLIP 路径校验失败；上传索引首先调用 CLIP，随后的人脸识别和目标分类也不会继续执行。不能通过单纯重命名保证权重兼容，需核对模型架构和权重格式。

验证：以替身隔离重型模型依赖，运行真实 ModelManager 的初始化、load_enabled 与 status；得到 clip enabled=true、exists=false；insightface/yolo 路径存在。未加载真实权重。

### 2. P1：训练相册的异步任务缺少懒加载所需的会话边界

位置：`backend/src/main/java/com/aiphoto/async/TrainingTaskService.java:31`、`:45`；`backend/src/main/java/com/aiphoto/entity/Album.java:46`。

`trainAlbum` 在异步线程中通过 `findById` 取得相册后访问默认懒加载的 ManyToMany photos；任务方法没有事务，Repository 也没有 fetch join/EntityGraph。按目前代码的会话边界，该集合访问会遇到 LazyInitializationException，训练不能正常提取样本。此项根据代码检查判断，尚未在数据库集成环境复现。

修复方向：在短事务中取得需要的照片 ID/数据，再做推理；避免把耗时推理放在长期数据库事务内。

### 3. P1：训练相册只判断同一个最近邻，漏掉其他匹配照片

位置：`backend/src/main/java/com/aiphoto/async/TrainingTaskService.java:85`—`:97`。

扫描每张照片时均执行相同向量查询且 limit=1，再检查返回的第一张是否等于当前照片。最多新增全局最近邻一张；如果最近邻本来就是训练样本，则一张都不会新增。空相册还会在 `embeddings.get(0)` 处失败。

逻辑复现：样本 ID=1，三张照片的距离为 0、0.1、0.2，相似度阈值为 0.75。应新增 ID=2、3，现有循环新增为空。

修复方向：一次查询全部符合阈值的候选，排除样本并去重；入口校验空样本及阈值。

### 4. P1：YOLO 标签类别与自动分类映射不匹配

位置：`ai-service/models/yolo_model.py:55`；`backend/src/main/java/com/aiphoto/async/PhotoIndexingService.java:201`、`:223`。

AI 服务返回的 category 只有 object/scene，后端却用 category 匹配 person/animal/food。因此 person、cat、dog、banana 等标签不会自动进入人物、动物或美食系统分类，只有按名称单独处理的 potted plant 等例外可能成功。

逻辑复现：按源代码提取场景集合并执行相同映射，上述四个标签均得到 object，后端映射结果为空。

`CategoryService.reclassifyAll` 也只在 category 为空时推断类别；已有 object 类别的 cat/dog/banana 标签不会进入这一步，名称兜底也不足以恢复这些类别。

### 5. P1：索引失败会留下部分人脸数据，重试可能重复创建

位置：`backend/src/main/java/com/aiphoto/async/PhotoIndexingService.java:71`—`:147`。

人脸及人物数量先写入数据库，之后才调用 YOLO；只有所有必需步骤成功后才保存照片 embedding。若 YOLO 失败，人脸记录仍然存在，而照片仍可能没有 embedding。`PhotoController.java:254` 的 index-all 按空 embedding 再次选择它，重复写入人脸并累加人物照片数。

当前索引没有按照片/阶段提供事务回滚或幂等处理。即使人物聚合和标签单独可用，用户也可能看到任务失败但人物数量持续增加。

修复方向：分开记录各阶段完成状态，持久化成功结果，并让人脸/标签写入可安全重试。

### 6. P1：“设为当前模型”没有切换推理模型

位置：`backend/src/main/java/com/aiphoto/service/ModelDownloadService.java:169`—`:174`；`backend/src/main/java/com/aiphoto/controller/SettingController.java:140`。

setAsCurrent 只将下载任务状态改为 INSTALLED，没有调用 ModelManagementService.configure，也没有更新 AI 服务 model-config.json 或重载模型。前端却提示已设为当前模型。

此外所有下载文件都命名为 `<modelId>.bin`，而 CLIP 要求固定 .pt 文件名、InsightFace 要求 ONNX 模型目录、BLIP-2 要求包含配置与处理器文件的完整目录，缺少针对各模型的安装步骤。在线目录的 URL 是通用拼接结果；本次没有验证这些远端 URL，不能断言其可下载。

### 7. P2：搜索“相似度阈值”实际上作为距离阈值

位置：`backend/src/main/java/com/aiphoto/service/SearchService.java:74`—`:98`；`backend/src/main/java/com/aiphoto/repository/PhotoRepository.java:61`。

UI 标注搜索相似度阈值，但后端直接将百分比除以 100 后用于余弦距离条件。设置 80% 时实际要求 similarity>0.2，而非 similarity>0.8；数值提高会放宽匹配范围，方向与用户预期相反。训练服务已经使用 1-threshold，二者语义不一致。

修复方向：明确区分相似度与距离，搜索查询传入 1-similarity，并验证边界。中文查询还保留了旧英文 CLIP 的固定词语替换，应针对 Chinese-CLIP 评估后移除或调整。

### 8. P2：视频上传会进入图片解码路径

位置：`backend/src/main/java/com/aiphoto/service/PhotoService.java:125`；`ai-service/models/clip_model.py:116`；`ai-service/models/yolo_model.py:39`。

后端支持 VIDEO 并触发同一索引流程，但 CLIP/YOLO 使用 PIL.Image.open，人脸模型使用 cv2.imdecode，没有视频抽帧。接口注释虽然写 image/video，普通 MP4/MOV 无法被这些图片解码器处理；文件名参数没有改变处理方式。

修复方向：先抽帧，定义视频向量、人脸和标签聚合策略，或明确排除未支持的视频 AI 索引。

### 9. P2：人脸聚合阈值固定读取用户 1

位置：`backend/src/main/java/com/aiphoto/async/PhotoIndexingService.java:72`；`backend/src/main/java/com/aiphoto/service/FaceService.java:139`。

设置页保存当前用户的配置，而聚合和重新聚合固定读取用户 1 的阈值，其他用户的设置不会按界面承诺生效。需要决定这是全局设置还是任务所属用户设置，并统一保存与读取方式。

### 10. P2：模型下载的 @Async 被类内调用绕过

位置：`backend/src/main/java/com/aiphoto/service/ModelDownloadService.java:90`、`:129`、`:161`、`:178`。

startDownload/resume/retry 直接调用同一实例的 executeDownload，未经过 Spring 异步代理，因此下载会占用请求线程，无法按当前注释描述立即返回异步任务。应把下载执行器移到独立 Bean 或显式线程池，并补齐断点续传响应校验。

## 功能覆盖与限制

| 功能 | 检查结果 |
|---|---|
| 图文语义向量/搜索 | 调用链已实现；当前默认 CLIP 文件缺失，阈值语义有误 |
| 人脸检测/人物聚合 | 封装与后端已实现，ONNX 文件存在；重试幂等、用户阈值有问题 |
| 目标标签/自动分类 | YOLO 权重文件存在；自动分类映射不匹配 |
| AI 图片描述 | 已接入并采用 best effort；当前 BLIP-2 默认禁用且模型目录不存在，caption 调用不会生成描述 |
| 相册训练 | 实质是向量质心匹配，不是微调模型；存在懒加载及最近邻匹配问题 |
| 视频 AI | 没有抽帧链路 |
| OCR/内容安全 | 模型目录展示了类型，但 AI 服务没有对应推理端点，类型映射返回 null，属于尚未接通的能力 |
| GPU | 本机检测到 RTX 4070 Ti，显存 12282 MiB；根 Compose 的 GPU 声明被注释，另有 docker/docker-compose.gpu.yml 覆盖配置。未验证容器 GPU 可见性 |

## 验证结果

- 7 个 AI Python 源文件通过 AST 语法检查。
- 已运行模型路径检查与分类/训练逻辑复现；这些检查不代表真实模型推理成功。
- 当前 Docker 引擎连接失败，localhost:8000/health 与 localhost:8080/actuator/health 均在 3 秒内未响应。无法在当前环境进行服务、数据库与真实 GPU 的端到端验收，也未检查其他 NAS 的部署。
- 可用的 Python 运行时缺少 torch、fastapi、cn_clip、insightface、ultralytics、transformers；未安装大规模 AI 依赖或下载模型。
- 后端 Maven 离线测试未进入执行：指定工作区缓存缺少 Spring Boot 3.3.5 parent POM。不能据此认定后端编译失败。
- 前端 vue-tsc 未通过：本地依赖缺少 element-plus、@element-plus/icons-vue 等类型，并有 main.ts 的相关类型错误。未获得完整前端构建通过的结果。

建议先解决默认模型文件、训练、分类映射、索引幂等和模型安装激活，再用独立测试数据验证：单张图片索引、服务失败后重试、多人聚合、中文语义检索、批量训练新增、启用/禁用单个模型，以及视频处理。
