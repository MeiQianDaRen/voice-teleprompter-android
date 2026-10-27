package com.olerast.suflyor

import com.olerast.suflyor.data.OnlineCatalog
import com.olerast.suflyor.data.OnlineLibraryException
import com.olerast.suflyor.data.OnlineProblem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class OnlineCatalogTest {
    private fun script(id: Any = "intro", content: Any = "大家好，今天介绍提词器。") = JSONObject()
        .put("id", id).put("title", "开场稿").put("content", content).put("speechLang", "zh")

    private fun catalog(vararg scripts: JSONObject): String =
        JSONObject().put("version", 1).put("scripts", JSONArray(scripts.toList())).toString()

    private fun fails(problem: OnlineProblem, action: () -> Unit): OnlineLibraryException {
        try { action() } catch (e: OnlineLibraryException) {
            assertEquals(problem, e.problem)
            return e
        }
        throw AssertionError("Expected $problem")
    }

    @Test
    fun readsChineseMarkdownWithoutChangingTheBody() {
        val content = "# 标题\n\n**大家好**！\n[停顿] 请看镜头。"
        val entry = OnlineCatalog.parse(catalog(script(content = content))).single()
        assertEquals("intro", entry.id)
        assertEquals("开场稿", entry.title)
        assertEquals(content, entry.content)
        assertEquals("zh", entry.speechLang)
        assertEquals(listOf(entry), OnlineCatalog.parse("\uFEFF" + catalog(script(content = content))))
    }

    @Test
    fun permitsEmptyCatalogAndDefaultSpeechLanguage() {
        assertTrue(OnlineCatalog.parse(catalog()).isEmpty())
        for (value in listOf(null, "auto", JSONObject.NULL)) {
            val script = script().apply { if (value == null) remove("speechLang") else put("speechLang", value) }
            assertEquals(null, OnlineCatalog.parse(catalog(script)).single().speechLang)
        }
        for (code in listOf("zh", "en", "ru")) {
            assertEquals(code, OnlineCatalog.parse(catalog(script().put("speechLang", code))).single().speechLang)
        }
    }

    @Test
    fun rejectsMalformedWrappersAndTrailingData() {
        for (text in listOf("", "[]", "not json", "{}", catalog() + " hidden", "{\"version\":1,\"scripts\":null}")) {
            val expected = if (text == "{}") OnlineProblem.VERSION else OnlineProblem.FORMAT
            fails(expected) { OnlineCatalog.parse(text) }
        }
        for (version in listOf("\"1\"", "0", "2", "1.0")) {
            fails(OnlineProblem.VERSION) { OnlineCatalog.parse("{\"version\":$version,\"scripts\":[]}") }
        }
    }

    @Test
    fun validatesEntriesBeforeAnyCacheWrites() {
        fails(OnlineProblem.ENTRY) { OnlineCatalog.parse(catalog(script(), script())) }
        fails(OnlineProblem.ENTRY) { OnlineCatalog.parse(catalog(script(42))) }
        fails(OnlineProblem.ENTRY) { OnlineCatalog.parse(catalog(script(content = 12))) }
        fails(OnlineProblem.ENTRY) { OnlineCatalog.parse(catalog(script(content = "  "))) }
        fails(OnlineProblem.ENTRY) { OnlineCatalog.parse(catalog(script("..\n../"))) }
        fails(OnlineProblem.ENTRY) { OnlineCatalog.parse(catalog(script().put("speechLang", "fr"))) }
        fails(OnlineProblem.ENTRY) { OnlineCatalog.parse(catalog(script().put("title", "x".repeat(201)))) }
        fails(OnlineProblem.ENTRY) { OnlineCatalog.parse(catalog(script(content = "中".repeat(OnlineCatalog.MAX_CONTENT_CHARS + 1)))) }
    }

    @Test
    fun enforcesCatalogSizeAndScriptCount() {
        fails(OnlineProblem.COUNT) {
            OnlineCatalog.parse(catalog(*(0..OnlineCatalog.MAX_SCRIPTS).map { script("s-$it") }.toTypedArray()))
        }
        fails(OnlineProblem.SIZE) { OnlineCatalog.parse(" ".repeat(OnlineCatalog.MAX_BYTES + 1)) }
    }

    @Test
    fun normalizesOnlyHttpAddresses() {
        assertEquals("https://example.com/scripts.json", OnlineCatalog.normalizeUrl(" https://example.com/a/../scripts.json "))
        assertEquals("http://127.0.0.1:8080/scripts", OnlineCatalog.normalizeUrl("http://127.0.0.1:8080/scripts"))
        for (url in listOf("", "/scripts", "file:///sdcard/a", "ftp://example.com/a", "https://u:p@example.com/a", "https://example.com/a#part", "http://example.com:0/a", "http://example.com:65536/a")) {
            fails(OnlineProblem.URL) { OnlineCatalog.normalizeUrl(url) }
        }
    }

    @Test
    fun cacheIdsAreStableAndCannotEscapeTheLibraryDirectory() {
        val first = OnlineCatalog.localId("https://example.com/scripts", "../../同一个稿件")
        assertEquals(first, OnlineCatalog.localId(" https://example.com/scripts ", "../../同一个稿件"))
        assertTrue(first.matches(Regex("online-[0-9a-f]{32}")))
        assertNotEquals(first, OnlineCatalog.localId("https://other.example/scripts", "../../同一个稿件"))
        assertNotEquals(first, OnlineCatalog.localId("https://example.com/scripts", "another-id"))
    }

    private data class Reply(val status: Int = 200, val body: ByteArray = byteArrayOf(), val headers: String = "", val delay: Long = 0)

    private class Server(private val reply: (String) -> Reply) : AutoCloseable {
        private val socket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${socket.localPort}"
        val requests = CopyOnWriteArrayList<String>()
        private val worker = thread(isDaemon = true) {
            while (!socket.isClosed) {
                runCatching {
                    socket.accept().use { client ->
                        client.soTimeout = 2000
                        val reader = client.getInputStream().bufferedReader()
                        val line = reader.readLine()
                        requests += line
                        while (!reader.readLine().isNullOrEmpty()) Unit
                        val response = reply(line.split(' ')[1])
                        if (response.delay > 0) Thread.sleep(response.delay)
                        val head = "HTTP/1.1 ${response.status} Status\r\nConnection: close\r\n${response.headers}\r\n"
                        client.getOutputStream().write(head.toByteArray(Charsets.US_ASCII))
                        client.getOutputStream().write(response.body)
                    }
                }
            }
        }
        override fun close() { socket.close(); worker.join(1500) }
    }

    @Test
    fun fetchUsesGetAndFollowsRelativeRedirects() {
        Server { path ->
            if (path == "/redirect") Reply(302, headers = "Location: /catalog\r\n")
            else Reply(body = catalog(script()).toByteArray(Charsets.UTF_8))
        }.use { server ->
            assertEquals("开场稿", OnlineCatalog.fetch(server.url + "/redirect").single().title)
            assertEquals(listOf("GET /redirect HTTP/1.1", "GET /catalog HTTP/1.1"), server.requests.toList())
        }
    }

    @Test
    fun distinguishesHttpErrorsAndNeverQuotesTheServerBody() {
        Server { Reply(503, "private server detail".toByteArray()) }.use { server ->
            val error = fails(OnlineProblem.HTTP) { OnlineCatalog.fetch(server.url) }
            assertEquals(503, error.status)
            assertEquals("HTTP", error.message)
        }
    }

    @Test
    fun rejectsOversizedResponsesBadUtf8AndRedirectLoops() {
        Server { Reply(headers = "Content-Length: ${OnlineCatalog.MAX_BYTES + 1}\r\n") }.use { server ->
            fails(OnlineProblem.SIZE) { OnlineCatalog.fetch(server.url) }
        }
        Server { Reply(body = byteArrayOf(0xc3.toByte(), 0x28)) }.use { server ->
            fails(OnlineProblem.FORMAT) { OnlineCatalog.fetch(server.url) }
        }
        Server { Reply(302, headers = "Location: /again\r\n") }.use { server ->
            fails(OnlineProblem.NETWORK) { OnlineCatalog.fetch(server.url) }
            assertEquals(6, server.requests.size)
        }
    }

    @Test
    fun reportsTimeoutSeparatelyFromHttpErrors() {
        Server { Reply(body = catalog().toByteArray(), delay = 500) }.use { server ->
            fails(OnlineProblem.TIMEOUT) { OnlineCatalog.fetch(server.url, readTimeoutMs = 100) }
        }
    }
}
