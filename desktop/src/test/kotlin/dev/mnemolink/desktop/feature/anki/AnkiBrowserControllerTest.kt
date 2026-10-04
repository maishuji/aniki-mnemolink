package dev.mnemolink.desktop.feature.anki

import dev.mnemolink.desktop.data.anki.AnkiConnectException
import dev.mnemolink.desktop.data.anki.AnkiFailureKind
import dev.mnemolink.desktop.data.anki.AnkiField
import dev.mnemolink.desktop.data.anki.AnkiNote
import dev.mnemolink.desktop.data.anki.AnkiRepository
import dev.mnemolink.desktop.data.anki.NoteSearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnkiBrowserControllerTest {
    private fun note(id: Long = 1L, concept: String = "cat") = AnkiNote(
        id,
        "Basic",
        listOf(
            AnkiField("Concept", concept, 8),
            AnkiField("Context", "a pet", 3),
            AnkiField("Mnemonic", "", 0)
        )
    )

    private inner class FakeRepository : AnkiRepository {
        var versionCalls = 0
        val queries = mutableListOf<String>()
        val reads = mutableListOf<Long>()
        var closeCalls = 0
        var versionAction: suspend () -> Int = { 6 }
        var searchAction: suspend (
            String
        ) -> NoteSearchResult = { NoteSearchResult(listOf(note()), 1) }
        var readAction: suspend (Long) -> AnkiNote = { note(it) }
        override suspend fun version(): Int {
            versionCalls++
            return versionAction()
        }
        override suspend fun searchNotes(query: String): NoteSearchResult {
            queries += query
            return searchAction(query)
        }
        override suspend fun getNote(noteId: Long): AnkiNote {
            reads += noteId
            return readAction(noteId)
        }
        override fun close() {
            closeCalls++
        }
    }

    private fun TestScope.connected(repo: FakeRepository): AnkiBrowserController {
        val controller = AnkiBrowserController({ repo }, this)
        controller.connect()
        runCurrent()
        assertEquals(AnkiConnection.Connected, controller.state.value.connection)
        return controller
    }
    private fun TestScope.withResults(repo: FakeRepository): AnkiBrowserController =
        connected(repo).also {
            it.search()
            runCurrent()
        }
    private suspend fun <T> late(gate: CompletableDeferred<T>): T =
        withContext(NonCancellable) { gate.await() }

    @Test fun constructionDoesNotCreateRepositoryOrAccessAnki() = runTest {
        var creations = 0
        AnkiBrowserController({
            creations++
            FakeRepository()
        }, this).use {
            assertEquals(0, creations)
            assertEquals(AnkiBrowserState(), it.state.value)
            assertFalse(it.state.value.canSearch)
            assertNull(it.mappedInput())
        }
    }

    @Test fun connectPassesKeyAndDoesNotAutomaticallySearch() = runTest {
        val repo = FakeRepository()
        var key: String? = null
        AnkiBrowserController({
            key = it
            repo
        }, this).use {
            it.connect("private-key")
            assertEquals(AnkiConnection.Connecting, it.state.value.connection)
            runCurrent()
            assertEquals("private-key", key)
            assertEquals(6, it.state.value.apiVersion)
            assertEquals(1, repo.versionCalls)
            assertTrue(it.state.value.canSearch)
            assertTrue(repo.queries.isEmpty())
            assertTrue(repo.reads.isEmpty())
        }
        assertEquals(1, repo.closeCalls)
    }

    @Test fun connectionFailureNeverSurfacesKeyOrRawMessage() = runTest {
        val repo = FakeRepository().apply {
            versionAction =
                { error("private-key secret-note authentication failed") }
        }
        AnkiBrowserController({ repo }, this).use {
            it.connect("private-key")
            runCurrent()
            assertEquals(AnkiConnection.Disconnected, it.state.value.connection)
            assertNull(it.state.value.apiVersion)
            assertEquals(
                "Could not connect to Anki. Check AnkiConnect and its API key.",
                it.state.value.errorMessage
            )
            assertEquals(1, repo.closeCalls)
        }
    }

    @Test fun typedAuthenticationFailureIsSafeAndClosesRepository() = runTest {
        val repo = FakeRepository().apply {
            versionAction = { throw AnkiConnectException(AnkiFailureKind.Authentication) }
        }
        AnkiBrowserController({ repo }, this).use {
            it.connect("private-key")
            runCurrent()
            assertEquals(AnkiConnection.Disconnected, it.state.value.connection)
            assertEquals(
                "Could not connect to Anki. Check AnkiConnect and its API key.",
                it.state.value.errorMessage
            )
            assertEquals(1, repo.closeCalls)
        }
    }

    @Test fun selectionOfTheSamePendingNoteIsSingleFlight() = runTest {
        val gate = CompletableDeferred<AnkiNote>()
        val repo = FakeRepository().apply { readAction = { gate.await() } }
        withResults(repo).use {
            it.selectNote(1)
            it.selectNote(1)
            runCurrent()
            it.selectNote(1)
            assertEquals(listOf(1L), repo.reads)
            gate.complete(note())
            runCurrent()
            assertEquals(1L, it.state.value.selectedNote?.noteId)
        }
    }

    @Test fun factoryFailureIsSafe() = runTest {
        AnkiBrowserController({ error("secret-key") }, this).use {
            it.connect("secret-key")
            runCurrent()
            assertEquals(AnkiConnection.Disconnected, it.state.value.connection)
            assertFalse(it.state.value.errorMessage!!.contains("secret-key"))
        }
    }

    @Test fun versionsBelowSixAreRejectedAndRepositoryClosed() = runTest {
        val repo = FakeRepository().apply { versionAction = { 5 } }
        AnkiBrowserController({ repo }, this).use {
            it.connect()
            runCurrent()
            assertEquals(AnkiConnection.Disconnected, it.state.value.connection)
            assertEquals("AnkiConnect version 6 or newer is required.", it.state.value.errorMessage)
            assertFalse(it.state.value.canSearch)
            assertEquals(1, repo.closeCalls)
        }
    }

    @Test fun newerVersionsAreAccepted() = runTest {
        val repo = FakeRepository().apply { versionAction = { 8 } }
        connected(repo).use { assertEquals(8, it.state.value.apiVersion) }
    }

    @Test fun connectIsSingleFlight() = runTest {
        val gate = CompletableDeferred<Int>()
        val repo = FakeRepository().apply { versionAction = { gate.await() } }
        var creations = 0
        AnkiBrowserController({
            creations++
            repo
        }, this).use {
            it.connect()
            it.connect()
            runCurrent()
            it.connect("another-key")
            assertEquals(1, creations)
            assertEquals(1, repo.versionCalls)
            gate.complete(6)
            runCurrent()
            assertEquals(AnkiConnection.Connected, it.state.value.connection)
        }
    }

    @Test fun explicitSearchUsesExactQueryAndAdapterReportedCapAndTotal() = runTest {
        val repo = FakeRepository().apply {
            searchAction =
                { NoteSearchResult(listOf(note(7)), 200) }
        }
        connected(repo).use {
            it.updateQuery("deck:Demo tag:custom ")
            assertTrue(repo.queries.isEmpty())
            it.search()
            assertEquals(AnkiOperation.Searching, it.state.value.operation)
            runCurrent()
            assertEquals(listOf("deck:Demo tag:custom "), repo.queries)
            assertEquals(listOf(note(7)), it.state.value.results)
            assertEquals(200, it.state.value.totalMatches)
            assertNull(it.state.value.selectedNote)
        }
    }

    @Test fun searchIsSingleFlightAndBlankQueryCannotSearch() = runTest {
        val gate = CompletableDeferred<NoteSearchResult>()
        val repo = FakeRepository().apply { searchAction = { gate.await() } }
        connected(repo).use {
            it.search()
            it.search()
            runCurrent()
            it.search()
            assertEquals(1, repo.queries.size)
            gate.complete(NoteSearchResult(emptyList(), 0))
            runCurrent()
            it.updateQuery("  ")
            assertFalse(it.state.value.canSearch)
            it.search()
            assertEquals(1, repo.queries.size)
        }
    }

    @Test fun queryEditClearsResultsSelectionMappingAndTotal() = runTest {
        val repo = FakeRepository()
        withResults(repo).use {
            it.selectNote(1)
            runCurrent()
            assertNotNull(it.mappedInput())
            it.updateQuery("new query")
            assertTrue(it.state.value.results.isEmpty())
            assertNull(it.state.value.totalMatches)
            assertNull(it.state.value.selectedNote)
            assertNull(it.state.value.mapping)
            assertNull(it.mappedInput())
            assertEquals(1, repo.queries.size)
        }
    }

    @Test fun searchImmediatelyClearsOldSelection() = runTest {
        val repo = FakeRepository()
        withResults(repo).use {
            it.selectNote(1)
            runCurrent()
            it.search()
            assertNull(it.state.value.selectedNote)
            assertNull(it.state.value.mapping)
            assertNull(it.mappedInput())
            runCurrent()
        }
    }

    @Test fun selectionOnlyAcceptsCurrentResultIdsAndReadsFreshContent() = runTest {
        val repo = FakeRepository().apply { readAction = { note(it, "<b>fresh</b>") } }
        withResults(repo).use {
            it.selectNote(99)
            runCurrent()
            assertTrue(repo.reads.isEmpty())
            it.selectNote(1)
            assertEquals(AnkiOperation.LoadingNote, it.state.value.operation)
            runCurrent()
            assertEquals(listOf(1L), repo.reads)
            assertEquals("<b>fresh</b>", it.state.value.selectedNote?.fields?.first()?.value)
            assertEquals("fresh", it.mappedInput()?.concept)
            assertEquals("cat", it.state.value.results.first().fields.first().value)
        }
    }

    @Test fun freshFieldReorderingIsAcceptedWithoutOrdinalAssumptions() = runTest {
        val repo = FakeRepository().apply {
            readAction =
                { note(it).copy(fields = note(it).fields.reversed()) }
        }
        withResults(repo).use {
            it.selectNote(1)
            runCurrent()
            assertNotNull(it.state.value.selectedNote)
            it.chooseConcept("Concept")
            it.chooseContext("Context")
            assertEquals("cat", it.mappedInput()?.concept)
        }
    }

    @Test fun freshIdentityOrSchemaMismatchIsRejected() = runTest {
        for (fresh in listOf(
            note(2),
            note().copy(modelName = "Changed"),
            note().copy(fields = note().fields.take(2)),
            note().copy(fields = note().fields + note().fields.first())
        )) {
            val repo = FakeRepository().apply { readAction = { fresh } }
            withResults(repo).use {
                it.selectNote(1)
                runCurrent()
                assertNull(it.state.value.selectedNote)
                assertNull(it.state.value.mapping)
                assertEquals(
                    "The note or its field schema changed. Search again.",
                    it.state.value.errorMessage
                )
            }
        }
    }

    @Test fun searchAndFreshReadErrorsAreSafeAndRecoverable() = runTest {
        val repo = FakeRepository()
        connected(repo).use {
            repo.searchAction = { error("private note key") }
            it.search()
            runCurrent()
            assertEquals("Could not search Anki notes.", it.state.value.errorMessage)
            assertTrue(it.state.value.canSearch)
            repo.searchAction = { NoteSearchResult(listOf(note()), 1) }
            it.search()
            runCurrent()
            repo.readAction = { error("private note key") }
            it.selectNote(1)
            runCurrent()
            assertEquals("Could not load the selected Anki note.", it.state.value.errorMessage)
            assertNull(it.mappedInput())
            repo.readAction = { note(it) }
            it.selectNote(1)
            runCurrent()
            assertNull(it.state.value.errorMessage)
            assertNotNull(it.mappedInput())
        }
    }

    @Test fun mappingEditsRecomputeInputAndNeverInvokeRepository() = runTest {
        val repo = FakeRepository()
        withResults(repo).use {
            it.selectNote(1)
            runCurrent()
            assertTrue(it.state.value.canLoadInput)
            it.chooseConcept("Absent")
            assertNull(it.mappedInput())
            assertNotNull(it.state.value.mappingError)
            it.chooseConcept("Context")
            it.chooseContext("Concept")
            assertEquals("a pet", it.mappedInput()?.concept)
            assertEquals("cat", it.state.value.inputPreview?.context)
            it.chooseContext(null)
            assertEquals("", it.mappedInput()?.context)
            it.chooseDestination("Context")
            assertNull(it.mappedInput())
            assertEquals(1, repo.queries.size)
            assertEquals(listOf(1L), repo.reads)
        }
    }

    @Test fun missingDestinationIsExposedAsSetupError() = runTest {
        val missing = note().copy(fields = note().fields.take(2))
        val repo = FakeRepository().apply {
            searchAction = { NoteSearchResult(listOf(missing), 1) }
            readAction = { missing }
        }
        withResults(repo).use {
            it.selectNote(1)
            runCurrent()
            assertNull(it.state.value.mapping?.destinationField)
            assertTrue(it.state.value.mappingError!!.contains("Set up"))
            assertEquals("cat", it.state.value.inputPreview?.concept)
            assertFalse(it.state.value.canLoadInput)
            assertNull(it.mappedInput())
            it.chooseContext(null)
            it.chooseDestination("Context")
            assertNotNull(it.mappedInput())
        }
    }

    @Test fun lateSearchAfterQueryEditCannotRepopulateResultsOrError() = runTest {
        for (fail in listOf(false, true)) {
            val gate = CompletableDeferred<NoteSearchResult>()
            val repo = FakeRepository().apply { searchAction = { late(gate) } }
            connected(repo).use {
                it.search()
                runCurrent()
                it.updateQuery("edited")
                if (fail) {
                    gate.completeExceptionally(IllegalStateException("secret"))
                } else {
                    gate.complete(NoteSearchResult(listOf(note()), 1))
                }
                runCurrent()
                assertEquals("edited", it.state.value.query)
                assertTrue(it.state.value.results.isEmpty())
                assertNull(it.state.value.errorMessage)
                assertEquals(AnkiOperation.Idle, it.state.value.operation)
            }
        }
    }

    @Test fun lateSelectionCannotReplaceNewerSelection() = runTest {
        val gate = CompletableDeferred<AnkiNote>()
        val repo = FakeRepository().apply {
            searchAction = { NoteSearchResult(listOf(note(1), note(2)), 2) }
            readAction = { if (it == 1L) late(gate) else note(it, "newer") }
        }
        withResults(repo).use {
            it.selectNote(1)
            runCurrent()
            it.selectNote(2)
            runCurrent()
            gate.complete(note(1, "stale"))
            runCurrent()
            assertEquals(2L, it.state.value.selectedNote?.noteId)
            assertEquals("newer", it.mappedInput()?.concept)
        }
    }

    @Test fun lateSelectionAfterQueryEditIsIgnored() = runTest {
        val gate = CompletableDeferred<AnkiNote>()
        val repo = FakeRepository().apply { readAction = { late(gate) } }
        withResults(repo).use {
            it.selectNote(1)
            runCurrent()
            it.updateQuery("edited")
            gate.complete(note())
            runCurrent()
            assertNull(it.state.value.selectedNote)
            assertNull(it.state.value.mapping)
            assertNull(it.mappedInput())
        }
    }

    @Test fun lateSearchAfterDisconnectCannotRepopulateState() = runTest {
        val gate = CompletableDeferred<NoteSearchResult>()
        val repo = FakeRepository().apply { searchAction = { late(gate) } }
        connected(repo).use {
            it.search()
            runCurrent()
            it.disconnect()
            gate.complete(NoteSearchResult(listOf(note()), 1))
            runCurrent()
            assertEquals(AnkiBrowserState(), it.state.value)
            assertEquals(1, repo.closeCalls)
        }
    }

    @Test fun lateFreshReadAfterReconnectCannotReplaceNewConnectionState() = runTest {
        val gate = CompletableDeferred<AnkiNote>()
        val oldRepo = FakeRepository().apply { readAction = { late(gate) } }
        val newRepo = FakeRepository().apply { versionAction = { 9 } }
        var creations = 0
        AnkiBrowserController({ if (creations++ == 0) oldRepo else newRepo }, this).use {
            it.connect()
            runCurrent()
            it.search()
            runCurrent()
            it.selectNote(1)
            runCurrent()
            it.connect("new-key")
            runCurrent()
            gate.complete(note())
            runCurrent()
            assertEquals(9, it.state.value.apiVersion)
            assertNull(it.state.value.selectedNote)
            assertTrue(it.state.value.results.isEmpty())
            assertEquals(1, oldRepo.closeCalls)
        }
        assertEquals(1, newRepo.closeCalls)
    }

    @Test fun lateHandshakeCannotUndoDisconnectOrNewConnection() = runTest {
        val gate = CompletableDeferred<Int>()
        val oldRepo = FakeRepository().apply { versionAction = { late(gate) } }
        val newRepo = FakeRepository().apply { versionAction = { 8 } }
        var creations = 0
        AnkiBrowserController({ if (creations++ == 0) oldRepo else newRepo }, this).use {
            it.connect()
            runCurrent()
            it.disconnect()
            it.connect()
            runCurrent()
            gate.complete(5)
            runCurrent()
            assertEquals(AnkiConnection.Connected, it.state.value.connection)
            assertEquals(8, it.state.value.apiVersion)
            assertNull(it.state.value.errorMessage)
            assertEquals(1, oldRepo.closeCalls)
        }
    }

    @Test fun queryEditInvalidatesInFlightHandshake() = runTest {
        val gate = CompletableDeferred<Int>()
        val repo = FakeRepository().apply { versionAction = { late(gate) } }
        AnkiBrowserController({ repo }, this).use {
            it.connect()
            runCurrent()
            it.updateQuery("new")
            gate.complete(6)
            runCurrent()
            assertEquals(AnkiBrowserState(query = "new"), it.state.value)
            assertEquals(1, repo.closeCalls)
        }
    }

    @Test fun closeIsIdempotentAndDoesNotCancelCallerScopeOrAllowLateSearch() = runTest {
        val gate = CompletableDeferred<NoteSearchResult>()
        val repo = FakeRepository().apply { searchAction = { late(gate) } }
        val controller = connected(repo)
        controller.search()
        runCurrent()
        controller.close()
        controller.close()
        gate.complete(NoteSearchResult(listOf(note()), 1))
        runCurrent()
        controller.connect()
        controller.search()
        controller.selectNote(1)
        controller.updateQuery("ignored")
        controller.chooseConcept("ignored")
        controller.disconnect()
        assertEquals(AnkiBrowserState(), controller.state.value)
        assertNull(controller.mappedInput())
        assertTrue(coroutineContext[Job]!!.isActive)
        assertEquals(1, repo.closeCalls)
        assertEquals(1, repo.versionCalls)
    }

    @Test fun closeInvalidatesLateHandshakeAndFreshRead() = runTest {
        val versionGate = CompletableDeferred<Int>()
        val repo = FakeRepository().apply { versionAction = { late(versionGate) } }
        val connecting = AnkiBrowserController({ repo }, this)
        connecting.connect()
        runCurrent()
        connecting.close()
        versionGate.complete(6)
        runCurrent()
        assertEquals(AnkiBrowserState(), connecting.state.value)
        assertEquals(1, repo.closeCalls)

        val readGate = CompletableDeferred<AnkiNote>()
        val readingRepo = FakeRepository().apply { readAction = { late(readGate) } }
        val reading = withResults(readingRepo)
        reading.selectNote(1)
        runCurrent()
        reading.close()
        readGate.complete(note())
        runCurrent()
        assertEquals(AnkiBrowserState(), reading.state.value)
        assertNull(reading.mappedInput())
    }

    @Test fun cancellationIsNotReportedAsAnError() = runTest {
        val repo = FakeRepository().apply {
            versionAction =
                { throw CancellationException("secret") }
        }
        AnkiBrowserController({ repo }, this).use {
            it.connect()
            runCurrent()
            assertEquals(AnkiConnection.Disconnected, it.state.value.connection)
            assertNull(it.state.value.errorMessage)
        }
        val other = FakeRepository()
        connected(other).use {
            other.searchAction = { throw CancellationException("secret") }
            it.search()
            runCurrent()
            assertEquals(AnkiOperation.Idle, it.state.value.operation)
            assertNull(it.state.value.errorMessage)
            other.searchAction = { NoteSearchResult(listOf(note()), 1) }
            it.search()
            runCurrent()
            other.readAction = { throw CancellationException("secret") }
            it.selectNote(1)
            runCurrent()
            assertEquals(AnkiOperation.Idle, it.state.value.operation)
            assertNull(it.state.value.errorMessage)
        }
    }
}
