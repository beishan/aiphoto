package com.aiphoto.android

import com.google.gson.JsonElement

data class User(val id: Long = 0, val username: String = "", val nickname: String? = null, val role: String = "USER")
data class Login(val token: String, val user: User)
data class ServerInfo(val product: String = "", val version: String = "", val apiVersion: Int = 0, val instanceId: String = "", val libraryMode: String = "", val capabilities: List<String> = emptyList())
data class Photo(
    val id: Long = 0, val originalFilename: String? = null, val thumbnailUrl: String? = null,
    val originalUrl: String? = null, val mediaType: String = "PHOTO", val favorite: Boolean = false,
    val width: Int? = null, val height: Int? = null, val fileSize: Long? = null,
    val exifDate: String? = null, val createdAt: String? = null, val deletedAt: String? = null,
    val rating: Int? = null, val note: String? = null, val aiCaption: String? = null,
    val gpsLat: Double? = null, val gpsLng: Double? = null, val tags: List<JsonElement>? = null,
    val people: List<Person>? = null,
) {
    val name get() = originalFilename ?: "照片 $id"
    val isVideo get() = mediaType == "VIDEO"
}
data class Album(val id: Long = 0, val name: String = "", val description: String? = null, val type: String = "VIRTUAL", val coverPhotoUrl: String? = null, val photoCount: Int = 0, val shared: Boolean = false)
data class Person(val id: Long = 0, val name: String? = null, val coverPhotoUrl: String? = null, val photoCount: Int = 0)
data class Label(val id: Long = 0, val name: String = "", val photoCount: Int = 0)
data class PhotoPage(val content: List<Photo> = emptyList(), val last: Boolean = true)
data class CursorPage(val content: List<Photo> = emptyList(), val nextCursor: Long? = null, val hasMore: Boolean = false)
data class SearchFilter(val query: String = "", val semantic: Boolean = false, val start: String = "", val end: String = "", val person: Long? = null, val tag: Long? = null, val type: String = "", val rating: Int = 0) {
    fun parameters(page: Int): Map<String, String> = buildMap {
        put("query", query); put("type", if (semantic) "semantic" else "text")
        put("page", page.toString()); put("size", "40")
        if (start.isNotBlank()) put("startDate", start)
        if (end.isNotBlank()) put("endDate", end)
        person?.let { put("personId", it.toString()) }; tag?.let { put("tagId", it.toString()) }
        if (type.isNotEmpty()) put("fileType", type)
        if (rating > 0) put("minRating", rating.toString())
    }
}
data class Feed(val photos: List<Photo> = emptyList(), val page: Int = 0, val cursor: Long? = null, val more: Boolean = true, val loading: Boolean = false, val offline: Boolean = false, val updated: Long = 0, val error: String? = null)
data class Transfer(val id: String, val name: String, val status: String, val progress: Float = 0f, val uri: String? = null, val photo: Photo? = null)
class ApiFailure(val status: Int, message: String) : Exception(message)
