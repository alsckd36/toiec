package com.toiec.vocabulary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.toiec.vocabulary.analytics.*
import com.toiec.vocabulary.data.*
import com.toiec.vocabulary.theme.Theme
import com.toiec.vocabulary.ui.main.*
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

/** Explicit UI fixtures; never written to the application's history. */
class LearningDatesTest {
    @get:Rule val ui=createComposeRule()
    private val today=LocalDate.now()
    private fun show(days: List<DayStats>) {
        val state=StudyUiState(catalog=Catalog(listOf(Day(1,"채용",1)),listOf(Vocabulary("a".repeat(24),1,"credential","신임장"))),busy=false,
            analytics=AnalyticsSummary(days,days.firstOrNull { it.date==today } ?: DayStats(today)))
        ui.setContent { Theme { StudyContent(state) } }
    }
    @Test fun noRecordsHasNoDatesOrCharts() {
        show(emptyList())
        ui.onNodeWithTag("home-list").performScrollToIndex(3)
        ui.onNodeWithText("아직 학습 기록이 없어요").assertIsDisplayed()
        ui.onNodeWithTag("home-list").performScrollToIndex(4)
        ui.onNodeWithTag("today-accuracy").assertTextEquals("—")
        ui.onNodeWithTag("accuracy-chart").assertDoesNotExist()
        saveDesignScreenshot("learning-days-00-empty-accuracy")
        ui.onNodeWithTag("home-list").performScrollToIndex(5)
        ui.onNodeWithTag("today-count").assertTextEquals("0개")
        ui.onNodeWithTag("count-chart").assertDoesNotExist()
        saveDesignScreenshot("learning-days-00-empty-count")
    }
    @Test fun singlePastDateHasOneDateAndNoTodaysFallback() {
        val day=today.minusDays(10)
        show(listOf(DayStats(day,3,1)))
        ui.onNodeWithTag("home-list").performScrollToIndex(4)
        ui.onNodeWithTag("today-accuracy").assertTextEquals("—")
        ui.onNodeWithTag("accuracy-date-$today").assertDoesNotExist()
        ui.onNodeWithTag("accuracy-date-$day").performScrollTo().performClick()
        ui.onNodeWithTag("accuracy-detail").assertTextContains("정답률 75%",substring=true)
        saveDesignScreenshot("learning-days-01-single-accuracy")
        ui.onNodeWithTag("home-list").performScrollToIndex(5)
        ui.onNodeWithTag("today-count").assertTextEquals("0개")
        ui.onNodeWithTag("count-date-$day").performScrollTo().performClick()
        ui.onNodeWithTag("count-detail").assertTextContains("전체 4개",substring=true)
        saveDesignScreenshot("learning-days-01-single-count")
    }
    @Test fun separatedDatesHaveNoBlankDatesAndBothDetailsRemainInteractive() {
        val days=listOf(DayStats(today.minusDays(20),2,1),DayStats(today.minusDays(8),3,1),DayStats(today,1,1))
        show(days)
        for(tag in listOf("accuracy","count")) {
            ui.onNodeWithTag("home-list").performScrollToIndex(if(tag=="accuracy") 4 else 5)
            ui.onNodeWithTag("$tag-date-${today.minusDays(1)}").assertDoesNotExist()
            days.forEach { day -> ui.onNodeWithTag("$tag-date-${day.date}").performScrollTo().performClick() }
            ui.onNodeWithTag("$tag-detail").assertTextContains(if(tag=="accuracy") "정답률 50%" else "전체 2개",substring=true)
            saveDesignScreenshot("learning-days-03-multiple-$tag")
        }
    }
}
