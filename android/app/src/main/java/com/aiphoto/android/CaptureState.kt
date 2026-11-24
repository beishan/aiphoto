package com.aiphoto.android

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
    var assets by mutableStateOf(emptyList<CaptureAsset>())
        private set
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
            assets = emptyList()
            more = true
        }
        loading = true
        error = null
        try {
            val result = reviewPage(page, jobFilter?.id)
            if (generation != reviewGeneration) return
            assets = (assets + result.content).distinctBy { it.id }
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
            selected = snapshot
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

    suspend fun perform(action: CaptureAction, targets: Collection<CaptureAsset>): Set<Long> {
        if (busy || targets.isEmpty()) return emptySet()
        busy = true
        error = null
        notice = null
        reviewGeneration++
        loading = false
        val completed = mutableSetOf<Long>()
        try {
            for ((job, entries) in targets.groupBy { it.jobId }) {
                for (chunk in entries.chunked(100)) {
                    val ids = chunk.map { it.id }.sorted()
                    try {
                        if (action == CaptureAction.IMPORT) {
                            completed += import(job, ids)
                        } else {
                            val suffix = when (action) {
                                CaptureAction.SKIP -> "skip"
                                CaptureAction.DELETE -> "delete"
                                else -> "delete-and-skip"
                            }
                            val body = if (action == CaptureAction.DELETE) ids else mapOf("ids" to ids)
                            val result = api.gson.fromJson(
                                api.write("crawl/jobs/$job/assets/$suffix", body = body), CaptureResult::class.java,
                            )
                            if (result.success == ids.size) completed += ids
                            else {
                                val remaining = mutableSetOf<Long>()
                                var next = 0
                                do {
                                    val actual = reviewPage(next++, job, 200)
                                    remaining += actual.content.map { it.id }
                                } while (!actual.last)
                                completed += ids.filterNot { it in remaining }
                            }
                        }
                    } catch (failure: Exception) {
                        record(failure)
                    }
                }
            }
            assets = assets.filterNot { it.id in completed }
            selected = selected - completed
            notice = "已${action.title} ${completed.size} 张" +
                if (completed.size != targets.size) "，${targets.size - completed.size} 张待处理" else ""
            if (completed.isNotEmpty()) {
                // Rebase offset pagination after rows are removed to avoid skipping a page.
                val desired = assets.size
                val saved = assets
                try {
                    val reloaded = mutableListOf<CaptureAsset>()
                    var next = 0
                    do {
                        val result = reviewPage(next++, jobFilter?.id)
                        reloaded += result.content
                        more = !result.last
                        if (jobFilter == null) count = result.totalElements
                    } while (more && reloaded.size < desired)
                    assets = reloaded.distinctBy { it.id }
                    page = next
                } catch (failure: Exception) {
                    assets = saved
                    more = false
                    record(failure)
                }
            }
        } finally {
            busy = false
        }
        return completed
    }

    private suspend fun import(job: Long, ids: List<Long>): Set<Long> {
        val key = importKeys.getOrPut(ids) { UUID.randomUUID().toString() }
        val request = mapOf("ids" to ids, "idempotencyKey" to key)
        api.write("crawl/jobs/$job/import-preview", body = request)
        val submitted = api.gson.fromJson(api.write("crawl/jobs/$job/import", body = request), CaptureImport::class.java)
        var status = submitted.status
        val endpoint = "crawl/imports/${submitted.batchId}"
        var checks = 0
        while (status in setOf("QUEUED", "RUNNING") && checks++ < 90) {
            delay(1500)
            status = api.get<CaptureImportBatch>(endpoint).status
        }
        if (status in setOf("QUEUED", "RUNNING")) error("入库仍在后台进行，重试可继续查询该批次")
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
