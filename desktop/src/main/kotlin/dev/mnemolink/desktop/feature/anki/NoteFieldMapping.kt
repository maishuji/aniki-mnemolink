package dev.mnemolink.desktop.feature.anki

import dev.mnemolink.app.domain.GenerationInput
import dev.mnemolink.app.domain.GenerationValidation
import dev.mnemolink.desktop.data.anki.AnkiNote
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

data class NoteFieldMapping(
    val conceptField: String,
    val contextField: String?,
    val destinationField: String?
) {
    /** A preview is available even when the destination or shared input validation is invalid. */
    fun preview(note: AnkiNote): GenerationInput? {
        if (sourceError(note) != null) return null
        val fields = note.fields.associateBy { it.name }
        return GenerationInput(
            concept = ankiHtmlToPlainText(fields.getValue(conceptField).value),
            context = contextField?.let { ankiHtmlToPlainText(fields.getValue(it).value) }.orEmpty()
        )
    }

    fun error(note: AnkiNote): String? {
        sourceError(note)?.let { return it }
        val destination =
            destinationField
                ?: return "Set up an existing destination field in Anki, then select it."
        if (note.fields.none {
                it.name == destination
            }
        ) {
            return "The destination field is missing from this note."
        }
        if (destination == conceptField ||
            destination == contextField
        ) {
            return "Source and destination fields must be distinct."
        }
        return GenerationValidation.inputError(preview(note)!!)
    }

    fun extract(note: AnkiNote): GenerationInput? = if (error(note) == null) preview(note) else null

    private fun sourceError(note: AnkiNote): String? = when {
        !hasUsableFieldSchema(note) -> "The note has an unsupported field schema."
        note.fields.none { it.name == conceptField } -> "Select an existing concept field."
        contextField != null &&
            note.fields.none {
                it.name == contextField
            } -> "Select an existing context field."
        conceptField == contextField -> "Concept and context fields must be distinct."
        else -> null
    }

    companion object {
        fun defaultFor(note: AnkiNote): NoteFieldMapping? {
            if (!hasUsableFieldSchema(note)) return null
            val destination = note.fields.firstOrNull {
                it.name.equals("Mnemonic", ignoreCase = true)
            }?.name
            val concept = note.fields.first().name
            val context = note.fields.firstOrNull {
                it.name != concept && it.name != destination
            }?.name
            return NoteFieldMapping(concept, context, destination)
        }
    }
}

internal fun hasUsableFieldSchema(note: AnkiNote): Boolean = note.fields.isNotEmpty() &&
    note.fields.all { it.name.isNotBlank() } &&
    note.fields.map { it.name }.distinct().size == note.fields.size

/** Extracts text without changing the source HTML or including active/media payloads. */
fun ankiHtmlToPlainText(html: String): String {
    val body = Jsoup.parseBodyFragment(html).body()
    body.select(
        "script, style, audio, video, img, picture, source, track, object, embed, iframe, svg, canvas, template, noscript"
    ).remove()
    val text = StringBuilder()
    fun separator(node: Element): String = when {
        node.normalName() == "br" -> "\n"
        node.normalName() == "p" -> "\n\n"
        node.tag().isBlock -> "\n"
        else -> ""
    }
    NodeTraversor.traverse(
        object : NodeVisitor {
            override fun head(node: Node, depth: Int) {
                when (node) {
                    is TextNode -> text.append(node.wholeText)
                    is Element -> text.append(separator(node))
                }
            }

            override fun tail(node: Node, depth: Int) {
                if (node is Element && node.normalName() != "br") text.append(separator(node))
            }
        },
        body
    )
    return text.toString()
        .replace(Regex("\\[sound\\s*:[^\\]\\r\\n]*(?:\\]|$)", RegexOption.IGNORE_CASE), "")
        .replace('\u00a0', ' ')
        .replace(Regex("[^\\S\\r\\n]+"), " ")
        .replace("\r\n", "\n").replace('\r', '\n')
        .split('\n').joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}
