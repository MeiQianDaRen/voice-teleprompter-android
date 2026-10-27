package com.olerast.suflyor.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Locale

enum class OnlineProblem { URL, NETWORK, TIMEOUT, HTTP, FORMAT, VERSION, SIZE, COUNT, ENTRY, STORAGE }

class OnlineLibraryException(val problem: OnlineProblem, val status: Int = 0) : IOException(problem.name)

/** Versioned, bounded UTF-8 JSON catalog. Fetching never sends microphone audio or local scripts. */
object OnlineCatalog {
    const val MAX_BYTES = 8 * 1024 * 1024
    const val MAX_SCRIPTS = 500
    const val MAX_CONTENT_CHARS = 100_000

    data class Entry(val id: String, val title: String, val content: String, val speechLang: String?)

    fun normalizeUrl(value: String): String {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: throw OnlineLibraryException(OnlineProblem.URL)
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("https", "http") || uri.host.isNullOrBlank() ||
            uri.rawUserInfo != null || uri.rawFragment != null || uri.port !in -1..65535 || uri.port == 0
        ) throw OnlineLibraryException(OnlineProblem.URL)
        return uri.normalize().toASCIIString()
    }

    /** Fixed safe file id: the same source URL + remote id updates a cached script instead of duplicating it. */
    fun localId(source: String, id: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest((normalizeUrl(source) + "\n" + id).toByteArray(Charsets.UTF_8))
        return "online-" + hash.take(16).joinToString("") { "%02x".format(it) }
    }

    fun parse(text: String): List<Entry> {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw OnlineLibraryException(OnlineProblem.SIZE)
        try {
            val tokener = JSONTokener(text.removePrefix("\uFEFF"))
            val root = tokener.nextValue() as? JSONObject ?: throw OnlineLibraryException(OnlineProblem.FORMAT)
            if (tokener.nextClean() != '\u0000') throw OnlineLibraryException(OnlineProblem.FORMAT)
            if (root.opt("version") != 1) throw OnlineLibraryException(OnlineProblem.VERSION)
            val scripts = root.opt("scripts") as? JSONArray ?: throw OnlineLibraryException(OnlineProblem.FORMAT)
            if (scripts.length() > MAX_SCRIPTS) throw OnlineLibraryException(OnlineProblem.COUNT)
            val ids = HashSet<String>()
            return (0 until scripts.length()).map { i ->
                val script = scripts.opt(i) as? JSONObject ?: throw OnlineLibraryException(OnlineProblem.ENTRY)
                fun field(name: String, max: Int): String {
                    val value = script.opt(name) as? String ?: throw OnlineLibraryException(OnlineProblem.ENTRY)
                    if (value.isBlank() || value.length > max) throw OnlineLibraryException(OnlineProblem.ENTRY)
                    return value
                }
                val id = field("id", 128).trim()
                if (id.any { it.isISOControl() } || !ids.add(id)) throw OnlineLibraryException(OnlineProblem.ENTRY)
                val title = field("title", 200).trim()
                val content = field("content", MAX_CONTENT_CHARS)
                val language = if (!script.has("speechLang") || script.isNull("speechLang")) null else {
                    when (val code = script.opt("speechLang")) {
                        "auto" -> null
                        "zh", "en", "ru" -> code as String
                        else -> throw OnlineLibraryException(OnlineProblem.ENTRY)
                    }
                }
                Entry(id, title, content, language)
            }
        } catch (e: OnlineLibraryException) {
            throw e
        } catch (_: Exception) {
            // JSON exceptions can quote the response body. Don't expose server content in logs or error messages.
            throw OnlineLibraryException(OnlineProblem.FORMAT)
        }
    }

    fun fetch(source: String, connectTimeoutMs: Int = 10_000, readTimeoutMs: Int = 15_000): List<Entry> {
        var url = URI(normalizeUrl(source))
        val deadline = System.nanoTime() + 30_000_000_000L
        try {
            repeat(6) { redirects ->
                if (System.nanoTime() > deadline) throw SocketTimeoutException()
                val connection = url.toURL().openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "application/json")
                    connection.connectTimeout = connectTimeoutMs
                    connection.readTimeout = readTimeoutMs
                    connection.instanceFollowRedirects = false
                    connection.useCaches = false
                    val status = connection.responseCode
                    if (status in setOf(301, 302, 303, 307, 308)) {
                        if (redirects == 5) throw OnlineLibraryException(OnlineProblem.NETWORK)
                        val location = connection.getHeaderField("Location") ?: throw OnlineLibraryException(OnlineProblem.HTTP, status)
                        val next = URI(normalizeUrl(url.resolve(location).toString()))
                        if (url.scheme.equals("https", true) && !next.scheme.equals("https", true)) {
                            throw OnlineLibraryException(OnlineProblem.URL)
                        }
                        url = next
                    } else {
                        if (status != HttpURLConnection.HTTP_OK) throw OnlineLibraryException(OnlineProblem.HTTP, status)
                        if (connection.contentLengthLong > MAX_BYTES) throw OnlineLibraryException(OnlineProblem.SIZE)
                        val bytes = ByteArrayOutputStream()
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                if (System.nanoTime() > deadline) throw SocketTimeoutException()
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (bytes.size() + count > MAX_BYTES) throw OnlineLibraryException(OnlineProblem.SIZE)
                                bytes.write(buffer, 0, count)
                            }
                        }
                        val decoder = Charsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                        val text = try { decoder.decode(ByteBuffer.wrap(bytes.toByteArray())).toString() } catch (_: Exception) {
                            throw OnlineLibraryException(OnlineProblem.FORMAT)
                        }
                        return parse(text)
                    }
                } finally {
                    connection.disconnect()
                }
            }
            throw OnlineLibraryException(OnlineProblem.NETWORK)
        } catch (e: OnlineLibraryException) {
            throw e
        } catch (_: SocketTimeoutException) {
            throw OnlineLibraryException(OnlineProblem.TIMEOUT)
        } catch (_: Exception) {
            throw OnlineLibraryException(OnlineProblem.NETWORK)
        }
    }
}
