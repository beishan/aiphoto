@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.aiphoto.android

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable fun BirthDateField(value: String, changed: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(value, changed, label = { Text("出生日期 YYYY-MM-DD") }, singleLine = true,
        trailingIcon = { IconButton({ open = true }) { Icon(Icons.Outlined.CalendarMonth, "选择出生日期") } },
        modifier = Modifier.fillMaxWidth())
    if (open) {
        val initial = calendarBirthDate(value, LocalDate.now())
        val picker = rememberDatePickerState(yearRange = 1..LocalDate.now().year, initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(LocalDate.now())
            })
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton({ picker.selectedDateMillis?.let { changed(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; open = false }, enabled = picker.selectedDateMillis != null) { Text("确定") }
        }, dismissButton = { TextButton({ open = false }) { Text("取消") } }) { DatePicker(picker) }
    }
}

@Composable fun TimelineScreen(vm: AppViewModel, nav: NavHostController) {
    LaunchedEffect(Unit) { vm.loadMonths() }
    var selected by rememberSaveable { mutableStateOf("") }
    var yearsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(vm.months) {
        if (vm.months.none { it.source == selected }) selected = vm.months.firstOrNull()?.source.orEmpty()
    }
    val current = vm.months.find { it.source == selected }
    val years = vm.months.map { it.year }.distinct()
    Column(Modifier.fillMaxSize()) {
        BackBar("精选时间线", nav) {
            IconButton({ vm.loadMonths(); current?.let { vm.loadFeed(it.source, true) } }) { Icon(Icons.Outlined.Refresh, "刷新精选时间线") }
        }
        Text("只展示你选入精选的照片，按 NAS 拍摄日期归档。", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        if (!vm.supports("family-timeline")) { EmptyState("请先升级 NAS 服务端", "精选时间线需要 0.23.0 或更新版本"); return@Column }
        if (vm.monthsLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
        vm.monthsError?.let { ErrorNotice(it, {}, vm::loadMonths) }
        if (vm.monthsOffline) Text("离线缓存 · 精选只读", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary)
        if (vm.months.isEmpty() && !vm.monthsLoading) EmptyState("还没有精选照片", "在看图或多选工具栏中选择“加入精选”")
        if (current != null) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton({ yearsOpen = true }) { Text(if (current.year == 0) "日期未知" else "${current.year} 年"); Icon(Icons.Outlined.ExpandMore, null) }
                    DropdownMenu(yearsOpen, { yearsOpen = false }) { years.forEach { year ->
                        DropdownMenuItem(text = { Text(if (year == 0) "拍摄日期未知" else "$year 年") }, onClick = { selected = vm.months.first { it.year == year }.source; yearsOpen = false })
                    } }
                }
                Text("${current.count} 张精选照片", style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.months.filter { it.year == current.year }.forEach { month -> FilterChip(month.source == selected, { selected = month.source }, { Text(if (month.year == 0) "拍摄日期未知" else "${month.month}月") }) }
            }
            key(selected) { Box(Modifier.weight(1f)) { PhotoFeedScreen(vm, nav, selected, current.title, header = {}) } }
        }
    }
}

@Composable fun PersonScreen(vm: AppViewModel, nav: NavHostController, id: Long) {
    val person = vm.people.find { it.id == id }
    val admin = vm.session?.user?.role == "ADMIN"
    var rename by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(person?.name.orEmpty()) }
    var facesMode by remember { mutableStateOf<String?>(null) }
    var mergePicker by remember { mutableStateOf(false) }
    var mergeTarget by remember { mutableStateOf<Person?>(null) }
    val enabled = person != null && !vm.busy && !vm.catalogOffline && vm.supports("people-edit")
    PhotoFeedScreen(vm, nav, "person:$id", person?.name ?: "人物", header = {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FloatingBack({ nav.popBackStack() })
            Text(person?.name ?: "未命名人物", style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text("共 ${person?.photoCount ?: 0} 张", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            IconButton({ vm.loadCatalog(); vm.loadFeed("person:$id", true) }) { Icon(Icons.Outlined.Refresh, "刷新人物") }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ name = person?.name.orEmpty(); rename = true }, enabled = enabled) { Text("改名") }
            OutlinedButton({ facesMode = "cover" }, enabled = enabled) { Text("选择封面") }
            if (admin) {
                OutlinedButton({ mergePicker = true }, enabled = enabled) { Text("合并人物") }
                OutlinedButton({ facesMode = "assign" }, enabled = enabled) { Text("修正归属") }
            }
        }
    })
    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text("人物改名") }, text = {
        Column { OutlinedTextField(name, { if (it.length <= 100) name = it }, label = { Text("人物名称") }, singleLine = true); Text("留空恢复为未命名，修改会影响家庭共享图库。", style = MaterialTheme.typography.bodySmall) }
    }, confirmButton = { TextButton({ vm.mutate({ it.write("people/$id", "PUT", mapOf("name" to name.trim())) }) { rename = false; vm.loadCatalog() } }, enabled = !vm.busy) { Text("保存名称") } }, dismissButton = { TextButton({ rename = false }) { Text("取消") } })
    if (mergePicker) PersonPicker(vm.people.filter { it.id != id }, "将此人物合并到", { mergePicker = false }) { mergeTarget = it; mergePicker = false }
    if (mergeTarget != null) ConfirmDialog("确认合并人物？", "将“${person?.name ?: "未命名"}”的全部人脸归入“${mergeTarget!!.name ?: "未命名"}”，源人物会被删除。当前不支持一键撤销，请先核对照片。", { mergeTarget = null }) {
        val target = mergeTarget!!; mergeTarget = null
        vm.mutate({ it.write("people/merge", body = mapOf("targetId" to target.id, "sourceId" to id)); vm.invalidateFeed("person:$id"); vm.invalidateFeed("person:${target.id}") }, "人物已合并") { vm.loadCatalog(); vm.feeds.remove("person:$id"); vm.feeds.remove("person:${target.id}"); nav.popBackStack() }
    }
    facesMode?.let { mode -> FaceManager(vm, id, mode, { facesMode = null }) }
}

@Composable private fun PersonPicker(people: List<Person>, title: String, dismiss: () -> Unit, selected: (Person) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { LazyColumn {
        if (people.isEmpty()) item { Text("没有其他人物可供选择") }
        items(people, key = { it.id }) { person -> TextButton({ selected(person) }, Modifier.fillMaxWidth()) { Text("${person.name ?: "未命名"} · ${person.photoCount} 张") } }
    } }, confirmButton = { TextButton(dismiss) { Text("关闭") } })
}

@Composable private fun FaceManager(vm: AppViewModel, personId: Long, mode: String, dismiss: () -> Unit) {
    var faces by remember { mutableStateOf<List<Face>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf<Face?>(null) }
    var target by remember { mutableStateOf<Person?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun load() {
        if (loading || !more) return
        scope.launch {
            loading = true; failure = null
            try { val result = vm.faces(personId, page); faces = (faces + result.content).distinctBy { it.id }; more = !result.last; page++ }
            catch (e: Exception) { if (e is CancellationException) throw e; failure = e.message ?: "人脸加载失败" }
            finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { load() }
    ModalBottomSheet(dismiss) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(if (mode == "cover") "选择人物封面" else "选择需要修正的人脸", style = MaterialTheme.typography.titleLarge)
            Text("预览成功后才可选择，请逐张核对人脸。", style = MaterialTheme.typography.bodySmall)
            failure?.let { ErrorNotice(it, { failure = null }, { load() }) }
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(faces.chunked(3)) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { face ->
                        var ready by remember(face.id) { mutableStateOf(false) }
                        Column(Modifier.weight(1f).padding(vertical = 8.dp).clickable(enabled = ready && !vm.busy) {
                        chosen = face
                        if (mode == "cover") confirm = true
                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                        PhotoImage(vm, face.previewUrl, Modifier.fillMaxWidth().aspectRatio(1f).clip(CircleShape), onLoad = { ready = it })
                        Text("人脸 #${face.id}", style = MaterialTheme.typography.labelSmall)
                    } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                } }
                if (loading) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                else if (more) item { TextButton({ load() }) { Text("加载更多人脸") } }
                else if (faces.isEmpty()) item { EmptyState("暂无有效人脸") }
            }
            TextButton(dismiss, Modifier.fillMaxWidth()) { Text("关闭") }
        }
    }
    if (chosen != null && mode == "assign" && !confirm) PersonPicker(vm.people.filter { it.id != personId }, "修正到哪个人物", { chosen = null }) { target = it; confirm = true }
    if (confirm && chosen != null) ConfirmDialog(if (mode == "cover") "设为人物封面？" else "修正人脸归属？", if (mode == "cover") "确认人脸 #${chosen!!.id} 是该人物。这会更新共享图库中的人物封面。" else "将人脸 #${chosen!!.id} 归入“${target?.name ?: "未命名"}”。照片原件不会移动；若该脸原为封面，将清除原人物封面。", { confirm = false; chosen = null; target = null }) {
        val face = chosen!!; val targetId = target?.id
        confirm = false; chosen = null
        vm.mutate({ client ->
            if (mode == "cover") client.write("people/$personId/cover-face", "PUT", mapOf("faceId" to face.id))
            else {
                client.write("people/faces/${face.id}/assign", body = mapOf("personId" to requireNotNull(targetId)))
                vm.invalidateFeed("person:$personId"); vm.invalidateFeed("person:$targetId")
            }
        }, "人物已更新") {
            vm.loadCatalog(); vm.loadFeed("person:$personId", true)
            targetId?.let { vm.feeds.remove("person:$it") }
            dismiss()
        }
    }
}
