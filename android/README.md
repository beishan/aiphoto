# aiphoto Android

原生 Kotlin + Jetpack Compose NAS 相册客户端。当前交付需求文档 M1 首版；宝宝成长时间线、精选时间线及高级管理属于文档 M2/M3，不在本次 APK 中伪造入口或数据。

## 功能

- NAS 地址检查、反向代理路径前缀、HTTPS 与用户显式允许的 HTTP。
- Keystore/AES-GCM 登录凭据、会话过期处理、退出及服务器实例隔离。
- 游标图库、多选收藏/加入相册/移入回收站、图片双指与双击缩放。
- Media3 视频播放、后台暂停；设备不支持编码时提示下载。
- 关键词及语义搜索，日期、人物、单标签、类型、评分筛选。
- 普通相册创建编辑、分页、移除关联、封面设置及删除；其他类型只读。
- 人物、分类及标签照片浏览，收藏与回收站恢复。
- 用户主动下载、取消、重试、临时 content URI 分享，图片详情、备注、评分。
- Room 元数据缓存、Coil 图片缓存、离线只读、主题、网格列数及缓存设置。
- 不申请手机照片库权限；没有手机扫描、上传、备份或双向同步。

## 构建

需要 JDK 17、Android SDK Platform 36；最低运行系统 Android 10/API 29。`local.properties` 写入本机 `sdk.dir`，或设置 `ANDROID_HOME`。不提交本地 SDK 路径。

```bash
cd android
./gradlew assembleDebug testDebugUnitTest lintDebug
```

可安装调试包：`app/build/outputs/apk/debug/app-debug.apk`。版本名读取仓库根 `VERSION`，版本码为 `major * 1000000 + minor * 1000 + patch`；次/修订位数需小于 1000，发布时保持单调递增。调试 APK 使用本机标准调试签名，正式分发需用户提供自己的签名材料。

正式包：`./gradlew assembleRelease`。默认产出 unsigned APK；提供以下环境变量后使用用户自己的签名：`AIPHOTO_KEYSTORE`、`AIPHOTO_STORE_PASSWORD`、`AIPHOTO_KEY_ALIAS`、`AIPHOTO_KEY_PASSWORD`。不要将密钥或密码提交到仓库。未来正式版本必须沿用同一个应用签名，否则无法覆盖升级。

## 安装连接

1. 先部署同版本后端与 Web 镜像（本次升级为 0.22.0），再安装 APK。
2. 输入 NAS 对外的服务根地址，例如 `https://photos.example.com/`；带代理前缀时输入 `https://nas.example.com/photos/`，不要附加 `/api`。
3. HTTP 必须勾选明确允许；自签名或过期证书不会被静默忽略。
4. 使用已有账户登录；不提供注册或手机上传入口。
5. 家庭共享图库中，收藏、评分、备注、移入回收站等会影响其他成员。

升级将使 `/media/**` 必须认证。Web 登录或携带 Bearer 的 API 请求会设置 HttpOnly、SameSite=Strict 的只读媒体会话 Cookie；Cookie 不用于 API 写入授权。已有 Web 会话刷新页面即可通过 API 重新获取 Cookie，失效时重新登录。永久删除及清空回收站现在仅限 ADMIN。反向代理应转发 Cookie、Authorization、Set-Cookie 和 Range，不要为 `/media/` 配置公共缓存。既有浏览器/外部代理中的旧公共缓存需在部署时清理。

服务端实例标识持久保存在照片存储根目录 `.aiphoto-instance-id`；迁移同一实例应保留该文件，替换为另一 NAS 时不能复用旧实例标识。客户端缓存按地址、实例 ID、账户 ID 隔离。

## 接口

新增 `GET /api/mobile/server-info`（公开最小兼容信息）、`GET /api/mobile/photos/{id}/preview`（需认证，最长边 1600 像素 JPEG）、`GET /api/albums/{id}/photos/page?page=0&size=40`（需认证，稳定 ID 倒序）。保留旧相册数组接口兼容 Web。修复 `/api/auth/me` 的空响应。

媒体通过同源 Bearer 请求加载，拒绝跨服务器地址和重定向，避免将凭据转发到第三方。Android 不直接连接数据库或 AI 推理服务。

## 缓存与文件

- 图片缓存默认 512 MiB，可调整为 128/256/512 MiB；元数据每个浏览来源最多保留前 160 项供离线使用。
- 浏览缓存不保证长期离线可用；无网时不排队提交编辑操作。
- 偏好按“地址 + 实例 + 账户”保存在本机；暂未实现偏好跨设备同步。
- 令牌通过 Android Keystore 加密，密码不持久化；禁止系统云备份与设备迁移携带应用私有数据。
- 下载使用系统“保存到”面板；导出文件不因清缓存或退出而删除。失败/取消会尝试移除本次产生的不完整文件。
- 分享副本位于私有缓存目录，通过 FileProvider 临时授予读取权限；24 小时后的副本在下次会话初始化时清理，也可手动清缓存。退出登录清理当前账户的分享缓存。
- 当前显示的缓存占用为图片缓存，不包括元数据及临时分享副本。
- 没有常驻下载服务；退出账户取消任务，强制停止应用或系统杀进程可能中断下载。

## 验证

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
# 连接模拟器或实机后
./gradlew connectedDebugAndroidTest
```

单元测试覆盖代理前缀、查询编码、同源限制、HTTP 显式允许、分页契约及 401/403/重定向。设备测试使用 MockWebServer 测试数据，不是 NAS 生产数据，不会自动修改用户 NAS。

## 当前边界

- M2 宝宝/精选时间线、人物归属维护，以及 M3 去重/采集/AI 管理未实现。
- 地图、像素编辑、元数据脱敏分享、视频转码、断点续传、跨设备偏好同步未实现。
- RAW 依赖 NAS 缩略图；不承诺手机解码全部 RAW/HEIC，视频支持取决于设备解码能力。
- 原图采用有限尺寸解码保证内存；不是无损像素级分块查看器。
- 预览 JPEG 按需生成，尚无服务端磁盘预览缓存；大图库/50MP/弱网/实体 NAS 的性能指标需实际部署验证。
- 搜索结果上限及语义准确度沿用现有后端，不承诺全库任意深度关键词/语义分页。
- 此版本为可安装的首版实现，真实 NAS、Android 10 实机、多用户并发、长时间弱网视频等仍需按需求矩阵完成部署验收。

UI 参考与原始需求见 [`../docs/android-client-requirements.md`](../docs/android-client-requirements.md)，许可见 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)。
