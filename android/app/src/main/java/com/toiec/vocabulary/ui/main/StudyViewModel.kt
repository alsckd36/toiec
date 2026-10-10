package com.toiec.vocabulary.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.toiec.vocabulary.data.Catalog
import com.toiec.vocabulary.data.FileNotebook
import com.toiec.vocabulary.study.Answer
import com.toiec.vocabulary.study.Question
import com.toiec.vocabulary.study.QuestionFactory
import com.toiec.vocabulary.study.StudySession
import com.toiec.vocabulary.study.DailyStudy
import com.toiec.vocabulary.study.DailyStore
import com.toiec.vocabulary.study.DailySession
import com.toiec.vocabulary.speech.*
import com.toiec.vocabulary.analytics.*
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.UUID
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, CUSTOM, NOTEBOOK, QUIZ, RESULT }
data class StudyUiState(
    val catalog: Catalog? = null,
    val selectedDay: Int = 1,
    val screen: Screen = Screen.HOME,
    val notebookIds: Set<String> = emptySet(),
    val question: Question? = null,
    val answer: Answer? = null,
    val index: Int = 0,
    val total: Int = 0,
    val correctCount: Int = 0,
    val review: Boolean = false,
    val daily: Boolean = false,
    val dailyRound: Int = 1,
    val dailyDay: Int = 1,
    val dailyAnswered: Int = 0,
    val sessionRound: Int = 1,
    val sessionDay: Int = 1,
    val sessionKey: String = "",
    val soundOn: Boolean = true,
    val soundSaving: Boolean = false,
    val speechStatus: SpeechStatus = SpeechStatus.LOADING,
    val busy: Boolean = true,
    val error: String? = null,
    val analytics: AnalyticsSummary = AnalyticsSummary.empty(),
)

class StudyViewModel(application: Application) : AndroidViewModel(application) {
    private val notebook = FileNotebook(File(application.filesDir, "wrong-answers-v1.txt"))
    private val mutable = MutableStateFlow(StudyUiState())
    val state = mutable.asStateFlow()
    private var session: StudySession? = null
    private var dailyStudy: DailyStudy? = null
    private var dailySession: DailySession? = null
    private var history: HistoryStore? = null
    private val soundSettings = SoundSettings(File(application.filesDir, "sound-settings-v1.txt"))
    private val speechEngine = AndroidSpeech(application, ::speechStatusChanged)
    private val speech = SpeechController(speechEngine)
    init {
        speechEngine.initialize(); load()
        viewModelScope.launch { while(isActive) { delay(60_000); refreshAnalytics() } }
    }
    fun refreshAnalytics() {
        val ledger=history ?: return
        mutable.value=mutable.value.copy(analytics=AnalyticsSummary.from(ledger.records(),LocalDate.now()))
    }

    private fun speechStatusChanged(status: SpeechStatus) {
        mutable.value = mutable.value.copy(speechStatus = status)
        speech.setStatus(status)
    }
    fun setSpeechForeground(active: Boolean) {
        speech.setForeground(active)
        if (active) refreshAnalytics()
        if (active && mutable.value.speechStatus == SpeechStatus.UNAVAILABLE) speechEngine.initialize()
    }
    fun replay() { speech.replay() }
    fun toggleSound() {
        if (mutable.value.soundSaving) return
        val previous = mutable.value.soundOn
        val enabled = !previous
        speech.setAutomatic(enabled)
        mutable.value = mutable.value.copy(soundOn = enabled, soundSaving = true, error = null)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { soundSettings.write(enabled) }
                mutable.value = mutable.value.copy(soundSaving = false)
            } catch (_: Exception) {
                speech.setAutomatic(previous)
                mutable.value = mutable.value.copy(soundOn = previous, soundSaving = false, error = "소리 설정을 저장하지 못했습니다. 다시 시도해 주세요.")
            }
        }
    }
    private fun updateSpeechQuestion() {
        val state = mutable.value
        val question = state.question
        if (state.screen == Screen.QUIZ && question != null) {
            speech.showQuestion("${state.sessionKey}:${state.index}:${question.vocabulary.id}", question.vocabulary.word)
        } else speech.showQuestion(null, null)
    }
    override fun onCleared() { speech.close(); super.onCleared() }

    fun load() {
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val (data, soundOn, analytics) = withContext(Dispatchers.IO) {
                    val json = getApplication<Application>().assets.open("vocabulary.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val catalog = Catalog.parse(json)
                    val dailyStore=DailyStore(File(getApplication<Application>().filesDir, "daily-progress-v1.json"), catalog)
                    val ledger=HistoryStore(File(getApplication<Application>().filesDir,"answer-history-v1.json"),notebook,dailyStore)
                    ledger.recover()
                    history=ledger
                    val study = DailyStudy(catalog, dailyStore, notebook, history=ledger)
                    val progress = study.progress()
                    dailyStudy = study
                    Triple(Triple(catalog, notebook.ids(), progress),soundSettings.read(),AnalyticsSummary.from(ledger.records(),LocalDate.now()))
                }
                val (catalog, ids, progress) = data
                speech.setAutomatic(soundOn)
                mutable.value = mutable.value.copy(catalog = catalog, notebookIds = ids, soundOn = soundOn, analytics = analytics, dailyRound = progress.round, dailyDay = progress.day, dailyAnswered = progress.answers.size, busy = false)
            } catch (error: Exception) {
                mutable.value = mutable.value.copy(busy = false, error = "단어 데이터 또는 학습 기록을 불러오지 못했습니다. 다시 시도해 주세요.")
            }
        }
    }

    fun selectDay(day: Int) { mutable.value = mutable.value.copy(selectedDay = day) }
    fun showCustom() { if (!mutable.value.busy) { speech.showQuestion(null, null); mutable.value = mutable.value.copy(screen = Screen.CUSTOM, error = null) } }
    fun showNotebook() { if (!mutable.value.busy) { speech.showQuestion(null, null); mutable.value = mutable.value.copy(screen = Screen.NOTEBOOK, error = null) } }
    fun home() {
        if (mutable.value.busy) return
        speech.showQuestion(null, null)
        session = null
        dailySession = null
        mutable.value = mutable.value.copy(screen = Screen.HOME, question = null, answer = null, error = null)
        refreshAnalytics()
    }

    fun startDaily() {
        if (mutable.value.busy || mutable.value.screen == Screen.QUIZ) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val active = withContext(Dispatchers.IO) { checkNotNull(dailyStudy).start() }
                session = null
                dailySession = active
                mutable.value = mutable.value.copy(sessionKey = UUID.randomUUID().toString())
                publishDaily()
            } catch (error: Exception) {
                mutable.value = mutable.value.copy(busy = false, error = "오늘의 학습을 불러오거나 저장하지 못했습니다. 다시 시도해 주세요.")
            }
        }
    }

    fun start(review: Boolean) {
        if (mutable.value.busy) return
        val catalog = mutable.value.catalog ?: return
        val entries = if (review) catalog.entries.filter { it.id in mutable.value.notebookIds }.distinctBy { it.id }
                      else catalog.entries.filter { it.day == mutable.value.selectedDay }
        if (entries.isEmpty()) return
        try {
            dailySession = null
            session = StudySession(entries, QuestionFactory(catalog.entries), notebook, review, history)
            mutable.value = mutable.value.copy(sessionKey = UUID.randomUUID().toString())
            publish()
        } catch (error: Exception) {
            mutable.value = mutable.value.copy(error = "문제를 구성하지 못했습니다. 홈에서 다시 시작해 주세요.")
        }
    }

    fun submit(option: Int) {
        val submittedAt=history?.now()
        dailySession?.let { active ->
            if (mutable.value.busy || active.answer != null || active.complete) return
            mutable.value = mutable.value.copy(busy = true, error = null)
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) { active.submit(option,submittedAt) }
                    publishDaily()
                } catch (error: Exception) {
                    refreshAnalytics()
                    mutable.value = mutable.value.copy(busy = false, error = "학습 진행을 저장하지 못했습니다. 홈에서 이어가거나 답을 다시 선택해 주세요.")
                }
            }
            return
        }
        val active = session ?: return
        if (mutable.value.busy || active.answer != null) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { active.submit(option,submittedAt) }
                val id = active.question!!.vocabulary.id
                val ids = when {
                    !result.isCorrect -> mutable.value.notebookIds + id
                    active.review -> mutable.value.notebookIds - id
                    else -> mutable.value.notebookIds
                }
                mutable.value = mutable.value.copy(notebookIds = ids)
                publish()
            } catch (error: Exception) {
                refreshAnalytics()
                mutable.value = mutable.value.copy(busy = false, error = "풀이 기록 또는 오답 기록을 저장하지 못했습니다. 저장 공간을 확인하고 답을 다시 선택해 주세요.")
            }
        }
    }

    fun next() {
        if (mutable.value.busy) return
        dailySession?.let {
            if (mutable.value.screen != Screen.QUIZ || it.answer == null) return
            speech.stop(); it.next(); publishDaily(); return
        }
        if (mutable.value.screen != Screen.QUIZ || session?.answer == null) return
        try { speech.stop(); session?.next(); publish() }
        catch (error: Exception) { mutable.value = mutable.value.copy(error = "다음 문제를 구성하지 못했습니다. 홈에서 다시 시작해 주세요.") }
    }

    private fun publishDaily() {
        val active = dailySession ?: return
        // No disk work on the UI thread: accepted writes have already completed.
        mutable.value = mutable.value.copy(
            screen = if (active.complete) Screen.RESULT else Screen.QUIZ,
            question = active.question, answer = active.answer, index = active.index, total = active.total,
            correctCount = active.correctCount, review = false, daily = true,
            sessionRound = active.round, sessionDay = active.day,
            dailyRound = active.progress.round,
            dailyDay = active.progress.day,
            dailyAnswered = active.progress.answers.size,
            notebookIds = if (active.answer?.isCorrect == false) mutable.value.notebookIds + active.question!!.vocabulary.id else mutable.value.notebookIds,
            busy = false, error = null,
        )
        updateSpeechQuestion()
        refreshAnalytics()
    }

    private fun publish() {
        val active = session ?: return
        mutable.value = mutable.value.copy(
            screen = if (active.complete) Screen.RESULT else Screen.QUIZ,
            question = active.question, answer = active.answer,
            index = active.index, total = active.total, correctCount = active.correctCount,
            review = active.review, daily = false, busy = false, error = null,
        )
        updateSpeechQuestion()
        refreshAnalytics()
    }
}
