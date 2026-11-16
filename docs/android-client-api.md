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
