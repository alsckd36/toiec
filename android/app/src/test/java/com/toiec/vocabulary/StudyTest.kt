package com.toiec.vocabulary

import com.toiec.vocabulary.data.*
import com.toiec.vocabulary.study.*
import java.io.File
import kotlin.random.Random
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StudyTest {
    @get:Rule val folder = TemporaryFolder()
    private fun fixture(): List<Vocabulary> = listOf(
        Vocabulary("a".repeat(24), 1, "one", "하나"),
        Vocabulary("b".repeat(24), 1, "two", "둘"),
        Vocabulary("c".repeat(24), 1, "three", "셋"),
        Vocabulary("d".repeat(24), 1, "four", "넷"),
        Vocabulary("e".repeat(24), 1, "five", "다섯"),
    )
    private fun catalog() = Catalog.parse(File(requireNotNull(System.getProperty("vocabulary.asset"))).readText())
    private fun session(store: Notebook, review: Boolean = false, entries: List<Vocabulary> = fixture().take(1)) =
        StudySession(entries, QuestionFactory(fixture(), Random(42)), store, review)
    private fun correct(session: StudySession) = session.question!!.options.indexOf(session.question!!.vocabulary.meaning)
    private fun wrong(session: StudySession) = (correct(session) + 1) % 4

    @Test fun assetHasAllRealDaysAndExactCounts() {
        val catalog = catalog()
        assertEquals((1..30).toList(), catalog.days.map { it.day })
        assertEquals(954, catalog.entries.size)
        assertEquals(954, catalog.entries.map { it.id }.toSet().size)
        catalog.days.forEach { assertEquals(if (it.day in setOf(11,13,14,20,28,30)) 31 else 32, it.count) }
        assertEquals("은행", catalog.days.single { it.day == 26 }.title)
        val root = JSONObject(File(requireNotNull(System.getProperty("vocabulary.asset"))).readText())
        assertEquals(30, root.getJSONArray("excludedSheets").length())
        val entries = root.getJSONArray("entries")
        for (i in 0 until entries.length()) assertFalse(entries.getJSONObject(i).getString("sourceSheet").startsWith("Test"))
        assertEquals("Day 25 (2)", root.getJSONArray("days").getJSONObject(25).getString("sourceSheet"))
    }

    @Test fun everyPackagedEntryCanProduceFourUniqueOptionsAndExactlyOneKey() {
        val catalog = catalog()
        for (seed in 0..2) {
            val factory = QuestionFactory(catalog.entries, Random(seed))
            for (entry in catalog.entries) {
                val question = factory.create(entry)
                assertEquals(entry.word, 4, question.options.size)
                assertEquals(entry.word, 4, question.options.toSet().size)
                assertEquals(entry.word, 1, question.options.count { it == entry.meaning })
            }
        }
    }

    @Test fun duplicateSensesAndOverlappingGlossesAreNotDistractors() {
        val entry = fixture().first().copy(meaning = "중요한, 핵심적인")
        val pool = fixture().drop(1) + entry + listOf(
            entry.copy(meaning = "필수적인"),
            Vocabulary("f".repeat(24),1,"similar","중요한"),
            Vocabulary("0".repeat(24),1,"related","매우 핵심적인"),
            Vocabulary("1".repeat(24),1,"another","필수적인"),
        )
        repeat(40) { seed ->
            val options = QuestionFactory(pool, Random(seed)).create(entry).options
            assertFalse(options.contains("중요한"))
            assertFalse(options.contains("매우 핵심적인"))
            assertFalse(options.contains("필수적인"))
        }
    }

    @Test fun nearSynonymsWithDifferentSpellingsAreExcluded() {
        val target = fixture().first().copy(meaning = "흥미진진한")
        val pool = fixture().drop(1) + target + Vocabulary("f".repeat(24),1,"synonym","재미있는")
        repeat(30) { seed -> assertFalse(QuestionFactory(pool, Random(seed)).create(target).options.contains("재미있는")) }
    }

    @Test fun correctPositionIsShuffled() {
        val positions = (0..40).map { seed ->
            val q = QuestionFactory(fixture(), Random(seed)).create(fixture().first())
            q.options.indexOf(q.vocabulary.meaning)
        }.toSet()
        assertEquals(setOf(0,1,2,3), positions)
    }

    @Test fun wrongAnswerIsImmediatelySavedAndImmutable() {
        val store = FileNotebook(File(folder.root,"wrong.txt"))
        val s = session(store)
        val answer = s.submit(wrong(s))
        assertFalse(answer.isCorrect)
        assertEquals(setOf(fixture().first().id), store.ids())
        assertSame(answer, s.submit(correct(s)))
        assertEquals(0, s.correctCount)
        assertEquals(answer.selected, s.answer!!.selected)
    }

    @Test fun repeatedWrongAnswersDoNotDuplicateRecords() {
        val file = File(folder.root,"wrong.txt")
        val store = FileNotebook(file)
        repeat(5) { val s = session(store); s.submit(wrong(s)) }
        assertEquals(1, store.ids().size)
        assertEquals(1, file.readLines().size)
    }

    @Test fun normalCorrectAnswerDoesNotEraseExistingWrongRecord() {
        val store = FileNotebook(File(folder.root,"wrong.txt"))
        store.update(fixture().first().id,true)
        val s = session(store)
        assertTrue(s.submit(correct(s)).isCorrect)
        assertEquals(1, s.correctCount)
        assertEquals(1, store.ids().size)
    }

    @Test fun reviewCorrectRemovesImmediatelyAndSurvivesReload() {
        val file = File(folder.root,"wrong.txt")
        FileNotebook(file).update(fixture().first().id,true)
        val store = FileNotebook(file)
        val s = session(store,review=true)
        assertTrue(s.submit(correct(s)).isCorrect)
        assertTrue(store.ids().isEmpty())
        assertTrue(FileNotebook(file).ids().isEmpty())
    }

    @Test fun reviewWrongKeepsRecordAcrossRestart() {
        val file = File(folder.root,"wrong.txt")
        FileNotebook(file).update(fixture().first().id,true)
        val s = session(FileNotebook(file),review=true)
        s.submit(wrong(s))
        assertEquals(setOf(fixture().first().id), FileNotebook(file).ids())
    }

    @Test fun independentWordsAndRemovalArePreservedAcrossRecreation() {
        val file = File(folder.root,"wrong.txt")
        val store = FileNotebook(file)
        fixture().forEach { store.update(it.id,true) }
        FileNotebook(file).update(fixture()[1].id,false)
        assertEquals(fixture().map { it.id }.toSet() - fixture()[1].id, FileNotebook(file).ids())
        assertFalse(folder.root.listFiles()!!.any { it.name.endsWith(".tmp") })
    }

    @Test fun storageFailureDoesNotGradeOrPermitAdvancing() {
        val failing = object: Notebook {
            override fun ids() = emptySet<String>()
            override fun update(id: String, add: Boolean) { throw java.io.IOException("disk full") }
        }
        val s = session(failing)
        assertThrows(java.io.IOException::class.java) { s.submit(wrong(s)) }
        assertNull(s.answer)
        assertEquals(0,s.correctCount)
        assertThrows(IllegalStateException::class.java) { s.next() }
    }

    @Test fun fullDayProgressOnlyMovesAfterAnswerAndCompletesWithExactScore() {
        val s = session(FileNotebook(File(folder.root,"wrong.txt")),entries=fixture())
        assertThrows(IllegalStateException::class.java) { s.next() }
        repeat(5) { index ->
            assertEquals(index,s.index)
            s.submit(if (index % 2 == 0) correct(s) else wrong(s))
            s.next()
        }
        assertTrue(s.complete)
        assertNull(s.question)
        assertEquals(3,s.correctCount)
        assertEquals(5,s.total)
    }

    @Test fun invalidSelectionDoesNotWriteOrGrade() {
        val store = FileNotebook(File(folder.root,"wrong.txt"))
        val s = session(store)
        assertThrows(IllegalArgumentException::class.java) { s.submit(4) }
        assertNull(s.answer)
        assertTrue(store.ids().isEmpty())
    }

    @Test fun cannotSilentlyCreateAmbiguousQuestionWithInsufficientPool() {
        assertThrows(IllegalArgumentException::class.java) { QuestionFactory(fixture().take(2)).create(fixture().first()) }
    }
}
