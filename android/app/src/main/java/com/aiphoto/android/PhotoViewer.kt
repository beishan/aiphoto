@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.aiphoto.android

import android.net.ConnectivityManager
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.sin

private const val LIKE_MODE_IDLE_MILLIS = 2_000L

@Composable fun PhotoViewer(vm: AppViewModel, nav: NavHostController, source: String, initialId: Long) {
    val feed = vm.feeds[source] ?: Feed()
    val photos = feed.photos
    val initial = remember {
        ((photos as? GalleryPages)?.indexOfId(initialId) ?: photos.indexOfFirst { it.id == initialId }).coerceAtLeast(0)
    }
    val pager = rememberPagerState(initialPage = initial) { photos.size }
    var controls by rememberSaveable { mutableStateOf(true) }
    var lastLikeAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val likeMode = lastLikeAt != null
    var shareOptions by remember { mutableStateOf(false) }
    var labels by remember { mutableStateOf(false) }
    var moreActions by remember { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    var albumPicker by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmText by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Photo?>(null) }
    var originalId by rememberSaveable { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) pending?.let { vm.download(it, uri) }; pending = null
    }
    val photo = photos.getOrNull(pager.currentPage)
    val canLike = !feed.offline && photo?.isVideo == false
    BackHandler(enabled = likeMode && canLike) { lastLikeAt = null }
    LaunchedEffect(lastLikeAt) {
        val lastTap = lastLikeAt ?: return@LaunchedEffect
        delay((LIKE_MODE_IDLE_MILLIS - (SystemClock.elapsedRealtime() - lastTap)).coerceAtLeast(0L))
        if (lastLikeAt == lastTap) lastLikeAt = null
    }
    LaunchedEffect(pager.currentPage, photos.size) {
        if (pager.currentPage >= photos.size - 3 && photos.isNotEmpty() && feed.more && !feed.loading && feed.error == null && !feed.offline) vm.loadFeed(source)
    }
    fun guard(message: String, action: () -> Unit) {
        val metered = context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered
        if ((metered && !vm.cellularOriginal) || message.isNotBlank()) {
            confirmText = listOfNotNull(message.takeIf { it.isNotBlank() }, "当前网络按流量计费，将读取原文件。".takeIf { metered && !vm.cellularOriginal }).joinToString("\n")
            confirmAction = action
        } else action()
    }
    fun like(id: Long, fromGesture: Boolean): Boolean {
        if (feed.offline) {
            vm.notice = "离线时无法点赞"
            return false
        }
        val sent = vm.likePhoto(id)
        if (sent && (fromGesture || lastLikeAt != null)) lastLikeAt = SystemClock.elapsedRealtime()
        return sent
    }
    DarkMediaSystemBars()
    Column(Modifier.fillMaxSize().background(Color(0xFF101411))) {
        if (controls || vm.error != null || feed.error != null || photo == null || photo.isVideo) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
        }
        if (controls) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("${if (photos.isEmpty()) 0 else pager.currentPage + 1} / ${photos.size}${if (feed.more) "+" else ""}", color = Color.White)
                photo?.let { Text(it.name, color = Color.LightGray, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (photo != null) {
                if (!photo.isVideo) TextButton({ guard("") { originalId = photo.id } }, enabled = !feed.offline) {
                    Text(if (originalId == photo.id) "原图" else "查看原图", color = Color.White)
                }
                Box {
                    IconButton({ moreActions = true }) { Icon(Icons.Outlined.MoreHoriz, "更多照片操作", tint = Color.White) }
                    DropdownMenu(moreActions, { moreActions = false }) {
                        DropdownMenuItem({ Text("详情") }, { moreActions = false; details = true })
                        DropdownMenuItem({ Text("加入相册") }, { moreActions = false; vm.loadCatalog(); albumPicker = true },
                            enabled = !vm.busy && !feed.offline)
                        DropdownMenuItem({ Text("下载") }, { moreActions = false; guard("") { pending = photo; export.launch(photo.name) } },
                            enabled = !feed.offline)
                        DropdownMenuItem({ Text("分享") }, { moreActions = false; shareOptions = true }, enabled = !feed.offline)
                        if (vm.supports("label-edit")) {
                            DropdownMenuItem({ Text("标签") }, { moreActions = false; vm.loadCatalog(); labels = true },
                                enabled = !feed.offline && !vm.busy)
                        }
                        if (vm.supports("family-timeline")) {
                            DropdownMenuItem({ Text(if (photo.inTimeline) "移出精选" else "加入精选") }, {
                                moreActions = false
                                vm.updatePhoto(photo.id, mapOf("inTimeline" to !photo.inTimeline))
                            }, enabled = !vm.busy && !feed.offline)
                        }
                        if ((source.startsWith("album:") || source.startsWith("baby:")) &&
                            vm.albums.find { it.id == source.substringAfter(':').toLongOrNull() }?.type in listOf("VIRTUAL", "BABY")) {
                            DropdownMenuItem({ Text("设为封面") }, {
                                moreActions = false
                                vm.mutate({ it.write("albums/${source.substringAfter(':')}/cover", "PUT", mapOf("photoId" to photo.id)) }) { vm.loadCatalog() }
                            }, enabled = !vm.busy && !feed.offline)
                        }
                    }
                }
            }
        }
        if (vm.error != null) ErrorNotice(vm.error!!, { vm.error = null })
        if (feed.error != null) ErrorNotice(feed.error, {}, { vm.loadFeed(source) })
        if (photo == null) { Text("照片已移除，请返回图库", Modifier.padding(24.dp), color = Color.White); return@Column }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val videoClearance = if (controls && photo.isVideo) Modifier.navigationBarsPadding().padding(bottom = 80.dp) else if (controls) Modifier.padding(bottom = 48.dp) else Modifier
            HorizontalPager(pager, modifier = Modifier.fillMaxSize().then(videoClearance), key = { photos[it].id }, beyondViewportPageCount = 0) { index ->
                val item = photos[index]
                if (item.isVideo) VideoContent(vm, item, active = pager.currentPage == index)
                else ZoomPhoto(vm, item, originalId == item.id, likeMode && canLike, { controls = !controls }) {
                    like(item.id, fromGesture = true)
                }
            }
            if (controls) ViewerActionGroup(
                photo = photo,
                canEdit = !vm.busy && !feed.offline,
                canLike = canLike,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
                onFavorite = { vm.updatePhoto(photo.id, mapOf("favorite" to !photo.favorite)) },
                onLike = { like(photo.id, fromGesture = false) },
                onDelete = { deleting = true },
            )
        }
        if (!controls && photo.isVideo) Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
    if (shareOptions && photo != null) AlertDialog(onDismissRequest = { shareOptions = false }, title = { Text("分享照片") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("原文件可能含位置及设备信息。副本将重新编码为 JPEG（最长边 2048、白色背景），去除 EXIF/XMP 等原文件元数据，但画面中的文字、人脸和水印仍会保留。")
            if (photo.mediaType == "PHOTO") Button({ shareOptions = false; guard("") { vm.download(photo, share = true, privacyCopy = true) } }, Modifier.fillMaxWidth()) { Text("分享去元数据副本") }
            OutlinedButton({ shareOptions = false; guard("原文件可能包含拍摄位置等元数据，将交给你选择的应用处理。") { vm.download(photo, share = true) } }, Modifier.fillMaxWidth()) { Text("分享原文件") }
            if (photo.mediaType != "PHOTO") Text("视频、GIF 和 RAW 暂不支持此副本转换。", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton({ shareOptions = false }) { Text("取消") } })
    if (labels && photo != null) PhotoLabels(vm, photo.id, { labels = false })
    if (details && photo != null) PhotoDetails(vm, photo, !feed.offline, { details = false })
    if (albumPicker && photo != null) AlbumPicker(vm, { albumPicker = false }) { album -> vm.batch(setOf(photo.id), "add", album.id) { albumPicker = false } }
    if (deleting && photo != null) ConfirmDialog("移入回收站？", "会影响 NAS 共享图库和其他客户端，不会删除手机文件。", { deleting = false }) { deleting = false; vm.batch(setOf(photo.id), "trash") { nav.popBackStack() } }
    if (confirmAction != null) ConfirmDialog("确认读取原文件", confirmText, { confirmAction = null }) { val action = confirmAction; confirmAction = null; action?.invoke() }
}
@Composable
private fun ViewerActionGroup(
    photo: Photo, canEdit: Boolean, canLike: Boolean, modifier: Modifier,
    onFavorite: () -> Unit, onLike: () -> Boolean, onDelete: () -> Unit,
) {
    var sequence by remember(photo.id) { mutableIntStateOf(0) }
    val likes = remember(photo.id) { mutableStateListOf<FloatingLike>() }
    val iconCenter = with(LocalDensity.current) { 22.dp.toPx() }
    Box(modifier.widthIn(max = 340.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFF8F8FA).copy(alpha = .82f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .72f)),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                ViewerAction(if (photo.favorite) "已收藏" else "收藏",
                    if (photo.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    canEdit, Modifier.weight(1f), onFavorite)
                ViewerAction("点赞", Icons.Outlined.ThumbUp, canLike, Modifier.weight(1f)) {
                    if (onLike()) likes.add(FloatingLike(sequence++, Offset(iconCenter, iconCenter)))
                }
                ViewerAction("删除", Icons.Outlined.DeleteOutline, canEdit, Modifier.weight(1f), onDelete)
            }
        }
        likes.forEach { like ->
            key(like.id) { FloatingThumb(like, Modifier.align(Alignment.Center)) { likes.remove(like) } }
        }
    }
}

@Composable
private fun ViewerAction(
    title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean,
    modifier: Modifier, action: () -> Unit,
) {
    val color = if (enabled) Color(0xFF171717) else Color(0xFF6E6E73)
    Column(
        modifier.heightIn(min = 52.dp).clickable(enabled = enabled, onClick = action).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(title, Modifier.padding(top = 3.dp), color = color, style = MaterialTheme.typography.labelSmall)
    }
}
@Composable private fun ZoomPhoto(
    vm: AppViewModel, photo: Photo, original: Boolean, likeMode: Boolean,
    onTap: () -> Unit, onLike: () -> Boolean,
) {
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }
    var failed by remember(photo.id, original) { mutableStateOf(false) }
    var likeSequence by remember(photo.id) { mutableIntStateOf(0) }
    val floatingLikes = remember(photo.id) { mutableStateListOf<FloatingLike>() }
    val latestTap by rememberUpdatedState(onTap)
    val latestLike by rememberUpdatedState(onLike)
    val context = LocalContext.current
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    Box(Modifier.fillMaxSize().testTag("viewer-photo").semantics {
        if (likeMode) stateDescription = "点赞模式"
    }.pointerInput(photo.id, likeMode) {
        fun like(at: Offset) {
            if (latestLike()) floatingLikes.add(FloatingLike(likeSequence++, at))
        }
        if (likeMode) detectTapGestures(onTap = ::like)
        else detectTapGestures(onTap = { latestTap() }, onDoubleTap = ::like)
    }, contentAlignment = Alignment.Center) {
        vm.imageLoader?.let { loader ->
            AsyncImage(model = ImageRequest.Builder(context).data(if (failed) vm.mediaUrl(photo.thumbnailUrl) else if (original) vm.mediaUrl(photo.originalUrl) else vm.preview(photo)).size(2048, 2048).build(), imageLoader = loader,
                contentDescription = photo.name, contentScale = ContentScale.FillWidth,
                onError = { failed = true }, modifier = Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                    .transformable(state = transform, canPan = { scale > 1f }))
        }
        if (failed) Text("预览不可用，使用缩略图；可下载原文件。", Modifier.align(Alignment.BottomCenter).padding(12.dp), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        floatingLikes.forEach { like ->
            key(like.id) {
                FloatingThumb(like, Modifier.align(AbsoluteAlignment.TopLeft)) { floatingLikes.remove(like) }
            }
        }
    }
}

private data class FloatingLike(val id: Int, val touch: Offset)

@Composable
private fun FloatingThumb(like: FloatingLike, modifier: Modifier, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val latestFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(like.id) {
        progress.animateTo(1f, tween(1050, easing = LinearEasing))
        latestFinished()
    }
    val density = LocalDensity.current
    val halfIcon = with(density) { 22.dp.toPx() }
    val rise = with(density) { 156.dp.toPx() }
    val drift = with(density) { 25.dp.toPx() }
    val direction = if (like.id % 2 == 0) 1f else -1f
    Icon(
        Icons.Filled.ThumbUp, contentDescription = null, tint = Color(0xFFFFD476),
        modifier = modifier.size(44.dp).graphicsLayer {
            val fraction = progress.value
            val curl = sin(fraction * 5.5f) * .52f + fraction * .65f
            translationX = like.touch.x - halfIcon + direction * drift * curl
            translationY = like.touch.y - halfIcon - rise * fraction * (1.36f - .36f * fraction)
            rotationZ = direction * (sin(fraction * 4.6f) * 12f - fraction * 15f)
            scaleX = .72f + .52f * (fraction * 4f).coerceAtMost(1f)
            scaleY = scaleX
            alpha = 1f - ((fraction - .28f) / .72f).coerceIn(0f, 1f)
        },
    )
}
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable private fun VideoContent(vm: AppViewModel, photo: Photo, active: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var failure by remember(photo.id) { mutableStateOf<String?>(null) }
    val client = vm.api ?: return
    val player = remember(photo.id, client) {
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, OkHttpDataSource.Factory(client.http)))).build().apply {
            vm.mediaUrl(photo.originalUrl)?.let { setMediaItem(MediaItem.fromUri(it)); prepare() }
            playWhenReady = false
            addListener(object : Player.Listener { override fun onPlayerError(error: PlaybackException) { failure = "视频无法播放，可能是网络或设备不支持此编码。可下载原文件。" } })
        }
    }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); player.release(); (context as? android.app.Activity)?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    LaunchedEffect(active, player) { if (active) player.play() else player.pause() }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player;
            setFullscreenButtonClickListener { full ->
                (context as? android.app.Activity)?.requestedOrientation = if (full) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
        failure?.let { Text(it, Modifier.align(Alignment.TopCenter).padding(20.dp), color = Color.White) }
    }
}
@Composable private fun PhotoDetails(vm: AppViewModel, fallback: Photo, editable: Boolean, dismiss: () -> Unit) {
    var detail by remember(fallback.id) { mutableStateOf(fallback) }
    var note by rememberSaveable(fallback.id) { mutableStateOf(fallback.note.orEmpty()) }
    var rating by rememberSaveable(fallback.id) { mutableIntStateOf(fallback.rating ?: 0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(fallback.id) {
        try { val result = vm.detail(fallback.id); detail = result; note = result.note.orEmpty(); rating = result.rating ?: 0 }
        catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; loadError = "暂时无法获取完整详情，以下为已有数据" }
    }
    ModalBottomSheet(dismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("照片详情", style = MaterialTheme.typography.headlineSmall)
            if (loadError != null) Text(loadError!!, color = MaterialTheme.colorScheme.error)
            Text(detail.name, fontWeight = FontWeight.SemiBold)
            DetailRow("拍摄时间", detail.exifDate?.replace('T', ' ')); DetailRow("入库时间", detail.createdAt?.replace('T', ' '))
            DetailRow("尺寸", if (detail.width != null && detail.height != null) "${detail.width} × ${detail.height}" else null)
            DetailRow("文件大小", detail.fileSize?.let { String.format(Locale.ROOT, "%.1f MB", it / 1048576.0) })
            DetailRow("类型", detail.mediaType)
            if (detail.gpsLat != null && detail.gpsLng != null) DetailRow("GPS", "${detail.gpsLat}, ${detail.gpsLng}")
            Text("评分：${if (rating == 0) "未评分" else rating.toString()}")
            Row { (1..5).forEach { n -> IconButton({ rating = if (rating == n) 0 else n }, enabled = editable && !vm.busy) { Icon(if (rating >= n) Icons.Outlined.Star else Icons.Outlined.StarBorder, "$n 星", tint = MaterialTheme.colorScheme.primary) } } }
            OutlinedTextField(note, { if (it.length <= 10000) note = it }, label = { Text("备注") }, minLines = 2, enabled = editable && !vm.busy, modifier = Modifier.fillMaxWidth())
            if (!detail.aiCaption.isNullOrBlank()) { Text("AI 描述", fontWeight = FontWeight.SemiBold); Text(detail.aiCaption!!) }
            detail.tags?.let { tags -> if (tags.isNotEmpty()) Text("标签：" + tags.joinToString(" · ") { if (it.isJsonPrimitive) it.asString else it.asJsonObject.get("name")?.asString.orEmpty() }) }
            detail.people?.let { people -> if (people.isNotEmpty()) Text("人物：" + people.joinToString(" · ") { it.name ?: "未命名" }) }
            Button({ vm.updatePhoto(detail.id, mapOf("note" to note, "rating" to rating)) { dismiss() } }, enabled = editable && !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("保存修改") }
            Text("收藏、评分和备注属于 NAS 共享图库。", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
        }
    }
}
@Composable private fun DetailRow(name: String, value: String?) { if (!value.isNullOrBlank()) Row(Modifier.fillMaxWidth()) { Text(name, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, Modifier.weight(2f)) } }
