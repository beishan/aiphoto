@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.aiphoto.android

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

internal val floatingNavClearance = 78.dp

@Composable
internal fun FloatingSegments(
    options: List<Pair<String, String>>,
    active: String,
    select: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp).widthIn(max = 352.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
        shadowElevation = 7.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp)) {
            options.forEach { (id, label) ->
                val chosen = active == id
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(CircleShape)
                        .background(if (chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .selectable(chosen, role = Role.Tab) { select(id) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
                        color = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun FloatingBack(onBack: () -> Unit, dark: Boolean = false) {
    FilledIconButton(onBack, colors = IconButtonDefaults.filledIconButtonColors(
        containerColor = if (dark) Color(0xFF252939) else MaterialTheme.colorScheme.surface,
        contentColor = if (dark) Color.White else MaterialTheme.colorScheme.onSurface,
    )) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
}

@Composable
internal fun ScrollingTitle(
    title: String,
    date: String?,
    modifier: Modifier = Modifier,
    showDateBackground: Boolean = true,
) {
    AnimatedContent(
        targetState = date,
        modifier = modifier,
        transitionSpec = {
            val direction = if (targetState == null) -1 else 1
            (slideInVertically { it * direction } + fadeIn())
                .togetherWith(slideOutVertically { -it * direction } + fadeOut())
        },
        label = "图库日期",
    ) { day ->
        if (day == null) Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        else if (!showDateBackground) Text(day, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        else Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .86f)) {
            Text(day, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.titleSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun CaptureScreen(vm: AppViewModel, nav: NavHostController) {
    val state = vm.capture ?: return
    var refreshAttempt by remember { mutableIntStateOf(0) }
    var checking by remember { mutableStateOf(true) }
    var refreshError by remember { mutableStateOf(false) }
    LaunchedEffect(refreshAttempt) {
        checking = true
        refreshError = false
        try {
            vm.refreshCapabilities()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            refreshError = true
        } finally {
            checking = false
        }
    }
    if (!vm.supports("mobile-capture")) {
        Column(Modifier.fillMaxSize()) {
            BackBar("采集", nav)
            when {
                checking -> EmptyState("正在检查服务端能力", "请稍候。")
                refreshError -> {
                    EmptyState("无法检查服务端能力", "请检查网络连接后重试。")
                    TextButton(onClick = { refreshAttempt++ }) { Text("重试") }
                }
                else -> {
                    EmptyState("服务端暂不支持安卓采集中心", "当前服务端版本：${vm.serverInfo?.version ?: "未知"}。请升级 NAS 后端至 0.26.0 或更新版本，并确认连接的是已升级的服务。")
                    TextButton(onClick = { refreshAttempt++ }) { Text("重新检查") }
                }
            }
        }
        return
    }
    val topInset = if (vm.error == null) WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() else 0.dp
    val bottomInset = bottomContentInset()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("overview") }
    var jobId by rememberSaveable { mutableStateOf<Long?>(null) }
    var reviewOrigin by rememberSaveable { mutableStateOf<Long?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var previewId by remember { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf<List<CaptureAsset>?>(null) }
    val grid = rememberLazyGridState()
    val options = listOf("overview" to "概览", "jobs" to "任务", "review" to "待整理")
    val preview = state.assets.firstOrNull { it.id == previewId }
    val job = state.jobs.firstOrNull { it.id == jobId }
    val showSegments = jobId == null && previewId == null && !selecting

    fun back() {
        if (state.busy) return
        when {
            previewId != null -> previewId = null
            selecting -> { selecting = false; state.clearSelection() }
            jobId != null -> jobId = null
            tab == "review" && reviewOrigin != null -> {
                jobId = reviewOrigin
                reviewOrigin = null
                tab = "jobs"
            }
            tab != "overview" -> tab = "overview"
            else -> nav.popBackStack()
        }
    }

    fun openReview(filter: CaptureJob?) {
        if (state.busy) return
        reviewOrigin = filter?.id
        jobId = null
        tab = "review"
        scope.launch {
            if (state.assets.isNotEmpty()) grid.scrollToItem(0)
            state.setFilter(filter)
        }
    }

    fun action(operation: CaptureAction, targets: List<CaptureAsset>, confirmed: Boolean = false): Job? {
        if (state.busy || targets.isEmpty()) return null
        if (operation == CaptureAction.DELETE && !confirmed) {
            deleting = targets
            return null
        }
        return scope.launch {
            val old = state.assets
            val originalPreview = previewId
            val completed = if (operation == CaptureAction.IMPORT && originalPreview != null && targets.singleOrNull()?.id == originalPreview) {
                if (vm.queueCapturePreviewImport(targets.single())) setOf(originalPreview) else emptySet()
            } else {
                state.perform(operation, targets)
            }
            if (originalPreview != null && originalPreview == previewId && originalPreview in completed) {
                val currentIndex = old.indexOfFirst { it.id == originalPreview }
                val remaining = state.assets.mapTo(hashSetOf()) { it.id }
                previewId = old.drop(currentIndex + 1).firstOrNull { it.id in remaining }?.id
                    ?: old.take(currentIndex.coerceAtLeast(0)).lastOrNull { it.id in remaining }?.id
                    ?: state.assets.firstOrNull()?.id
            }
            if (selecting && state.selected.isEmpty() && completed.isNotEmpty()) selecting = false
        }
    }

    BackHandler { back() }
    LaunchedEffect(tab) {
        if (tab == "review") {
            if (state.assets.isEmpty() && state.more && !state.loading) state.loadReview()
        } else {
            while (true) {
                state.refreshJobs()
                delay(8_000)
            }
        }
    }
    LaunchedEffect(grid, tab, state) {
        if (tab == "review") snapshotFlow {
            grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index to grid.layoutInfo.totalItemsCount
        }.collect { (last, total) ->
            if (last != null && last >= total - 9 && state.error == null) state.loadReview()
        }
    }

    Box(Modifier.fillMaxSize().background(if (preview != null) Color(0xFF101218) else MaterialTheme.colorScheme.background)) {
        when {
            preview != null -> CapturePreview(vm, state, preview, ::back, { previewId = it }) { operation, asset ->
                action(operation, listOf(asset))
            }

            tab == "review" -> CaptureReview(vm, state, grid, selecting, ::back, {
                selecting = true
                state.clearSelection()
            }, { asset ->
                if (selecting) state.toggle(asset) else {
                    previewId = asset.id
                    if (state.assets.indexOf(asset) >= state.assets.size - 6) scope.launch { state.loadReview() }
                }
            }, { asset ->
                selecting = true
                state.toggle(asset)
            }, { scope.launch { state.selectAll() } }, { operation ->
                action(operation, state.selected.values.toList())
            }, { openReview(null) }, { scope.launch { state.loadReview(reset = true) } })

            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset + 68.dp,
                    bottom = bottomInset + if (showSegments) floatingNavClearance else 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    job != null -> {
                        item { CaptureJobCard(job) }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (job.status == "RUNNING" || job.status == "QUEUED")
                                    OutlinedButton({ scope.launch { state.control(job, "pause") } }, enabled = !state.busy) { Text("暂停") }
                                if (job.status in setOf("PAUSED", "FAILED", "PARTIAL", "CANCELLED"))
                                    OutlinedButton({ scope.launch { state.control(job, "resume") } }, enabled = !state.busy) {
                                        Text(if (job.status == "PAUSED") "继续" else "重试")
                                    }
                            }
                        }
                        if (job.phase == "REVIEW") item {
                            Button({ openReview(job) }, enabled = !state.busy) { Text("查看待整理照片") }
                        }
                    }
                    tab == "jobs" -> {
                        if (state.jobs.isEmpty() && !state.jobsLoading) item { EmptyState("暂无采集任务") }
                        items(state.jobs, key = { it.id }) { entry -> CaptureJobCard(entry) { jobId = entry.id } }
                    }
                    else -> {
                        item {
                            Card(onClick = { openReview(null) }, colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            )) {
                                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("待整理")
                                    Text("${state.count} 张照片", style = MaterialTheme.typography.headlineMedium)
                                    Text("去整理 ›")
                                }
                            }
                        }
                        if (state.disk?.blocked == true) item { Text("NAS 剩余空间不足，采集已暂停", color = MaterialTheme.colorScheme.error) }
                        item { Text("正在运行", style = MaterialTheme.typography.titleMedium) }
                        val tasks = state.jobs.filter { it.active }
                        if (tasks.isEmpty() && !state.jobsLoading) item { Text("当前没有运行中的任务") }
                        items(tasks, key = { it.id }) { entry -> CaptureJobCard(entry) { jobId = entry.id } }
                        state.jobs.firstOrNull { it.status == "COMPLETED" }?.let { recent ->
                            item { Text("最近完成", style = MaterialTheme.typography.titleMedium) }
                            item { CaptureJobCard(recent) { jobId = recent.id } }
                        }
                    }
                }
                if (state.jobsLoading && state.jobs.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
        }
        if (preview == null && tab != "review") Row(
            Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(start = 12.dp, top = 6.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FloatingBack(::back)
            when {
                jobId != null -> Text("任务详情", style = MaterialTheme.typography.titleLarge)
                tab == "jobs" -> Text("采集任务", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (showSegments) FloatingSegments(options, tab, { next ->
            if (next == "review" && tab != "review" && state.jobFilter != null) openReview(null)
            else tab = next
        }, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        state.error?.let { text ->
            Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (preview != null || showSegments || selecting) 76.dp else 8.dp),
                shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text, Modifier.padding(10.dp).widthIn(max = 240.dp), style = MaterialTheme.typography.bodySmall)
                    TextButton({
                        state.error = null
                        if (preview == null) scope.launch {
                            if (tab == "review") state.loadReview(reset = true) else state.refreshJobs()
                        }
                    }) { Text(if (preview == null) "刷新" else "关闭") }
                }
            }
        }
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(state.notice) { state.notice?.let { snack.showSnackbar(it, duration = SnackbarDuration.Short) } }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp))
        if (tab == "review" && state.importing) {
            CaptureImportProgress(Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
    deleting?.let { targets ->
        ConfirmDialog("删除 ${targets.size} 张照片？", "照片会移出待整理区，采集副本按 NAS 清理策略处理。",
            { deleting = null }) { deleting = null; action(CaptureAction.DELETE, targets, confirmed = true) }
    }
}

@Composable
private fun CaptureJobCard(job: CaptureJob, open: (() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth().then(if (open == null) Modifier else Modifier.clickable(onClick = open))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(job.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(job.title, color = MaterialTheme.colorScheme.primary)
            }
            val processed = job.imagesProcessed ?: job.imagesDownloaded?.toLong() ?: 0
            val total = job.imagesTotal ?: 0
            if (total > 0) {
                LinearProgressIndicator({ (processed.toFloat() / total).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                Text("已处理 $processed / $total 张", style = MaterialTheme.typography.bodySmall)
            } else Text("已下载 ${job.imagesDownloaded ?: 0} 张", style = MaterialTheme.typography.bodySmall)
            job.siteId?.let { Text("来源网站 $it", style = MaterialTheme.typography.bodySmall) }
            job.errorMessage?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun CaptureReview(
    vm: AppViewModel, state: CaptureState, grid: LazyGridState, selecting: Boolean,
    back: () -> Unit, select: () -> Unit, open: (CaptureAsset) -> Unit, longSelect: (CaptureAsset) -> Unit,
    all: () -> Unit, action: (CaptureAction) -> Unit, clearFilter: () -> Unit, refresh: () -> Unit,
) {
    val topInset = if (vm.error == null) WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() else 0.dp
    val bottomInset = bottomContentInset()
    val scrollThreshold = with(LocalDensity.current) { 32.dp.roundToPx() }
    val scrolled by remember(scrollThreshold) { derivedStateOf {
        grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > scrollThreshold
    } }
    val date by remember { derivedStateOf {
        if (!scrolled || state.assets.isEmpty()) null else {
            val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            dayLabel(state.assets.getOrNull(if (!grid.canScrollForward && last >= state.assets.size - 1)
                state.assets.lastIndex else grid.firstVisibleItemIndex)?.createdAt)
        }
    } }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(
                GridCells.Fixed(3), state = grid, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = topInset + 64.dp,
                    bottom = if (selecting) 8.dp else floatingNavClearance + bottomInset),
                verticalArrangement = Arrangement.spacedBy(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(state.assets, key = { it.id }) { image ->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                        .semantics { contentDescription = image.title }
                        .combinedClickable(enabled = !state.busy, onClick = { open(image) }, onLongClick = { longSelect(image) })) {
                        PhotoImage(vm, state.media(image.id), Modifier.fillMaxSize())
                        if (selecting) Icon(
                            if (image.id in state.selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            if (image.id in state.selected) "已选" else "未选",
                            Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).background(Color.Black.copy(alpha = .4f), CircleShape),
                            tint = Color.White,
                        )
                    }
                }
                if (state.loading) item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                if (state.assets.isEmpty() && !state.loading) item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("待整理区没有照片", retry = refresh)
                }
            }
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FloatingBack(back)
                ScrollingTitle(state.jobFilter?.name ?: "待整理", date, Modifier.weight(1f))
                if (state.jobFilter != null) IconButton(clearFilter, enabled = !state.busy) { Icon(Icons.Outlined.Close, "清除任务筛选") }
                if (selecting) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("已选 ${state.selected.size} 张", Modifier.padding(horizontal = 6.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                    FilledTonalIconButton(all, enabled = !state.busy && state.assets.isNotEmpty()) { Icon(Icons.Outlined.SelectAll, "全选") }
                } else FilledTonalButton(select, enabled = !state.busy && state.assets.isNotEmpty(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (scrolled) .62f else 1f)),
                    contentPadding = PaddingValues(horizontal = 12.dp)) { Text("选择") }
            }
            if (state.busy) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top = topInset + 58.dp).fillMaxWidth())
        }
        if (selecting) Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                CaptureAction.entries.forEach { operation ->
                    TextButton({ action(operation) }, Modifier.weight(1f).heightIn(min = 56.dp),
                        enabled = !state.busy && state.selected.isNotEmpty(), contentPadding = PaddingValues(horizontal = 2.dp)) {
                        Text(operation.title, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CapturePreview(
    vm: AppViewModel, state: CaptureState, photo: CaptureAsset, back: () -> Unit,
    select: (Long) -> Unit, action: (CaptureAction, CaptureAsset) -> Job?,
) {
    val assets = state.assets
    val index = assets.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = index) { state.assets.size }
    val latestSelect by rememberUpdatedState(select)
    var menu by remember { mutableStateOf(false) }
    var animating by remember { mutableStateOf(false) }
    val ready = !state.busy && !animating && !pager.isScrollInProgress
    BackHandler(enabled = animating) { }

    LaunchedEffect(photo.id) {
        if (state.assets.getOrNull(pager.settledPage)?.id != photo.id) pager.scrollToPage(index)
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage to pager.isScrollInProgress }.collect { (page, scrolling) ->
            if (!scrolling && !animating && !state.busy) {
                state.assets.getOrNull(page)?.let { latestSelect(it.id) }
            }
        }
    }
    val nearEnd = index >= assets.lastIndex - 5
    LaunchedEffect(nearEnd, assets.size, state.more) {
        if (nearEnd && state.more) state.loadReview()
    }

    DarkMediaSystemBars()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FloatingBack({ if (!animating) back() }, dark = true)
            Text("${pager.currentPage + 1} / ${assets.size}", Modifier.weight(1f).padding(start = 10.dp), color = Color.White)
            Box {
                IconButton({ menu = true }, enabled = ready) { Icon(Icons.Outlined.MoreHoriz, "更多操作", tint = Color.White) }
                DropdownMenu(menu, { menu = false }) {
                    CaptureAction.entries.forEach { operation ->
                        DropdownMenuItem({ Text(operation.title) }, { menu = false; action(operation, photo) }, enabled = ready)
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().testTag("capture-preview-image")) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                key = { assets[it].id },
                pageSpacing = 12.dp,
                beyondViewportPageCount = 1,
                userScrollEnabled = !state.busy && !animating,
            ) { page ->
                val asset = assets[page]
                CapturePreviewPage(
                    vm, state, asset, assets.getOrNull(page + 1) ?: assets.getOrNull(page - 1),
                    active = asset.id == photo.id, ready = ready,
                    setAnimating = { animating = it }, action = action,
                )
            }
            if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(12.dp).size(24.dp))
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton({ action(CaptureAction.IMPORT, photo) }, enabled = ready) { Text("↑ 入库", color = Color.White) }
            TextButton({ action(CaptureAction.DELETE_SKIP, photo) }, enabled = ready) { Text("↓ 删除并忽略", color = Color.White) }
        }
    }
}

@Composable
private fun CapturePreviewPage(
    vm: AppViewModel,
    state: CaptureState,
    asset: CaptureAsset,
    next: CaptureAsset?,
    active: Boolean,
    ready: Boolean,
    setAnimating: (Boolean) -> Unit,
    action: (CaptureAction, CaptureAsset) -> Job?,
) {
    val scope = rememberCoroutineScope()
    val canGesture by rememberUpdatedState(active && ready)
    val latestAction by rememberUpdatedState(action)
    val latestSetAnimating by rememberUpdatedState(setAnimating)
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var travelFraction by remember { mutableFloatStateOf(0f) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(active) {
        if (!active) {
            zoom = 1f
            pan = Offset.Zero
        }
    }
    Box(Modifier.fillMaxSize().clipToBounds().pointerInput(asset.id) {
        val edge = 20.dp.toPx()
        val threshold = 64.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!canGesture) return@awaitEachGesture
            val inside = down.position.x in edge..(size.width - edge)
            var travel = Offset.Zero
            var vertical = false
            var horizontal = false
            var transformed = zoom > 1f
            var event = awaitPointerEvent(PointerEventPass.Initial)
            while (event.changes.any { it.pressed }) {
                val pinching = event.changes.count { it.pressed } > 1
                val movement = event.changes.firstOrNull { it.id == down.id }?.positionChange() ?: Offset.Zero
                if (pinching || zoom > 1f) {
                    transformed = true
                    drag = Offset.Zero
                    travelFraction = 0f
                    if (pinching) zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                    val shift = pan + if (pinching) event.calculatePan() else movement
                    val maxX = size.width * (zoom - 1f) / 2f
                    val maxY = size.height * (zoom - 1f) / 2f
                    pan = Offset(shift.x.coerceIn(-maxX, maxX), shift.y.coerceIn(-maxY, maxY))
                    event.changes.forEach { it.consume() }
                } else if (!transformed && !horizontal && inside) {
                    travel += movement
                    if (!vertical && travel.getDistance() > viewConfiguration.touchSlop) {
                        vertical = abs(travel.y) > abs(travel.x)
                        horizontal = !vertical
                    }
                    if (vertical) {
                        drag = Offset(travel.x * .38f, travel.y)
                        travelFraction = drag.y / size.height.coerceAtLeast(1)
                        event.changes.forEach { it.consume() }
                    }
                }
                event = awaitPointerEvent(PointerEventPass.Initial)
            }
            if (!vertical || transformed) return@awaitEachGesture

            val from = drag
            val fromFraction = travelFraction
            val operation = if (from.y < 0) CaptureAction.IMPORT else CaptureAction.DELETE_SKIP
            val fling = abs(from.y) >= threshold
            val direction = if (from.y < 0f) -1f else 1f
            val end = if (fling) Offset(from.x + direction * size.width * .16f, direction * size.height * 1.2f)
                else Offset.Zero
            val endFraction = if (fling) direction * 1.2f else 0f
            latestSetAnimating(true)
            scope.launch {
                try {
                    animate(0f, 1f, animationSpec = tween(if (fling) 220 else 160)) { fraction, _ ->
                        drag = from + (end - from) * fraction
                        travelFraction = fromFraction + (endFraction - fromFraction) * fraction
                    }
                    if (fling) latestAction(operation, asset)?.join()
                } finally {
                    drag = Offset.Zero
                    travelFraction = 0f
                    latestSetAnimating(false)
                }
            }
        }
    }) {
        if (next != null && travelFraction != 0f) {
            PhotoImage(vm, state.media(next.id), Modifier.fillMaxSize().graphicsLayer {
                val reveal = (abs(travelFraction) * 2.5f).coerceIn(0f, 1f)
                alpha = reveal
                scaleX = .94f + .06f * reveal
                scaleY = scaleX
            }, ContentScale.FillWidth, background = Color(0xFF101218))
        }
        Box(Modifier.fillMaxSize().graphicsLayer {
            scaleX = zoom
            scaleY = zoom
            translationX = pan.x + drag.x
            translationY = pan.y + drag.y
            rotationZ = (travelFraction * 18f).coerceIn(-16f, 16f)
            alpha = (1f - (abs(travelFraction) - .50f).coerceAtLeast(0f) * 1.8f).coerceAtLeast(0f)
        }) {
            PhotoImage(vm, state.media(asset.id), Modifier.fillMaxSize(), ContentScale.FillWidth, background = Color(0xFF101218))
            if (active && !failed) {
                val context = LocalContext.current
                val request = remember(context, asset.id, retry) {
                    ImageRequest.Builder(context).data(vm.mediaUrl(state.media(asset.id, original = true))).build()
                }
                vm.imageLoader?.let { loader ->
                    AsyncImage(model = request, imageLoader = loader, contentDescription = null,
                        contentScale = ContentScale.FillWidth, onError = { failed = true }, modifier = Modifier.fillMaxSize())
                }
            }
        }
        if (active && failed && ready) {
            TextButton({ retry++; failed = false }, Modifier.align(Alignment.BottomCenter)) { Text("重试原图") }
        }
    }
}

@Composable
private fun CaptureImportProgress(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "入库流光")
    val position by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "流光位置",
    )
    Canvas(modifier.fillMaxWidth().height(2.dp).semantics {
        contentDescription = "正在入库"
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }) {
        drawRect(Color(0xFF666BE8).copy(alpha = .28f))
        val width = size.width * .58f
        val start = (size.width + width) * position - width
        drawRect(Brush.horizontalGradient(
            listOf(Color.Transparent, Color(0xFF8878FF), Color(0xFF64DFFF), Color(0xFFF591E8), Color.Transparent),
            startX = start, endX = start + width,
        ))
    }
}
