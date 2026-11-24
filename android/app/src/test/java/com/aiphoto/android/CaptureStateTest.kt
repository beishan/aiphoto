package com.aiphoto.android

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureStateTest {
    private val asset = """{"id":12,"jobId":3,"originalFilename":"sea.jpg","createdAt":"2026-11-24T10:00:00"}"""

    @Test fun previewImportHidesPendingPhotoAndRestoresItOnFailure() = runBlocking {
        MockWebServer().use { server ->
            val api = ApiClient(ServerAddress(server.url("/").toString(), true), "token")
            val state = CaptureState(api, admin = false)
            server.enqueue(MockResponse().setBody("""{"content":[$asset],"last":true,"totalElements":1}"""))
            server.enqueue(MockResponse().setBody("""{"batchId":41,"status":"FAILED"}"""))
            server.enqueue(MockResponse().setBody("""{"content":[{"assetId":12,"status":"FAILED"}],"last":true}"""))
            state.loadReview()
            val photo = state.assets.single()
            kotlinx.coroutines.coroutineScope {
                assertTrue(state.queuePreviewImport(photo, this))
                assertTrue(state.assets.isEmpty())
                assertTrue(!state.busy)
                assertTrue(!state.queuePreviewImport(photo, this))
            }
            assertEquals(photo, state.assets.single())
            assertTrue(state.error.orEmpty().contains("入库失败"))
        }
    }

    @Test fun filtersReviewAndDeletesWithOneAuthenticatedWrite() = runBlocking {
        MockWebServer().use { server ->
            val api = ApiClient(ServerAddress(server.url("/nas/").toString(), true), "token")
            val state = CaptureState(api, admin = false)
            server.enqueue(MockResponse().setBody("""{"content":[$asset],"last":true,"totalElements":1}"""))
            server.enqueue(MockResponse().setBody("""{"success":1}"""))
            server.enqueue(MockResponse().setBody("""{"content":[],"last":true,"totalElements":0}"""))

            state.setFilter(CaptureJob(3, "海边", "COMPLETED", "REVIEW"))
            val result = state.perform(CaptureAction.DELETE_SKIP, state.assets)

            assertEquals(setOf(12L), result)
            assertTrue(state.assets.isEmpty())
            assertNull(state.error)
            assertEquals("11 月 24 日", dayLabel("2026-11-24T10:00:00"))
            val review = server.takeRequest()
            assertEquals("3", review.requestUrl?.queryParameter("jobId"))
            assertEquals("DOWNLOADED", review.requestUrl?.queryParameter("status"))
            val mutation = server.takeRequest()
            assertEquals("/nas/api/crawl/jobs/3/assets/delete-and-skip", mutation.path)
            assertEquals("Bearer token", mutation.getHeader("Authorization"))
            assertEquals("""{"ids":[12]}""", mutation.body.readUtf8())
            assertEquals("3", server.takeRequest().requestUrl?.queryParameter("jobId"))
        }
    }

    @Test fun importKeepsItemsThatFailedInTheServerBatch() = runBlocking {
        MockWebServer().use { server ->
            val api = ApiClient(ServerAddress(server.url("/").toString(), true), "token")
            val state = CaptureState(api, admin = false)
            val other = """{"id":13,"jobId":3}"""
            server.enqueue(MockResponse().setBody("""{"content":[$asset,$other],"last":true,"totalElements":2}"""))
            server.enqueue(MockResponse().setBody("""{"batchId":41,"status":"PARTIAL"}"""))
            server.enqueue(MockResponse().setBody("""{"content":[{"assetId":12,"status":"SUCCESS"},{"assetId":13,"status":"FAILED"}],"last":true}"""))
            server.enqueue(MockResponse().setBody("""{"content":[$other],"last":true,"totalElements":1}"""))

            state.loadReview()
            state.assets.forEach(state::toggle)
            val completed = state.perform(CaptureAction.IMPORT, state.selected.values)

            assertEquals(setOf(12L), completed)
            assertEquals(setOf(13L), state.selected.keys)
            assertEquals(13L, state.assets.single().id)
            assertEquals("已入库 1 张，1 张待处理", state.notice)
            server.takeRequest()
            val submitted = server.takeRequest()
            assertEquals("/api/crawl/jobs/3/import", submitted.path)
        }
    }
}
