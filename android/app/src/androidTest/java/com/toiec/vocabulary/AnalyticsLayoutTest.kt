package com.toiec.vocabulary

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.toiec.vocabulary.analytics.AnalyticsSummary
import com.toiec.vocabulary.analytics.DayStats
import com.toiec.vocabulary.data.*
import com.toiec.vocabulary.theme.Theme
import com.toiec.vocabulary.ui.main.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AnalyticsLayoutTest {
    @get:Rule val ui=createComposeRule()
    @Test fun largeFontChartLabelsAndDetailsStayScrollableWithoutTextOverflow() { verifyLayout(480.dp) }
    @Test fun narrowPhoneLabelsAndDetailsStayReadableAtTwoHundredPercent() { verifyLayout(320.dp) }
    private fun verifyLayout(width: Dp) {
        val suffix=if(width==320.dp) "-320dp" else ""
        val day=LocalDate.now()
        val state=StudyUiState(catalog=Catalog(listOf(Day(1,"채용",1)),listOf(Vocabulary("a".repeat(24),1,"credential","신임장"))),busy=false,analytics=AnalyticsSummary(listOf(DayStats(day,3,1)),DayStats(day,3,1)))
        ui.setContent {
            val density=LocalDensity.current
            Box(Modifier.width(width)) { CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) { Theme { StudyContent(state) } } }
        }
        ui.onNodeWithTag("home-list").performScrollToIndex(5)
        ui.onNodeWithTag("count-date-$day").performScrollTo().performClick()
        ui.onNodeWithTag("count-detail").performScrollTo().assertIsDisplayed()
        val results=mutableListOf<TextLayoutResult>()
        ui.onNodeWithTag("count-detail").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue(results.isNotEmpty()); assertFalse(results.single().hasVisualOverflow)
        saveDesignScreenshot("analytics-06-large-font-count$suffix")
        ui.onNodeWithTag("count-date-$day").performScrollTo().assertIsDisplayed()
        val textResults=mutableListOf<TextLayoutResult>()
        ui.onNode(hasText(day.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd"))) and hasAnyAncestor(hasTestTag("count-date-$day")),useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textResults) }
        val textLayout=textResults.single()
        assertFalse("date size=${textLayout.size}; lines=${textLayout.lineCount}; horizontal=${textLayout.didOverflowWidth}; vertical=${textLayout.didOverflowHeight}",textLayout.hasVisualOverflow)
        saveDesignScreenshot("analytics-07-large-font-dates$suffix")
    }
}
