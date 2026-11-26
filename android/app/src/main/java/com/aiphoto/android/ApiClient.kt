package com.aiphoto.android

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiClient(
    val address: ServerAddress,
    token: String?,
    private val renew: ((String) -> String?)? = null,
    val unauthorized: () -> Unit = {},
) {
    val gson = Gson()
    @Volatile private var accessToken = token
    private val renewalLock = Any()
    val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .addInterceptor { chain ->
            val original = chain.request()
            require(original.url.scheme == address.base.scheme && original.url.host == address.base.host && original.url.port == address.base.port && original.url.encodedPath.startsWith(address.base.encodedPath)) { "请求超出当前服务器范围" }
            val token = accessToken
            val request = original.newBuilder().apply {
                if (token != null) header("Authorization", "Bearer $token")
            }.build()
            val result = chain.proceed(request)
            if (result.code != 401 || token == null) return@addInterceptor result
            val replacement = try {
                synchronized(renewalLock) {
                    if (token != accessToken) accessToken
                    else renew?.invoke(token)?.also { accessToken = it }
                }
            } catch (failure: Exception) {
                result.close()
                throw IOException("暂时无法恢复登录，请重试", failure)
            }
            if (replacement == null) {
                unauthorized()
                return@addInterceptor result
            }
            if (request.body?.isOneShot() == true || request.body?.isDuplex() == true) return@addInterceptor result
            result.close()
            chain.proceed(request.newBuilder().header("Authorization", "Bearer $replacement").build()).also {
                if (it.code == 401) unauthorized()
            }
        }.build()

    internal fun renewLogin(expectedInstance: String, credentials: RememberedLogin): Login? {
        val infoRequest = Request.Builder().url(address.api("mobile/server-info")).build()
        http.newCall(infoRequest).execute().use {
            checkResult(it)
            val info = gson.fromJson(it.body!!.charStream(), ServerInfo::class.java)
            if (info.product != "aiphoto" || info.instanceId != expectedInstance) return null
        }
        val body = gson.toJson(credentials).toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(address.api("auth/login")).post(body).build()
        return http.newCall(request).execute().use {
            if (it.code in setOf(400, 401, 403)) return null
            checkResult(it)
            gson.fromJson(it.body!!.charStream(), Login::class.java)
        }
    }

    suspend fun response(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) { _, value, _ -> value.close() }
                else response.close()
            }
        })
    }
    suspend inline fun <reified T> get(path: String, params: Map<String, String> = emptyMap()): T =
        decode(Request.Builder().url(address.api(path, params)).build())
    suspend inline fun <reified T> decode(request: Request): T = withContext(Dispatchers.IO) {
        response(request).use { result ->
            checkResult(result)
            gson.fromJson<T>(result.body!!.charStream(), object : TypeToken<T>() {}.type)
        }
    }
    suspend fun write(path: String, method: String = "POST", body: Any? = emptyMap<String, String>(), oneShot: Boolean = false): String {
        val payload = if (method == "DELETE" && body == null) null
            else gson.toJson(body).toRequestBody("application/json".toMediaType())
        val requestBody = if (oneShot && payload != null) object : RequestBody() {
            override fun contentType() = payload.contentType()
            override fun contentLength() = payload.contentLength()
            override fun writeTo(sink: BufferedSink) = payload.writeTo(sink)
            override fun isOneShot() = true
        } else payload
        val request = Request.Builder().url(address.api(path)).method(method, requestBody).build()
        return withContext(Dispatchers.IO) { response(request).use { checkResult(it); it.body?.string().orEmpty() } }
    }
    fun checkResult(response: Response) {
        if (response.isSuccessful) return
        val message = when (response.code) {
            401 -> "登录已失效，请重新登录"
            403 -> "没有执行此操作的权限"
            404 -> "内容不存在或已被删除"
            413 -> "文件超过服务器限制"
            415 -> "暂不支持此媒体格式"
            in 300..399 -> "服务器返回重定向，请填写最终服务地址"
            else -> runCatching { gson.fromJson(response.peekBody(4096).string(), Map::class.java)["message"] as? String }.getOrNull() ?: "服务器请求失败（${response.code}）"
        }
        throw ApiFailure(response.code, message)
    }
}
