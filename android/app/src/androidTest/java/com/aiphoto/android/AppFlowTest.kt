package com.aiphoto.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val server = MockWebServer()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private var favorite = false
    private val image = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=", 0)
    private fun photo(): String = """{"id":1,"originalFilename":"海边.jpg","thumbnailUrl":"/media/thumbs/sea.jpg","originalUrl":"/media/photos/sea.jpg","mediaType":"PHOTO","favorite":$favorite,"width":4032,"height":3024,"fileSize":12345,"rating":3,"note":"海边的午后"}"""
    @Before fun setup() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                val path = request.requestUrl!!.encodedPath.removePrefix("/nas/api/")
                if (path == "mobile/server-info") return json("""{"product":"aiphoto","version":"0.22.0","apiVersion":1,"instanceId":"test-nas-instance","capabilities":["album-pagination","authenticated-media"]}""")
                if (path == "auth/login") return json("""{"token":"test-session","user":{"id":1,"username":"tester","role":"USER"}}""")
                if (request.getHeader("Authorization") != "Bearer test-session") return MockResponse().setResponseCode(401)
                if (path == "photos/browse") return json("""{"content":[${photo()}],"nextCursor":1,"hasMore":false}""")
                if (path == "albums") return json("""[{"id":1,"name":"山海之间","type":"VIRTUAL","photoCount":1,"shared":false}]""")
                if (path == "people") return json("""[{"id":1,"name":"小满","photoCount":1}]""")
                if (path == "tags" || path == "categories") return json("[]")
                if (path == "photos/1/detail") return json(photo())
                if (path == "photos/1" && request.method == "PUT") { favorite = request.body.readUtf8().contains("true"); return json(photo()) }
                if (path == "users/me") return json("""{"id":1,"username":"tester","role":"USER"}""")
                if (path.startsWith("mobile/photos") || request.requestUrl!!.encodedPath.contains("/media/")) return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(image))
                if (path == "search" || path.contains("/photos/page") || path == "photos/favorites") return json("""{"content":[${photo()}],"last":true}""")
                return MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun waitText(value: String) { compose.waitUntil(15000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() } }
    private fun back() { androidx.test.espresso.Espresso.pressBack(); compose.waitForIdle() }

    @Test fun connectsLogsInBrowsesAndUsesAuthenticatedApiWithoutMediaPermissions() {
        compose.onNodeWithText("服务器地址").performTextReplacement(server.url("/nas/").toString())
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onNodeWithText("连接服务器").performScrollTo().performClick()
        waitText("欢迎回家")
        compose.onNodeWithText("用户名").performTextInput("tester")
        compose.onNodeWithText("密码").performTextInput("password")
        compose.onNodeWithText("登录", useUnmergedTree = true).performScrollTo().performClick()
        waitText("最近入库")
        compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("海边.jpg").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("海边.jpg").performClick()
        waitText("查看原图")
        compose.onNodeWithText("收藏").performClick()
        waitText("已收藏")
        compose.onNodeWithText("详情").performClick()
        waitText("照片详情")
        compose.onNodeWithText("海边的午后").assertExists()
        back(); back()
        compose.onNodeWithContentDescription("搜索照片").performClick()
        compose.onNodeWithText("搜索照片、人物或场景").performTextInput("海边")
        compose.onNodeWithContentDescription("提交搜索").performClick()
        compose.waitUntil(15000) { requests.any { it.requestUrl?.encodedPath == "/nas/api/search" } }
        compose.onNodeWithContentDescription("返回").performClick()
        waitText("最近入库")
        compose.onNodeWithText("相册", useUnmergedTree = true).performClick()
        waitText("山海之间")
        compose.onNodeWithText("山海之间").performClick()
        waitText("从 NAS 图库添加照片")
        compose.waitUntil(10000) { requests.any { it.requestUrl?.encodedPath == "/nas/api/albums/1/photos/page" } }
        val permissions = compose.activity.packageManager.getPackageInfo(compose.activity.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        Assert.assertFalse(permissions.any { it.contains("READ_MEDIA") || it.contains("READ_EXTERNAL_STORAGE") || it.contains("MANAGE_EXTERNAL_STORAGE") })
        Assert.assertTrue(requests.filter { it.requestUrl?.encodedPath?.contains("/media/") == true }.all { it.getHeader("Authorization") == "Bearer test-session" })
    }
    @After fun cleanup() { server.shutdown() }
}
