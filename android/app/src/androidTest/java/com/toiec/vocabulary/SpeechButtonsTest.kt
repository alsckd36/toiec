package com.toiec.vocabulary

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import com.toiec.vocabulary.data.*
import com.toiec.vocabulary.study.*
import com.toiec.vocabulary.speech.SpeechStatus
import com.toiec.vocabulary.theme.Theme
import com.toiec.vocabulary.ui.main.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Layout/interaction test for a local emulator run; no actual voice playback is asserted here. */
class SpeechButtonsTest {
    @get:Rule val ui = createComposeRule()
    @Test fun toggleHasAccessibleStateAndReplayWorksOffAfterGrading() {
        val entry=Vocabulary("a".repeat(24),1,"credential","신임장, 자격 증명서")
        val state=mutableStateOf(StudyUiState(
            catalog=Catalog(listOf(Day(1,"채용",1)),listOf(entry)),
            screen=Screen.QUIZ, question=Question(entry,listOf(entry.meaning,"협력하다","늘리다","줄이다")),
            answer=Answer(entry.meaning,entry.meaning), total=1, busy=false, speechStatus=SpeechStatus.READY,
        ))
        var replayCount=0
        ui.setContent { Theme { StudyContent(state.value, toggleSound={ state.value=state.value.copy(soundOn=!state.value.soundOn) }, replay={ replayCount++ }) } }
        ui.onNodeWithTag("sound-toggle").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"소리 ON")).performClick()
        ui.onNodeWithContentDescription("자동 발음 켜기")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"소리 OFF"))
        ui.onNodeWithTag("speech-replay").assertIsEnabled().performClick()
        assertEquals(1,replayCount)
    }
}
