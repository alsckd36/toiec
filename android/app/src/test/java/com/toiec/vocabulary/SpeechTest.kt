package com.toiec.vocabulary

import com.toiec.vocabulary.speech.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SpeechTest {
    @get:Rule val folder = TemporaryFolder()
    private class Port : SpeechPort {
        val events = mutableListOf<String>()
        var succeeds = true
        override fun speak(text: String): Boolean { events += "speak:$text"; return succeeds }
        override fun stop() { events += "stop" }
        override fun shutdown() { events += "shutdown" }
        val spoken get() = events.filter { it.startsWith("speak:") }
    }
    private fun ready(port: Port) = SpeechController(port).apply { setStatus(SpeechStatus.READY); setForeground(true) }

    @Test fun firstVisibleQuestionPlaysOnceDespiteGradingAndScreenRefreshes() {
        val port=Port(); val speech=ready(port)
        assertTrue(speech.automatic)
        speech.showQuestion("daily:0:id1","credential")
        repeat(20) { speech.showQuestion("daily:0:id1","credential") }
        speech.setStatus(SpeechStatus.READY)
        assertEquals(listOf("speak:credential"),port.spoken)
    }
    @Test fun allLearningModesUseSameRulesAndNewSessionOfSameWordPlaysAgain() {
        val port=Port(); val speech=ready(port)
        for(mode in listOf("daily","custom","review")) {
            speech.showQuestion("$mode:0:id1","credential")
            speech.showQuestion("$mode:0:id1","credential")
        }
        speech.showQuestion("daily-session2:0:id1","credential")
        assertEquals(4,port.spoken.size)
    }
    @Test fun nextQuestionStopsPreviousBeforeSpeakingEvenIfEnglishWordIsSame() {
        val port=Port(); val speech=ready(port)
        speech.showQuestion("session:0:id1","one")
        speech.showQuestion("session:1:id1","one")
        assertEquals(listOf("stop","speak:one","stop","speak:one"),port.events)
    }
    @Test fun offSuppressesAutomaticButReplayStillWorksIncludingAfterGrading() {
        val port=Port(); val speech=ready(port)
        speech.setAutomatic(false)
        speech.showQuestion("q1","look forward to")
        assertTrue(port.spoken.isEmpty())
        assertTrue(speech.replay())
        speech.showQuestion("q1","look forward to") // grading republishes the same key
        assertTrue(speech.replay())
        assertEquals(listOf("speak:look forward to","speak:look forward to"),port.spoken)
    }
    @Test fun turningOnNeverSpeaksCurrentQuestionAndNextQuestionAutoplays() {
        val port=Port(); val speech=ready(port)
        speech.setAutomatic(false); speech.showQuestion("q1","one")
        speech.setAutomatic(true); speech.showQuestion("q1","one")
        assertTrue(port.spoken.isEmpty())
        speech.showQuestion("q2","two")
        assertEquals(listOf("speak:two"),port.spoken)
    }
    @Test fun delayedInitializationPlaysOnlyCurrentQuestionExactlyOnce() {
        val port=Port(); val speech=SpeechController(port)
        speech.setForeground(true)
        speech.showQuestion("q1","one"); speech.showQuestion("q2","two")
        assertTrue(port.spoken.isEmpty())
        speech.setStatus(SpeechStatus.READY); speech.setStatus(SpeechStatus.READY)
        assertEquals(listOf("speak:two"),port.spoken)
    }
    @Test fun offThenOnWhileInitializingDoesNotRevivePendingAutoplay() {
        val port=Port(); val speech=SpeechController(port)
        speech.setForeground(true); speech.showQuestion("q1","one")
        speech.setAutomatic(false); speech.setAutomatic(true)
        speech.setStatus(SpeechStatus.READY)
        assertTrue(port.spoken.isEmpty())
    }
    @Test fun manualReplayConsumesPendingAndFlushesPreviousSpeech() {
        val port=Port(); val speech=ready(port)
        speech.showQuestion("q1","one"); assertTrue(speech.replay())
        speech.setStatus(SpeechStatus.READY); speech.showQuestion("q1","one")
        assertEquals(listOf("stop","speak:one","stop","speak:one"),port.events)
    }
    @Test fun backgroundStopsAndReturnDoesNotRepeatSameQuestion() {
        val port=Port(); val speech=ready(port)
        speech.showQuestion("q1","one"); speech.setForeground(false)
        assertEquals("stop",port.events.last())
        assertFalse(speech.replay())
        speech.setForeground(true); speech.showQuestion("q1","one")
        assertEquals(1,port.spoken.size)
        speech.showQuestion("q2","two"); assertEquals(2,port.spoken.size)
    }
    @Test fun initializationCompletingInBackgroundOrAfterLeavingCannotSpeakOldQuestion() {
        val port=Port(); val speech=SpeechController(port)
        speech.setForeground(true); speech.showQuestion("q1","one")
        speech.setForeground(false); speech.setStatus(SpeechStatus.READY); speech.setForeground(true)
        assertTrue(port.spoken.isEmpty())
        speech.setStatus(SpeechStatus.LOADING); speech.showQuestion("q2","two")
        speech.showQuestion(null,null); speech.setStatus(SpeechStatus.READY)
        assertTrue(port.spoken.isEmpty()); assertFalse(speech.replay())
    }
    @Test fun leavingQuizStopsSpeechAndDoesNotPermitReplay() {
        val port=Port(); val speech=ready(port)
        speech.showQuestion("q1","one"); speech.showQuestion(null,null)
        assertEquals("stop",port.events.last()); assertFalse(speech.replay())
    }
    @Test fun unavailableEngineOrFailedSpeakDoesNotLoopOrThrow() {
        val port=Port(); val speech=ready(port)
        speech.setStatus(SpeechStatus.UNAVAILABLE); speech.showQuestion("q1","one")
        assertTrue(port.spoken.isEmpty()); assertFalse(speech.replay())
        speech.setStatus(SpeechStatus.READY) // installing data does not replay an old question
        assertTrue(port.spoken.isEmpty())
        port.succeeds=false; speech.showQuestion("q2","two")
        repeat(5) { speech.showQuestion("q2","two"); speech.setStatus(SpeechStatus.READY) }
        assertEquals(listOf("speak:two"),port.spoken)
    }
    @Test fun closeStopsShutsDownOnceAndIgnoresLateCallbacks() {
        val port=Port(); val speech=ready(port)
        speech.showQuestion("q1","one"); speech.close(); speech.close()
        val before=port.events.toList()
        speech.setStatus(SpeechStatus.READY); speech.setForeground(true); speech.showQuestion("q2","two")
        assertFalse(speech.replay()); assertEquals(before,port.events)
        assertEquals(1,port.events.count { it == "shutdown" })
    }
    @Test fun defaultOnAndChangesPersistAcrossSettingsInstances() {
        val file=File(folder.root,"sound-settings-v1.txt")
        assertTrue(SoundSettings(file).read())
        SoundSettings(file).write(false); assertFalse(SoundSettings(file).read())
        SoundSettings(file).write(true); assertTrue(SoundSettings(file).read())
    }
    @Test fun savedOffIsAppliedBeforeFirstQuestionOnNextLaunch() {
        val file=File(folder.root,"sound-settings-v1.txt"); SoundSettings(file).write(false)
        val port=Port(); val speech=ready(port)
        speech.setAutomatic(SoundSettings(file).read()); speech.showQuestion("new-launch:q1","one")
        assertTrue(port.spoken.isEmpty()); assertTrue(speech.replay())
    }
    @Test fun failedSettingWritePreservesPreviousFileAndUserLearningFiles() {
        val blocked=File(folder.root,"blocked").apply { writeText("blocked") }
        val settings=SoundSettings(File(blocked,"sound-settings-v1.txt"))
        val wrong=File(folder.root,"wrong-answers-v1.txt").apply { writeText("existing wrong answers") }
        val daily=File(folder.root,"daily-progress-v1.json").apply { writeText("existing progress") }
        try { settings.write(false); fail("Expected save failure") } catch(_: Exception) { }
        assertEquals("blocked",blocked.readText())
        assertEquals("existing wrong answers",wrong.readText()); assertEquals("existing progress",daily.readText())
    }
}
