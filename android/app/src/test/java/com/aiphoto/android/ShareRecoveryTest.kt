package com.aiphoto.android

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ShareRecoveryTest {
    @Test fun cachedRetryRetainsPrivacyChoiceAndLegacyDownloadsRemainDownloads() {
        val json = Gson()
        val record = Transfer("id", "private-name.jpg", "下载中", photo = Photo(id = 1), shareMode = "copy")
        val restored = json.fromJson(json.toJson(record), Transfer::class.java)
        assertEquals("copy", restored.shareMode)
        assertEquals(1L, restored.photo?.id)
        val legacy = json.fromJson("""{"id":"old","name":"old.jpg","status":"failed"}""", Transfer::class.java)
        assertNull(legacy.shareMode)
    }
}
