package com.toiec.vocabulary

import com.toiec.vocabulary.analytics.*
import com.toiec.vocabulary.data.*
import com.toiec.vocabulary.study.*
import java.io.File
import java.time.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AnalyticsTest {
    @get:Rule val folder=TemporaryFolder()
    private val catalog by lazy { Catalog.parse(File(requireNotNull(System.getProperty("vocabulary.asset"))).readText()) }
    private val notebook by lazy { FileNotebook(File(folder.root,"wrong-answers-v1.txt")) }
    private val daily by lazy { DailyStore(File(folder.root,"daily-progress-v1.json"),catalog) }
    private var time=ZonedDateTime.of(2026,10,10,12,0,0,0,ZoneId.of("Asia/Seoul"))
    private fun history(store: Notebook=notebook)=HistoryStore(File(folder.root,"answer-history-v1.json"),store,daily) { time }
    private fun custom(ledger: HistoryStore,review: Boolean=false)=StudySession(catalog.entries.take(1),QuestionFactory(catalog.entries,Random(3)),notebook,review,ledger)
    private fun answer(session: StudySession,right: Boolean=true): Answer {
        val correct=session.question!!.options.indexOf(session.question!!.vocabulary.meaning)
        return session.submit(if(right) correct else (correct+1)%4)
    }
    private fun dailyAnswer(session: DailySession,right: Boolean=true) {
        val correct=session.question!!.options.indexOf(session.question!!.vocabulary.meaning)
        session.submit(if(right) correct else (correct+1)%4)
    }
    @Test fun threeCorrectOneWrongProduceFourAndSeventyFivePercent() {
        val ledger=history()
        repeat(3) { answer(custom(ledger)) }; answer(custom(ledger),false)
        val day=AnalyticsSummary.from(ledger.records(),time.toLocalDate()).today
        assertEquals(4,day.total); assertEquals(3,day.correct); assertEquals(1,day.wrong); assertEquals(75.0,day.accuracy!!,0.0)
    }
    @Test fun repeatedSameWordAndDayCountEveryNewSessionEvenAfterCorrect() {
        val ledger=history()
        answer(custom(ledger)); answer(custom(ledger)); answer(custom(ledger),false)
        assertEquals(1,ledger.records().map { it.wordId }.distinct().size)
        assertEquals(3,ledger.records().map { it.id }.distinct().size)
        val day=AnalyticsSummary.from(ledger.records(),time.toLocalDate()).today
        assertEquals(3,day.total); assertEquals(2,day.correct); assertEquals(1,day.wrong)
    }
    @Test fun partialExitReentryAddsButQuestionAndAnswerRedisplayDoNot() {
        val ledger=history()
        val session=custom(ledger)
        repeat(4) { session.question; session.answer; ledger.records() }
        assertTrue(ledger.records().isEmpty())
        val accepted=answer(session,false)
        repeat(8) { assertEquals(accepted,session.submit(0)); session.question; session.answer }
        assertEquals(1,ledger.records().size)
        answer(custom(history())) // new process/session after leaving
        assertEquals(2,history().records().size)
    }
    @Test fun midnightUsesEachSubmissionDateAndStoredDatesNeverRebucket() {
        val ledger=history()
        val entries=catalog.entries.take(2)
        val session=StudySession(entries,QuestionFactory(catalog.entries),notebook,false,ledger)
        time=time.withHour(23).withMinute(59)
        answer(session); session.next()
        time=time.plusMinutes(2)
        answer(session,false)
        val records=history().records()
        assertEquals(listOf(LocalDate.of(2026,10,10),LocalDate.of(2026,10,11)),records.map { it.localDate })
        val summary=AnalyticsSummary.from(records,time.toLocalDate())
        assertEquals(1,summary.today.total); assertEquals(0,summary.today.correct)
        assertEquals(1,summary.days[5].total); assertEquals(100.0,summary.days[5].accuracy!!,0.0)
        time=time.withZoneSameInstant(ZoneId.of("America/Los_Angeles"))
        assertEquals(records.map { it.localDate },history().records().map { it.localDate })
    }
    @Test fun emptyAndGapDaysHaveNoAccuracyButZeroCountsAndExactlySevenDates() {
        val empty=AnalyticsSummary.empty(time.toLocalDate())
        assertFalse(empty.hasRecords); assertNull(empty.today.accuracy); assertEquals(0,empty.today.total)
        val ledger=history(); answer(custom(ledger))
        time=time.plusDays(2)
        val summary=AnalyticsSummary.from(ledger.records(),time.toLocalDate())
        assertTrue(summary.hasRecords); assertEquals(7,summary.days.size)
        assertNull(summary.days[5].accuracy); assertNull(summary.today.accuracy)
        assertEquals(0,summary.today.total); assertEquals(1,summary.days[4].total)
    }
    @Test fun aggregatesAnswersNotMeanOfSessionRatesOrCumulativeDates() {
        val ledger=history()
        answer(custom(ledger))
        repeat(9) { answer(custom(ledger),false) }
        time=time.plusDays(1); repeat(3) { answer(custom(ledger)) }
        val summary=AnalyticsSummary.from(ledger.records(),time.toLocalDate())
        assertEquals(10,summary.days[5].total); assertEquals(10.0,summary.days[5].accuracy!!,0.0)
        assertEquals(3,summary.today.total); assertEquals(100.0,summary.today.accuracy!!,0.0)
        summary.days.forEach { assertEquals(it.correct+it.wrong,it.total) }
    }
    @Test fun reviewRemovalPreservesPastWrongAndAddsCorrectSubmission() {
        val ledger=history()
        answer(custom(ledger),false); assertEquals(1,notebook.ids().size)
        answer(custom(ledger,review=true)); assertTrue(notebook.ids().isEmpty())
        val records=history().records()
        assertEquals(2,records.size); assertFalse(records[0].isCorrect); assertTrue(records[1].isCorrect)
        assertEquals(LearningMode.REVIEW,records[1].mode)
    }
    @Test fun dailyResumeKeepsSubmissionsAndRecordsRoundDayAndMode() {
        val ledger=history()
        var session=DailyStudy(catalog,daily,notebook,Random(2),ledger).start()
        dailyAnswer(session); val order=daily.read().questions
        session=DailyStudy(catalog,daily,notebook,Random(9),history()).start()
        assertEquals(1,session.index); assertEquals(order[1],session.question)
        dailyAnswer(session,false)
        val records=history().records()
        assertEquals(2,records.size)
        assertTrue(records.all { it.round==1 && it.day==1 && it.mode==LearningMode.DAILY })
    }
    @Test fun pendingJournalRecoversAfterProcessDeathWithoutDuplicateOrLostDailyAnswer() {
        val failing=object: Notebook {
            override fun ids()=notebook.ids()
            override fun update(id: String,add: Boolean) { error("Interrupted notebook save") }
        }
        val ledger=history(failing)
        val session=DailyStudy(catalog,daily,notebook,Random(2),ledger).start()
        try { dailyAnswer(session,false); fail("Expected interrupted commit") } catch(_: IllegalStateException) { }
        assertNull(session.answer); assertEquals(0,daily.read().answers.size)
        assertEquals(1,ledger.records().size)
        val recovered=history(); recovered.recover()
        val resumed=DailyStudy(catalog,daily,notebook,Random(7),recovered).start()
        assertEquals(1,resumed.index); assertEquals(1,notebook.ids().size)
        assertEquals(1,recovered.records().size)
        repeat(4) { history().recover() }
        assertEquals(1,history().records().size)
    }
    @Test fun retryKeepsOriginalAnswerTimestampAndOneRecordEvenWhenClockChanges() {
        var failOnce=true
        val flaky=object: Notebook {
            override fun ids()=notebook.ids()
            override fun update(id: String,add: Boolean) {
                if(failOnce) { failOnce=false; error("Interrupted write") }; notebook.update(id,add)
            }
        }
        val ledger=history(flaky); val session=custom(ledger)
        try { answer(session,false); fail("Expected interrupted write") } catch(_: IllegalStateException) { }
        val event=ledger.records().single()
        time=time.plusDays(1)
        assertEquals(event.answer,answer(session,true)) // a retry cannot change the first durable submission
        assertEquals(listOf(event),history().records())
    }
    @Test fun oldDailyAndNotebookAreNotInventedAsHistoricalAnswers() {
        notebook.update(catalog.entries.first().id,true)
        val old=DailyStudy(catalog,daily,notebook,Random(1)).start()
        dailyAnswer(old); old.next(); dailyAnswer(old,false)
        val ledger=history(); ledger.recover()
        assertTrue(ledger.records().isEmpty())
        val new=DailyStudy(catalog,daily,notebook,Random(4),ledger).start()
        assertEquals(2,new.index); dailyAnswer(new)
        assertEquals(1,ledger.records().size)
    }
    @Test fun dayThirtyInterruptedLastAnswerAdvancesRoundOnlyOnce() {
        daily.write(DailyProgress(round=2,day=30))
        val ledger=history(); val session=DailyStudy(catalog,daily,notebook,Random(1),ledger).start()
        repeat(session.total-1) { dailyAnswer(session); session.next() }
        dailyAnswer(session)
        repeat(5) { session.submit(0); history().recover() }
        assertEquals(3,daily.read().round); assertEquals(1,daily.read().day)
        assertEquals(session.total,history().records().size)
        assertTrue(history().records().all { it.round==2 && it.day==30 })
    }
}
