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
    private var inTimeline = false
    private var babyAdded = false
    private var personName = "小满"
    private val image = java.io.ByteArrayOutputStream().use { stream ->
        val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(40, 99, 78))
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        bitmap.recycle(); stream.toByteArray()
    }
    private fun photo(): String = """{"id":1,"originalFilename":"海边.jpg","thumbnailUrl":"/media/thumbs/sea.jpg","originalUrl":"/media/photos/sea.jpg","mediaType":"PHOTO","favorite":$favorite,"inTimeline":$inTimeline,"exifDate":"2026-11-15T12:30:00","width":4032,"height":3024,"fileSize":12345,"rating":3,"note":"海边的午后"}"""
    @Before fun setup() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                val path = request.requestUrl!!.encodedPath.removePrefix("/nas/api/")
                if (path == "mobile/server-info") return json("""{"product":"aiphoto","version":"0.23.0","apiVersion":1,"instanceId":"test-nas-instance","capabilities":["album-pagination","authenticated-media","family-timeline","baby-timeline-page","people-edit"]}""")
                if (path == "auth/login") return json("""{"token":"test-session","user":{"id":1,"username":"tester","role":"ADMIN"}}""")
                if (request.getHeader("Authorization") != "Bearer test-session") return MockResponse().setResponseCode(401)
                if (path == "photos/browse") return json("""{"content":[${photo()}],"nextCursor":1,"hasMore":false}""")
                if (path == "albums" && request.method == "POST") {
                    babyAdded = true
                    return json("""{"id":2,"name":"小满的成长","type":"BABY","birthDate":"2024-02-29","photoCount":1}""")
                }
                if (path == "albums" && babyAdded) return json("""[{"id":1,"name":"山海之间","type":"VIRTUAL","photoCount":1},{"id":2,"name":"小满的成长","type":"BABY","birthDate":"2024-02-29","photoCount":1}]""")
                if (path == "albums") return json("""[{"id":1,"name":"山海之间","type":"VIRTUAL","photoCount":1,"shared":false}]""")
                if (path == "people") return json("""[{"id":1,"name":"$personName","photoCount":1},{"id":2,"name":"妈妈","photoCount":2}]""")
                if (path == "people/1" && request.method == "PUT") {
                    personName = "小满同学"
                    return json("""{"id":1,"name":"$personName","photoCount":1}""")
                }
                if (path == "people/1/faces/page") return json("""{"content":[{"id":7,"photoId":1,"previewUrl":"/api/mobile/faces/7/preview","personId":1}],"last":true}""")
                if (path == "people/1/cover-face") return json("{}")
                if (path == "timeline/months") return json("""[{"year":2026,"month":11,"count":1}]""")
                if (path == "timeline/undated/count") return json("""{"count":1}""")
                if (path == "albums/2/baby-timeline/page") return json("""{"content":[${photo().dropLast(1)},"ageLabel":"2 岁 8 个月"}],"last":true}""")
                if (path == "tags" || path == "categories") return json("[]")
                if (path == "photos/1/detail") return json(photo())
                if (path == "photos/1" && request.method == "PUT") {
                    val values = com.google.gson.JsonParser.parseString(request.body.readUtf8()).asJsonObject
                    if (values.has("favorite")) favorite = values["favorite"].asBoolean
                    if (values.has("inTimeline")) inTimeline = values["inTimeline"].asBoolean
                    return json(photo())
                }
                if (path == "users/me") return json("""{"id":1,"username":"tester","role":"ADMIN"}""")
                if (path.startsWith("mobile/photos") || path.startsWith("mobile/faces") || request.requestUrl!!.encodedPath.contains("/media/")) return MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(image))
                if (path == "search" || path.contains("/photos/page") || path == "photos/favorites" || path == "people/1/photos" || path == "timeline/photos" || path == "timeline/undated") return json("""{"content":[${photo()}],"last":true}""")
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
        // Family milestone: create a baby album and browse server-provided age groups.
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("创建相册").performClick()
        compose.onNodeWithText("宝宝相册").performClick()
        compose.onNodeWithText("出生日期 YYYY-MM-DD").performTextInput("2024-02-29")
        compose.onNodeWithText("相册名称").performTextInput("小满的成长")
        compose.onNodeWithText("保存相册").performScrollTo().performClick()
        waitText("小满的成长")
        compose.onNodeWithText("小满的成长").performClick()
        waitText("2 岁 8 个月")
        compose.onNodeWithContentDescription("海边.jpg").performClick()
        compose.onNodeWithText("加入精选").performScrollTo().performClick()
        waitText("移出精选")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("发现", useUnmergedTree = true).performClick()
        waitText("精选时间线")
        compose.onNodeWithText("精选时间线").performClick()
        waitText("11月")
        compose.waitUntil(10000) { requests.any { it.requestUrl?.encodedPath == "/nas/api/timeline/photos" } }
        compose.onNodeWithContentDescription("返回").performClick()
        waitText("小满")
        compose.onNodeWithText("小满").performClick()
        compose.onNodeWithText("改名").performClick()
        compose.onNodeWithText("人物名称").performTextReplacement("小满同学")
        compose.onNodeWithText("保存名称").performClick()
        waitText("小满同学")
        compose.onNodeWithText("选择封面").performClick()
        waitText("人脸 #7")
        compose.waitUntil(15000) { compose.onAllNodes(hasText("人脸 #7") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("人脸 #7").performClick()
        waitText("设为人物封面？")
        compose.onNodeWithText("确认").performClick()
        compose.waitUntil(10000) { requests.any { it.requestUrl?.encodedPath == "/nas/api/people/1/cover-face" && it.method == "PUT" } }
        val create = requests.first { it.requestUrl?.encodedPath == "/nas/api/albums" && it.method == "POST" }.body.clone().readUtf8()
        Assert.assertTrue(create.contains("2024-02-29")); Assert.assertTrue(create.contains("BABY"))
        Assert.assertTrue(requests.any { it.requestUrl?.encodedPath == "/nas/api/albums/2/baby-timeline/page" && it.requestUrl?.queryParameter("size") == "40" })
        val permissions = compose.activity.packageManager.getPackageInfo(compose.activity.packageName, android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        Assert.assertFalse(permissions.any { it.contains("READ_MEDIA") || it.contains("READ_EXTERNAL_STORAGE") || it.contains("MANAGE_EXTERNAL_STORAGE") })
        Assert.assertTrue(requests.filter { it.requestUrl?.encodedPath?.contains("/media/") == true }.all { it.getHeader("Authorization") == "Bearer test-session" })
    }
    @After fun cleanup() { server.shutdown() }
}
