package dev.mnemolink.desktop.feature.anki

import dev.mnemolink.app.domain.GenerationValidation
import dev.mnemolink.desktop.data.anki.AnkiField
import dev.mnemolink.desktop.data.anki.AnkiNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFieldMappingTest {
    @Test
    fun deeplyNestedHtmlDoesNotOverflowThePreviewTraversal() {
        val html = "<div>".repeat(2_000) + "text" + "</div>".repeat(2_000)
        assertEquals("text", ankiHtmlToPlainText(html))
    }
    private fun note(concept: String = "cat", context: String = "a pet") = AnkiNote(
        42L,
        "Basic",
        listOf(
            AnkiField("Concept", concept, 9),
            AnkiField("Context", context, 2),
            AnkiField("Mnemonic", "<b>unchanged destination</b>", 0)
        )
    )
    private val mapping = NoteFieldMapping("Concept", "Context", "Mnemonic")

    @Test fun defaultsUseNamesNotOrdinals() {
        assertEquals(mapping, NoteFieldMapping.defaultFor(note()))
        val reordered = note().copy(
            fields = listOf(note().fields[1], note().fields[0], note().fields[2])
        )
        assertEquals(
            NoteFieldMapping("Context", "Concept", "Mnemonic"),
            NoteFieldMapping.defaultFor(reordered)
        )
        assertEquals(mapping.extract(note()), mapping.extract(reordered))
    }

    @Test fun destinationIsOnlyAnExactCaseInsensitiveMnemonic() {
        val renamed = note().copy(
            fields = note().fields.map {
                if (it.name == "Mnemonic") it.copy(name = "mNeMoNiC") else it
            }
        )
        assertEquals("mNeMoNiC", NoteFieldMapping.defaultFor(renamed)?.destinationField)
        for (name in listOf("Answer", "MyMnemonic", "Mnemonic ")) {
            val other = renamed.copy(
                fields = renamed.fields.map {
                    if (it.name == "mNeMoNiC") it.copy(name = name) else it
                }
            )
            assertNull(NoteFieldMapping.defaultFor(other)?.destinationField)
        }
    }

    @Test fun missingDestinationRequiresSetupButAllowsPreview() {
        val withoutDestination = note().copy(fields = note().fields.take(2))
        val defaults = NoteFieldMapping.defaultFor(withoutDestination)!!
        assertNull(defaults.destinationField)
        assertTrue(defaults.error(withoutDestination)!!.contains("Set up"))
        assertEquals("cat", defaults.preview(withoutDestination)?.concept)
        assertNull(defaults.extract(withoutDestination))
    }

    @Test fun mappingRequiresEverySelectedNameToExist() {
        for (invalid in listOf(
            mapping.copy(conceptField = "concept"),
            mapping.copy(contextField = "Absent"),
            mapping.copy(destinationField = "Absent")
        )) {
            assertNotNull(invalid.error(note()))
            assertNull(invalid.extract(note()))
        }
    }

    @Test fun allSelectedFieldsMustBeDistinct() {
        for (invalid in listOf(
            mapping.copy(contextField = "Concept"),
            mapping.copy(destinationField = "Concept"),
            mapping.copy(destinationField = "Context")
        )) {
            assertNotNull(invalid.error(note()))
            assertNull(invalid.extract(note()))
        }
    }

    @Test fun contextIsOptional() {
        val input = mapping.copy(contextField = null).extract(note("cat", "{{c1::unused}}"))!!
        assertEquals("cat", input.concept)
        assertEquals("", input.context)
    }

    @Test fun emptyAndAmbiguousSchemasAreRejected() {
        for (invalid in listOf(
            note().copy(fields = emptyList()),
            note().copy(fields = note().fields + note().fields.first()),
            note().copy(fields = listOf(AnkiField("", "value", 0)))
        )) {
            assertNull(NoteFieldMapping.defaultFor(invalid))
            assertNotNull(mapping.error(invalid))
        }
    }

    @Test fun htmlExtractionLeavesAllRawFieldsExactlyUnchanged() {
        val source =
            note("<div class='c'>caf&eacute; &amp; 猫<br>pet</div>", "<p>first</p><p>second</p>")
        val before = source.fields.map { it.value }
        assertEquals("café & 猫\npet", mapping.extract(source)?.concept)
        assertEquals("first\n\nsecond", mapping.extract(source)?.context)
        assertEquals(before, source.fields.map { it.value })
        assertEquals("<b>unchanged destination</b>", source.fields.last().value)
    }

    @Test fun scriptsStylesAndAllMediaPayloadsAreRemoved() {
        val html = "a<script>SECRET</script><style>SECRET</style><img alt='SECRET' src='SECRET'>" +
            "<audio>SECRET</audio><video>SECRET</video>" +
            "<object>SECRET</object><iframe>SECRET</iframe>" +
            "<svg><text>SECRET</text></svg><picture>SECRET</picture><canvas>SECRET</canvas>" +
            "<embed src='SECRET'><source src='SECRET'><track src='SECRET'>b"
        assertEquals("ab", ankiHtmlToPlainText(html))
    }

    @Test fun soundPayloadIsRemovedWithoutExposingFileNames() {
        assertEquals("a b", ankiHtmlToPlainText("a [sound:private.mp3] b"))
        assertEquals("cat", ankiHtmlToPlainText("cat[SOUND :private.mp3]"))
        assertEquals("cat", ankiHtmlToPlainText("cat[sound:private.mp3"))
    }

    @Test fun paragraphsBreaksAndEntitiesPreservePlainText() {
        assertEquals(
            "one\ntwo\n\nthree & four\n\nfive café 猫",
            ankiHtmlToPlainText(
                "<p>one<br>two</p><p>three &amp; four</p><p>five&nbsp;caf&eacute; 猫</p>"
            )
        )
        assertEquals("word", ankiHtmlToPlainText("<b>wo</b><i>rd</i>"))
    }

    @Test fun sharedConceptLimitIsEnforcedWithoutTruncation() {
        val raw = "a".repeat(GenerationValidation.MAX_CONCEPT_LENGTH + 1)
        val source = note("<b>$raw</b>")
        assertEquals(raw, mapping.preview(source)?.concept)
        assertEquals("Concept is too long (maximum 200).", mapping.error(source))
        assertNull(mapping.extract(source))
    }

    @Test fun sharedContextLimitIsEnforcedWithoutTruncation() {
        val raw = "a".repeat(GenerationValidation.MAX_CONTEXT_LENGTH + 1)
        val source = note(context = raw)
        assertEquals(raw, mapping.preview(source)?.context)
        assertEquals("Context is too long (maximum 2000).", mapping.error(source))
        assertNull(mapping.extract(source))
    }

    @Test fun exactLimitsRemainValid() {
        assertNotNull(
            mapping.extract(
                note(
                    "a".repeat(GenerationValidation.MAX_CONCEPT_LENGTH),
                    "b".repeat(GenerationValidation.MAX_CONTEXT_LENGTH)
                )
            )
        )
    }

    @Test fun blankAndImageOnlyConceptsFailSharedValidation() {
        for (concept in listOf("", " &nbsp; ", "<img src='secret' alt='cat'>", "[sound:cat.mp3]")) {
            assertEquals("Enter a concept.", mapping.error(note(concept)))
            assertNull(mapping.extract(note(concept)))
        }
    }

    @Test fun clozeInEitherSelectedSourceIsRejectedNotRewritten() {
        for (source in listOf(note("{{c1::<b>cat</b>::hint}}"), note(context = "{{c2::pet}}"))) {
            assertEquals("Cloze or template markup is not allowed.", mapping.error(source))
            assertNull(mapping.extract(source))
            assertTrue(mapping.preview(source).toString().contains("{{c"))
        }
    }

    @Test fun clozeInUnselectedDestinationDoesNotInvalidateSources() {
        val source = note().copy(
            fields = note().fields.map {
                if (it.name == "Mnemonic") it.copy(value = "{{c1::ignored}}") else it
            }
        )
        assertNotNull(mapping.extract(source))
    }

    @Test fun sharedUnsafeTextValidationAlsoApplies() {
        assertNotNull(mapping.error(note("cat\u001Fdog")))
        assertNotNull(mapping.error(note("&lt;b&gt;cat&lt;/b&gt;")))
    }
}
