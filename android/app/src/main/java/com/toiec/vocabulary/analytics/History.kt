package com.toiec.vocabulary.analytics

import com.toiec.vocabulary.data.Notebook
import com.toiec.vocabulary.study.*
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.ZonedDateTime
import org.json.JSONArray
import org.json.JSONObject

enum class LearningMode { DAILY, CUSTOM, REVIEW }
data class Submission(
    val id: String, val wordId: String, val mode: LearningMode, val round: Int?, val day: Int,
    val selected: String, val correct: String, val submittedAt: String, val localDate: LocalDate,
) {
    val isCorrect get() = selected == correct
    val answer get() = Answer(selected, correct)
}
data class SubmissionEffects(val wordId: String? = null, val addWrong: Boolean = false, val daily: String? = null)

/** Durable submission + recovery journal. Replays effects idempotently after interrupted saves. */
class HistoryStore(
    private val file: File, private val notebook: Notebook, private val dailyStore: DailyStore,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    private var loaded = false
    private var events = emptyList<Submission>()
    private var pending: SubmissionEffects? = null
    fun now(): ZonedDateTime = clock()
    @Synchronized fun records(): List<Submission> { load(); return events.toList() }
    @Synchronized fun existing(id: String): Submission? { load(); return events.firstOrNull { it.id == id } }
    private fun load() {
        if (loaded) return
        if (file.exists()) {
            val root=JSONObject(file.readText())
            require(root.getInt("version")==1)
            val rows=root.getJSONArray("events")
            events=List(rows.length()) { i -> decode(rows.getJSONObject(i)) }
            require(events.distinctBy { it.id }.size == events.size)
            pending=root.optJSONObject("pending")?.let { row ->
                SubmissionEffects(if(row.isNull("wordId")) null else row.getString("wordId"),row.getBoolean("addWrong"),if(row.isNull("daily")) null else row.getString("daily"))
            }
        }
        loaded=true
    }
    @Synchronized fun recover() {
        load()
        val effects=pending ?: return
        effects.wordId?.let { notebook.update(it,effects.addWrong) }
        effects.daily?.let(dailyStore::restore)
        persist(events,null)
        pending=null
    }
    @Synchronized fun accept(
        id: String, question: Question, mode: LearningMode, round: Int?, answer: Answer,
        effects: SubmissionEffects, submittedAt: ZonedDateTime? = null,
    ): Answer {
        load(); recover()
        existing(id)?.let { return it.answer }
        val time=submittedAt ?: now()
        val event=Submission(id,question.vocabulary.id,mode,round,question.vocabulary.day,answer.selected,answer.correct,time.toString(),time.toLocalDate())
        val updated=events+event
        persist(updated,effects) // Accept a submission exactly once before applying independently durable files.
        events=updated; pending=effects
        recover()
        return event.answer
    }
    private fun persist(records: List<Submission>, effects: SubmissionEffects?) {
        val root=JSONObject().put("version",1).put("events",JSONArray(records.map(::encode)))
        root.put("pending",effects?.let { JSONObject().put("wordId",it.wordId ?: JSONObject.NULL).put("addWrong",it.addWrong).put("daily",it.daily ?: JSONObject.NULL) } ?: JSONObject.NULL)
        check(file.parentFile!!.let { it.exists() || it.mkdirs() })
        val temp=File.createTempFile("history-",".tmp",file.parentFile)
        try {
            FileOutputStream(temp).use { it.write(root.toString().toByteArray(Charsets.UTF_8)); it.fd.sync() }
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }
    private fun encode(event: Submission)=JSONObject().put("id",event.id).put("wordId",event.wordId).put("mode",event.mode.name)
        .put("round",event.round ?: JSONObject.NULL).put("day",event.day).put("selected",event.selected).put("correct",event.correct)
        .put("isCorrect",event.isCorrect).put("submittedAt",event.submittedAt).put("localDate",event.localDate.toString())
    private fun decode(row: JSONObject): Submission {
        val event=Submission(row.getString("id"),row.getString("wordId"),LearningMode.valueOf(row.getString("mode")),
            if(row.isNull("round")) null else row.getInt("round"),row.getInt("day"),row.getString("selected"),row.getString("correct"),row.getString("submittedAt"),LocalDate.parse(row.getString("localDate")))
        require(event.isCorrect == row.getBoolean("isCorrect"))
        require(ZonedDateTime.parse(event.submittedAt).toLocalDate() == event.localDate)
        return event
    }
}

data class DayStats(val date: LocalDate, val correct: Int = 0, val wrong: Int = 0) {
    val total get() = correct + wrong
    val accuracy: Double? get() = if(total == 0) null else 100.0 * correct / total
}
data class AnalyticsSummary(val days: List<DayStats>, val today: DayStats) {
    val hasRecords get() = days.isNotEmpty()
    companion object {
        fun from(records: List<Submission>, today: LocalDate): AnalyticsSummary {
            val grouped=records.groupBy { it.localDate }
            fun stats(date: LocalDate): DayStats {
                val answers=grouped[date].orEmpty()
                return DayStats(date,answers.count { it.isCorrect },answers.count { !it.isCorrect })
            }
            val learningDates=grouped.keys.sorted().takeLast(7)
            return AnalyticsSummary(learningDates.map(::stats),stats(today))
        }
        fun empty(today: LocalDate = LocalDate.now()) = from(emptyList(),today)
    }
}
