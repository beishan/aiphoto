@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.aiphoto.android

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.platform.LocalContext
import java.text.DateFormat
import java.util.Date

@Composable
fun AiphotoApp(vm: AppViewModel) {
    if (vm.session == null) { ConnectionScreens(vm); return }
    key(vm.session!!.user.id, vm.store.address) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route.orEmpty()
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(vm.notice) { vm.notice?.let { snack.showSnackbar(it); vm.notice = null } }
        Scaffold(snackbarHost = { SnackbarHost(snack, modifier = Modifier.padding(bottom = if (route.startsWith("viewer")) 80.dp else 0.dp)) }, bottomBar = {
            if (route in listOf("gallery", "albums", "discover", "mine")) NavigationBar {
                listOf(Triple("gallery", "照片", Icons.Outlined.PhotoLibrary), Triple("albums", "相册", Icons.Outlined.PhotoAlbum), Triple("discover", "发现", Icons.Outlined.Explore), Triple("mine", "我的", Icons.Outlined.Person)).forEach { (path, label, icon) ->
                    NavigationBarItem(selected = route == path, onClick = { nav.navigate(path) { popUpTo("gallery") { saveState = true }; launchSingleTop = true; restoreState = true } }, icon = { Icon(icon, label) }, label = { Text(label) })
                }
            }
        }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if (vm.error != null && !route.startsWith("viewer")) ErrorNotice(vm.error!!, { vm.error = null })
                NavHost(nav, "gallery", Modifier.weight(1f)) {
                    composable("gallery") { PhotoFeedScreen(vm, nav, "gallery", "照片", root = true) }
                    composable("albums") { AlbumsScreen(vm, nav) }
                    composable("timeline") { TimelineScreen(vm, nav) }
                    composable("person/{id}") { e -> PersonScreen(vm, nav, e.arguments!!.getString("id")!!.toLong()) }
                    composable("discover") { DiscoverScreen(vm, nav) }
                    composable("mine") { MineScreen(vm, nav) }
                    composable("settings") { SettingsScreen(vm, nav) }
                    composable("about") { AboutScreen(vm, nav) }
                    composable("search") { SearchScreen(vm, nav) }
                    composable("feed/{source}/{title}") { e -> PhotoFeedScreen(vm, nav, e.arguments!!.getString("source")!!, e.arguments!!.getString("title")!!) }
                    composable("album/{id}") { e -> AlbumScreen(vm, nav, e.arguments!!.getString("id")!!.toLong()) }
                    composable("album-edit/{id}") { e -> AlbumEditor(vm, nav, e.arguments!!.getString("id")!!.toLong()) }
                    composable("pick/{id}") { e -> PhotoFeedScreen(vm, nav, "gallery", "从 NAS 图库选择", pickAlbum = e.arguments!!.getString("id")!!.toLong()) }
                    composable("viewer/{source}/{id}") { e -> PhotoViewer(vm, nav, e.arguments!!.getString("source")!!, e.arguments!!.getString("id")!!.toLong()) }
                    composable("downloads") { DownloadsScreen(vm, nav) }
                }
            }
        }
    }
}
fun NavHostController.feed(source: String, title: String) { navigate("feed/${Uri.encode(source)}/${Uri.encode(title)}") }
fun NavHostController.viewer(source: String, id: Long) { navigate("viewer/${Uri.encode(source)}/$id") }
@Composable fun BackBar(title: String, nav: NavHostController, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }, actions = actions)
}
@Composable fun ErrorNotice(message: String, dismiss: () -> Unit, retry: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        if (retry != null) TextButton(retry) { Text("重试") }
        IconButton(dismiss) { Icon(Icons.Outlined.Close, "关闭提示") }
    } }
}
@Composable fun EmptyState(title: String, subtitle: String = "", retry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (retry != null) OutlinedButton(retry) { Text("重新加载") }
    }
}
@Composable fun PhotoImage(vm: AppViewModel, path: String?, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop, onLoad: ((Boolean) -> Unit)? = null) {
    val context = LocalContext.current
    val url = vm.mediaUrl(path)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.outline)
        vm.imageLoader?.let { loader -> AsyncImage(model = ImageRequest.Builder(context).data(url).crossfade(true).build(), imageLoader = loader, contentDescription = null, contentScale = scale, onSuccess = { onLoad?.invoke(true) }, onError = { onLoad?.invoke(false) }, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
fun ConnectionScreens(vm: AppViewModel) {
    var address by rememberSaveable { mutableStateOf(vm.store.address) }
    var allowHttp by rememberSaveable { mutableStateOf(vm.store.allowHttp) }
    var username by rememberSaveable { mutableStateOf(vm.store.username) }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).imePadding().padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(36.dp))
        Icon(Icons.Outlined.Landscape, "aiphoto", Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp), tint = MaterialTheme.colorScheme.primary)
        Text("aiphoto", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text(if (vm.connected) "欢迎回家" else "连接你的相册", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(if (vm.connected) "照片与 AI 数据保存在你的 NAS" else "随时看看，家的每一刻", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (vm.error != null) ErrorNotice(vm.error!!, { vm.error = null })
        if (!vm.connected) {
            OutlinedTextField(address, { address = it }, label = { Text("服务器地址") }, placeholder = { Text("https://photos.example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !vm.busy)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(allowHttp, { allowHttp = it }); Text("允许未加密 HTTP（仅用于可信网络）", style = MaterialTheme.typography.bodySmall) }
            Button({ vm.connect(address, allowHttp) }, enabled = address.isNotBlank() && !vm.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (vm.busy) "正在检查服务…" else "连接服务器") }
            Text("连接自己的 NAS，无手机相册备份。", style = MaterialTheme.typography.bodySmall)
        } else {
            AssistChip(onClick = { vm.changeConnection() }, label = { Text("${Uri.parse(vm.store.address).host} · 更换服务器") }, leadingIcon = { Icon(Icons.Outlined.Dns, null) })
            OutlinedTextField(username, { username = it }, label = { Text("用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !vm.busy)
            OutlinedTextField(password, { password = it }, label = { Text("密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !vm.busy)
            Button({ vm.login(username.trim(), password); password = "" }, enabled = username.isNotBlank() && password.isNotEmpty() && !vm.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (vm.busy) "登录中…" else "登录") }
            Text("使用现有 NAS 账户登录。家庭共享图库中的收藏、评分和备注会影响其他成员。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PhotoFeedScreen(vm: AppViewModel, nav: NavHostController, source: String, title: String, root: Boolean = false, pickAlbum: Long? = null, header: @Composable (() -> Unit)? = null) {
    val feed = vm.feeds[source] ?: Feed()
    var selected by rememberSaveable(source) { mutableStateOf(longArrayOf()) }
    var selecting by rememberSaveable(source) { mutableStateOf(pickAlbum != null) }
    var albumPicker by remember { mutableStateOf(false) }
    var confirmTrash by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val cells = remember(feed.photos, source) { feedCells(feed.photos, source) }
    LaunchedEffect(vm.failedBatchIds) {
        if (selecting && vm.failedBatchIds.isNotEmpty()) selected = selected.filter { it in vm.failedBatchIds }.toLongArray()
    }
    LaunchedEffect(source) { if (vm.feeds[source] == null) vm.loadFeed(source, true) }
    LaunchedEffect(gridState, source) {
        snapshotFlow { (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) to gridState.layoutInfo.totalItemsCount }.collect { (last, total) ->
            val current = vm.feeds[source] ?: return@collect
            if (last >= total - 9 && current.photos.isNotEmpty() && !current.loading && current.more && current.error == null && !current.offline) vm.loadFeed(source)
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (root) TopAppBar(title = { Text("照片", fontWeight = FontWeight.Bold) }, actions = {
            IconButton({ nav.navigate("search") }) { Icon(Icons.Outlined.Search, "搜索照片") }
            IconButton({ vm.loadFeed(source, true) }) { Icon(Icons.Outlined.Refresh, "刷新") }
        }) else if (header == null) BackBar(title, nav) { IconButton({ vm.loadFeed(source, true) }) { Icon(Icons.Outlined.Refresh, "刷新") } }
        header?.invoke()
        if (root) {
            OutlinedButton({ nav.navigate("search") }, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Icon(Icons.Outlined.Search, null); Spacer(Modifier.width(8.dp)); Text("搜索照片、人物或场景", Modifier.weight(1f)) }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(true, {}, { Text("最近入库") }); FilterChip(false, { nav.feed("favorites", "收藏") }, { Text("收藏") })
            }
        }
        if (source == "trash") Text("恢复后重新显示在图库中，收藏和精选标记不自动还原。保留期由 NAS 配置决定。", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        if (feed.offline) Text("离线缓存 · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(feed.updated))}", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        if (feed.error != null) ErrorNotice(feed.error, { vm.feeds[source] = feed.copy(error = null) }, { vm.loadFeed(source, true) })
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (selecting) "已选 ${selected.size} 项" else "已加载 ${feed.photos.size} 张", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            if (selecting) TextButton({ selected = feed.photos.map { it.id }.toLongArray() }) { Text("选择已加载项") }
            TextButton({ if (pickAlbum == null) selecting = !selecting; selected = longArrayOf() }) { Text(if (selecting) "取消" else "选择") }
        }
        if (feed.photos.isEmpty() && !feed.loading) EmptyState(if (feed.error == null) "这里还没有照片" else "暂时无法加载", if (root) "请先通过 Web 管理端向 NAS 导入照片" else "换个条件再看看", { vm.loadFeed(source, true) })
        LazyVerticalGrid(GridCells.Fixed(vm.columns), state = gridState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            items(cells, key = { it.key }, span = { if (it.photo == null) GridItemSpan(maxLineSpan) else GridItemSpan(1) }) { cell ->
                if (cell.photo == null) {
                    Text(cell.label.orEmpty(), Modifier.padding(horizontal = 12.dp, vertical = 14.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    val photo = cell.photo
                Box(Modifier.aspectRatio(1f).semantics { contentDescription = photo.name }.clip(RoundedCornerShape(5.dp)).combinedClickable(onClick = {
                    if (selecting) selected = if (photo.id in selected) selected.filter { it != photo.id }.toLongArray() else selected + photo.id
                    else if (source != "trash") nav.viewer(source, photo.id)
                    else { selecting = true; selected = longArrayOf(photo.id) }
                }, onLongClick = { selecting = true; selected = longArrayOf(photo.id) })) {
                    PhotoImage(vm, photo.thumbnailUrl, Modifier.fillMaxSize())
                    if (photo.favorite || photo.isVideo) Icon(if (photo.isVideo) Icons.Outlined.PlayCircle else Icons.Outlined.Favorite, if (photo.isVideo) "视频" else "已收藏", Modifier.align(Alignment.BottomStart).padding(6.dp).size(20.dp), tint = Color.White)
                    if (selecting) Icon(if (photo.id in selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, if (photo.id in selected) "已选择" else "未选择", Modifier.align(Alignment.TopEnd).padding(6.dp).background(if (photo.id in selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = .3f), CircleShape).size(24.dp), tint = Color.White)
                }
                }
            }
            if (feed.loading) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            else if (feed.more && feed.photos.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { TextButton({ vm.loadFeed(source) }, Modifier.fillMaxWidth()) { Text("加载更多") } }
        }
        if (selecting && selected.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val enabled = !vm.busy && !feed.offline
            if (pickAlbum != null) Button({ vm.batch(selected.toSet(), "add", pickAlbum) { nav.popBackStack(); vm.loadFeed(if (vm.albums.find { it.id == pickAlbum }?.type == "BABY") "baby:$pickAlbum" else "album:$pickAlbum", true) } }, enabled = enabled) { Text("添加到相册（${selected.size}）") }
            else if (source == "trash") Button({ vm.batch(selected.toSet(), "restore") { selected = longArrayOf() } }, enabled = enabled) { Text("恢复已选照片") }
            else {
                Button({ vm.batch(selected.toSet(), "favorite") { selected = longArrayOf(); selecting = false } }, enabled = enabled) { Text("收藏") }
                OutlinedButton({ vm.loadCatalog(); albumPicker = true }, enabled = enabled) { Text("加入相册") }
                if (source.startsWith("album:") || source.startsWith("baby:")) OutlinedButton({ vm.batch(selected.toSet(), "remove", source.substringAfter(':').toLong()) { selected = longArrayOf() } }, enabled = enabled) { Text("移出相册") }
                if (vm.supports("family-timeline")) OutlinedButton({ vm.batch(selected.toSet(), if (source.startsWith("timeline:")) "timelineOff" else "timelineOn") { selected = longArrayOf(); selecting = false } }, enabled = enabled) { Text(if (source.startsWith("timeline:")) "移出精选" else "加入精选") }
                TextButton({ confirmTrash = true }, enabled = enabled) { Text("移入回收站") }
            }
        }
    }
    if (albumPicker) AlbumPicker(vm, { albumPicker = false }) { album -> vm.batch(selected.toSet(), "add", album.id) { selected = longArrayOf(); selecting = false; albumPicker = false } }
    if (confirmTrash) ConfirmDialog("移入回收站？", "这会影响 NAS 共享图库和其他客户端，不会删除手机文件。", { confirmTrash = false }) { confirmTrash = false; vm.batch(selected.toSet(), "trash") { selected = longArrayOf(); selecting = false } }
}
@Composable fun ConfirmDialog(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) }, confirmButton = { TextButton(confirm) { Text("确认") } }, dismissButton = { TextButton(dismiss) { Text("取消") } })
}
@Composable fun AlbumPicker(vm: AppViewModel, dismiss: () -> Unit, selected: (Album) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("加入相册") }, text = {
        LazyColumn { if (vm.albums.none { it.type == "VIRTUAL" || (it.type == "BABY" && vm.supports("baby-timeline-page")) }) item { Text("暂无可编辑相册，请先在相册页创建") }; items(vm.albums.filter { it.type == "VIRTUAL" || (it.type == "BABY" && vm.supports("baby-timeline-page")) }) { album -> TextButton({ selected(album) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text(album.name) } } }
    }, confirmButton = { TextButton(dismiss) { Text("关闭") } })
}
