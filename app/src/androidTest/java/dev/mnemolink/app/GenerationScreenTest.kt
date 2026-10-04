package dev.mnemolink.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenerationScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun demoCanBeGeneratedEditedAndApprovedWithoutClaimingASave() {
        compose.onNodeWithTag("approve").assertIsNotEnabled()
        compose.onNodeWithTag("generate").performScrollTo().performClick()
        compose.onNodeWithTag("draft").assertTextContains("Mock mnemonic", substring = true)
        compose.onNodeWithTag(
            "draft"
        ).performScrollTo().performTextReplacement("My edited memory aid")
        compose.onNodeWithTag("approve").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithTag(
            "approved"
        ).assertTextContains(compose.activity.getString(R.string.local_approval_notice))
        compose.onNodeWithTag("approve").assertIsNotEnabled()
    }

    @Test
    fun changingSourceInvalidatesDraftAndApproval() {
        approveDraft()
        compose.onNodeWithTag(
            "concept"
        ).performScrollTo().performTextReplacement("different concept")
        compose.onNodeWithTag("approved").assertDoesNotExist()
        compose.onNodeWithTag("draft").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.EditableText,
                AnnotatedString("")
            )
        )
        compose.onNodeWithTag("approve").assertIsNotEnabled()
    }

    @Test
    fun discardRemovesDraftAndLocalApproval() {
        approveDraft()
        compose.onNodeWithTag("cancel").performScrollTo().performClick()
        compose.onNodeWithTag("approved").assertDoesNotExist()
        compose.onNodeWithTag("approve").assertIsNotEnabled()
    }

    @Test
    fun invalidDraftCannotBeApproved() {
        compose.onNodeWithTag(
            "draft"
        ).performScrollTo().performTextReplacement("[sound:unsafe.mp3]")
        compose.onNodeWithTag("approve").assertIsNotEnabled()
        compose.onNodeWithTag("draft-error").assertTextContains("Anki sound markup is not allowed.")
    }

    @Test
    fun activityRecreationRetainsEditedDraftAndApproval() {
        approveDraft()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("draft").assertTextContains("My edited memory aid")
        compose.onNodeWithTag(
            "approved"
        ).assertTextContains(compose.activity.getString(R.string.local_approval_notice))
    }

    private fun approveDraft() {
        compose.onNodeWithTag(
            "draft"
        ).performScrollTo().performTextReplacement("My edited memory aid")
        compose.onNodeWithTag("approve").performScrollTo().performClick()
    }
}
