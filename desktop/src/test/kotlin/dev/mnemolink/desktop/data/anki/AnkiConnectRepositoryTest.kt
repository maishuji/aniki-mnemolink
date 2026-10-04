package dev.mnemolink.desktop.data.anki

import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnkiConnectRepositoryTest {
    private lateinit var server: MockWebServer
    private val repositories = mutableListOf<AnkiConnectRepository>()

    @Before fun start() {
        server = MockWebServer()
        server.start(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0)
    }

    @After fun stop() {
        repositories.forEach { it.close() }
        server.shutdown()
    }

    private fun repository(
        key: String = "",
        timeout: Long = 2_000,
        endpoint: String = server.url("/").toString()
    ) = AnkiConnectRepository(key, endpoint, timeout).also { repositories.add(it) }

    private fun enqueue(result: String) {
        server.enqueue(MockResponse().setBody("{\"result\":$result,\"error\":null}"))
    }

    private fun note(
        id: Long = 1,
        fields: String = "{\"Front\":{\"value\":\"text\",\"order\":0}}",
        model: String = "\"Basic\""
    ) = "{\"noteId\":$id,\"modelName\":$model,\"fields\":$fields,\"tags\":[],\"cards\":[10]}"

    private fun request(): JsonObject {
        val request = server.takeRequest(2, TimeUnit.SECONDS) ?: error("Expected local request")
        assertEquals("POST", request.method)
        assertEquals("/", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        val json = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals(JsonPrimitive(6), json["version"])
        assertTrue(json["params"] is JsonObject)
        return json
    }

    private fun failure(kind: AnkiFailureKind, block: suspend () -> Unit): AnkiConnectException =
        runBlocking {
            try {
                block()
                error("Expected failure $kind")
            } catch (e: AnkiConnectException) {
                assertEquals(kind, e.kind)
                assertEquals("AnkiConnect request could not be completed.", e.message)
                assertNull(e.cause)
                e
            }
        }

    @Test fun versionEnvelope() = runBlocking {
        enqueue("6")
        assertEquals(6, repository().version())
        val request = request()
        assertEquals(JsonPrimitive("version"), request["action"])
        assertEquals(JsonObject(emptyMap()), request["params"])
        assertFalse(request.containsKey("key"))
    }

    @Test fun oldVersionIsReturnedForControllerToCheck() = runBlocking {
        enqueue("5")
        assertEquals(5, repository().version())
    }

    @Test fun nonblankKeyIsSentUnmodifiedButNotPrinted() = runBlocking {
        enqueue("6")
        val repository = repository(" secret-key ")
        repository.version()
        assertEquals(JsonPrimitive(" secret-key "), request()["key"])
        assertFalse(repository.toString().contains("secret-key"))
    }

    @Test fun whitespaceKeyIsOmitted() = runBlocking {
        enqueue("6")
        repository(" \t\n").version()
        assertFalse(request().containsKey("key"))
    }

    @Test fun authenticationFailureIsRedacted() {
        server.enqueue(
            MockResponse().setBody(
                """{"result":null,"error":"Valid API KEY secret-key must be provided"}"""
            )
        )
        val error = failure(AnkiFailureKind.Authentication) { repository("secret-key").version() }
        assertFalse(error.toString().contains("secret-key"))
    }

    @Test fun serverRejectionDoesNotLeakQueryOrContent() {
        server.enqueue(
            MockResponse().setBody(
                """{"result":null,"error":"private-query private-note private-key"}"""
            )
        )
        val error =
            failure(AnkiFailureKind.Rejected) {
                repository("private-key").searchNotes("private-query")
            }
        assertFalse(error.toString().contains("private"))
    }

    @Test fun searchCapsBodiesAtTwentyAndPreservesTotalAndRawValues() = runBlocking {
        enqueue((1..25).joinToString(prefix = "[", postfix = "]"))
        val fields =
            """{"Back":{"value":"","order":1},"Front":{"value":"<b>raw</b>  ","order":0}}"""
        enqueue((1L..20L).joinToString(prefix = "[", postfix = "]") { note(it, fields) })
        val result = repository().searchNotes("deck:Japanese")
        assertEquals(25, result.totalMatches)
        assertEquals(20, result.notes.size)
        assertEquals(
            listOf(AnkiField("Front", "<b>raw</b>  ", 0), AnkiField("Back", "", 1)),
            result.notes.first().fields
        )
        val find = request()
        assertEquals(JsonPrimitive("findNotes"), find["action"])
        assertEquals(JsonPrimitive("deck:Japanese"), find["params"]!!.jsonObject["query"])
        val info = request()
        assertEquals(JsonPrimitive("notesInfo"), info["action"])
        assertEquals(
            JsonArray(
                (1L..20L).map {
                    JsonPrimitive(it)
                }
            ),
            info["params"]!!.jsonObject["notes"]
        )
        assertEquals(2, server.requestCount)
    }

    @Test fun emptySearchDoesNotRequestBodies() = runBlocking {
        enqueue("[]")
        assertEquals(NoteSearchResult(emptyList(), 0), repository().searchNotes("tag:none"))
        assertEquals(1, server.requestCount)
    }

    @Test fun deletedSearchNotesAreSkippedWithoutChangingTotal() = runBlocking {
        enqueue("[1,2,3]")
        enqueue("[${note(1)},{},${note(3)}]")
        val result = repository().searchNotes("tag:x")
        assertEquals(3, result.totalMatches)
        assertEquals(listOf(1L, 3L), result.notes.map { it.noteId })
    }

    @Test fun getNoteSendsOnlyRequestedId() = runBlocking {
        enqueue("[${note(9)}]")
        assertEquals(9L, repository().getNote(9).noteId)
        val request = request()
        assertEquals(JsonPrimitive("notesInfo"), request["action"])
        assertEquals(JsonArray(listOf(JsonPrimitive(9))), request["params"]!!.jsonObject["notes"])
    }

    @Test fun deletedSingleNoteIsNotFound() {
        enqueue("[{}]")
        failure(AnkiFailureKind.NotFound) { repository().getNote(1) }
    }

    @Test fun invalidQueriesNeverSendRequests() {
        val repository = repository()
        listOf("", " \t\n", "a".repeat(513)).forEach { query ->
            failure(AnkiFailureKind.Rejected) { repository.searchNotes(query) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun maximumLengthQueryIsAcceptedWithoutTrimming() = runBlocking {
        enqueue("[]")
        val query = " " + "a".repeat(511)
        repository().searchNotes(query)
        assertEquals(JsonPrimitive(query), request()["params"]!!.jsonObject["query"])
    }

    @Test fun invalidInputNoteIdsNeverSendRequests() {
        listOf(0L, -1L, Long.MIN_VALUE).forEach { id ->
            failure(AnkiFailureKind.Rejected) { repository().getNote(id) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun invalidFindIdsAreProtocolFailures() {
        listOf(
            "[0]", "[-1]", "[1,1]", "[\"1\"]", "[1.0]", "[true]", "[null]",
            "[9223372036854775808]", "[1e3]", "{}"
        ).forEach {
            enqueue(it)
            failure(AnkiFailureKind.Protocol) { repository().searchNotes("tag:x") }
        }
    }

    @Test fun allFindIdsAreValidatedEvenBeyondCap() {
        enqueue((1..20).joinToString(prefix = "[", postfix = ",0]"))
        failure(AnkiFailureKind.Protocol) { repository().searchNotes("tag:x") }
        assertEquals(1, server.requestCount)
    }

    @Test fun malformedEnvelopesAreProtocolFailures() {
        listOf(
            "{}", "[]", "null", "not-json", "{\"result\":6}", "{\"error\":null}",
            "{\"result\":6,\"error\":false}",
            "{\"result\":6,\"error\":{}}",
            "{\"result\":6,\"error\":12}"
        ).forEach {
            server.enqueue(MockResponse().setBody(it))
            failure(AnkiFailureKind.Protocol) { repository().version() }
        }
    }

    @Test fun invalidVersionsAreProtocolFailures() {
        listOf("\"6\"", "null", "true", "6.0", "-1", "2147483648", "[]").forEach {
            enqueue(it)
            failure(AnkiFailureKind.Protocol) { repository().version() }
        }
    }

    @Test fun unrequestedAndDuplicateAndReorderedIdentitiesAreRejected() {
        listOf(
            "[${note(3)},${note(2)}]",
            "[${note(1)},${note(1)}]",
            "[${note(2)},${note(1)}]"
        ).forEach {
            enqueue("[1,2]")
            enqueue(it)
            failure(AnkiFailureKind.Protocol) { repository().searchNotes("tag:x") }
        }
    }

    @Test fun missingExtraAndMalformedNoteEntriesAreRejected() {
        listOf(
            "[]", "[{},{}]", "[null]", "[true]", "{}", "[{\"noteId\":1}]",
            "[{\"noteId\":\"1\",\"modelName\":\"Basic\",\"fields\":{}}]",
            "[${note(0)}]", "[${note(2)}]"
        ).forEach {
            enqueue(it)
            failure(AnkiFailureKind.Protocol) { repository().getNote(1) }
        }
    }

    @Test fun invalidModelNamesAreRejected() {
        listOf("\" \"", "null", "42", "{}").forEach {
            enqueue("[${note(model = it)}]")
            failure(AnkiFailureKind.Protocol) { repository().getNote(1) }
        }
    }

    @Test fun invalidFieldSchemasAreRejected() {
        listOf(
            "null", "[]", "{\" \":{\"value\":\"x\",\"order\":0}}",
            "{\"A\":{\"value\":null,\"order\":0}}", "{\"A\":{\"value\":1,\"order\":0}}",
            "{\"A\":{\"order\":0}}", "{\"A\":{\"value\":\"x\"}}", "{\"A\":null}",
            "{\"A\":{\"value\":\"x\",\"order\":\"0\"}}", "{\"A\":{\"value\":\"x\",\"order\":0.0}}",
            "{\"A\":{\"value\":\"x\",\"order\":-1}}", "{\"A\":{\"value\":\"x\",\"order\":1}}",
            "{\"A\":{\"value\":\"x\",\"order\":0},\"B\":{\"value\":\"\",\"order\":0}}"
        ).forEach {
            enqueue("[${note(fields = it)}]")
            failure(AnkiFailureKind.Protocol) { repository().getNote(1) }
        }
    }

    @Test fun loopbackUrlPolicyRejectsUnsafeAndNormalizedPaths() {
        listOf(
            "https://127.0.0.1/", "http://example.com/", "http://localhost.example/",
            "http://192.168.0.1/",
            "http://0.0.0.0/", "http://[::2]/", "http://user:secret@127.0.0.1/",
            "http://@127.0.0.1/",
            "http://127.0.0.1/?", "http://127.0.0.1/#", "http://127.0.0.1/a",
            "http://127.0.0.1/a/../",
            "http://127.0.0.1/%2f", "http://127.1/", "http://2130706433/", "not a URL"
        ).forEach {
            failure(AnkiFailureKind.Rejected) { repository(endpoint = it) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun localhostIsCanonicalizedToLiteral() = runBlocking {
        enqueue("6")
        repository(endpoint = "http://localhost:${server.port}/").version()
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("127.0.0.1:${server.port}", request.getHeader("Host"))
    }

    @Test fun ipv6AndOtherLiteralLoopbacksAreAcceptedWithoutConnecting() {
        repository(endpoint = "http://[::1]:8765/")
        repository(endpoint = "http://[0:0:0:0:0:0:0:1]:8765/")
        repository(endpoint = "http://127.0.0.2:8765/")
        assertEquals(0, server.requestCount)
    }

    @Test fun invalidTimeoutIsRejectedSafely() {
        listOf(0L, -1L, Long.MAX_VALUE).forEach {
            failure(AnkiFailureKind.Rejected) { repository(timeout = it) }
        }
    }

    @Test fun disconnectedServerIsUnavailableAndNotRetried() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        failure(AnkiFailureKind.Unavailable) { repository().version() }
        assertEquals(1, server.requestCount)
    }

    @Test fun callTimeoutCoversDelayedBody() {
        server.enqueue(
            MockResponse().setBody(
                "{\"result\":6,\"error\":null}"
            ).setBodyDelay(2, TimeUnit.SECONDS)
        )
        failure(AnkiFailureKind.Unavailable) { repository(timeout = 100).version() }
    }

    @Test fun redirectIsNotFollowed() {
        server.enqueue(
            MockResponse().setResponseCode(302).addHeader("Location", server.url("/redirect"))
        )
        failure(AnkiFailureKind.Rejected) { repository().version() }
        assertEquals(1, server.requestCount)
    }

    @Test fun httpErrorBodyIsNotExposed() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("private-key private-content"))
        failure(AnkiFailureKind.Rejected) { repository().version() }
    }

    @Test fun declaredOversizeIsRejected() {
        server.enqueue(MockResponse().setBody("x".repeat(1024 * 1024 + 1)))
        failure(AnkiFailureKind.TooLarge) { repository().version() }
    }

    @Test fun chunkedOversizeIsRejectedWhileStreaming() {
        server.enqueue(MockResponse().setChunkedBody("x".repeat(1024 * 1024 + 1), 4096))
        failure(AnkiFailureKind.TooLarge) { repository().version() }
    }

    @Test fun exactBodyLimitIsAccepted() = runBlocking {
        val body = "{\"result\":6,\"error\":null}"
        server.enqueue(
            MockResponse().setChunkedBody(body + " ".repeat(1024 * 1024 - body.length), 4096)
        )
        assertEquals(6, repository().version())
    }

    // Inspect the owned client's lifecycle without exposing a public injection surface.
    private fun client(repository: AnkiConnectRepository): OkHttpClient {
        val field = AnkiConnectRepository::class.java.getDeclaredField("client")
        field.isAccessible = true
        return field.get(repository) as OkHttpClient
    }

    @Test fun cancellationDuringBodyReadCancelsCallAndReleasesWorker() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                "{\"result\":6,\"error\":null}"
            ).setBodyDelay(2, TimeUnit.SECONDS)
        )
        val repository = repository()
        val idle = CountDownLatch(1)
        client(repository).dispatcher.idleCallback = Runnable { idle.countDown() }
        val job = async(Dispatchers.Default) { repository.version() }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(idle.await(2, TimeUnit.SECONDS))
        assertEquals(0, client(repository).dispatcher.runningCallsCount())
        enqueue("6")
        assertEquals(6, repository.version())
    }

    @Test fun malformedResponseIsClosedAndConnectionCanBeReused() = runBlocking {
        server.enqueue(MockResponse().setBody("malformed private-content"))
        enqueue("6")
        val repository = repository()
        failure(AnkiFailureKind.Protocol) { repository.version() }
        assertEquals(6, repository.version())
        assertEquals(0, server.takeRequest(2, TimeUnit.SECONDS)!!.sequenceNumber)
        assertEquals(1, server.takeRequest(2, TimeUnit.SECONDS)!!.sequenceNumber)
    }

    @Test fun closeCancelsInflightCallAndIsIdempotent() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val repository = repository()
        val job = async(Dispatchers.Default) {
            failure(AnkiFailureKind.Unavailable) { repository.version() }
        }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        repository.close()
        repository.close()
        job.await()
        assertTrue(client(repository).dispatcher.executorService.isShutdown)
        failure(AnkiFailureKind.Unavailable) { repository.version() }
        assertEquals(1, server.requestCount)
    }

    @Test fun cancellationBeforeResponseHeadersReleasesWorker() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val repository = repository()
        val idle = CountDownLatch(1)
        client(repository).dispatcher.idleCallback = Runnable { idle.countDown() }
        val job = async(Dispatchers.Default) { repository.version() }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(idle.await(2, TimeUnit.SECONDS))
        assertEquals(0, client(repository).dispatcher.runningCallsCount())
    }

    @Test fun oversizeResponseIsClosedAndSubsequentCallSucceeds() = runBlocking {
        server.enqueue(MockResponse().setChunkedBody("x".repeat(1024 * 1024 + 1), 4096))
        enqueue("6")
        val repository = repository()
        val idle = CountDownLatch(1)
        client(repository).dispatcher.idleCallback = Runnable { idle.countDown() }
        failure(AnkiFailureKind.TooLarge) { repository.version() }
        assertTrue(idle.await(2, TimeUnit.SECONDS))
        assertEquals(0, client(repository).dispatcher.runningCallsCount())
        assertEquals(6, repository.version())
    }

    @Test fun successfulResponseReturnsConnectionToPoolAndCloseEvictsIt() = runBlocking {
        enqueue("6")
        val repository = repository()
        val idle = CountDownLatch(1)
        client(repository).dispatcher.idleCallback = Runnable { idle.countDown() }
        repository.version()
        assertTrue(idle.await(2, TimeUnit.SECONDS))
        assertEquals(1, client(repository).connectionPool.idleConnectionCount())
        repository.close()
        assertEquals(0, client(repository).connectionPool.connectionCount())
    }

    @Test fun clientDisablesProxiesRedirectsAndRetries() {
        val client = client(repository())
        assertEquals(java.net.Proxy.NO_PROXY, client.proxy)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
    }
}
