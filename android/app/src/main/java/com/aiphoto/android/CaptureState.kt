package com.aiphoto.android

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.UUID

internal data class CaptureJob(
    val id: Long,
    val name: String,
    val status: String,
    val phase: String,
    val siteId: Long? = null,
    val imagesDownloaded: Int? = null,
    val imagesTotal: Long? = null,
    val imagesProcessed: Long? = null,
    val errorMessage: String? = null,
    val createdAt: String? = null,
) {
    val active get() = status in setOf("RUNNING", "QUEUED")
    val title get() = when (status) {
        "RUNNING" -> "运行中"
        "QUEUED" -> "排队中"
        "PAUSED" -> "已暂停"
        "COMPLETED" -> "已完成"
        "PARTIAL" -> "部分完成"
        "FAILED" -> "失败"
        "CANCELLED" -> "已取消"
        else -> status
    }
}

internal data class CaptureAsset(val id: Long, val jobId: Long, val originalFilename: String?, val createdAt: String?) {
    val title get() = originalFilename ?: "采集照片 $id"
}
internal data class CapturePage<T>(val content: List<T>, val last: Boolean, val totalElements: Long)
internal data class CaptureDisk(val blocked: Boolean = false)
private data class CaptureImport(val batchId: Long, val status: String)
private data class CaptureImportBatch(val status: String)
private data class CaptureImportItem(val assetId: Long, val status: String, val photoId: Long?)
private data class CaptureResult(val success: Int)
internal enum class CaptureAction(val title: String) {
    IMPORT("入库"), SKIP("忽略"), DELETE("删除"), DELETE_SKIP("删除并忽略")
}

internal fun dayLabel(value: String?) = runCatching {
    LocalDate.parse(value?.take(10)).let { "${it.monthValue} 月 ${it.dayOfMonth} 日" }
}.getOrDefault("日期未知")

internal class CaptureState(private val api: ApiClient, private val admin: Boolean) {
    var jobs by mutableStateOf(emptyList<CaptureJob>())
        private set
    private var reviewAssets by mutableStateOf(emptyList<CaptureAsset>())
    private val previewActions = mutableStateMapOf<Long, Pair<CaptureAction, Job>>()
    private var importingSelection by mutableStateOf(false)
    val importing by derivedStateOf { importingSelection || previewActions.values.any { it.first == CaptureAction.IMPORT } }
    val assets by derivedStateOf { reviewAssets.filterNot { it.id in previewActions } }
    var count by mutableLongStateOf(0)
        private set
    var jobFilter by mutableStateOf<CaptureJob?>(null)
        private set
    var disk by mutableStateOf<CaptureDisk?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var jobsLoading by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var more by mutableStateOf(true)
        private set
    var selected by mutableStateOf(emptyMap<Long, CaptureAsset>())
        private set
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
        private set
    private var page = 0
    private var reviewGeneration = 0
    var previewRevision by mutableIntStateOf(0)
        private set
    private val removalMutex = Mutex()
    private val importKeys = mutableMapOf<List<Long>, String>()

    suspend fun refreshJobs() {
        if (jobsLoading) return
        jobsLoading = true
        try {
            jobs = api.get<List<CaptureJob>>("crawl/jobs").sortedWith(
                compareByDescending<CaptureJob> { it.active }.thenByDescending { it.id },
            )
            count = reviewPage(0, null, 1).totalElements
            if (admin) {
                try {
                    disk = api.get("settings/crawler/disk-guard")
                } catch (failure: ApiFailure) {
                    if (failure.status !in setOf(403, 404)) throw failure
                }
            }
        } catch (failure: Exception) {
            record(failure)
        } finally {
            jobsLoading = false
        }
    }

    private suspend fun reviewPage(page: Int, job: Long?, size: Int = 60): CapturePage<CaptureAsset> =
        api.get("crawl/review/assets", buildMap {
            put("page", page.toString())
            put("size", size.toString())
            put("status", "DOWNLOADED")
            job?.let { put("jobId", it.toString()) }
        })

    suspend fun setFilter(job: CaptureJob?) {
        jobFilter = job
        selected = emptyMap()
        loadReview(reset = true)
    }

    suspend fun loadReview(reset: Boolean = false) {
        if (!reset && (loading || !more || busy)) return
        val generation = if (reset) ++reviewGeneration else reviewGeneration
        if (reset) {
            page = 0
            reviewAssets = emptyList()
            more = true
        }
        loading = true
        error = null
        try {
            val result = reviewPage(page, jobFilter?.id)
            if (generation != reviewGeneration) return
            reviewAssets = (reviewAssets + result.content).distinctBy { it.id }
            if (jobFilter == null) count = result.totalElements
            page++
            more = !result.last
        } catch (failure: Exception) {
            if (generation == reviewGeneration || failure is CancellationException) record(failure)
        } finally {
            if (generation == reviewGeneration) loading = false
        }
    }

    fun toggle(asset: CaptureAsset) {
        if (busy) return
        selected = if (asset.id in selected) selected - asset.id else selected + (asset.id to asset)
    }

    fun clearSelection() { selected = emptyMap() }

    suspend fun selectAll() {
        if (busy) return
        busy = true
        error = null
        try {
            val snapshot = linkedMapOf<Long, CaptureAsset>()
            var next = 0
            do {
                val result = reviewPage(next++, jobFilter?.id, 200)
                result.content.forEach { snapshot[it.id] = it }
            } while (!result.last)
            selected = snapshot.filterKeys { it !in previewActions }
        } catch (failure: Exception) {
            record(failure)
        } finally {
            busy = false
        }
    }

    suspend fun control(job: CaptureJob, verb: String) {
        if (busy) return
        busy = true
        error = null
        try {
            api.write("crawl/jobs/${job.id}/$verb")
            refreshJobs()
        } catch (failure: Exception) {
            record(failure)
        } finally {
            busy = false
        }
    }

    suspend fun perform(action: CaptureAction, targets: Collection<CaptureAsset>, onRemoved: (Set<Long>) -> Unit = {}): Set<Long> {
        if (busy || targets.isEmpty() || targets.any { it.id in previewActions }) return emptySet()
        busy = true
        importingSelection = action == CaptureAction.IMPORT
        error = null
        notice = null
        reviewGeneration++
        loading = false
        val completed = mutableSetOf<Long>()
        try {
            jobs@ for ((job, entries) in targets.groupBy { it.jobId }) {
                for (chunk in entries.chunked(100)) {
                    val ids = chunk.map { it.id }.sorted()
                    try {
                        if (action == CaptureAction.IMPORT) {
                            val imported = withTimeoutOrNull(12_000L) { import(job, ids) }
                            if (imported == null) {
                                error = "暂未确认入库结果。可再次上滑查询或继续提交同一批次。"
                                break@jobs
                            }
                            completed += imported
                        } else {
                            completed += remove(action, job, ids)
                        }
                    } catch (failure: Exception) {
                        record(failure)
                    }
                }
            }
            reviewAssets = reviewAssets.filterNot { it.id in completed }
            onRemoved(completed)
            selected = selected - completed
            if ((action != CaptureAction.DELETE_SKIP || completed.size != targets.size) &&
                (error == null || completed.isNotEmpty())) notice = "已${action.title} ${completed.size} 张" +
                if (completed.size != targets.size) "，${targets.size - completed.size} 张待处理" else ""
            if (completed.isNotEmpty()) {
                // Rebase offset pagination after rows are removed to avoid skipping a page.
                val generation = reviewGeneration
                val desired = assets.size
                val saved = reviewAssets
                try {
                    val reloaded = mutableListOf<CaptureAsset>()
                    var next = 0
                    do {
                        val result = reviewPage(next++, jobFilter?.id)
                        if (generation != reviewGeneration) break
                        reloaded += result.content
                        more = !result.last
                        if (jobFilter == null) count = result.totalElements
                    } while (more && reloaded.size < desired)
                    if (generation == reviewGeneration) {
                        reviewAssets = reloaded.distinctBy { it.id }
                        page = next
                    }
                } catch (failure: Exception) {
                    if (generation == reviewGeneration) {
                        reviewAssets = saved
                        more = false
                    }
                    record(failure)
                }
            }
        } finally {
            importingSelection = false
            busy = false
        }
        return completed
    }

    private suspend fun remove(action: CaptureAction, job: Long, ids: List<Long>): List<Long> = removalMutex.withLock {
        val suffix = when (action) {
            CaptureAction.SKIP -> "skip"
            CaptureAction.DELETE -> "delete"
            else -> "delete-and-skip"
        }
        val body = if (action == CaptureAction.DELETE) ids else mapOf("ids" to ids)
        val result = api.gson.fromJson(
            api.write("crawl/jobs/$job/assets/$suffix", body = body), CaptureResult::class.java,
        )
        if (result.success == ids.size) return@withLock ids
        else {
            val remaining = mutableSetOf<Long>()
            var next = 0
            do {
                val actual = reviewPage(next++, job, 200)
                remaining += actual.content.map { it.id }
            } while (!actual.last)
            ids.filterNot { it in remaining }
        }
    }

    fun queuePreviewAction(action: CaptureAction, asset: CaptureAsset, scope: CoroutineScope): Boolean {
        if (busy || asset.id in previewActions) return false
        error = null
        selected = selected - asset.id
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            var succeeded = false
            try {
                val completed = if (action == CaptureAction.IMPORT) {
                    withTimeoutOrNull(180_000L) { import(asset.jobId, listOf(asset.id)) }
                } else {
                    remove(action, asset.jobId, listOf(asset.id))
                }
                when {
                    completed == null -> error = "暂未确认 ${asset.title} 的入库结果，可再次上滑查询。"
                    asset.id !in completed -> error = "${asset.title}${action.title}失败，已留在待整理。"
                    else -> {
                        succeeded = true
                        reviewGeneration++
                        loading = false
                        reviewAssets = reviewAssets.filterNot { it.id == asset.id }
                        page = 0
                        previewRevision++
                        if (action != CaptureAction.DELETE_SKIP) notice = "${asset.title}已${action.title}"
                    }
                }
            } catch (failure: Exception) {
                record(failure)
            } finally {
                if (!succeeded && (jobFilter == null || jobFilter?.id == asset.jobId) &&
                    reviewAssets.none { it.id == asset.id }) {
                    reviewAssets = reviewAssets + asset
                }
                previewActions.remove(asset.id)
            }
        }
        previewActions[asset.id] = action to job
        job.start()
        return true
    }

    fun cancelPreviewActions() {
        previewActions.values.toList().forEach { it.second.cancel() }
    }

    private suspend fun import(job: Long, ids: List<Long>): Set<Long> {
        val key = importKeys.getOrPut(ids) { UUID.randomUUID().toString() }
        val request = mapOf("ids" to ids, "idempotencyKey" to key)
        val submitted = api.gson.fromJson(api.write("crawl/jobs/$job/import", body = request), CaptureImport::class.java)
        var status = submitted.status
        val endpoint = "crawl/imports/${submitted.batchId}"
        while (status == "QUEUED" || status == "RUNNING") {
            delay(1500)
            status = api.get<CaptureImportBatch>(endpoint).status
        }
        val completed = mutableSetOf<Long>()
        var next = 0
        do {
            val result = api.get<CapturePage<CaptureImportItem>>("$endpoint/items", mapOf("page" to "${next++}", "size" to "100"))
            completed += result.content.filter { it.status == "SUCCESS" || it.status == "SKIPPED" && it.photoId != null }.map { it.assetId }
        } while (!result.last)
        importKeys.remove(ids)
        return completed
    }

    fun media(id: Long, original: Boolean = false): String =
        api.address.api("crawl/assets/$id/${if (original) "content" else "thumbnail"}").toString()

    private fun record(failure: Exception) {
        if (failure is CancellationException) throw failure
        error = failure.message ?: "加载采集中心失败"
    }
}
