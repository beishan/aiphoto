package com.aiphoto.android

import org.junit.Assert.*
import org.junit.Test

class ServerAddressTest {
    @Test fun preservesProxyPrefixAndEncodesQuery() {
        val address = ServerAddress("https://nas.example/photos", false)
        assertEquals("https://nas.example/photos/api/search?query=%E6%B5%B7%E8%BE%B9", address.api("search", mapOf("query" to "海边")).toString())
        assertEquals("https://nas.example/photos/media/thumbs/a.jpg", address.media("/media/thumbs/a.jpg").toString())
    }
    @Test fun doesNotDuplicatePrefixForAbsoluteMedia() {
        val address = ServerAddress("https://nas.example/photos/", false)
        assertEquals("https://nas.example/photos/media/a.jpg", address.media("https://nas.example/photos/media/a.jpg").toString())
        assertEquals("https://nas.example/photos/media/a.jpg", address.media("/photos/media/a.jpg").toString())
    }
    @Test fun rejectsCredentialLeaksAndUnexpectedOrigins() {
        val address = ServerAddress("https://nas.example/photos/", false)
        listOf("https://other.example/photos/a.jpg", "http://nas.example/photos/a.jpg", "https://nas.example:8443/photos/a.jpg", "../secret", "//evil.example/a.jpg").forEach {
            assertThrows(IllegalArgumentException::class.java) { address.media(it) }
        }
        assertThrows(IllegalArgumentException::class.java) { ServerAddress("http://192.168.1.2", false) }
        assertThrows(IllegalArgumentException::class.java) { ServerAddress("https://user:password@nas.example", false) }
        assertThrows(IllegalArgumentException::class.java) { ServerAddress("https://nas.example/?token=secret", false) }
    }
    @Test fun optInAllowsHttpAndSearchUsesRealContract() {
        assertEquals("http", ServerAddress("http://192.168.1.2", true).base.scheme)
        val params = SearchFilter(query = "海边", semantic = true, person = 12, type = "VIDEO", rating = 3).parameters(2)
        assertEquals("semantic", params["type"]); assertEquals("12", params["personId"])
        assertEquals("VIDEO", params["fileType"]); assertEquals("2", params["page"])
    }
}
