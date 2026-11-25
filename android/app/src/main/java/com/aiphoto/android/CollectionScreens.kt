@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.aiphoto.android

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable fun AlbumsScreen(vm: AppViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { vm.loadCatalog() }
    Column {
        TopAppBar(title = { Text("相册", fontWeight = FontWeight.Bold) }, actions = {
            IconButton({ vm.loadCatalog() }) { Icon(Icons.Outlined.Refresh, "刷新相册") }
            IconButton({ nav.navigate("album-edit/0") }) { Icon(Icons.Outlined.Add, "创建相册") }
        })
        if (vm.catalogOffline) Text("离线缓存 · 相册只读", Modifier.padding(16.dp))
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (vm.albums.isEmpty() && !vm.busy) EmptyState("还没有相册", "点击右上角创建普通相册")
        LazyVerticalGrid(GridCells.Adaptive(160.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = floatingNavClearance + bottomContentInset()), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            items(vm.albums, key = { it.id }) { album -> Column(Modifier.clickable { nav.navigate("album/${album.id}") }) {
                PhotoImage(vm, album.coverPhotoUrl, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)))
                Text(album.name, Modifier.padding(top = 8.dp), fontWeight = FontWeight.SemiBold)
                Text("${album.photoCount} 张 · ${when (album.type) { "BABY" -> "宝宝相册"; "DIRECTORY" -> "目录"; "TRAINING" -> "训练集"; else -> "普通相册" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
    }
}
@Composable fun AlbumScreen(vm: AppViewModel, nav: NavHostController, id: Long) {
    val album = vm.albums.find { it.id == id }
    val editable = album?.type == "VIRTUAL" || (album?.type == "BABY" && vm.supports("baby-timeline-page"))
    val source = if (album?.type == "BABY" && vm.supports("baby-timeline-page")) "baby:$id" else "album:$id"
    var menu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    PhotoFeedScreen(vm, nav, source, album?.name ?: "相册", header = {
        BackBar(album?.name ?: "相册", nav) {
            IconButton({ vm.loadFeed(source, true) }) { Icon(Icons.Outlined.Refresh, "刷新") }
            if (editable) {
                IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "相册操作") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("编辑相册") }, onClick = { menu = false; nav.navigate("album-edit/$id") })
                    DropdownMenuItem(text = { Text("删除相册") }, onClick = { menu = false; deleting = true })
                }
            }
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!album?.description.isNullOrBlank()) Text(album!!.description!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (album?.type == "BABY") Text("出生于 ${album.birthDate ?: "未设置"} · 按 NAS 拍摄日期计算月龄", style = MaterialTheme.typography.bodySmall)
            if (editable) OutlinedButton({ nav.navigate("pick/$id") }, enabled = !vm.catalogOffline) { Icon(Icons.Outlined.Add, null); Text("从 NAS 图库添加照片") }
            else Text("此相册只读；宝宝时间线需要升级 NAS 服务端。", style = MaterialTheme.typography.bodySmall)
        }
    })
    if (deleting) ConfirmDialog("删除相册？", "仅删除相册与照片的关联，图库原件仍保留。", { deleting = false }) {
        deleting = false
        vm.mutate({ it.write("albums/$id", "DELETE", null) }, "相册已删除") { nav.popBackStack(); vm.loadCatalog() }
    }
}
@Composable fun AlbumEditor(vm: AppViewModel, nav: NavHostController, id: Long) {
    val album = vm.albums.find { it.id == id }
    var name by rememberSaveable { mutableStateOf(album?.name.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(album?.description.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(album?.type ?: "VIRTUAL") }
    var birth by rememberSaveable { mutableStateOf(album?.birthDate.orEmpty()) }
    var validation by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().imePadding()) {
        BackBar(if (id == 0L) "新建相册" else "编辑相册", nav)
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Icon(Icons.Outlined.PhotoAlbum, null, Modifier.size(100.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(24.dp), tint = MaterialTheme.colorScheme.primary)
            if (id == 0L && vm.supports("baby-timeline-page")) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(type == "VIRTUAL", { type = "VIRTUAL" }, { Text("普通相册") })
                FilterChip(type == "BABY", { type = "BABY" }, { Text("宝宝相册") })
            }
            if (type == "BABY") BirthDateField(birth, { birth = it })
            if (validation != null) Text(validation!!, color = MaterialTheme.colorScheme.error)
            OutlinedTextField(name, { if (it.length <= 100) name = it }, label = { Text("相册名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { if (it.length <= 1000) description = it }, label = { Text("相册描述（选填）") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Text("创建后可从 NAS 图库选择照片，不读取手机相册。", style = MaterialTheme.typography.bodySmall)
            Button({
                val date = runCatching { java.time.LocalDate.parse(birth) }.getOrNull()
                if (type == "BABY" && (date == null || date.year < 1 || date.isAfter(java.time.LocalDate.now()))) validation = "请选择有效的出生日期，不能晚于今天"
                else vm.mutate({ client ->
                    client.write(if (id == 0L) "albums" else "albums/$id", if (id == 0L) "POST" else "PUT", buildMap<String, Any> {
                        put("name", name.trim()); put("description", description); put("type", type); put("shared", album?.shared ?: false)
                        if (type == "BABY") put("birthDate", birth)
                    })
                    if (id != 0L && type == "BABY") vm.invalidateFeed("baby:$id")
                }, "相册已保存") {
                    if (id != 0L && type == "BABY") vm.loadFeed("baby:$id", true)
                    vm.loadCatalog(); nav.popBackStack()
                }
            }, enabled = name.isNotBlank() && !vm.busy && !vm.catalogOffline, modifier = Modifier.fillMaxWidth()) { Text("保存相册") }
        }
    }
}
@Composable fun DiscoverScreen(vm: AppViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) {
        vm.loadCatalog()
        vm.loadFeed("favorites", true)
        vm.loadFeed("likes", true)
        if (vm.supports("family-timeline")) vm.loadMonths()
    }
    val timelineCover = vm.months.firstOrNull()?.source?.let { vm.feeds[it]?.photos?.firstOrNull()?.thumbnailUrl }
    LaunchedEffect(vm.months.firstOrNull()?.source) {
        vm.months.firstOrNull()?.source?.let { if (vm.feeds[it] == null) vm.loadFeed(it) }
    }
    LazyColumn(Modifier.testTag("discover-list"), contentPadding = PaddingValues(bottom = floatingNavClearance + bottomContentInset()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { TopAppBar(title = { Text("发现", fontWeight = FontWeight.Bold) }, actions = {
            IconButton({ vm.loadCatalog(); vm.loadFeed("favorites", true); vm.loadFeed("likes", true) }) { Icon(Icons.Outlined.Refresh, "刷新") }
        }) }
        if (vm.catalogOffline) item { Text("离线缓存", Modifier.padding(horizontal = 16.dp)) }
        item {
            DiscoverHeading("人物", "查看全部人物") { nav.navigate("people") }
            if (vm.people.isEmpty()) Text("尚未识别到人物", Modifier.padding(horizontal = 16.dp))
            else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.people.take(8), key = { it.id }) { person ->
                    PersonCover(vm, person, Modifier.width(142.dp)) { nav.navigate("person/${person.id}") }
                }
            }
        }
        item { DiscoveryPhotoStrip(vm, nav, "收藏", "favorites") }
        item { DiscoveryPhotoStrip(vm, nav, "点赞", "likes") }
        val hasTimeline = vm.supports("family-timeline")
        val hasBaby = vm.supports("baby-timeline-page")
        if (hasTimeline || hasBaby) item {
            val baby = vm.albums.firstOrNull { it.type == "BABY" }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("家庭记忆", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (hasTimeline) DiscoveryCover(vm, timelineCover, "精选时间线", Modifier.weight(1f), 1.55f) { nav.navigate("timeline") }
                    if (hasBaby) DiscoveryCover(vm, baby?.coverPhotoUrl, "宝宝相册", Modifier.weight(1f), 1.55f) {
                        if (baby != null) nav.navigate("album/${baby.id}") else nav.navigate("albums")
                    }
                }
            }
        }
        if (vm.supports("label-edit")) item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ nav.navigate("labels/tag") }) { Text("标签管理") }
                OutlinedButton({ nav.navigate("labels/category") }) { Text("分类管理") }
            }
        }
        if (vm.categories.isNotEmpty()) item { Text("探索照片", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium) }
        items(vm.categories) { category ->
            ListItem(headlineContent = { Text(category.name) }, supportingContent = { Text("${category.photoCount} 张照片") },
                leadingContent = { Icon(Icons.Outlined.Category, null) },
                modifier = Modifier.clickable { nav.feed("category:${category.id}", category.name) })
        }
        if (vm.tags.isNotEmpty()) item {
            Text("标签", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.tags.forEach { tag -> AssistChip({ nav.feed("tag:${tag.id}", tag.name) }, { Text(tag.name) }) }
            }
        }
    }
}

@Composable
private fun DiscoverHeading(title: String, label: String, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(open) { Icon(Icons.Outlined.ChevronRight, label, tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun DiscoveryPhotoStrip(vm: AppViewModel, nav: NavHostController, title: String, source: String) {
    val feed = vm.feeds[source] ?: Feed()
    val open = { nav.feed(source, title) }
    DiscoverHeading(title, "查看全部$title", open)
    if (feed.photos.isEmpty()) {
        Text(if (feed.loading) "加载中…" else if (feed.error != null) "暂时无法加载" else "暂无${title}照片",
            Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(feed.photos.take(10), key = { it.id }) { photo ->
            PhotoImage(vm, photo.thumbnailUrl, Modifier.size(100.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "打开${title}列表", onClick = open))
        }
    }
}

@Composable private fun PersonCover(vm: AppViewModel, person: Person, modifier: Modifier = Modifier, open: () -> Unit) =
    DiscoveryCover(vm, person.coverPhotoUrl, person.name ?: "未命名", modifier, 1f, open)

@Composable
private fun DiscoveryCover(vm: AppViewModel, path: String?, title: String, modifier: Modifier, ratio: Float, open: () -> Unit) {
    Box(modifier.aspectRatio(ratio).clip(RoundedCornerShape(12.dp)).clickable(onClick = open)) {
        PhotoImage(vm, path, Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .58f)))))
        Text(title, Modifier.align(Alignment.BottomStart).padding(horizontal = 7.dp, vertical = 6.dp),
            color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun AllPeopleScreen(vm: AppViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { vm.loadCatalog() }
    Column(Modifier.fillMaxSize()) {
        BackBar("全部人物", nav)
        if (vm.people.isEmpty()) EmptyState("暂无人物", retry = { vm.loadCatalog() })
        else LazyVerticalGrid(GridCells.Fixed(4), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + bottomContentInset()),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.people, key = { it.id }) { person -> PersonCover(vm, person) { nav.navigate("person/${person.id}") } }
        }
    }
}

@Composable fun SearchScreen(vm: AppViewModel, nav: NavHostController) {
    var query by rememberSaveable { mutableStateOf(vm.filter.query) }
    var semantic by rememberSaveable { mutableStateOf(vm.filter.semantic) }
    var filters by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(vm.filter) }
    var validation by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (vm.people.isEmpty()) vm.loadCatalog() }
    Column(Modifier.fillMaxSize()) {
        BackBar("搜索", nav)
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, label = { Text("搜索照片、人物或场景") }, singleLine = true, modifier = Modifier.weight(1f))
            IconButton({ runCatching { vm.search(draft.copy(query = query.trim(), semantic = semantic)) }.onFailure { validation = it.message } }) { Icon(Icons.Outlined.Search, "提交搜索") }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!semantic, { semantic = false }, { Text("关键词") }); FilterChip(semantic, { semantic = true }, { Text("AI 语义") }); AssistChip({ filters = true }, { Text("筛选") }, leadingIcon = { Icon(Icons.Outlined.Tune, null) })
        }
        if (semantic) Text("由 NAS 本地 AI 搜索，识别结果取决于模型和索引状态。", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        if (validation != null) ErrorNotice(validation!!, { validation = null })
        if (vm.history.isNotEmpty()) FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { vm.history.forEach { previous -> SuggestionChip({ query = previous }, { Text(previous) }) }; TextButton({ vm.clearHistory() }) { Text("清空历史") } }
        if (vm.feeds["search"] != null) Box(Modifier.weight(1f)) { PhotoFeedScreen(vm, nav, "search", "搜索结果", header = {}) }
        else EmptyState("从一个词开始", "也可以按日期、人物和类型组合查找")
    }
    if (filters) ModalBottomSheet({ filters = false }) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("筛选照片", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge); TextButton({ draft = SearchFilter() }) { Text("重置") } }
            OutlinedTextField(draft.start, { draft = draft.copy(start = it) }, label = { Text("开始日期 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.end, { draft = draft.copy(end = it) }, label = { Text("结束日期 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("人物"); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(draft.person == null, { draft = draft.copy(person = null) }, { Text("全部") }); vm.people.forEach { p -> FilterChip(draft.person == p.id, { draft = draft.copy(person = p.id) }, { Text(p.name ?: "未命名") }) } }
            Text("标签"); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(draft.tag == null, { draft = draft.copy(tag = null) }, { Text("全部") }); vm.tags.forEach { t -> FilterChip(draft.tag == t.id, { draft = draft.copy(tag = t.id) }, { Text(t.name) }) } }
            Text("类型"); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("" to "全部", "PHOTO" to "照片", "VIDEO" to "视频", "GIF" to "动图", "RAW" to "RAW").forEach { (value, text) -> FilterChip(draft.type == value, { draft = draft.copy(type = value) }, { Text(text) }) } }
            Text("最低评分：${if (draft.rating == 0) "不限" else draft.rating.toString()}")
            Slider(draft.rating.toFloat(), { draft = draft.copy(rating = it.toInt()) }, valueRange = 0f..5f, steps = 4)
            Button({ runCatching { vm.search(draft.copy(query = query.trim(), semantic = semantic)); filters = false }.onFailure { validation = it.message; filters = false } }, Modifier.fillMaxWidth()) { Text("查看结果") }
            Spacer(Modifier.height(12.dp))
        }
    }
}
@Composable fun MenuRow(title: String, icon: ImageVector, subtitle: String? = null, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }, supportingContent = subtitle?.let { { Text(it) } }, trailingContent = { Icon(Icons.Outlined.ChevronRight, null) }, modifier = Modifier.clickable(onClick = onClick))
}
@Composable fun MineScreen(vm: AppViewModel, nav: NavHostController) {
    var change by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(bottom = bottomContentInset())) {
        item { BackBar("我的", nav) }
        item { ListItem(headlineContent = { Text(vm.session?.user?.nickname ?: vm.session?.user?.username.orEmpty(), style = MaterialTheme.typography.headlineSmall) }, supportingContent = { Text("${android.net.Uri.parse(vm.store.address).host} · 家庭共享图库") }, leadingContent = { Icon(Icons.Outlined.AccountCircle, null, Modifier.size(56.dp)) }) }
        item { HorizontalDivider(Modifier.padding(16.dp)) }
        item { MenuRow("收藏", Icons.Outlined.FavoriteBorder) { nav.feed("favorites", "收藏") } }
        item { MenuRow("回收站", Icons.Outlined.DeleteOutline) { nav.feed("trash", "回收站") } }
        item { MenuRow("下载记录", Icons.Outlined.Download) { nav.navigate("downloads") } }
        item { HorizontalDivider(Modifier.padding(16.dp)) }
        item { MenuRow("服务器连接", Icons.Outlined.Dns, vm.store.address) { change = true } }
        item { MenuRow("外观与缓存", Icons.Outlined.Palette) { nav.navigate("settings") } }
        item { MenuRow("关于 aiphoto", Icons.Outlined.Info) { nav.navigate("about") } }
    }
    if (change) ConfirmDialog("更换服务器？", "将退出当前账户并清除私有浏览缓存。已导出的文件会保留。", { change = false }) { vm.logout(); vm.changeConnection() }
}
@Composable fun SettingsScreen(vm: AppViewModel, nav: NavHostController) {
    var logout by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.measureCache() }
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        BackBar("外观与缓存", nav)
        Text("主题", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) -> FilterChip(vm.theme == value, { vm.chooseTheme(value) }, { Text(label) }) } }
        Text("图库列数", Modifier.padding(16.dp)); Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (3..5).forEach { n -> FilterChip(vm.columns == n, { vm.chooseColumns(n) }, { Text("$n 列") }) } }
        ListItem(headlineContent = { Text("移动网络加载原图") }, supportingContent = { Text("关闭时每次加载或下载原文件前确认") }, trailingContent = { Switch(vm.cellularOriginal, vm::setCellular) })
        ListItem(headlineContent = { Text("搜索历史") }, trailingContent = { Switch(vm.historyEnabled, vm::setHistory) })
        TextButton(vm::clearHistory, Modifier.padding(horizontal = 16.dp)) { Text("清空搜索历史") }
        HorizontalDivider(Modifier.padding(16.dp))
        Text("缓存上限", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(128, 256, 512).forEach { n -> FilterChip(vm.cacheLimit == n, { vm.chooseCacheLimit(n) }, { Text("$n MB") }) } }
        ListItem(headlineContent = { Text("当前图片缓存") }, trailingContent = { Text("${vm.cacheBytes / (1024 * 1024)} MB") })
        OutlinedButton(vm::clearCache, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("清理浏览缓存") }
        Text("清理缓存不会删除已导出的文件。缓存按服务器与账户隔离，离线时仅供只读浏览。", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        TextButton({ logout = true }, Modifier.fillMaxWidth().padding(16.dp)) { Icon(Icons.AutoMirrored.Outlined.Logout, null); Text("退出登录") }
    }
    if (logout) ConfirmDialog("退出登录？", "停止播放与下载，移除登录凭据和私有浏览缓存。", { logout = false }) { vm.logout() }
}
@Composable fun AboutScreen(vm: AppViewModel, nav: NavHostController) {
    var version by remember { mutableStateOf(vm.serverInfo?.version ?: "未获取") }
    var licenses by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val licenseText = remember { context.assets.open("THIRD_PARTY_NOTICES.md").bufferedReader().use { it.readText() } + "\n\n" + context.assets.open("APACHE-2.0.txt").bufferedReader().use { it.readText() } }
    LaunchedEffect(Unit) { runCatching { vm.api?.get<ServerInfo>("mobile/server-info")?.version }.getOrNull()?.let { version = it } }
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
        BackBar("关于 aiphoto", nav)
        Text("aiphoto", Modifier.padding(24.dp), style = MaterialTheme.typography.headlineLarge)
        ListItem(headlineContent = { Text("客户端版本") }, trailingContent = { Text(BuildConfig.VERSION_NAME) })
        ListItem(headlineContent = { Text("服务端版本") }, trailingContent = { Text(version) })
        Text("私有 NAS 照片客户端。AI 分析在 NAS 完成；手机保存必要的浏览缓存及你主动导出的文件。分享给其他应用后，文件由对应应用处理。", Modifier.padding(24.dp))
        TextButton({ licenses = !licenses }, Modifier.padding(horizontal = 16.dp)) { Text(if (licenses) "收起完整许可" else "查看完整开源许可") }
        if (licenses) Text(licenseText, Modifier.padding(24.dp), style = MaterialTheme.typography.bodySmall)
        Text("开源组件", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
        Text("Kotlin · Apache-2.0\nAndroidX / Compose / Room / Media3 · Apache-2.0\nOkHttp · Apache-2.0\nGson · Apache-2.0\nCoil · Apache-2.0\n\n版权归各项目贡献者所有。完整声明见随源码提供的 THIRD_PARTY_NOTICES.md。", Modifier.padding(24.dp))
    }
}
@Composable fun DownloadsScreen(vm: AppViewModel, nav: NavHostController) {
    var pending by remember { mutableStateOf<Photo?>(null) }
    var retry by remember { mutableStateOf<Transfer?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) pending?.let { vm.download(it, uri) }; pending = null
    }
    LazyColumn(contentPadding = PaddingValues(bottom = bottomContentInset())) {
        item { BackBar("下载记录", nav) }
        item { Text("仅记录你主动下载或分享的文件。重试时可重新选择保存位置。", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
        if (vm.transfers.isEmpty()) item { EmptyState("暂无下载记录") }
        items(vm.transfers, key = { it.id }) { transfer ->
            ListItem(headlineContent = { Text(transfer.name) }, supportingContent = { Column { Text((if (transfer.shareMode == "copy") "JPEG 副本 · " else if (transfer.shareMode == "original") "原文件分享 · " else "") + transfer.status); if (transfer.status == "下载中") LinearProgressIndicator(progress = { transfer.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) } }, leadingContent = { Icon(Icons.Outlined.Download, null) }, trailingContent = { if (transfer.status == "下载中") IconButton({ vm.cancelTransfer(transfer.id) }) { Icon(Icons.Outlined.Close, "取消下载") } else if (transfer.status != "已完成" && transfer.photo != null) TextButton({ retry = transfer }) { Text("重试") } })
        }
    }
    retry?.let { transfer -> ConfirmDialog("确认重试？", "将重新从 NAS 读取原文件，可能使用移动数据。" + when (transfer.shareMode) {
        "copy" -> "继续生成最长边 2048 的去元数据 JPEG 副本，不会回退到原件分享。"
        "original" -> "继续分享原文件，可能包含位置等元数据。"
        else -> "请选择原文件保存位置。"
    }, { retry = null }) {
        retry = null
        transfer.photo?.let { photo ->
            if (transfer.shareMode != null) vm.download(photo, share = true, privacyCopy = transfer.shareMode == "copy")
            else { pending = photo; export.launch(transfer.name) }
        }
    } }

}
