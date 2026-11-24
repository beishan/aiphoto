package com.aiphoto.android

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.aiphoto.android.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import okio.Path.Companion.toOkioPath

class AppViewModel(application: Application) : AndroidViewModel(application) {
    val store = SessionStore(application)
    private val db = Room.databaseBuilder(application, CacheDatabase::class.java, "browse-cache.db").build()
    private val gson = Gson()
    var session by mutableStateOf(store.load()); private set
    var serverInfo by mutableStateOf<ServerInfo?>(store.info?.takeIf { it.instanceId == store.instanceId }); private set
    var connected by mutableStateOf(session != null); private set
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var api: ApiClient? = null; private set
    var imageLoader by mutableStateOf<ImageLoader?>(null); private set
    var albums by mutableStateOf<List<Album>>(emptyList()); private set
    var people by mutableStateOf<List<Person>>(emptyList()); private set
    var tags by mutableStateOf<List<Label>>(emptyList()); private set
    var categories by mutableStateOf<List<Label>>(emptyList()); private set
    var catalogOffline by mutableStateOf(false); private set
    var filter by mutableStateOf(SearchFilter()); private set
    val feeds = mutableStateMapOf<String, Feed>()
    var transfers by mutableStateOf<List<Transfer>>(emptyList()); private set
    var failedBatchIds by mutableStateOf<Set<Long>>(emptySet()); private set
    val shareReady = MutableSharedFlow<SharedAsset>(extraBufferCapacity = 1)
    var theme by mutableStateOf("system"); private set
    var columns by mutableIntStateOf(3); private set
    var cellularOriginal by mutableStateOf(false); private set
    var historyEnabled by mutableStateOf(true); private set
    var history by mutableStateOf<List<String>>(emptyList()); private set
    var cacheLimit by mutableIntStateOf(512); private set
    var cacheBytes by mutableLongStateOf(0); private set
    var months by mutableStateOf<List<MonthSummary>>(emptyList()); private set
    var monthsLoading by mutableStateOf(false); private set
    var monthsOffline by mutableStateOf(false); private set
    var monthsError by mutableStateOf<String?>(null); private set
    private var monthsJob: Job? = null
    private var epoch = 0
    private var feedJobs = mutableMapOf<String, Job>()
    private var transferJobs = mutableMapOf<String, Job>()
    private var catalogJob: Job? = null
    private var mutationJob: Job? = null
    private var authJob: Job? = null
    internal var capture: CaptureState? = null
        private set
    private var cacheScope: String = ""
    private val preferences get() = getApplication<Application>().getSharedPreferences("preferences_$cacheScope", Context.MODE_PRIVATE)
    init { session?.let { install(it); refreshSession() } }

    private fun install(login: Login) {
        cacheScope = MessageDigest.getInstance("SHA-256").digest((store.address + ":" + store.instanceId + ":" + login.user.id).toByteArray()).joinToString("") { "%02x".format(it) }
        val generation = epoch
        api = ApiClient(ServerAddress(store.address, store.allowHttp), login.token) {
            viewModelScope.launch { if (epoch == generation && session != null) logout("登录已失效，请重新登录") }
        }
        capture = CaptureState(checkNotNull(api), login.user.role == "ADMIN")
        theme = preferences.getString("theme", "system")!!
        columns = preferences.getInt("columns", 3).coerceIn(3, 5)
        cellularOriginal = preferences.getBoolean("cellular", false)
        historyEnabled = preferences.getBoolean("historyEnabled", true)
        history = preferences.getString("history", "")!!.split('\n').filter { it.isNotBlank() }
        cacheLimit = preferences.getInt("cacheLimit", 512)
        createImageLoader()
        val shareScope = cacheScope
        viewModelScope.launch(Dispatchers.IO) {
            File(getApplication<Application>().cacheDir, "shares/$shareScope").listFiles()?.forEach {
                if (System.currentTimeMillis() - it.lastModified() > 24 * 60 * 60 * 1000L) it.deleteRecursively()
            }
        }
        viewModelScope.launch {
            val previous = cached<List<Transfer>>("transfers") ?: emptyList()
            if (epoch == generation) transfers = previous.map { if (it.status == "下载中") it.copy(status = "已中断，请重新下载") else it }
        }
    }
    private fun createImageLoader() {
        imageLoader?.shutdown()
        imageLoader?.diskCache?.shutdown()
        imageLoader = ImageLoader.Builder(getApplication<Application>())
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { api!!.http })); add(AnimatedImageDecoder.Factory()) }
            .diskCache { DiskCache.Builder().directory(File(getApplication<Application>().cacheDir, "images/$cacheScope").toOkioPath()).maxSizeBytes(cacheLimit * 1024L * 1024L).build() }
            .build()
    }
    private fun refreshSession() {
        val generation = epoch
        viewModelScope.launch {
            try {
                val info = api!!.get<ServerInfo>("mobile/server-info")
                if (generation != epoch) return@launch
                if (info.instanceId != store.instanceId) { logout("服务器实例已变更，请重新连接并登录"); connected = false; return@launch }
                serverInfo = info; store.info = info
                val user = api!!.get<User>("users/me")
                if (generation == epoch) { session = session?.copy(user = user); session?.let(store::save) }
            } catch (e: Exception) { if (e !is CancellationException && generation == epoch && e !is ApiFailure) notice = "网络不可达，可查看已缓存内容" }
        }
    }
    fun connect(address: String, http: Boolean) {
        if (busy) return
        authJob = viewModelScope.launch {
            busy = true; error = null
            try {
                val server = ServerAddress(address, http)
                val info = ApiClient(server, null).get<ServerInfo>("mobile/server-info")
                require(info.product == "aiphoto" && !info.instanceId.isNullOrBlank() && info.apiVersion == 1 && info.capabilities.containsAll(listOf("album-pagination", "authenticated-media"))) { "服务端不兼容，请先升级到支持安卓客户端的版本" }
                store.address = server.base.toString(); store.allowHttp = http; store.instanceId = info.instanceId
                serverInfo = info; store.info = info; connected = true
            } catch (e: Exception) { if (e !is CancellationException) error = readable(e) }
            finally { busy = false }
        }
    }
    fun changeConnection() { authJob?.cancel(); busy = false; connected = false; error = null }
    fun login(username: String, password: String) {
        if (busy) return
        authJob = viewModelScope.launch {
            busy = true; error = null
            try {
                val guest = ApiClient(ServerAddress(store.address, store.allowHttp), null)
                val result = gson.fromJson(guest.write("auth/login", body = mapOf("username" to username, "password" to password)), Login::class.java)
                require(result.token.isNotBlank() && result.user.id > 0) { "服务器登录响应无效" }
                epoch++; store.save(result); session = result; install(result)
            } catch (e: Exception) { if (e !is CancellationException) error = readable(e) }
            finally { busy = false }
        }
    }
    fun logout(message: String? = null) {
        epoch++; monthsJob?.cancel(); months = emptyList(); monthsLoading = false; monthsOffline = false; monthsError = null; authJob?.cancel(); feedJobs.values.forEach { it.cancel() }; feedJobs.clear()
        catalogJob?.cancel(); mutationJob?.cancel(); transferJobs.values.forEach { it.cancel() }; transferJobs.clear()
        api?.http?.dispatcher?.cancelAll(); api?.http?.connectionPool?.evictAll()
        imageLoader?.shutdown(); imageLoader = null
        val oldScope = cacheScope
        session = null; api = null; capture = null; store.clear(); feeds.clear(); albums = emptyList(); people = emptyList(); tags = emptyList(); categories = emptyList(); transfers = emptyList(); busy = false; error = message
        viewModelScope.launch(Dispatchers.IO) {
            // Exported SAF files are user-owned. Only private browser/temporary files are removed.
            db.entries().clearPrefix("$oldScope:%")
            File(getApplication<Application>().cacheDir, "images/$oldScope").deleteRecursively()
            File(getApplication<Application>().cacheDir, "shares/$oldScope").deleteRecursively()
        }
    }
    private fun readable(e: Exception): String = when (e) {
        is ApiFailure -> e.message.orEmpty()
        is javax.net.ssl.SSLException -> "证书校验失败，请使用可信 HTTPS 证书"
        is java.net.UnknownHostException -> "无法解析服务器地址"
        is java.net.SocketTimeoutException -> "连接超时，请检查网络和 NAS 状态"
        is java.io.IOException -> "网络连接失败，请检查服务器与网络"
        else -> e.message ?: "操作失败，请重试"
    }
    private suspend inline fun <reified T> cached(key: String): T? {
        val fullKey = "$cacheScope:$key"
        return withContext(Dispatchers.IO) { db.entries().get(fullKey)?.let { runCatching { gson.fromJson<T>(it.json, object : TypeToken<T>() {}.type) }.getOrNull() } }
    }
    private suspend fun cache(key: String, value: Any, scope: String = cacheScope) {
        withContext(Dispatchers.IO) { db.entries().put(CacheEntry("$scope:$key", gson.toJson(value), System.currentTimeMillis())) }
    }
    fun loadCatalog() {
        catalogJob?.cancel()
        val generation = epoch; val client = api ?: return
        catalogJob = viewModelScope.launch {
            busy = true; error = null
            try {
                cached<List<Album>>("albums")?.let { if (generation == epoch) albums = it }
                cached<List<Person>>("people")?.let { if (generation == epoch) people = it }
                cached<List<Label>>("tags")?.let { if (generation == epoch) tags = it }
                cached<List<Label>>("categories")?.let { if (generation == epoch) categories = it }
                val results = coroutineScope {
                    val a = async { client.get<List<Album>>("albums") }; val p = async { client.get<List<Person>>("people") }
                    val t = async { client.get<List<Label>>("tags") }; val c = async { client.get<List<Label>>("categories") }
                    Catalog(a.await(), p.await(), t.await(), c.await())
                }
                if (generation != epoch) return@launch
                albums = results.albums; people = results.people; tags = results.tags; categories = results.categories; catalogOffline = false
                cache("albums", albums); cache("people", people); cache("tags", tags); cache("categories", categories)
            } catch (e: Exception) { if (e !is CancellationException && generation == epoch) { catalogOffline = true; error = readable(e) } }
            finally { if (generation == epoch) busy = false }
        }
    }
    private data class Catalog(val albums: List<Album>, val people: List<Person>, val tags: List<Label>, val categories: List<Label>)
    fun search(value: SearchFilter) {
        require(value.start.isBlank() || runCatching { java.time.LocalDate.parse(value.start) }.isSuccess) { "开始日期格式应为 YYYY-MM-DD" }
        require(value.end.isBlank() || runCatching { java.time.LocalDate.parse(value.end) }.isSuccess) { "结束日期格式应为 YYYY-MM-DD" }
        require(value.start.isBlank() || value.end.isBlank() || value.start <= value.end) { "开始日期不能晚于结束日期" }
        filter = value
        if (historyEnabled && value.query.isNotBlank()) {
            history = (listOf(value.query.trim()) + history).distinct().take(10)
            preferences.edit().putString("history", history.joinToString("\n")).apply()
        }
        loadFeed("search", true)
    }
    fun loadFeed(source: String, refresh: Boolean = false) {
        val old = feeds[source] ?: Feed()
        if (!refresh && (old.loading || !old.more)) return
        if (refresh) feedJobs[source]?.cancel()
        val generation = epoch; val client = api ?: return
        val query = filter
        feeds[source] = if (refresh) old.copy(loading = true, error = null) else old.copy(loading = true, error = null)
        feedJobs[source] = viewModelScope.launch {
            val base = if (refresh) Feed(photos = old.photos) else old
            try {
                if (old.photos.isEmpty() && source != "search") cached<Feed>("feed:$source")?.let { if (generation == epoch) feeds[source] = it.copy(loading = true, offline = true) }
                val next: List<Photo>; val more: Boolean; var cursor: Long? = null
                if (source == "gallery") {
                    val page = client.get<CursorPage>("photos/browse", buildMap { put("size", "40"); if (!refresh) base.cursor?.let { put("beforeId", it.toString()) } })
                    next = page.content; more = page.hasMore; cursor = page.nextCursor
                } else {
                    val pageNumber = if (refresh) 0 else base.page
                    val path = when {
                        source == "favorites" -> "photos/favorites"
                        source == "likes" -> "photos/liked"
                        source == "trash" -> "photos/trash"
                        source == "search" -> "search"
                        source.startsWith("timeline:") -> if (source == "timeline:0:0") "timeline/undated" else "timeline/photos"
                        source.startsWith("baby:") -> "albums/${source.substringAfter(':')}/baby-timeline/page"
                        source.startsWith("album:") -> "albums/${source.substringAfter(':')}/photos/page"
                        source.startsWith("person:") -> "people/${source.substringAfter(':')}/photos"
                        source.startsWith("category:") -> "categories/${source.substringAfter(':')}/photos"
                        else -> "search"
                    }
                    val params = when {
                        source.startsWith("timeline:") -> mapOf("year" to source.split(':')[1], "month" to source.split(':')[2], "page" to "$pageNumber", "size" to "40")
                        source == "search" -> query.parameters(pageNumber)
                        source.startsWith("tag:") -> mapOf("tagId" to source.substringAfter(':'), "page" to "$pageNumber", "size" to "40")
                        else -> mapOf("page" to "$pageNumber", "size" to "40")
                    }
                    val page = client.get<PhotoPage>(path, params); next = page.content; more = !page.last
                }
                if (generation != epoch) return@launch
                val combined = (if (refresh) next else base.photos + next).distinctBy { it.id }
                val loaded = Feed(combined, (if (refresh) 0 else base.page) + 1, cursor, more, updated = System.currentTimeMillis())
                feeds[source] = loaded
                if (source != "search") cache("feed:$source", loaded.copy(photos = loaded.photos.take(160), page = 0, cursor = null, more = true))
            } catch (e: Exception) {
                if (e !is CancellationException && generation == epoch) feeds[source] = (feeds[source] ?: old).copy(loading = false, offline = e is java.io.IOException, error = readable(e))
            }
        }
    }
    fun mutate(action: suspend (ApiClient) -> Unit, success: String = "已保存", done: () -> Unit = {}) {
        if (busy) return
        val client = api ?: return; val generation = epoch
        mutationJob = viewModelScope.launch {
            busy = true; error = null
            try {
                action(client)
                if (generation == epoch) { notice = success; done() }
            } catch (e: Exception) { if (e !is CancellationException && generation == epoch) error = readable(e) }
            finally { if (generation == epoch) busy = false }
        }
    }
    fun updatePhoto(id: Long, values: Map<String, Any>, done: () -> Unit = {}) = mutate({ client ->
        val updated = gson.fromJson(client.write("photos/$id", "PUT", values), Photo::class.java)
        currentCoroutineContext().ensureActive()
        check(api === client) { "会话已切换" }
        feeds.keys.toList().forEach { source ->
            val updatedPhotos = feeds.getValue(source).photos.mapNotNull {
                if (it.id != id) it
                else if ((source == "favorites" && !updated.favorite) || (source.startsWith("timeline:") && !updated.inTimeline)) null
                else updated.copy(ageLabel = it.ageLabel)
            }
            feeds[source] = feeds.getValue(source).copy(photos = updatedPhotos)
        }
        persistFeeds()
        if ("inTimeline" in values) loadMonths()
    }, done = done)
    fun batch(ids: Set<Long>, operation: String, album: Long? = null, done: () -> Unit = {}) = mutate({ client ->
        failedBatchIds = emptySet()
        var success = 0; val failed = mutableListOf<Long>()
        for (id in ids) {
            try {
                when (operation) {
                    "tagAdd" -> client.write("tags/photos/$id", body = mapOf("tagId" to requireNotNull(album)))
                    "tagRemove" -> client.write("tags/photos/$id/$album", "DELETE", null)
                    "categoryAdd" -> client.write("categories/$album/photos/$id")
                    "categoryRemove" -> client.write("categories/$album/photos/$id", "DELETE", null)
                    "timelineOn", "timelineOff" -> client.write("photos/$id", "PUT", mapOf("inTimeline" to (operation == "timelineOn")))
                    "favorite" -> client.write("photos/$id", "PUT", mapOf("favorite" to true))
                    "trash" -> client.write("photos/$id", "DELETE", null)
                    "restore" -> client.write("photos/trash/$id/restore")
                    "remove" -> client.write("albums/$album/photos/$id", "DELETE", null)
                    "add" -> client.write("albums/$album/photos/$id")
                }
                success++
            } catch (e: Exception) {
                if (e is CancellationException || (e is ApiFailure && e.status == 401)) throw e
                failed.add(id)
            }
        }
        currentCoroutineContext().ensureActive()
        check(api === client) { "会话已切换" }
        feeds.keys.toList().forEach { key ->
            val feed = feeds.getValue(key)
            feeds[key] = feed.copy(photos = feed.photos.mapNotNull { p ->
                if (p.id !in ids || p.id in failed) p else when (operation) {
                    "tagRemove" -> if (key == "tag:$album") null else p
                    "categoryRemove" -> if (key == "category:$album") null else p
                    "timelineOn" -> p.copy(inTimeline = true)
                    "timelineOff" -> if (key.startsWith("timeline:")) null else p.copy(inTimeline = false)
                    "favorite" -> p.copy(favorite = true)
                    "trash" -> null
                    "restore" -> if (key == "trash") null else p
                    "remove" -> if (key == "album:$album" || key == "baby:$album") null else p
                    else -> p
                }
            })
        }
        failedBatchIds = failed.toSet()
        persistFeeds()
        if (operation.startsWith("tag") || operation.startsWith("category")) {
            if (album != null) {
                val related = (if (operation.startsWith("tag")) "tag:" else "category:") + album
                invalidateFeed(related); loadFeed(related, true)
            }
            if (failed.isEmpty()) loadCatalog()
        }
        if (operation.startsWith("timeline") || operation == "trash" || operation == "restore") loadMonths()
        if (failed.isNotEmpty()) throw IllegalStateException("成功 $success 项；失败 ID：${failed.joinToString()}。请保留失败项重试。")
    }, success = "已处理 ${ids.size} 项", done = done)
    private suspend fun persistFeeds() {
        feeds.toMap().filterKeys { it != "search" }.forEach { (source, feed) ->
            cache("feed:$source", feed.copy(photos = feed.photos.take(160), page = 0, cursor = null, more = true))
        }
    }
    suspend fun invalidateFeed(source: String) {
        currentCoroutineContext().ensureActive()
        feedJobs.remove(source)?.cancel()
        feeds.remove(source)
        val key = "$cacheScope:feed:$source"
        withContext(Dispatchers.IO) { db.entries().clearPrefix(key) }
    }
    fun supports(feature: String) = serverInfo?.capabilities?.contains(feature) == true
    fun loadMonths() {
        if (!supports("family-timeline")) return
        monthsJob?.cancel()
        val client = api ?: return; val generation = epoch
        monthsJob = viewModelScope.launch {
            monthsLoading = true; monthsError = null
            try {
                cached<List<MonthSummary>>("timeline-months")?.let { if (generation == epoch) months = it }
                val dated = client.get<List<MonthSummary>>("timeline/months")
                val undated = client.get<Map<String, Long>>("timeline/undated/count")["count"] ?: 0L
                if (generation == epoch) {
                    months = dated + if (undated > 0) listOf(MonthSummary(0, 0, undated)) else emptyList()
                    monthsOffline = false; cache("timeline-months", months)
                }
            } catch (e: Exception) {
                if (e !is CancellationException && generation == epoch) { monthsError = readable(e); monthsOffline = true }
            } finally { if (generation == epoch) monthsLoading = false }
        }
    }
    suspend fun faces(id: Long, page: Int): FacePage = api!!.get("people/$id/faces/page", mapOf("page" to "$page", "size" to "40"))
    suspend fun detail(id: Long): Photo = api!!.get("photos/$id/detail")
    fun mediaUrl(path: String?): String? = path?.let { runCatching { api?.address?.media(it)?.toString() }.getOrNull() }
    fun preview(photo: Photo): String? = if (photo.mediaType == "PHOTO") api?.address?.api("mobile/photos/${photo.id}/preview")?.toString() else mediaUrl(photo.thumbnailUrl)
    fun chooseTheme(value: String) { theme = value; preferences.edit().putString("theme", value).apply() }
    fun chooseColumns(value: Int) { columns = value; preferences.edit().putInt("columns", value).apply() }
    fun setCellular(value: Boolean) { cellularOriginal = value; preferences.edit().putBoolean("cellular", value).apply() }
    fun setHistory(value: Boolean) { historyEnabled = value; preferences.edit().putBoolean("historyEnabled", value).apply(); if (!value) clearHistory() }
    fun clearHistory() { history = emptyList(); preferences.edit().remove("history").apply() }
    fun chooseCacheLimit(value: Int) { cacheLimit = value; preferences.edit().putInt("cacheLimit", value).apply(); createImageLoader() }
    fun measureCache() { viewModelScope.launch(Dispatchers.IO) { val size = File(getApplication<Application>().cacheDir, "images/$cacheScope").walkTopDown().filter { it.isFile }.sumOf { it.length() }; withContext(Dispatchers.Main) { cacheBytes = size } } }
    fun clearCache() {
        viewModelScope.launch { withContext(Dispatchers.IO) { db.entries().clear(); imageLoader?.diskCache?.clear(); File(getApplication<Application>().cacheDir, "shares/$cacheScope").deleteRecursively() }; imageLoader?.memoryCache?.clear(); cacheBytes = 0; notice = "浏览缓存已清理，导出的文件不受影响" }
    }
    fun download(photo: Photo, destination: Uri? = null, share: Boolean = false, privacyCopy: Boolean = false) {
        if (privacyCopy && (!share || destination != null || photo.mediaType != "PHOTO")) { error = "仅静态照片支持去除元数据的分享副本"; return }
        val client = api ?: return; val generation = epoch; val scope = cacheScope
        val url = mediaUrl(photo.originalUrl) ?: run { error = "原文件地址不可用"; return }
        val id = UUID.randomUUID().toString()
        transfers = listOf(Transfer(id, photo.name, "下载中", photo = photo, shareMode = if (share) (if (privacyCopy) "copy" else "original") else null)) + transfers.take(99)
        transferJobs[id] = viewModelScope.launch {
            val file = File(getApplication<Application>().cacheDir, "shares/$scope/$id/${if (privacyCopy) "original-input.bin" else File(photo.name).name}")
            var sharedFile = file
            try {
                cache("transfers", transfers, scope)
                withContext(Dispatchers.IO) {
                    file.parentFile!!.mkdirs()
                    val network = async { client.response(Request.Builder().url(url).build()) }
                    val response = network.await()
                    // Closing the body interrupts blocked reads immediately when this download is canceled.
                    val watcher = launch(Dispatchers.IO) { try { awaitCancellation() } finally { response.close() } }
                    try { response.use { response ->
                        client.checkResult(response)
                        val body = response.body ?: error("没有文件内容")
                        val total = body.contentLength()
                        val usable = file.parentFile!!.usableSpace
                        require(total < 0 || total < usable - 16 * 1024 * 1024) { "手机可用空间不足" }
                        body.byteStream().use { input -> file.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024); var count = 0L; var last = 0L
                            while (true) {
                                ensureActive(); val n = input.read(buffer); if (n < 0) break
                                output.write(buffer, 0, n); count += n
                                if (System.currentTimeMillis() - last > 250) {
                                    last = System.currentTimeMillis()
                                    withContext(Dispatchers.Main) { if (epoch == generation) transfers = transfers.map { if (it.id == id) it.copy(progress = if (total > 0) count.toFloat() / total else 0f) else it } }
                                }
                            }
                            require(total < 0 || count == total) { "文件未完整下载，请重试" }
                        } }
                    }
                    } finally { watcher.cancel() }
                    if (privacyCopy) {
                        ensureActive()
                        sharedFile = File(file.parentFile, "shared-photo.jpg")
                        require(sharedFile.canonicalPath != file.canonicalPath) { "请重试分享副本" }
                        PrivacyCopy.write(file, sharedFile)
                        ensureActive()
                        file.delete()
                    }
                    if (destination != null) {
                        getApplication<Application>().contentResolver.openOutputStream(destination, "w")!!.use { output -> file.inputStream().use { input -> input.copyTo(output) } }
                        file.delete()
                    }
                }
                if (generation != epoch) return@launch
                transfers = transfers.map { if (it.id == id) it.copy(status = "已完成", progress = 1f, uri = destination?.toString()) else it }
                cache("transfers", transfers, scope)
                if (share) shareReady.emit(SharedAsset(sharedFile, if (privacyCopy) "分享 JPEG 副本" else "分享原文件")) else notice = "文件已保存"
            } catch (e: Exception) {
                file.delete(); if (sharedFile != file) sharedFile.delete()
                if (destination != null) withContext(NonCancellable + Dispatchers.IO) { runCatching { android.provider.DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, destination) } }
                if (generation == epoch) {
                    transfers = transfers.map { if (it.id == id) it.copy(status = if (e is CancellationException) "已取消" else readable(e)) else it }
                    if (e !is CancellationException) { error = readable(e); cache("transfers", transfers, scope) }
                }
            } finally { transferJobs.remove(id) }
        }
    }
    fun cancelTransfer(id: String) { transferJobs[id]?.cancel() }
    override fun onCleared() { api?.http?.dispatcher?.cancelAll(); imageLoader?.shutdown(); db.close() }
}
