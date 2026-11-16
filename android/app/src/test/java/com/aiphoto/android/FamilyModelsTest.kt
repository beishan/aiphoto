package com.aiphoto.android

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class FamilyModelsTest {
    @Test fun babyGroupsRemainContiguousAcrossPageBoundaryAndKeepStableKeys() {
        val first = listOf(Photo(id = 1, ageLabel = "出生前"), Photo(id = 2, ageLabel = "1 个月"))
        val next = first + listOf(Photo(id = 3, ageLabel = "1 个月"), Photo(id = 4))
        val cells = feedCells(next, "baby:9")
        assertEquals(listOf("出生前", "1 个月", "拍摄日期未知"), cells.mapNotNull { it.label })
        assertEquals(feedCells(first, "baby:9").map { it.key }, cells.take(4).map { it.key })
        assertEquals(4, cells.count { it.photo != null })
    }
    @Test fun timelineGroupsUseExifDateAndGalleryHasNoSyntheticSections() {
        val photos = listOf(Photo(id = 1, exifDate = "2026-11-15T12:30:00"), Photo(id = 2, exifDate = "2026-11-15T23:59:00"), Photo(id = 3))
        assertEquals(listOf("2026-11-15", "拍摄日期未知"), feedCells(photos, "timeline:2026:11").mapNotNull { it.label })
        assertEquals(3, feedCells(photos, "gallery").size)
        assertEquals("timeline:0:0", MonthSummary(0, 0, 2).source)
    }
    @Test fun realFamilyContractsPreserveBirthdayAndSelectionFlags() {
        val json = Gson()
        val baby = json.fromJson("""{"id":1,"type":"BABY","birthDate":"2024-02-29"}""", Album::class.java)
        assertEquals("2024-02-29", baby.birthDate)
        val page = json.fromJson("""{"content":[{"id":8,"ageLabel":"1 岁","inTimeline":true}],"last":true}""", PhotoPage::class.java)
        assertTrue(page.content.single().inTimeline)
        assertEquals("1 岁", page.content.single().ageLabel)
        val faces = json.fromJson("""{"content":[{"id":7,"previewUrl":"/api/mobile/faces/7/preview","personId":2}],"last":false}""", FacePage::class.java)
        assertEquals("/api/mobile/faces/7/preview", faces.content.single().previewUrl)
        assertFalse(faces.last)
    }
    @Test fun invalidOrFutureTypedBirthdayCannotCrashDatePickerInitialization() {
        val today = java.time.LocalDate.of(2026, 11, 16)
        for (value in listOf("", "not-a-date", "9999-01-01", "0000-01-01", "2024-02-30")) {
            assertEquals(today, calendarBirthDate(value, today))
        }
        assertEquals(java.time.LocalDate.of(2024, 2, 29), calendarBirthDate("2024-02-29", today))
        assertEquals(java.time.LocalDate.of(1800, 1, 1), calendarBirthDate("1800-01-01", today))
    }

}
