package com.toiec.vocabulary.study

import com.toiec.vocabulary.analytics.*
import java.time.ZonedDateTime
import com.toiec.vocabulary.data.Catalog
import com.toiec.vocabulary.data.Notebook
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

data class DailyProgress(val round: Int = 1, val day: Int = 1, val token: String = "", val questions: List<Question> = emptyList(), val answers: List<Int> = emptyList())

/** Separate from the existing notebook. A failed replacement never overwrites accepted progress. */
class DailyStore(private val file: File, private val catalog: Catalog) {
    @Synchronized fun read(): DailyProgress {
        if (!file.exists()) return DailyProgress()
        return parse(file.readText())
    }
    private fun parse(json: String): DailyProgress {
        val root = JSONObject(json)
        require(root.getInt("version") == 1)
        val round = root.getInt("round"); val day = root.getInt("day")
        require(round >= 1 && day in 1..30)
        val rows = root.getJSONArray("questions")
        val questions = List(rows.length()) { i ->
            val row = rows.getJSONObject(i)
            val entry = catalog.entries.single { it.id == row.getString("id") }
            val choices = row.getJSONArray("options")
            val options = List(choices.length()) { choices.getString(it) }
            require(entry.day == day && options.size == 4 && options.distinct().size == 4 && options.count { it == entry.meaning } == 1)
            Question(entry, options)
        }
        val values = root.getJSONArray("answers")
        val answers = List(values.length()) { values.getInt(it) }
        require(answers.all { it in 0..3 } && (questions.isEmpty() && answers.isEmpty() || answers.size < questions.size))
        if (questions.isNotEmpty()) require(questions.map { it.vocabulary.id }.toSet() == catalog.entries.filter { it.day == day }.map { it.id }.toSet() && questions.distinctBy { it.vocabulary.id }.size == questions.size)
        return DailyProgress(round, day, root.getString("token"), questions, answers)
    }
    fun encode(progress: DailyProgress): String {
        val root = JSONObject().put("version", 1).put("round", progress.round).put("day", progress.day).put("token", progress.token)
            .put("questions", JSONArray(progress.questions.map { JSONObject().put("id", it.vocabulary.id).put("options", JSONArray(it.options)) }))
            .put("answers", JSONArray(progress.answers))
        return root.toString()
    }
    @Synchronized fun restore(json: String) { write(parse(json)) }
    @Synchronized fun write(progress: DailyProgress) {
        val json = encode(progress)
        check(file.parentFile!!.let { it.exists() || it.mkdirs() })
        val temp = File.createTempFile("daily-", ".tmp", file.parentFile)
        try {
            FileOutputStream(temp).use { it.write(json.toByteArray(Charsets.UTF_8)); it.fd.sync() }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }
    @Synchronized fun accept(expected: DailyProgress, updated: DailyProgress, notebookWrite: () -> Unit) {
        check(read() == expected) { "이미 처리된 학습입니다. 홈에서 이어가세요." }
        notebookWrite() // A repeated notebook add is harmless if the progress write fails.
        write(updated)
    }
}

class DailyStudy(private val catalog: Catalog, private val store: DailyStore, private val notebook: Notebook, private val random: Random = Random.Default, private val history: HistoryStore? = null) {
    fun progress(): DailyProgress { history?.recover(); return store.read() }
    fun start(): DailySession {
        history?.recover()
        var progress = store.read()
        if (progress.questions.isEmpty()) {
            val factory = QuestionFactory(catalog.entries, random)
            progress = progress.copy(token = UUID.randomUUID().toString(), questions = catalog.entries.filter { it.day == progress.day }.shuffled(random).map(factory::create))
            store.write(progress) // Freeze every question and option order before displaying the first one.
        }
        return DailySession(progress, store, notebook, history)
    }
}

class DailySession(private var saved: DailyProgress, private val store: DailyStore, private val notebook: Notebook, private val history: HistoryStore? = null) {
    val progress: DailyProgress get() = saved
    val round = saved.round
    val day = saved.day
    val total = saved.questions.size
    private val questions = saved.questions
    var index = saved.answers.size; private set
    var correctCount = saved.answers.indices.count { questions[it].options[saved.answers[it]] == questions[it].vocabulary.meaning }; private set
    var answer: Answer? = null; private set
    val question: Question? get() = questions.getOrNull(index)
    val complete: Boolean get() = index >= total
    fun submit(option: Int, submittedAt: ZonedDateTime? = null): Answer {
        answer?.let { return it }
        val question = checkNotNull(question)
        require(option in 0..3)
        val key = "daily:${saved.token}:$index"
        val previouslyRecorded = history?.existing(key)
        val result = previouslyRecorded?.answer ?: Answer(question.options[option], question.vocabulary.meaning)
        val acceptedOption = question.options.indexOf(result.selected)
        val answers = saved.answers + acceptedOption
        val updated = if (answers.size == total) {
            if (day == 30) DailyProgress(round = Math.addExact(round, 1), day = 1) else DailyProgress(round = round, day = day + 1)
        } else saved.copy(answers = answers)
        if (history == null) store.accept(saved, updated) { if (!result.isCorrect) notebook.update(question.vocabulary.id, true) }
        else {
            if (previouslyRecorded == null) check(store.read() == saved) { "이미 처리된 학습입니다." }
            history.accept(key,question,LearningMode.DAILY,round,result,
                SubmissionEffects(if(!result.isCorrect) question.vocabulary.id else null,true,store.encode(updated)),submittedAt)
        }
        saved = updated
        if (result.isCorrect) correctCount++
        answer = result
        return result
    }
    fun next() {
        if (complete) return
        check(answer != null)
        index++
        answer = null
    }
}
