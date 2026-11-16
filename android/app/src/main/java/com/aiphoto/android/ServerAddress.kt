package com.aiphoto.android

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** All API/media paths are scoped to the configured reverse-proxy prefix. */
class ServerAddress(raw: String, allowHttp: Boolean) {
    val base: HttpUrl
    init {
        val url = raw.trim().toHttpUrl()
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) { "地址不能包含用户名、密码、查询参数或片段" }
        require(url.isHttps || allowHttp) { "HTTP 未加密，请先确认允许 HTTP 连接" }
        base = url.newBuilder().encodedPath(url.encodedPath.trimEnd('/') + "/").build()
    }
    fun api(path: String, params: Map<String, String> = emptyMap()): HttpUrl =
        base.resolve("api/" + path.removePrefix("/"))!!.newBuilder().apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
    fun media(path: String): HttpUrl {
        val candidate = if (path.startsWith("https://") || path.startsWith("http://")) path.toHttpUrl()
        else if (path.startsWith(base.encodedPath) && base.encodedPath != "/") base.resolve(path)!!
        else base.resolve(path.removePrefix("/")) ?: error("无效的媒体地址")
        require(candidate.scheme == base.scheme && candidate.host == base.host && candidate.port == base.port) { "拒绝加载其他服务器的媒体地址" }
        require(candidate.encodedPath.startsWith(base.encodedPath)) { "媒体路径不属于当前服务器" }
        return candidate
    }
}
