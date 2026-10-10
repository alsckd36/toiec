package com.toiec.vocabulary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.toiec.vocabulary.ui.main.*
import java.io.File
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AnalyticsFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val model get()=ViewModelProvider(ui.activity)[StudyViewModel::class.java]
    private fun idle() { ui.waitUntil(120_000) { !model.state.value.busy && model.state.value.catalog!=null } }
    private fun capture(name: String) { ui.waitForIdle(); Thread.sleep(700); saveDesignScreenshot(name) }
    private fun answers(): Int {
        val file=File(ui.activity.filesDir,"answer-history-v1.json")
        return if(file.exists()) JSONObject(file.readText()).getJSONArray("events").length() else 0
    }
    private fun submit(right: Boolean) {
        idle()
        val question=checkNotNull(model.state.value.question)
        val correct=question.options.indexOf(question.vocabulary.meaning)
        val option=if(right) correct else (correct+1)%4
        ui.onNodeWithTag("quiz-list").performScrollToIndex(option+1)
        ui.onNodeWithTag("option-$option").performClick()
        ui.waitUntil(120_000) { !model.state.value.busy && model.state.value.answer!=null }
    }
    private fun leave() {
        ui.onNodeWithTag("home").performClick()
        ui.onNodeWithTag("confirm-home").performClick(); idle()
    }
    @Test fun realSubmissionsUpdateNativeCardsAndGraphTapWithoutPhantomAnswers() {
        idle(); assertEquals(0,answers())
        capture("analytics-01-home-empty")
        ui.onNodeWithTag("home-list").performScrollToIndex(3)
        ui.onNodeWithText("아직 학습 기록이 없어요").assertIsDisplayed()
        capture("analytics-02-empty-analysis")
        ui.onNodeWithTag("home-list").performScrollToIndex(4)
        ui.onNodeWithTag("today-accuracy").assertTextEquals("—")
        capture("analytics-02b-empty-accuracy")
        ui.onNodeWithTag("home-list").performScrollToIndex(5)
        ui.onNodeWithTag("today-count").assertTextEquals("0개")
        ui.onNodeWithTag("count-detail").performScrollTo()
        capture("analytics-02c-empty-count")
        assertEquals(0,answers())
        ui.onNodeWithTag("home-list").performScrollToIndex(3)
        ui.onNodeWithTag("analytics-start").performClick()
        submit(true)
        assertEquals(1,answers())
        // Reading state and requesting pronunciation do not submit an answer.
        ui.runOnIdle { model.replay(); model.refreshAnalytics() }
        assertEquals(1,answers())
        ui.onNodeWithTag("next").performClick(); submit(true)
        ui.onNodeWithTag("next").performClick(); submit(true)
        ui.onNodeWithTag("next").performClick(); submit(false)
        assertEquals(4,answers())
        ui.runOnIdle { model.submit(0) }
        assertEquals(4,answers())
        leave()
        assertEquals(4,model.state.value.analytics.today.total)
        assertEquals(75.0,model.state.value.analytics.today.accuracy!!,0.0)
        ui.onNodeWithTag("home-list").performScrollToIndex(4)
        ui.onNodeWithTag("today-accuracy").assertTextEquals("75%")
        capture("analytics-03-accuracy-75")
        val date=LocalDate.now()
        ui.onNodeWithTag("accuracy-date-$date").performScrollTo().performClick()
        ui.onNodeWithTag("accuracy-detail").assertTextContains("정답률 75%",substring=true)
        ui.onNodeWithTag("home-list").performScrollToIndex(5)
        ui.onNodeWithTag("today-count").assertTextEquals("4개")
        ui.onNodeWithTag("count-detail").performScrollTo()
        capture("analytics-04-count-four")
        ui.onNodeWithTag("count-date-${date.minusDays(1)}").performScrollTo().performClick()
        ui.onNodeWithTag("count-chart").performTouchInput { click(centerRight-androidx.compose.ui.geometry.Offset(20f,0f)) }
        ui.onNodeWithTag("count-detail").performScrollTo().assertTextContains("전체 4개 · 맞힌 문제 3개 · 틀린 문제 1개",substring=true)
        capture("analytics-05-count-detail")
        ui.onNodeWithTag("count-date-${date.minusDays(1)}").performScrollTo().performClick()
        ui.onNodeWithTag("count-detail").assertTextContains("전체 0개",substring=true)
        ui.onNodeWithTag("home-list").performScrollToIndex(4)
        ui.onNodeWithTag("accuracy-date-${date.minusDays(1)}").performScrollTo().performClick()
        ui.onNodeWithTag("accuracy-detail").assertTextContains("답변 없음 · 정답률 —",substring=true)
        assertEquals(4,answers())
        ui.activityRule.scenario.recreate(); idle()
        assertEquals(4,answers())
        assertEquals(4,model.state.value.dailyAnswered)
        ui.onNodeWithTag("home-list").performScrollToIndex(0)
        ui.onNodeWithTag("daily-start").performClick(); idle()
        assertEquals(4,model.state.value.index)
        submit(true); assertEquals(5,answers()); leave()
        // Custom entry does not alter the daily pointer, but always creates a fresh submission.
        ui.onNodeWithTag("home-list").performScrollToIndex(1)
        ui.onNodeWithTag("custom").performClick(); ui.onNodeWithTag("start").performClick()
        submit(false); leave(); assertEquals(6,answers())
        ui.onNodeWithTag("home-list").performScrollToIndex(1)
        ui.onNodeWithTag("custom").performClick(); ui.onNodeWithTag("start").performClick()
        submit(true); leave(); assertEquals(7,answers())
        assertEquals(5,model.state.value.dailyAnswered)
        ui.onNodeWithTag("home-list").performScrollToIndex(2)
        ui.onNodeWithTag("notebook").performClick(); ui.onNodeWithTag("review").performClick()
        val wrong=checkNotNull(model.state.value.question).vocabulary.id
        submit(true)
        assertFalse(wrong in model.state.value.notebookIds)
        assertEquals(8,answers())
        assertTrue(JSONObject(File(ui.activity.filesDir,"answer-history-v1.json").readText()).getJSONArray("events").toString().contains(wrong))
    }
}
