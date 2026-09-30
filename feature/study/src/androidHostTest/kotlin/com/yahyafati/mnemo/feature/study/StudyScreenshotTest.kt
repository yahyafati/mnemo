package com.yahyafati.mnemo.feature.study

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.yahyafati.mnemo.core.designsystem.theme.MnemoTheme
import com.yahyafati.mnemo.core.model.Card
import com.yahyafati.mnemo.core.model.Note
import com.yahyafati.mnemo.core.model.NoteKind
import com.yahyafati.mnemo.core.model.NoteType
import com.yahyafati.mnemo.core.model.Rating
import com.yahyafati.mnemo.core.model.StudyCard
import com.yahyafati.mnemo.core.ui.adaptive.LocalWindowLayout
import com.yahyafati.mnemo.core.ui.adaptive.WindowLayout
import com.yahyafati.mnemo.core.ui.card.CardResponse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant

/**
 * Screenshots of the study screen (docs/design/active-study-review.html) for each card type, and
 * the landscape layout. Record: ./gradlew :feature:study:recordRoborazziAndroidHostTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class StudyScreenshotTest {
    private val now = Instant.parse("2026-10-22T09:00:00Z")
    private val intervals = mapOf(
        Rating.Again to Duration.ofMinutes(1),
        Rating.Hard to Duration.ofMinutes(6),
        Rating.Good to Duration.ofMinutes(10),
        Rating.Easy to Duration.ofDays(8),
    )

    private fun card(kind: NoteKind, vararg fields: String, hint: String? = null): StudyCard {
        val note = Note("n", "d", NoteType.builtIn(kind).id, fields.toList(), createdAt = now, updatedAt = now, hint = hint)
        return StudyCard(Card("card-7", "n", "d", 0, due = now, createdAt = now, updatedAt = now), note, kind, "Cognitive Neuroscience")
    }

    @Composable
    private fun Reviewing(
        card: StudyCard,
        revealed: Boolean,
        response: CardResponse = CardResponse(),
        suggested: Rating? = null,
        dark: Boolean = false,
        layout: WindowLayout = WindowLayout(),
    ) {
        MnemoTheme(darkTheme = dark) {
            CompositionLocalProvider(LocalWindowLayout provides layout) {
                StudyScreen(
                    uiState = StudyUiState(
                        deckName = "Cognitive Neuroscience",
                        phase = StudyPhase.Reviewing(card, revealed, intervals, 7, 24, canUndo = true, response = response, suggestedRating = suggested),
                    ),
                    onAction = {},
                    onEditNote = {},
                    onDone = {},
                    doneLabel = "Done",
                )
            }
        }
    }

    private val basic = card(
        NoteKind.Basic,
        "What is the role of **long-term potentiation** in the hippocampus?",
        "The persistent strengthening of synapses after repeated stimulation.\n\n- NMDA receptors\n- `Ca²⁺` influx",
        hint = "Think *\"cells that fire together…\"*",
    )

    @Test
    fun basicQuestion() = captureRoboImage("src/androidHostTest/screenshots/study_basic_question.png") {
        Reviewing(basic, revealed = false, response = CardResponse(hintShown = true))
    }

    @Test
    fun basicAnswerLight() = captureRoboImage("src/androidHostTest/screenshots/study_basic_answer_light.png") { Reviewing(basic, revealed = true) }

    @Test
    fun basicAnswerDark() = captureRoboImage("src/androidHostTest/screenshots/study_basic_answer_dark.png") { Reviewing(basic, revealed = true, dark = true) }

    @Test
    fun multipleChoiceAnswered() = captureRoboImage("src/androidHostTest/screenshots/study_choice_answered.png") {
        val choice = card(NoteKind.MultipleChoice, "Which structure drives fear conditioning?", "Amygdala", "Hippocampus\nCerebellum\nThalamus")
        val wrong = choice.sides.choices!!.indexOf("Hippocampus")
        Reviewing(choice, revealed = true, response = CardResponse(chosen = wrong), suggested = Rating.Again)
    }

    @Test
    fun typeInQuestion() = captureRoboImage("src/androidHostTest/screenshots/study_type_in_question.png") {
        Reviewing(card(NoteKind.TypeIn, "Neurotransmitter of the neuromuscular junction?", "Acetylcholine"), revealed = false, response = CardResponse(typed = "Acetylcho"))
    }

    @Test
    fun typeInChecked() = captureRoboImage("src/androidHostTest/screenshots/study_type_in_checked.png") {
        Reviewing(
            card(NoteKind.TypeIn, "Neurotransmitter of the neuromuscular junction?", "Acetylcholine"),
            revealed = true,
            response = CardResponse(typed = "Acetylchloine"),
            suggested = Rating.Again,
        )
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun landscapePhone() = captureRoboImage("src/androidHostTest/screenshots/study_landscape.png") {
        Reviewing(basic, revealed = true, layout = WindowLayout(wide = true, short = true))
    }
}
