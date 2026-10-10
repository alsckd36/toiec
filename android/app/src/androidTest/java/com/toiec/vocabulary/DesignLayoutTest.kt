package com.toiec.vocabulary

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import com.toiec.vocabulary.data.Catalog
import com.toiec.vocabulary.study.Question
import com.toiec.vocabulary.theme.Theme
import com.toiec.vocabulary.ui.main.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DesignLayoutTest {
    @get:Rule val ui = createComposeRule()
    @Test fun twoHundredPercentFontAndLongestMeaningRemainScrollableWithoutClipping() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = Catalog.parse(context.assets.open("vocabulary.json").bufferedReader().use { it.readText() })
        val longest = catalog.entries.maxBy { it.meaning.length }
        val options = listOf(longest.meaning,"여러 부서와 협력하여 업무의 진행 상황을 확인하는 것","우선적으로 처리하다","상품을 진열하는 선반")
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                Theme { StudyContent(StudyUiState(catalog=catalog,screen=Screen.QUIZ,busy=false,total=32,question=Question(longest,options))) }
            }
        }
        ui.onNodeWithTag("word").assertIsDisplayed()
        ui.onNodeWithText(longest.meaning, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        ui.onNodeWithText(longest.meaning, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue(results.isNotEmpty())
        saveDesignScreenshot("07-large-font-diagnostic")
        val layout = results.first()
        assertFalse("text=${longest.meaning}; size=${layout.size}; lines=${layout.lineCount}; horizontal=${layout.didOverflowWidth}; vertical=${layout.didOverflowHeight}", layout.hasVisualOverflow)
        saveDesignScreenshot("07-large-font-long-meaning")
        ui.onNodeWithTag("option-3").performScrollTo().assertIsDisplayed()
        val bounds = ui.onNodeWithTag("option-3").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.height >= 64 * context.resources.displayMetrics.density)
        ui.onNodeWithTag("next").assertIsDisplayed().assertIsNotEnabled()
        val nextBounds = ui.onNodeWithTag("next").fetchSemanticsNode().boundsInRoot
        assertTrue(nextBounds.bottom <= context.resources.displayMetrics.heightPixels)
        saveDesignScreenshot("08-large-font-last-option")
    }
}
