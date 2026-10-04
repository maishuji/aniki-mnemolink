package dev.mnemolink.desktop.feature.anki

import dev.mnemolink.app.domain.GenerationInput
import dev.mnemolink.desktop.data.anki.AnkiNote
import dev.mnemolink.desktop.data.anki.AnkiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AnkiConnection { Disconnected, Connecting, Connected }
enum class AnkiOperation { Idle, Searching, LoadingNote }

data class AnkiBrowserState(
    val connection: AnkiConnection = AnkiConnection.Disconnected,
    val operation: AnkiOperation = AnkiOperation.Idle,
    val apiVersion: Int? = null,
    val query: String = "tag:MnemoLinkDemo",
    val results: List<AnkiNote> = emptyList(),
    val totalMatches: Int? = null,
    val selectedNote: AnkiNote? = null,
    val mapping: NoteFieldMapping? = null,
    val errorMessage: String? = null
) {
    val canSearch: Boolean
        get() = connection == AnkiConnection.Connected &&
            operation == AnkiOperation.Idle &&
            query.isNotBlank() &&
            query.length <= 512
    val mappingError: String?
        get() = selectedNote?.let { note ->
            val fields = mapping
            if (fields ==
                null
            ) {
                "Select source and destination fields."
            } else {
                fields.error(note)
            }
        }
    val inputPreview: GenerationInput?
        get() = selectedNote?.let { mapping?.preview(it) }
    val canLoadInput: Boolean
        get() = connection == AnkiConnection.Connected &&
            operation == AnkiOperation.Idle &&
            selectedNote != null &&
            mapping != null &&
            mappingError == null
}

/** All methods, including close, must be called on the supplied scope's single UI thread. */
class AnkiBrowserController(
    private val factory: (String) -> AnkiRepository,
    scope: CoroutineScope
) : AutoCloseable {
    private val ownerJob = SupervisorJob(scope.coroutineContext[Job])
    private val ownScope = CoroutineScope(scope.coroutineContext + ownerJob)
    private val mutableState = MutableStateFlow(AnkiBrowserState())
    val state: StateFlow<AnkiBrowserState> = mutableState.asStateFlow()
    private var repository: AnkiRepository? = null
    private var operationJob: Job? = null
    private var loadingNoteId: Long? = null
    private var version = 0L
    private var closed = false

    fun connect(apiKey: String = "") {
        if (closed ||
            !ownerJob.isActive ||
            state.value.connection == AnkiConnection.Connecting
        ) {
            return
        }
        val token = invalidate()
        closeRepository()
        mutableState.value =
            AnkiBrowserState(connection = AnkiConnection.Connecting, query = state.value.query)
        operationJob = ownScope.launch {
            try {
                val repo = factory(apiKey)
                if (!isCurrent(token)) {
                    safelyClose(repo)
                    return@launch
                }
                repository = repo
                val apiVersion = repo.version()
                if (!isCurrent(token)) return@launch
                if (apiVersion < 6) {
                    closeRepository()
                    mutableState.value = state.value.copy(
                        connection = AnkiConnection.Disconnected,
                        errorMessage = "AnkiConnect version 6 or newer is required."
                    )
                } else {
                    mutableState.value =
                        state.value.copy(
                            connection = AnkiConnection.Connected,
                            apiVersion = apiVersion
                        )
                }
            } catch (cancelled: CancellationException) {
                if (isCurrent(token)) {
                    closeRepository()
                    mutableState.value = state.value.copy(connection = AnkiConnection.Disconnected)
                }
                throw cancelled
            } catch (_: Exception) {
                if (isCurrent(token)) {
                    closeRepository()
                    mutableState.value = state.value.copy(
                        connection = AnkiConnection.Disconnected,
                        errorMessage = "Could not connect to Anki. " +
                            "Check AnkiConnect and its API key."
                    )
                }
            }
        }
    }

    fun disconnect() {
        if (closed) return
        invalidate()
        closeRepository()
        mutableState.value = AnkiBrowserState(query = state.value.query)
    }

    fun updateQuery(query: String) {
        if (closed || query == state.value.query) return
        invalidate()
        // Query edits also invalidate an in-flight connection handshake.
        if (state.value.connection == AnkiConnection.Connecting) {
            closeRepository()
            mutableState.value = AnkiBrowserState(query = query)
        } else {
            mutableState.value = state.value.copy(
                query = query,
                operation = AnkiOperation.Idle,
                results = emptyList(),
                totalMatches = null,
                selectedNote = null,
                mapping = null,
                errorMessage = null
            )
        }
    }

    fun search() {
        if (closed || !ownerJob.isActive || !state.value.canSearch) return
        val repo = repository ?: return
        val query = state.value.query
        val token = invalidate()
        mutableState.value = state.value.copy(
            operation = AnkiOperation.Searching,
            results = emptyList(),
            totalMatches = null,
            selectedNote = null,
            mapping = null,
            errorMessage = null
        )
        operationJob = ownScope.launch {
            try {
                val result = repo.searchNotes(query)
                if (isCurrent(token)) {
                    mutableState.value = state.value.copy(
                        operation = AnkiOperation.Idle,
                        results = result.notes.map(::snapshot),
                        totalMatches = result.totalMatches
                    )
                }
            } catch (cancelled: CancellationException) {
                if (isCurrent(token)) {
                    mutableState.value =
                        state.value.copy(operation = AnkiOperation.Idle)
                }
                throw cancelled
            } catch (_: Exception) {
                if (isCurrent(token)) {
                    mutableState.value = state.value.copy(
                        operation = AnkiOperation.Idle,
                        errorMessage = "Could not search Anki notes."
                    )
                }
            }
        }
    }

    fun selectNote(noteId: Long) {
        if (closed ||
            !ownerJob.isActive ||
            state.value.connection != AnkiConnection.Connected ||
            state.value.operation == AnkiOperation.Searching ||
            (state.value.operation == AnkiOperation.LoadingNote && loadingNoteId == noteId)
        ) {
            return
        }
        val expected = state.value.results.singleOrNull { it.noteId == noteId } ?: return
        val repo = repository ?: return
        val token = invalidate()
        loadingNoteId = noteId
        mutableState.value = state.value.copy(
            operation = AnkiOperation.LoadingNote,
            selectedNote = null,
            mapping = null,
            errorMessage = null
        )
        operationJob = ownScope.launch {
            try {
                val fresh = snapshot(repo.getNote(noteId))
                if (!isCurrent(token)) return@launch
                val schemaMatches =
                    fresh.noteId == expected.noteId &&
                        fresh.modelName == expected.modelName &&
                        hasUsableFieldSchema(fresh) &&
                        hasUsableFieldSchema(expected) &&
                        fresh.fields.map { it.name }.toSet() ==
                        expected.fields.map { it.name }.toSet()
                mutableState.value = if (schemaMatches) {
                    state.value.copy(
                        operation = AnkiOperation.Idle,
                        selectedNote = fresh,
                        mapping = NoteFieldMapping.defaultFor(fresh)
                    )
                } else {
                    state.value.copy(
                        operation = AnkiOperation.Idle,
                        errorMessage = "The note or its field schema changed. Search again."
                    )
                }
            } catch (cancelled: CancellationException) {
                if (isCurrent(token)) {
                    mutableState.value =
                        state.value.copy(operation = AnkiOperation.Idle)
                }
                throw cancelled
            } catch (_: Exception) {
                if (isCurrent(token)) {
                    mutableState.value = state.value.copy(
                        operation = AnkiOperation.Idle,
                        errorMessage = "Could not load the selected Anki note."
                    )
                }
            }
        }
    }

    fun chooseConcept(field: String) = updateMapping { it.copy(conceptField = field) }
    fun chooseContext(field: String?) = updateMapping { it.copy(contextField = field) }
    fun chooseDestination(field: String) = updateMapping { it.copy(destinationField = field) }

    fun mappedInput(): GenerationInput? = if (!closed && state.value.canLoadInput) {
        state.value.selectedNote?.let { state.value.mapping?.extract(it) }
    } else {
        null
    }

    private fun updateMapping(change: (NoteFieldMapping) -> NoteFieldMapping) {
        if (closed || state.value.operation != AnkiOperation.Idle) return
        val mapping = state.value.mapping ?: return
        mutableState.value = state.value.copy(mapping = change(mapping), errorMessage = null)
    }

    private fun invalidate(): Long {
        version++
        operationJob?.cancel()
        operationJob = null
        loadingNoteId = null
        return version
    }

    private fun isCurrent(token: Long) = !closed && ownerJob.isActive && version == token
    private fun snapshot(note: AnkiNote) = note.copy(fields = note.fields.toList())
    private fun safelyClose(repo: AnkiRepository) {
        try {
            repo.close()
        } catch (
            _: Exception
        ) { /* Cleanup must not expose transport details. */ }
    }
    private fun closeRepository() {
        val repo = repository
        repository = null
        if (repo != null) safelyClose(repo)
    }

    override fun close() {
        if (closed) return
        closed = true
        invalidate()
        closeRepository()
        ownScope.cancel()
        mutableState.value = AnkiBrowserState(query = state.value.query)
    }
}
