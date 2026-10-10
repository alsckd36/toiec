package com.toiec.vocabulary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.toiec.vocabulary.ui.main.*
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Run in a fresh instrumentation process AFTER AnalyticsFlowTest has made eight real submissions. */
class AnalyticsRestartTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun freshProcessLoadsRealRecordsAndDailyPointerWithoutRecounting() {
        val large=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("largeFont")=="true"
        val suffix=if(large) "-system-font-200" else ""
        val model=ViewModelProvider(ui.activity)[StudyViewModel::class.java]
        ui.waitUntil(120_000) { !model.state.value.busy && model.state.value.catalog!=null }
        val events=JSONObject(File(ui.activity.filesDir,"answer-history-v1.json").readText()).getJSONArray("events")
        assertEquals(8,events.length())
        assertEquals(8,model.state.value.analytics.today.total)
        assertEquals(6,model.state.value.analytics.today.correct)
        assertEquals(2,model.state.value.analytics.today.wrong)
        assertEquals(5,model.state.value.dailyAnswered)
        assertEquals(1,model.state.value.dailyRound); assertEquals(1,model.state.value.dailyDay)
        ui.onNodeWithTag("home-list").performScrollToIndex(4)
        ui.onNodeWithTag("today-accuracy").assertTextEquals("75%")
        ui.waitForIdle(); Thread.sleep(700); saveDesignScreenshot("analytics-08-restarted-accuracy$suffix")
        ui.onNodeWithTag("home-list").performScrollToIndex(5)
        ui.onNodeWithTag("count-detail").performScrollTo().assertTextContains("전체 8개 · 맞힌 문제 6개 · 틀린 문제 2개",substring=true)
        ui.waitForIdle(); Thread.sleep(700); saveDesignScreenshot("analytics-09-restarted-count$suffix")
        assertEquals(8,JSONObject(File(ui.activity.filesDir,"answer-history-v1.json").readText()).getJSONArray("events").length())
    }
}
