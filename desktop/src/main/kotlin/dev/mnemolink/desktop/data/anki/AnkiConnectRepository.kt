package dev.mnemolink.desktop.data.anki

import java.io.IOException
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

/** Read-only API verified against the archived FooSoft/anki-connect plugin/__init__.py and web.py. */
class AnkiConnectRepository(
    private val apiKey: String = "",
    endpoint: String = "http://127.0.0.1:8765/",
    callTimeoutMillis: Long = 10_000
) : AnkiRepository {
    private val url = loopbackUrl(endpoint)
    private val closed = AtomicBoolean(false)
    private val client = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .callTimeout(checkedTimeout(callTimeoutMillis), TimeUnit.MILLISECONDS)
        .build()

    override suspend fun version(): Int {
        val value = integer(request("version"))
        if (value < 0 || value > Int.MAX_VALUE) fail(AnkiFailureKind.Protocol)
        return value.toInt()
    }

    override suspend fun searchNotes(query: String): NoteSearchResult {
        if (query.isBlank() || query.length > 512) fail(AnkiFailureKind.Rejected)
        val result = request("findNotes", buildJsonObject { put("query", query) }) as? JsonArray
            ?: fail(AnkiFailureKind.Protocol)
        val ids = result.map { positiveId(it) }
        if (ids.toSet().size != ids.size) fail(AnkiFailureKind.Protocol)
        if (ids.isEmpty()) return NoteSearchResult(emptyList(), 0)
        // findNotes has no backend pagination; never fetch more than 20 note bodies.
        return NoteSearchResult(notesInfo(ids.take(20)).filterNotNull(), ids.size)
    }

    override suspend fun getNote(noteId: Long): AnkiNote {
        if (noteId <= 0) fail(AnkiFailureKind.Rejected)
        return notesInfo(listOf(noteId)).single() ?: fail(AnkiFailureKind.NotFound)
    }

    private suspend fun notesInfo(ids: List<Long>): List<AnkiNote?> {
        val result = request(
            "notesInfo",
            buildJsonObject {
                put("notes", JsonArray(ids.map { JsonPrimitive(it) }))
            }
        ) as? JsonArray ?: fail(AnkiFailureKind.Protocol)
        if (result.size != ids.size) fail(AnkiFailureKind.Protocol)
        val seen = mutableSetOf<Long>()
        return result.mapIndexed { index, element ->
            val note = element as? JsonObject ?: fail(AnkiFailureKind.Protocol)
            if (note.isEmpty()) return@mapIndexed null
            val id = positiveId(note["noteId"])
            if (id != ids[index] || !seen.add(id)) fail(AnkiFailureKind.Protocol)
            val model = text(note["modelName"]).also {
                if (it.isBlank()) fail(AnkiFailureKind.Protocol)
            }
            val fields = note["fields"] as? JsonObject ?: fail(AnkiFailureKind.Protocol)
            val ordered = fields.map { (name, element) ->
                if (name.isBlank()) fail(AnkiFailureKind.Protocol)
                val field = element as? JsonObject ?: fail(AnkiFailureKind.Protocol)
                val order = integer(field["order"])
                if (order < 0 || order >= fields.size) fail(AnkiFailureKind.Protocol)
                AnkiField(name, text(field["value"]), order.toInt())
            }.sortedBy { it.order }
            if (ordered.map { it.order } != ordered.indices.toList()) fail(AnkiFailureKind.Protocol)
            AnkiNote(id, model, ordered)
        }
    }

    private suspend fun request(
        action: String,
        params: JsonObject = JsonObject(emptyMap())
    ): JsonElement {
        if (closed.get()) fail(AnkiFailureKind.Unavailable)
        val envelope = buildJsonObject {
            put("action", action)
            put("version", 6)
            put("params", params)
            if (apiKey.isNotBlank()) put("key", apiKey)
        }
        val request = Request.Builder().url(url)
            .post(
                envelope.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            )
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            if (closed.get()) call.cancel()
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(
                        AnkiConnectException(AnkiFailureKind.Unavailable)
                    )
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        // Consume on OkHttp's worker, not the caller's coroutine dispatcher. The
                        // call timeout remains active until the body is consumed or closed.
                        val result = response.use {
                            if (!continuation.isActive) return
                            if (!it.isSuccessful) fail(AnkiFailureKind.Rejected)
                            val body = it.body ?: fail(AnkiFailureKind.Protocol)
                            if (body.contentLength() >
                                MAX_BODY_BYTES
                            ) {
                                fail(AnkiFailureKind.TooLarge)
                            }
                            val buffer = Buffer()
                            val source = body.source()
                            while (true) {
                                val read = source.read(
                                    buffer,
                                    minOf(
                                        8192L,
                                        MAX_BODY_BYTES + 1 - buffer.size
                                    )
                                )
                                if (read == -1L) break
                                if (buffer.size > MAX_BODY_BYTES) fail(AnkiFailureKind.TooLarge)
                            }
                            parseEnvelope(buffer.readUtf8())
                        }
                        continuation.resume(result)
                    } catch (e: AnkiConnectException) {
                        continuation.resumeWithException(e)
                    } catch (_: IOException) {
                        continuation.resumeWithException(
                            AnkiConnectException(AnkiFailureKind.Unavailable)
                        )
                    } catch (_: SerializationException) {
                        continuation.resumeWithException(
                            AnkiConnectException(AnkiFailureKind.Protocol)
                        )
                    } catch (_: IllegalArgumentException) {
                        continuation.resumeWithException(
                            AnkiConnectException(AnkiFailureKind.Protocol)
                        )
                    }
                }
            })
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    override fun toString(): String = "AnkiConnectRepository"

    private fun parseEnvelope(body: String): JsonElement {
        val envelope =
            Json.parseToJsonElement(body) as? JsonObject ?: fail(AnkiFailureKind.Protocol)
        if (!envelope.containsKey("result") ||
            !envelope.containsKey("error")
        ) {
            fail(AnkiFailureKind.Protocol)
        }
        val error = envelope.getValue("error")
        if (error != JsonNull) {
            val message = text(error)
            fail(
                if (message.contains(
                        "api key",
                        ignoreCase = true
                    )
                ) {
                    AnkiFailureKind.Authentication
                } else {
                    AnkiFailureKind.Rejected
                }
            )
        }
        return envelope.getValue("result")
    }

    private companion object {
        const val MAX_BODY_BYTES = 1024L * 1024

        fun fail(kind: AnkiFailureKind): Nothing = throw AnkiConnectException(kind)

        fun text(element: JsonElement?): String {
            val primitive = element as? JsonPrimitive ?: fail(AnkiFailureKind.Protocol)
            if (!primitive.isString) fail(AnkiFailureKind.Protocol)
            return primitive.content
        }

        fun integer(element: JsonElement?): Long {
            val primitive = element as? JsonPrimitive ?: fail(AnkiFailureKind.Protocol)
            if (primitive.isString ||
                !primitive.content.matches(Regex("-?(0|[1-9][0-9]*)"))
            ) {
                fail(AnkiFailureKind.Protocol)
            }
            return primitive.longOrNull ?: fail(AnkiFailureKind.Protocol)
        }

        fun positiveId(element: JsonElement?): Long = integer(element).also {
            if (it <= 0) fail(AnkiFailureKind.Protocol)
        }

        fun checkedTimeout(value: Long): Long {
            if (value <= 0 || value > Int.MAX_VALUE) fail(AnkiFailureKind.Rejected)
            return value
        }

        fun loopbackUrl(endpoint: String): HttpUrl {
            val uri = try {
                URI(endpoint)
            } catch (_: Exception) {
                fail(AnkiFailureKind.Rejected)
            }
            if (!uri.scheme.equals("http", ignoreCase = true) ||
                uri.rawUserInfo != null ||
                uri.rawAuthority?.contains('@') == true ||
                uri.rawQuery != null ||
                uri.rawFragment != null ||
                uri.rawPath !in listOf("", "/")
            ) {
                fail(AnkiFailureKind.Rejected)
            }
            val host = uri.host ?: fail(AnkiFailureKind.Rejected)
            val url = endpoint.toHttpUrlOrNull() ?: fail(AnkiFailureKind.Rejected)
            val ipv4 = host.split('.')
            val isIpv4Loopback = ipv4.size == 4 &&
                ipv4[0] == "127" &&
                ipv4.all {
                    it.matches(Regex("0|[1-9][0-9]{0,2}")) && it.toInt() <= 255
                }
            if (host.equals(
                    "localhost",
                    ignoreCase = true
                )
            ) {
                return url.newBuilder().host("127.0.0.1").build()
            }
            if (!isIpv4Loopback && url.host != "::1") fail(AnkiFailureKind.Rejected)
            return url
        }
    }
}
