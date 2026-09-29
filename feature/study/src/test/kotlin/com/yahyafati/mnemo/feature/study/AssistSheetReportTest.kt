package com.yahyafati.mnemo.feature.study

import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.StudyAssist
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.feature.study.component.AssistSheetContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The "Report" action on AI output (Play's AI-generated content policy): asks first, sends nothing itself. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AssistSheetReportTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setSheet(text: String, running: Boolean = false) {
        val now = Instant.EPOCH
        val note = Note("n", "d", NoteType.Basic.id, listOf("What makes ATP?", "Mitochondria"), createdAt = now, updatedAt = now)
        val card = StudyCard(Card("c", "n", "d", 0, due = now, createdAt = now, updatedAt = now), note, NoteKind.Basic, "Biology")
        composeRule.setContent {
            MnemoTheme {
                AssistSheetContent(StudyAssistUiState(), AssistSheet(card, StudyAssist.Explain, text = text, running = running), onAction = {})
            }
        }
    }

    @Test
    fun reportAsksBeforeOpeningADraftAndSendsNothingOnCancel() {
        setSheet("Mitochondria make ATP through oxidative phosphorylation.")

        composeRule.onNodeWithText("Report").performClick()
        composeRule.onNodeWithText("Report this AI output?").assertIsDisplayed()
        // The sheet's own text, and the same text in the dialog: what will be in the draft.
        composeRule.onAllNodesWithText("Mitochondria make ATP through oxidative phosphorylation.").assertCountEquals(2)
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("Report this AI output?").assertDoesNotExist()
        assertNull(shadowOf(composeRule.activity).nextStartedActivity)
    }

    @Test
    fun confirmingOpensAPrefilledIssueDraftInTheBrowser() {
        setSheet("Mitochondria make ATP through oxidative phosphorylation.")

        composeRule.onNodeWithText("Report").performClick()
        composeRule.onNodeWithText("Open draft").performClick()

        val intent = assertNotNull(shadowOf(composeRule.activity).nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        val url = intent.dataString.orEmpty()
        assertTrue(url.startsWith("https://github.com/yahyafati/mnemo/issues/new?"), url)
        assertTrue("Mitochondria" in java.net.URLDecoder.decode(url, "UTF-8"), url)
    }

    @Test
    fun noReportWhileTheAnswerIsStillStreaming() {
        setSheet("Mitochondria make", running = true)
        composeRule.onNodeWithText("Report").assertDoesNotExist()
    }
}
