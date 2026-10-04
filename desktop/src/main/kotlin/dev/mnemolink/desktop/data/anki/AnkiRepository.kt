package dev.mnemolink.desktop.data.anki

data class AnkiField(val name: String, val value: String, val order: Int)
data class AnkiNote(val noteId: Long, val modelName: String, val fields: List<AnkiField>)
data class NoteSearchResult(val notes: List<AnkiNote>, val totalMatches: Int)

interface AnkiRepository : AutoCloseable {
    suspend fun version(): Int
    suspend fun searchNotes(query: String): NoteSearchResult
    suspend fun getNote(noteId: Long): AnkiNote
    override fun close()
}

enum class AnkiFailureKind {
    Unavailable,
    Authentication,
    Rejected,
    Protocol,
    TooLarge,
    NotFound,
    Incompatible
}

// Deliberately retain neither a remote message nor an underlying exception cause.
class AnkiConnectException(val kind: AnkiFailureKind) :
    Exception("AnkiConnect request could not be completed.")
