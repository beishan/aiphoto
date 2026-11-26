package com.aiphoto.android

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ApiClientTest {
    @Test fun expiredRequestSignsInAgainAndRetriesWithNewToken() = runBlocking {
        MockWebServer().use { server ->
            val address = ServerAddress(server.url("/nas/").toString(), true)
            val guest = ApiClient(address, null)
            val api = ApiClient(address, "expired", renew = {
                guest.renewLogin("original", RememberedLogin("family", "test-password"))?.token
            })
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setBody("""{"product":"aiphoto","instanceId":"original"}"""))
            server.enqueue(MockResponse().setBody("""{"token":"renewed","user":{"id":42}}"""))
            server.enqueue(MockResponse().setBody("""{"id":42}"""))

            assertEquals(42L, api.get<User>("users/me").id)
            assertEquals("Bearer expired", server.takeRequest().getHeader("Authorization"))
            assertEquals("/nas/api/mobile/server-info", server.takeRequest().path)
            val login = server.takeRequest()
            assertEquals("/nas/api/auth/login", login.path)
            assertNull(login.getHeader("Authorization"))
            assertEquals("test-password", api.gson.fromJson(login.body.readUtf8(), RememberedLogin::class.java).password)
            assertEquals("Bearer renewed", server.takeRequest().getHeader("Authorization"))
        }
    }

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
