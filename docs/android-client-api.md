# 安卓首版 API 契约（0.22.0 / API 1）

客户端访问用户配置的服务根地址，自动追加 `api/`。除登录、注册、退出与最小服务信息外，API 需要 `Authorization: Bearer <JWT>`。不直接访问 AI 服务。

## 最小服务信息

`GET /api/mobile/server-info`：

```json
{
  "product": "aiphoto",
  "version": "0.22.0",
  "apiVersion": 1,
  "instanceId": "持久 UUID",
  "libraryMode": "SHARED",
  "capabilities": ["album-pagination", "authenticated-media", "preview", "shared-library-edit"]
}
```

`instanceId` 在照片存储根目录 `.aiphoto-instance-id` 中持久化。只展示兼容信息，不公开用户、路径或照片统计。客户端要求 API 1 及必要能力；服务实例改变时重新登录。

## 媒体与预览

`GET /api/mobile/photos/{id}/preview` 需认证，对非回收站 PHOTO 生成最长边 1600 像素 JPEG；无法解码或非 PHOTO 返回 415，客户端退回缩略图，不自动改为原图。当前按需生成，尚未增加服务端预览磁盘缓存。

`GET/HEAD /media/photos/**`、`/media/thumbs/**` 需要认证。安卓图片、视频和下载均携带同源 Bearer；Web 通过 HttpOnly、SameSite=Strict 会话 Cookie `aiphoto_media` 读取媒体。Cookie 只对 GET/HEAD 媒体资源生效，不能授权 API 写入。Bearer 的优先级高于 Cookie，禁用账户与过期令牌不能读取资源。

普通原件及缩略图 `Cache-Control: no-store`；受保护头像及图标改为 private/must-revalidate，不能作为公共 CDN 资源。Android 的私有应用缓存由自身策略管理。视频沿用 Spring MVC ResourceHttpRequestHandler 的 Range 能力：有效范围 206，越界范围 416。代理不得去除 Range 或缓存为公共内容。

登录及认证 API 请求为 Web 设置媒体 Cookie；`POST /api/auth/logout` 清除 Cookie。现有 JWT 无服务端注销黑名单，已签发 Bearer 仍遵循原有到期规则；安卓退出销毁本地令牌，不承诺远程撤销所有设备会话。

## 相册分页

保留 `GET /api/albums/{id}/photos` 数组接口。新增：

`GET /api/albums/{id}/photos/page?page=0&size=40`

返回 Spring Page：`content`、`number`、`size`、`last`、`totalElements` 等。过滤软删除照片，按照片 ID 倒序；page 最小 0，size 限制 1—100。相册不存在返回 404。重复向同一相册添加同一照片不再创建重复关联。

封面使用缩略图，避免相册/人物列表默认下载原件。

## 登录与操作

`POST /api/auth/login` 保留 `{token,user}` 契约。`GET /api/auth/me` 修复为返回当前用户，安卓日常使用 `GET /api/users/me`。手机无注册和上传入口。

- 图库：`GET /api/photos/browse?beforeId=...&size=40` → `{content,nextCursor,hasMore}`，表示最近入库顺序。
- 搜索：`GET /api/search`，复用 `query/type/startDate/endDate/personId/tagId/fileType/minRating/page/size`。
- 详情/编辑：`GET /api/photos/{id}/detail`、`PUT /api/photos/{id}`。更新仅传编辑字段，0 分为清除评分。
- 收藏：`GET /api/photos/favorites`；移入回收站：`DELETE /api/photos/{id}`。
- 回收站：`GET /api/photos/trash`、`POST /api/photos/trash/{id}/restore`。
- 相册、人物、分类、标签：沿用现有接口。
- 多项整理由安卓逐项调用并记录失败 ID；恢复/关联操作不做无条件网络重试。

不存在资源、无权限及不支持格式分别保留 404/403/415。API 错误包含 `message`；新增状态异常带 `code: HTTP_404` 等，权限异常带 `code: FORBIDDEN`。认证失效为 401，客户端集中回到登录；403 不作为会话失效处理。

永久删除及清空回收站要求 ADMIN，安卓无对应入口。共享图库中收藏、备注等不是个人私有字段；恢复照片不会自动恢复此前清除的收藏/时间线标记及封面引用。


## 0.23.0 家庭功能扩展（仍为 API 1）

新增 capability：`family-timeline`、`baby-timeline-page`、`people-edit`。旧版安卓可继续使用原有接口；新版安卓连接 0.22.0 时隐藏或禁用家庭功能。

### 宝宝时间线

- `POST /api/albums`：`type: BABY` 时必须提供有效 `birthDate`（YYYY-MM-DD，不晚于服务端当天），名称 1—100 字符。
- `PUT /api/albums/{id}`：可更新宝宝出生日期；未传的名称/描述/共享字段保留，不允许更改相册类型。
- `GET /api/albums/{id}/baby-timeline/page?page=0&size=40`：Spring Page，按 EXIF 日期升序、ID 升序，未知日期在末尾；排除回收站照片，size 限制 1—100。
- `content` 仍为 PhotoDTO，增加 `ageLabel`，其他媒体地址、尺寸、收藏和精选字段保留。相册不匹配或缺少出生日期为 400，不存在为 404。
- `GET /api/albums/{id}/baby-timeline`：补齐原 Web 调用，保留年龄标签到照片数组的映射格式；同样排除回收站并保留日期未知照片。

年龄规则：按 NAS 保存的 EXIF 本地日期，不进行手机时区转换。出生前单独分组；未满一个日历月按出生后经过天数显示（出生当天第 0 天），满月后按整月显示，一岁后按年和余月显示。月末周年截断到对应月份最后一天；未知日期显示“拍摄日期未知”。修改生日只改变分组，不改原文件、EXIF 或相册关联。

### 精选时间线

复用 `GET /api/timeline/months` 和 `GET /api/timeline/photos?year=2026&month=11&page=0&size=40`。增加 `GET /api/timeline/undated/count` → `{count}`，以及 `GET /api/timeline/undated?page=0&size=40`；后者排除软删除并按 ID 倒序，size 限制 1—100。

加入或移出精选使用 `PUT /api/photos/{id}`，只传 `inTimeline`，表示明确设置目标状态，不调用非幂等 toggle。照片的 `inTimeline` 已补齐到相册、人物和分类结果；移出后同步更新已加载列表及本机缓存。

### 人物整理

- `PUT /api/people/{id}`：改名，空白恢复未命名，最多 100 字符。
- `GET /api/people/{id}/faces/page?page=0&size=40`：新增分页，排除回收站照片中的人脸，ID 升序，size 1—100。FaceDTO 新增 `previewUrl`，旧 `photoUrl` 保持兼容。
- `GET /api/mobile/faces/{id}/preview`：Bearer 认证，生成最长边 256 像素 JPEG 裁剪，no-store；失效/回收站资源 404，无效或越界区域 422。复用 Thumbnailator，最多并发生成 2 张，5 秒内不能取得执行名额返回 429。
- `PUT /api/people/{id}/cover-face`：封面必须属于该人物且照片未在回收站。
- `POST /api/people/merge`、`POST /api/people/faces/{id}/assign`：仅 ADMIN；自我合并拒绝，操作与聚类采用同一事务锁；归属修正清除失效的原人物封面并刷新双方去重后的照片数。
- 合并保留目标人物，删除源人物；尽可能保留封面，合并首次/末次出现时间。当前无一键撤销接口。

当前图库仍为家庭共享模式；登录成员可改名和设置封面，管理员执行合并和归属修正。人物照片列表改为数据库去重分页，避免全量加载人脸后在内存分页。修改生日及人物合并/归属修正后清除相关本机列表缓存。


## 0.24.0 整理与本地分享副本

新增 capability `label-edit`，安卓据此显示维护入口，API 版本仍为 1。

### 词条与关联

- 沿用 `/api/tags`、`/api/categories` 新建/更新接口。标签名称 1—50 字符、说明最多 500 字符；分类名称 1—100 字符；颜色使用 `#RRGGBB`。名称去除首尾空白。
- AI 标签词条及系统分类元数据只读；自建分类可手工整理。删除标签、分类及合并标签须 ADMIN，删除不删除原照片。合并拒绝同一标签，且只允许人工词条。
- 照片标签添加使用 `POST /api/tags/photos/{photoId}` 与 `{tagId}`。即使已有 AI 关联，也会转为 MANUAL/置信度 1.0；同时删除该照片/标签的排除记录。
- 移除标签使用 `DELETE /api/tags/photos/{photoId}/{tagId}`，保存排除记录；AI 写入在相同照片行锁下检查排除表，避免并发索引悄悄恢复用户已移除的标签。词条整体删除后外键级联清除对应排除记录。
- `photo_tag_exclusions(photo_id,tag_id)` 随 `schema.sql` 幂等创建，外键随照片/标签删除级联。现有 NAS 数据库由 Spring SQL 初始化（mode=always）执行新增 DDL，无需重新初始化存储卷。
- 自建分类关联 `POST /api/categories/{categoryId}/photos/{photoId}` 使用 manual upsert；移除采用单项 native delete，不重写其他关联。`clearAutoAssignedPhotos` 只删除 auto，不影响手动选择。
- 安卓多选逐项执行，展示部分失败并保留失败 ID；相关列表失效后重新加载。离线只读。

### 分享副本

不新增上传或服务端分享接口。手机复用认证的原图 GET，在私有缓存中由 [Android ImageDecoder](https://developer.android.com/reference/android/graphics/ImageDecoder) 解码（方向、尺寸和 sRGB）后使用 [Bitmap.compress](https://developer.android.com/reference/android/graphics/Bitmap) 生成新的 JPEG。只将新文件通过 FileProvider 精确授权 URI 交给用户选择的应用，不传 NAS 地址、令牌或原始文件名。

副本最长边 2048、JPEG 质量 92、透明部分白底；新文件不拷贝源 EXIF/XMP，旋转体现在像素中。JPEG 的新尺寸/编码信息仍存在，不承诺隐藏画面内容中的身份或位置。原文件字节不修改；失败或取消时清理不完整副本，不能回退到原件分享。暂不转换视频、动图和 RAW。
