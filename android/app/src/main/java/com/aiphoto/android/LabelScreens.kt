@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.aiphoto.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.CancellationException

@Composable fun LabelsScreen(vm: AppViewModel, nav: NavHostController, category: Boolean) {
    var create by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadCatalog() }
    val labels = if (category) vm.categories else vm.tags
    Column {
        BackBar(if (category) "分类管理" else "标签管理", nav) {
            IconButton(vm::loadCatalog) { Icon(Icons.Outlined.Refresh, "刷新词条") }
            IconButton({ create = true }, enabled = !vm.busy && !vm.catalogOffline) { Icon(Icons.Outlined.Add, "新建词条") }
        }
        OutlinedTextField(query, { query = it }, label = { Text("查找名称") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Text(if (category) "系统分类由 AI 维护；自建分类可手动整理。" else "人工标签可维护；AI 词条只读，可逐张确认或移除。", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        if (vm.catalogOffline) Text("离线缓存 · 仅浏览", Modifier.padding(horizontal = 16.dp))
        if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(contentPadding = PaddingValues(bottom = bottomContentInset())) {
            items(labels.filter { it.name.contains(query, ignoreCase = true) }, key = { it.id }) { label ->
                ListItem(headlineContent = { Text(label.name) }, supportingContent = { Text("${label.photoCount} 张 · ${labelOrigin(label, category)}") },
                    leadingContent = { Icon(if (category) Icons.Outlined.Category else Icons.AutoMirrored.Outlined.Label, null) },
                    trailingContent = { Icon(Icons.Outlined.ChevronRight, null) }, modifier = Modifier.clickable { nav.navigate("label/${if (category) "category" else "tag"}/${label.id}") })
            }
            if (labels.isEmpty() && !vm.busy) item { EmptyState("暂无词条", "点击右上角创建") }
        }
    }
    if (create) LabelEditor(vm, category, null, { create = false })
}
fun labelOrigin(label: Label, category: Boolean) = if (category) { if (label.isSystem) "系统分类" else "自建分类" } else if (label.type == "MANUAL") "人工标签" else "AI 标签"

@Composable fun LabelScreen(vm: AppViewModel, nav: NavHostController, category: Boolean, id: Long) {
    val label = (if (category) vm.categories else vm.tags).find { it.id == id }
    var edit by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    val source = "${if (category) "category" else "tag"}:$id"
    PhotoFeedScreen(vm, nav, source, label?.name ?: "照片", header = {
        BackBar(label?.name ?: "词条已不存在", nav) {
            if (label?.editable(category) == true) {
                IconButton({ edit = true }, enabled = !vm.busy && !vm.catalogOffline) { Icon(Icons.Outlined.Edit, "编辑词条") }
                if (vm.session?.user?.role == "ADMIN") IconButton({ remove = true }, enabled = !vm.busy && !vm.catalogOffline) { Icon(Icons.Outlined.Delete, "删除词条") }
            }
        }
        if (label != null) Text(labelOrigin(label, category) + (label.description?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
    })
    if (edit && label != null) LabelEditor(vm, category, label, { edit = false })
    if (remove && label != null) ConfirmDialog("删除${if (category) "分类" else "标签"}？", "将删除“${label.name}”及共享图库中的相关关联，原照片仍保留。当前不支持一键撤销；AI 之后仍可能识别并生成同名词条。", { remove = false }) {
        remove = false
        vm.mutate({ client -> client.write("${if (category) "categories" else "tags"}/$id", "DELETE", null); vm.invalidateFeed(source) }, "词条已删除") { vm.loadCatalog(); nav.popBackStack() }
    }
}

@Composable private fun LabelEditor(vm: AppViewModel, category: Boolean, label: Label?, dismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(label?.name.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(label?.color ?: "#28634E") }
    var description by rememberSaveable { mutableStateOf(label?.description.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (label == null) "新建${if (category) "分类" else "标签"}" else "编辑词条") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(name, { if (it.length <= if (category) 100 else 50) name = it }, label = { Text("名称") }, singleLine = true)
            OutlinedTextField(color, { if (it.length <= 7) color = it }, label = { Text("颜色 #RRGGBB") }, singleLine = true)
            if (!category) OutlinedTextField(description, { if (it.length <= 500) description = it }, label = { Text("说明（选填）") }, maxLines = 3)
            Text("修改对家庭共享图库生效。", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton({
        if (name.isBlank() || !color.matches(Regex("#[0-9a-fA-F]{6}"))) error = "请填写名称及 #RRGGBB 颜色"
        else vm.mutate({ client -> client.write((if (category) "categories" else "tags") + (label?.let { "/${it.id}" } ?: ""), if (label == null) "POST" else "PUT", buildMap {
            put("name", name.trim()); put("color", color); if (!category) put("description", description)
        }) }) { vm.loadCatalog(); dismiss() }
    }, enabled = !vm.busy && name.isNotBlank()) { Text("保存词条") } }, dismissButton = { TextButton(dismiss) { Text("取消") } })
}

@Composable fun AssociationPicker(vm: AppViewModel, ids: Set<Long>, dismiss: () -> Unit, done: () -> Unit) {
    var category by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Label?>(null) }
    val options = if (category) vm.categories.filter { !it.isSystem } else vm.tags
    AlertDialog(onDismissRequest = dismiss, title = { Text("整理 ${ids.size} 张照片") }, text = {
        Column {
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!category, { category = false }, { Text("标签") }); FilterChip(category, { category = true }, { Text("分类") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!remove, { remove = false }, { Text("添加") }); FilterChip(remove, { remove = true }, { Text("移除") })
            }
            Text(if (category) "仅整理自建分类，训练重算可能重新分配自动结果。" else "添加将标为人工确认；移除会阻止 AI 重新添加，直至你手动添加。", style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                items(options, key = { it.id }) { label -> TextButton({ chosen = label }, Modifier.fillMaxWidth(), enabled = !vm.busy) { Text("${label.name} · ${labelOrigin(label, category)}") } }
                if (options.isEmpty()) item { Text("请先创建可用词条") }
            }
        }
    }, confirmButton = { TextButton(dismiss) { Text("关闭") } })
    chosen?.let { label -> ConfirmDialog("确认${if (remove) "移除" else "添加"}？", "为 ${ids.size} 张共享照片${if (remove) "移除" else "添加"}“${label.name}”。不会删除照片原件。", { chosen = null }) {
        chosen = null
        vm.batch(ids, (if (category) "category" else "tag") + (if (remove) "Remove" else "Add"), label.id, done)
    } }
}

@Composable fun PhotoLabels(vm: AppViewModel, photoId: Long, dismiss: () -> Unit) {
    var tags by remember { mutableStateOf<List<com.google.gson.JsonElement>>(emptyList()) }
    var failure by remember { mutableStateOf<String?>(null) }
    var organizing by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(photoId, revision) {
        try { tags = vm.detail(photoId).tags.orEmpty() } catch (e: Exception) { if (e is CancellationException) throw e; failure = e.message ?: "详情加载失败" }
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text("照片标签") }, text = {
        LazyColumn {
            failure?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (tags.isEmpty()) item { Text("暂无标签") }
            items(tags) { value ->
                if (value.isJsonObject) {
                    val obj = value.asJsonObject
                    val name = obj.get("name")?.asString.orEmpty()
                    val source = obj.get("source")?.takeUnless { it.isJsonNull }?.asString
                    ListItem(headlineContent = { Text(name) }, supportingContent = { Text(if (source == "MANUAL") "人工确认" else "AI 识别") })
                }
            }
        }
    }, confirmButton = { TextButton({ organizing = true }, enabled = !vm.busy) { Text("整理标签与分类") } }, dismissButton = { TextButton(dismiss) { Text("关闭") } })
    if (organizing) AssociationPicker(vm, setOf(photoId), { organizing = false }) { organizing = false; revision++ }
}
