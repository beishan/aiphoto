package com.aiphoto.android

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ApiClientTest {
    @Test fun realPagingContractCarriesBearerAndPrefix() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"content":[{"id":42,"originalFilename":"sea.jpg","favorite":true}],"nextCursor":42,"hasMore":true}"""))
            val api = ApiClient(ServerAddress(server.url("/photos/").toString(), true), "private-token")
            val page = api.get<CursorPage>("photos/browse", mapOf("size" to "40"))
            assertEquals(42L, page.content.single().id); assertTrue(page.hasMore)
            val request = server.takeRequest()
            assertEquals("/photos/api/photos/browse?size=40", request.path)
            assertEquals("Bearer private-token", request.getHeader("Authorization"))
        }
    }
    @Test fun forbiddenIsNotLogoutAndRedirectIsNotFollowed() = runBlocking {
        MockWebServer().use { server ->
            var expired = false
            val api = ApiClient(ServerAddress(server.url("/").toString(), true), "token") { expired = true }
            server.enqueue(MockResponse().setResponseCode(403))
            try { api.get<User>("users/me"); fail() } catch (e: ApiFailure) { assertEquals(403, e.status) }
            assertFalse(expired)
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://other.example/"))
            try { api.get<User>("users/me"); fail() } catch (e: ApiFailure) { assertEquals(302, e.status) }
            server.enqueue(MockResponse().setResponseCode(401))
            try { api.get<User>("users/me"); fail() } catch (e: ApiFailure) { assertEquals(401, e.status) }
            assertTrue(expired)
        }
    }
}
