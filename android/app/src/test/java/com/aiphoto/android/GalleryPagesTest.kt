package com.aiphoto.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GalleryPagesTest {
    @Test fun manyCursorPagesKeepTheirOrderAndStableIndexes() {
        var loaded: List<Photo> = emptyList()
        val total = 8_000
        val pageSize = 40
        repeat(total / pageSize) { page ->
            val high = total - page * pageSize
            val incoming = List(pageSize) { Photo(id = (high - it).toLong()) }
            loaded = GalleryPages.append(loaded, incoming, loaded.lastOrNull()?.id)
        }
        val gallery = loaded as GalleryPages
        assertEquals(total, gallery.size)
        assertEquals(8_000L, gallery.first().id)
        assertEquals(7_960L, gallery[40].id)
        assertEquals(1L, gallery.last().id)
        assertEquals(6_999, gallery.indexOfId(1_001))
        assertEquals(-1, gallery.indexOfId(8_001))
        assertEquals(7_841L, gallery.take(160).last().id)
    }

    @Test fun appendRejectsCursorOverlapAndKeepsAnExistingSnapshot() {
        val original = GalleryPages.append(emptyList(), listOf(Photo(id = 5), Photo(id = 4)), null)
        val edited = original.filterNot { it.id == 4L }
        val appended = GalleryPages.append(edited, listOf(Photo(id = 4), Photo(id = 3), Photo(id = 3)), 4)
        assertEquals(listOf(5L, 4L), original.map { it.id })
        assertEquals(listOf(5L, 3L), appended.map { it.id })
        assertSame(appended, GalleryPages.append(appended, listOf(Photo(id = 3)), 3))
    }
}
