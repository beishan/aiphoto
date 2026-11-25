@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.aiphoto.android

import android.net.ConnectivityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import java.util.Locale

@Composable fun PhotoViewer(vm: AppViewModel, nav: NavHostController, source: String, initialId: Long) {
    val feed = vm.feeds[source] ?: Feed()
    val photos = feed.photos
    val initial = remember {
        ((photos as? GalleryPages)?.indexOfId(initialId) ?: photos.indexOfFirst { it.id == initialId }).coerceAtLeast(0)
    }
    val pager = rememberPagerState(initialPage = initial) { photos.size }
    var controls by rememberSaveable { mutableStateOf(true) }
    var shareOptions by remember { mutableStateOf(false) }
    var labels by remember { mutableStateOf(false) }
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
    DarkMediaSystemBars()
    Column(Modifier.fillMaxSize().background(Color(0xFF101411))) {
        if (controls || vm.error != null || feed.error != null || photo == null || photo.isVideo) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.safeDrawing))
        }
        if (controls) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = Color.White) }
            Text("${if (photos.isEmpty()) 0 else pager.currentPage + 1} / ${photos.size}${if (feed.more) "+" else ""}", Modifier.weight(1f), color = Color.White)
            if (photo != null && !photo.isVideo) TextButton({ guard("") { originalId = photo.id } }, enabled = !feed.offline) { Text(if (originalId == photo.id) "原图" else "查看原图", color = Color.White) }
        }
        if (vm.error != null) ErrorNotice(vm.error!!, { vm.error = null })
        if (feed.error != null) ErrorNotice(feed.error, {}, { vm.loadFeed(source) })
        if (photo == null) { Text("照片已移除，请返回图库", Modifier.padding(24.dp), color = Color.White); return@Column }
        HorizontalPager(pager, modifier = Modifier.weight(1f), key = { photos[it].id }, beyondViewportPageCount = 0) { index ->
            val item = photos[index]
            if (item.isVideo) VideoContent(vm, item, active = pager.currentPage == index) else ZoomPhoto(vm, item, originalId == item.id) { controls = !controls }
        }
        if (controls) Text(photo.name, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        if (controls) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
            ViewerAction(if (photo.favorite) "已收藏" else "收藏", if (photo.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, !vm.busy && !feed.offline) { vm.updatePhoto(photo.id, mapOf("favorite" to !photo.favorite)) }
            ViewerAction("详情", Icons.Outlined.Info, true) { details = true }
            ViewerAction("加入相册", Icons.Outlined.CreateNewFolder, !vm.busy && !feed.offline) { vm.loadCatalog(); albumPicker = true }
            ViewerAction("下载", Icons.Outlined.Download, !feed.offline) { guard("") { pending = photo; export.launch(photo.name) } }
            ViewerAction("分享", Icons.Outlined.Share, !feed.offline) { shareOptions = true }
            if (vm.supports("label-edit")) ViewerAction("标签", Icons.AutoMirrored.Outlined.Label, !feed.offline && !vm.busy) { vm.loadCatalog(); labels = true }
            if (vm.supports("family-timeline")) ViewerAction(if (photo.inTimeline) "移出精选" else "加入精选", Icons.Outlined.History, !vm.busy && !feed.offline) {
                vm.updatePhoto(photo.id, mapOf("inTimeline" to !photo.inTimeline))
            }
            if ((source.startsWith("album:") || source.startsWith("baby:")) && vm.albums.find { it.id == source.substringAfter(':').toLongOrNull() }?.type in listOf("VIRTUAL", "BABY")) {
                ViewerAction("设为封面", Icons.Outlined.PhotoAlbum, !vm.busy && !feed.offline) {
                    vm.mutate({ it.write("albums/${source.substringAfter(':')}/cover", "PUT", mapOf("photoId" to photo.id)) }, "已设为封面") { vm.loadCatalog() }
                }
            }
            ViewerAction("删除", Icons.Outlined.DeleteOutline, !vm.busy && !feed.offline) { deleting = true }
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
@Composable private fun ViewerAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, action: () -> Unit) {
    Column(Modifier.widthIn(min = 64.dp).clickable(enabled = enabled, onClick = action).padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, title, tint = if (enabled) Color.White else Color.Gray, modifier = Modifier.size(22.dp))
        Text(title, Modifier.padding(top = 4.dp), color = if (enabled) Color.White else Color.Gray, style = MaterialTheme.typography.labelSmall)
    }
}
@Composable private fun ZoomPhoto(vm: AppViewModel, photo: Photo, original: Boolean, onTap: () -> Unit) {
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var offset by remember(photo.id) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var failed by remember(photo.id, original) { mutableStateOf(false) }
    val context = LocalContext.current
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + pan else androidx.compose.ui.geometry.Offset.Zero
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        vm.imageLoader?.let { loader ->
            AsyncImage(model = ImageRequest.Builder(context).data(if (failed) vm.mediaUrl(photo.thumbnailUrl) else if (original) vm.mediaUrl(photo.originalUrl) else vm.preview(photo)).size(2048, 2048).build(), imageLoader = loader,
                contentDescription = photo.name, contentScale = ContentScale.FillWidth,
                onError = { failed = true }, modifier = Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
                    .pointerInput(photo.id) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { scale = if (scale > 1f) 1f else 2f; offset = androidx.compose.ui.geometry.Offset.Zero }) }
                    .transformable(state = transform, canPan = { scale > 1f }))
        }
        if (failed) Text("预览不可用，使用缩略图；可下载原文件。", Modifier.align(Alignment.BottomCenter).padding(12.dp), color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
    }
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
