package com.toiec.vocabulary.study

import com.toiec.vocabulary.analytics.*
import java.time.ZonedDateTime
import java.util.UUID
import com.toiec.vocabulary.data.Notebook
import com.toiec.vocabulary.data.Vocabulary
import java.text.Normalizer
import kotlin.random.Random

data class Question(val vocabulary: Vocabulary, val options: List<String>)
data class Answer(val selected: String, val correct: String) { val isCorrect: Boolean get() = selected == correct }

/** Conservatively excludes known alternate senses and overlapping glosses, including duplicate English entries. */
class QuestionFactory(private val pool: List<Vocabulary>, private val random: Random = Random.Default) {
    // Broad equivalence families are exclusions, never replacement translations.
    // Excluding near-synonyms is deliberately conservative: it is better to pick a clearly unrelated distractor.
    private val synonymFamilies = listOf(
        listOf("중요", "핵심", "필수", "중대", "결정적"),
        listOf("흥미", "재미", "매혹", "매력", "호기심"),
        listOf("신뢰", "신용", "믿을", "확실", "명백", "분명"),
        listOf("당황", "혼란", "혼동", "어리둥절"),
        listOf("증가", "늘리", "늘어", "상승", "높이", "증대", "인상"),
        listOf("감소", "줄어", "줄이", "줄다", "축소", "하락", "저하", "떨어"),
        listOf("수집", "모으", "모으다", "축적", "얻다", "획득", "습득", "거두"),
        listOf("제공", "공급", "지급", "배급", "나누어", "분배"),
        listOf("억제", "제지", "막다", "방지", "방해", "금지", "예방"),
        listOf("연기", "미루", "지연", "늦게", "뒤늦"),
        listOf("요구", "요청", "간청", "청구"),
        listOf("고용", "채용", "모집"),
        listOf("능숙", "능력", "유능", "다재", "우수", "뛰어", "탁월"),
        listOf("평온", "고요", "차분", "평화", "침착"),
        listOf("포기", "그만두", "물러", "사임", "사직"),
        listOf("허가", "허용", "승인", "인가"),
        listOf("수정", "변경", "바꾸", "변화", "개정"),
        listOf("일치", "부합", "준수", "따르", "조화"),
        listOf("견디", "참다", "인내", "견뎌"),
        listOf("필요", "요구", "필수"),
        listOf("가짜", "허위", "거짓", "모조"),
    )
    private fun tokens(meaning: String): Set<String> {
        val base = meaning
        .replace(Regex("\\([^)]*\\)"), "")
        .split(Regex("[,;，；/·]"))
        .map { Normalizer.normalize(it, Normalizer.Form.NFKC).replace(Regex("[\\s~.!?]"), "").lowercase() }
        .filter { it.isNotBlank() }.toSet()
        val families = synonymFamilies.mapIndexedNotNull { index, stems ->
            if (base.any { token -> stems.any(token::contains) }) "#family$index" else null
        }
        return base + families
    }

    private fun overlaps(left: Set<String>, right: Set<String>): Boolean = left.any { a ->
        right.any { b -> a == b || (!a.startsWith("#") && !b.startsWith("#") && minOf(a.length, b.length) >= 2 && (a.contains(b) || b.contains(a))) }
    }

    fun create(entry: Vocabulary): Question {
        val known = pool.filter { it.id == entry.id }.flatMap { tokens(it.meaning) }.toSet() + tokens(entry.meaning)
        val selected = mutableListOf(entry.meaning)
        val used = known.toMutableSet()
        for (candidate in pool.shuffled(random)) {
            val parts = tokens(candidate.meaning)
            if (candidate.id == entry.id || parts.isEmpty() || overlaps(used, parts)) continue
            if (selected.any { it.trim() == candidate.meaning.trim() }) continue
            selected += candidate.meaning
            used += parts
            if (selected.size == 4) break
        }
        require(selected.size == 4) { "서로 다른 안전한 보기를 구성할 수 없습니다: ${entry.word}" }
        return Question(entry, selected.shuffled(random))
    }
}

class StudySession(
    entries: List<Vocabulary>,
    private val factory: QuestionFactory,
    private val notebook: Notebook,
    val review: Boolean,
    private val history: HistoryStore? = null,
) {
    private val entries = entries.toList()
    private val token = UUID.randomUUID().toString()
    var index = 0; private set
    var correctCount = 0; private set
    val total: Int get() = entries.size
    var question: Question? = entries.firstOrNull()?.let(factory::create); private set
    var answer: Answer? = null; private set
    val complete: Boolean get() = index >= total

    fun submit(option: Int, submittedAt: ZonedDateTime? = null): Answer {
        answer?.let { return it }
        val question = checkNotNull(question)
        require(option in question.options.indices)
        val key = "${if(review) "review" else "custom"}:$token:$index"
        val result = history?.existing(key)?.answer ?: Answer(question.options[option], question.vocabulary.meaning)
        // Persist first. Failed writes leave the question unanswered and safe to retry.
        if (history == null) {
            if (!result.isCorrect) notebook.update(question.vocabulary.id, add = true)
            else if (review) notebook.update(question.vocabulary.id, add = false)
        } else history.accept(key,question,if(review) LearningMode.REVIEW else LearningMode.CUSTOM,null,result,
            SubmissionEffects(if(!result.isCorrect || review) question.vocabulary.id else null,!result.isCorrect),submittedAt)
        if (result.isCorrect) correctCount++
        answer = result
        return result
    }

    fun next() {
        check(answer != null) { "답을 먼저 선택하세요." }
        val nextIndex = index + 1
        val nextQuestion = entries.getOrNull(nextIndex)?.let(factory::create)
        index = nextIndex
        question = nextQuestion
        answer = null
    }
}
