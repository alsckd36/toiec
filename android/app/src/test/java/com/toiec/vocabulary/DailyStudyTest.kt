package com.toiec.vocabulary

import com.toiec.vocabulary.data.*
import com.toiec.vocabulary.study.*
import java.io.File
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DailyStudyTest {
    @get:Rule val folder = TemporaryFolder()
    private fun catalog() = Catalog.parse(File(requireNotNull(System.getProperty("vocabulary.asset"))).readText())
    private fun notebook() = FileNotebook(File(folder.root, "wrong-answers-v1.txt"))
    private fun store() = DailyStore(File(folder.root, "daily-progress-v1.json"), catalog())
    private fun engine(seed: Int = 42) = DailyStudy(catalog(), store(), notebook(), Random(seed))
    private fun answer(session: DailySession, right: Boolean = true): Answer {
        val correct = session.question!!.options.indexOf(session.question!!.vocabulary.meaning)
        return session.submit(if (right) correct else (correct + 1) % 4)
    }
    private fun finish(session: DailySession, right: Boolean = true) {
        while (!session.complete) { if (session.answer == null) answer(session, right); session.next() }
    }

    @Test fun firstLaunchStartsRoundOneDayOneAndFreezesEveryQuestion() {
        assertEquals(DailyProgress(), engine().progress())
        val session = engine().start()
        assertEquals(1,session.round); assertEquals(1,session.day); assertEquals(32,session.total)
        val saved = store().read()
        assertEquals(32,saved.questions.size)
        assertEquals(catalog().entries.filter { it.day == 1 }.map { it.id }.toSet(), saved.questions.map { it.vocabulary.id }.toSet())
        saved.questions.forEach { assertEquals(4,it.options.distinct().size); assertEquals(1,it.options.count { value -> value == it.vocabulary.meaning }) }
        assertEquals(saved,store().read())
    }
    @Test fun processRecreationResumesUnansweredWithExactQuestionAndOptionOrders() {
        val session = engine().start()
        val order = store().read().questions
        answer(session,false); session.next(); answer(session,true)
        // Kill before pressing Next. The accepted answer must never be presented for resubmission.
        val resumed = engine(123).start()
        assertEquals(2,resumed.index); assertEquals(1,resumed.correctCount)
        assertNull(resumed.answer); assertEquals(order[2],resumed.question)
        assertEquals(order,store().read().questions)
        assertEquals(1,notebook().ids().size)
    }
    @Test fun lastAnswerCompletesDespiteWrongAnswersBeforeResultOrNextButton() {
        val session = engine().start()
        repeat(session.total - 1) { answer(session,false); session.next() }
        answer(session,false)
        val next = engine(8).progress()
        assertEquals(1,next.round); assertEquals(2,next.day)
        assertTrue(next.questions.isEmpty()); assertEquals(32,notebook().ids().size)
        val resumed = engine(8).start()
        assertEquals(2,resumed.day); assertEquals(0,resumed.index)
        assertEquals(0,resumed.correctCount)
    }
    @Test fun sameDayCanStartNextDayWithoutAnyClockOrDateDependency() {
        finish(engine().start())
        val next = engine().start()
        assertEquals(2,next.day); assertEquals(1,next.round)
        finish(next)
        assertEquals(3,engine().start().day)
    }
    @Test fun customAndReviewNeverChangeDailyProgress() {
        val daily = engine().start(); answer(daily,false)
        val before = store().read()
        val custom = StudySession(catalog().entries.filter { it.day == 30 },QuestionFactory(catalog().entries,Random(1)),notebook(),false)
        while(!custom.complete) { custom.submit(custom.question!!.options.indexOf(custom.question!!.vocabulary.meaning)); custom.next() }
        val wrong = catalog().entries.filter { it.id in notebook().ids() }
        val review = StudySession(wrong,QuestionFactory(catalog().entries),notebook(),true)
        review.submit(review.question!!.options.indexOf(review.question!!.vocabulary.meaning)); review.next()
        assertEquals(before,store().read()); assertTrue(notebook().ids().isEmpty())
        assertEquals(1,engine().start().index)
    }
    @Test fun dayThirtyMovesOnceToNextRoundDayOneAndKeepsNotebook() {
        val id = catalog().entries.first().id
        notebook().update(id,true)
        store().write(DailyProgress(round=1,day=30))
        val session = engine().start()
        repeat(session.total-1) { answer(session); session.next() }
        val first = answer(session)
        repeat(10) { assertEquals(first,session.submit(0)) }
        assertEquals(2,store().read().round); assertEquals(1,store().read().day)
        repeat(10) { session.next() }
        assertTrue(session.complete)
        val next = engine(456).start()
        assertEquals(2,next.round); assertEquals(1,next.day); assertEquals(0,next.index)
        assertEquals(0,next.correctCount); assertEquals(setOf(id),notebook().ids())
        repeat(4) { assertEquals(2,engine().progress().round) }
    }
    @Test fun fullRoundsIncrementExactlyOnceAndNewRoundHasNewOrderAndAnswers() {
        val first = engine(42).start()
        val firstOrder = store().read().questions
        finish(first)
        for(day in 2..30) { val session=engine(day).start(); assertEquals(day,session.day); finish(session) }
        val second = engine(987).start()
        assertEquals(2,second.round); assertEquals(1,second.day)
        assertTrue(store().read().answers.isEmpty())
        assertNotEquals(firstOrder,store().read().questions)
        assertNotEquals(firstOrder.map { it.vocabulary.id },store().read().questions.map { it.vocabulary.id })
        assertTrue(firstOrder.indices.any { firstOrder[it].options != store().read().questions[it].options })
        finish(second)
        for(day in 2..30) finish(engine(day+100).start())
        assertEquals(3,engine().progress().round); assertEquals(1,engine().progress().day)
    }
    @Test fun staleSessionCannotDoubleIncrementOrWriteWrongNotebookEntries() {
        store().write(DailyProgress(round=1,day=30))
        val original=engine().start()
        repeat(original.total-1) { answer(original); original.next() }
        val stale=engine().start()
        answer(original)
        try { answer(stale,false); fail("Expected stale session rejection") } catch(_: IllegalStateException) { }
        assertEquals(2,engine().progress().round); assertTrue(notebook().ids().isEmpty())
    }
    @Test fun failedProgressWriteDoesNotAcceptAnswerAndCanBeRetried() {
        val session=engine().start()
        val file=File(folder.root,"daily-progress-v1.json")
        val saved=file.readBytes()
        file.delete(); file.mkdir()
        try { answer(session,false); fail("Expected write failure") } catch(_: Exception) { }
        assertNull(session.answer); assertEquals(0,session.correctCount)
        file.delete(); file.writeBytes(saved)
        answer(session,false)
        assertEquals(1,store().read().answers.size); assertEquals(1,notebook().ids().size)
    }
    @Test fun notebookFailureCannotAcceptDailyAnswerOrAdvanceDay() {
        val session=engine().start()
        val before=store().read()
        File(folder.root,"wrong-answers-v1.txt").mkdir()
        try { answer(session,false); fail("Expected notebook write failure") } catch(_: Exception) { }
        assertNull(session.answer)
        assertEquals(before,store().read())
    }
    @Test fun unreadableProgressIsNeverSilentlyResetAndNotebookIsPreserved() {
        val id=catalog().entries.first().id
        notebook().update(id,true)
        val file=File(folder.root,"daily-progress-v1.json")
        file.writeText("invalid saved state")
        try { engine().start(); fail("Expected invalid state rejection") } catch(_: Exception) { }
        assertEquals("invalid saved state",file.readText())
        assertEquals(setOf(id),notebook().ids())
    }

}
