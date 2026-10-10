package com.toiec.vocabulary

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.toiec.vocabulary.data.Catalog
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

internal fun saveDesignScreenshot(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    val folder = File(instrumentation.targetContext.filesDir, "design-screenshots").apply { mkdirs() }
    File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
}

class DesignFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun waitTag(tag: String) {
        ui.waitUntil(120_000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithTag(tag).assertIsDisplayed()
    }
    private fun choice(index: Int): String = ui.onNodeWithTag("option-$index", useUnmergedTree = true)
        .fetchSemanticsNode().children.flatMap { texts(it) }.joinToString(" ")
    private fun texts(node: androidx.compose.ui.semantics.SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty() + node.children.flatMap { texts(it) }

    @Test fun nativeLearningReviewAndResultWithPersistentStorage() {
        waitTag("custom")
        ui.onNodeWithTag("custom").performClick()
        waitTag("start")
        saveDesignScreenshot("01-home")
        ui.onNodeWithTag("start").performClick()
        waitTag("word")
        val word = ui.onNodeWithTag("word").fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        val catalog = Catalog.parse(ui.activity.assets.open("vocabulary.json").bufferedReader().use { it.readText() })
        val entry = catalog.entries.single { it.word == word }
        val correct = (0..3).single { choice(it).contains(entry.meaning) }
        saveDesignScreenshot("02-question")
        ui.onNodeWithTag("option-${(correct+1)%4}").performScrollTo().performClick()
        waitTag("feedback")
        ui.onNodeWithText("오답노트에 저장했습니다.").assertIsDisplayed()
        val file = File(ui.activity.filesDir,"wrong-answers-v1.txt")
        assertEquals(setOf(entry.id),file.readLines().filter { it.isNotBlank() }.toSet())
        saveDesignScreenshot("03-wrong-answer")
        // Answered choices must not allow a second submission.
        ui.onNodeWithTag("option-$correct").performScrollTo().assertIsNotEnabled()
        assertEquals(setOf(entry.id),file.readLines().filter { it.isNotBlank() }.toSet())
        ui.onNodeWithTag("home").performClick()
        ui.onNodeWithTag("confirm-home").performClick()
        waitTag("notebook")
        ui.onNodeWithTag("notebook").performClick()
        waitTag("review")
        ui.onNodeWithText(entry.word).assertIsDisplayed()
        saveDesignScreenshot("04-notebook")
        ui.onNodeWithTag("review").performClick()
        waitTag("word")
        val reviewCorrect = (0..3).single { choice(it).contains(entry.meaning) }
        ui.onNodeWithTag("option-$reviewCorrect").performScrollTo().performClick()
        waitTag("feedback")
        ui.onNodeWithText("오답노트에서 제거했습니다.").assertIsDisplayed()
        assertTrue(file.readText().isBlank())
        saveDesignScreenshot("05-correct-answer")
        ui.onNodeWithTag("next").performClick()
        waitTag("result-home")
        ui.onNodeWithText("1 / 1").assertIsDisplayed()
        saveDesignScreenshot("06-result")
    }
}
