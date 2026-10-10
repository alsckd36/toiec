package com.toiec.vocabulary.ui.main

import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.toiec.vocabulary.R
import com.toiec.vocabulary.speech.SpeechStatus
import androidx.compose.ui.semantics.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toiec.vocabulary.data.Catalog
import com.toiec.vocabulary.data.Day
import com.toiec.vocabulary.data.Vocabulary
import com.toiec.vocabulary.study.Question
import com.toiec.vocabulary.theme.Theme

@Composable
fun HomeScreen(model: StudyViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var voiceSettingsError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(model, lifecycleOwner) {
        model.setSpeechForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.setSpeechForeground(true)
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) model.setSpeechForeground(false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); model.setSpeechForeground(false) }
    }
    val openVoiceSettings = {
        voiceSettingsError = null
        try { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
        catch (_: Exception) {
            try { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
            catch (_: Exception) {
                try { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                catch (_: Exception) { voiceSettingsError = "휴대폰 설정에서 텍스트 음성 변환의 영어 음성 데이터를 설치해 주세요." }
            }
        }
    }
    var confirmExit by rememberSaveable { mutableStateOf(false) }
    val requestHome = {
        if (!state.busy) {
            if (state.screen == Screen.QUIZ) confirmExit = true else model.home()
        }
    }
    BackHandler(enabled = state.screen != Screen.HOME, onBack = requestHome)
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false }, title = { Text("학습을 마칠까요?") },
            text = { Text(if (state.daily) "오늘의 학습 진행과 오답 기록은 저장되어 있습니다. 다시 시작하면 미응답 문제부터 이어갑니다." else "저장된 오답 기록은 유지됩니다. 현재 학습 진행은 종료됩니다.") },
            confirmButton = { TextButton(onClick = { confirmExit = false; model.home() }, modifier = Modifier.testTag("confirm-home")) { Text("홈으로") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("계속 학습") } },
        )
    }
    StudyContent(state, model::selectDay, model::start, model::showNotebook, requestHome, model::submit, model::next, model::load, model::showCustom, model::startDaily, model::toggleSound, model::replay, openVoiceSettings)
    voiceSettingsError?.let { message -> AlertDialog(onDismissRequest = { voiceSettingsError = null }, title = { Text("음성 설정 안내") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { voiceSettingsError = null }) { Text("확인") } }) }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Action(text: String, tag: String, enabled: Boolean = true, secondary: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(tag)
    if (secondary) {
        OutlinedButton(onClick, modifier, enabled, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), contentPadding = PaddingValues(16.dp)) { Text(text) }
    } else {
        Button(onClick, modifier, enabled, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(16.dp)) { Text(text) }
    }
}

@Composable
fun StudyContent(
    state: StudyUiState,
    selectDay: (Int) -> Unit = {}, start: (Boolean) -> Unit = {}, showNotebook: () -> Unit = {},
    home: () -> Unit = {}, submit: (Int) -> Unit = {}, next: () -> Unit = {}, reload: () -> Unit = {}, showCustom: () -> Unit = {}, startDaily: () -> Unit = {},
    toggleSound: () -> Unit = {}, replay: () -> Unit = {}, voiceSettings: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxHeight().widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).testTag("study-root"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.screen == Screen.HOME) {
                Caption("매일, 한 장씩")
                Text("토익 영단어", style = MaterialTheme.typography.headlineMedium)
            } else {
                TextButton(onClick = home, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp).testTag("home"), contentPadding = PaddingValues(horizontal = 0.dp)) { Text("‹  홈으로") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            val catalog = state.catalog
            if (catalog == null) {
                if (state.busy) CircularProgressIndicator() else Action("다시 불러오기", "reload", onClick = reload)
            } else when (state.screen) {
                Screen.HOME -> {
                    LazyColumn(Modifier.weight(1f).testTag("home-list"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item {
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("오늘의 학습", style = MaterialTheme.typography.titleLarge)
                                    Text("${state.dailyRound}회독 · DAY ${state.dailyDay}", style = MaterialTheme.typography.headlineMedium)
                                    Caption(if (state.dailyAnswered > 0) "${state.dailyAnswered}문제 완료 · 미응답 문제부터 이어갑니다" else "날짜와 관계없이 순서대로 학습해요")
                                    Action(if (state.dailyAnswered > 0) "학습 이어하기" else "학습 시작", "daily-start", enabled = !state.busy, onClick = startDaily)
                                }
                            }
                        }
                        item { Action("맞춤학습", "custom", secondary = true, enabled = !state.busy, onClick = showCustom) }
                        item { Action("오답노트 · ${state.notebookIds.size}개", "notebook", secondary = true, enabled = !state.busy, onClick = showNotebook) }
                        item {
                            Column(verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.testTag("analytics-heading")) {
                                Text("나의 학습 분석",style=MaterialTheme.typography.headlineSmall)
                                if(!state.analytics.hasRecords) {
                                    Text("아직 학습 기록이 없어요",style=MaterialTheme.typography.bodyMedium)
                                    Action("학습 시작","analytics-start",enabled=!state.busy,onClick=startDaily)
                                }
                            }
                        }
                        item { AnalyticsCard(state.analytics,accuracy=true) }
                        item { AnalyticsCard(state.analytics,accuracy=false) }
                    }
                }
                Screen.CUSTOM -> {
                    Text("맞춤학습", style = MaterialTheme.typography.headlineMedium)
                    Caption("${catalog.days.size}개 DAY · ${catalog.entries.size}개의 단어와 표현")
                    LazyColumn(Modifier.weight(1f).testTag("day-list"), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                        items(catalog.days, key = { it.day }) { day ->
                            val selected = state.selectedDay == day.day
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("day-${day.day}").selectable(selected, role = Role.RadioButton) { selectDay(day.day) },
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    RadioButton(selected, onClick = null)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("DAY ${day.day} · ${day.title}", style = MaterialTheme.typography.titleMedium)
                                        Caption("단어·표현 ${day.count}개")
                                    }
                                }
                            }
                        }
                    }
                    Action("DAY ${state.selectedDay} 학습 시작", "start", onClick = { start(false) })
                    Action("오답노트 · ${state.notebookIds.size}개", "notebook", secondary = true, onClick = showNotebook)
                }
                Screen.NOTEBOOK -> {
                    val wrong = catalog.entries.filter { it.id in state.notebookIds }.distinctBy { it.id }
                    Text("오답노트", style = MaterialTheme.typography.headlineMedium)
                    Caption("${wrong.size}개 · 복습에서 맞히면 목록에서 지워집니다")
                    LazyColumn(Modifier.weight(1f).testTag("notebook-list"), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                        if (wrong.isEmpty()) item {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("차곡차곡, 다시 익혀요", style = MaterialTheme.typography.titleLarge)
                                    Text("아직 저장된 오답이 없습니다. DAY 학습에서 틀린 단어가 여기에 모입니다.")
                                }
                            }
                        }
                        items(wrong, key = { it.id }) { word ->
                            Surface(shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Caption("DAY ${word.day}")
                                    Text(word.word, style = MaterialTheme.typography.titleLarge)
                                    Text(word.meaning, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                    Action("오답 복습 시작", "review", enabled = wrong.isNotEmpty(), onClick = { start(true) })
                }
                Screen.QUIZ -> {
                    state.question?.let { question ->
                        val list = rememberLazyListState()
                        LaunchedEffect(state.answer, state.index) {
                            if (state.answer != null) list.scrollToItem(5)
                            else list.scrollToItem(0)
                        }
                        Caption("${if (state.review) "오답 복습" else if (state.daily) "${state.sessionRound}회독 · DAY ${state.sessionDay}" else "맞춤학습 · DAY ${question.vocabulary.day}"} · ${state.index + 1} / ${state.total}")
                        LinearProgressIndicator(progress = { (state.index + 1).toFloat() / state.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(3.dp), trackColor = MaterialTheme.colorScheme.outlineVariant)
                        LazyColumn(Modifier.weight(1f).testTag("quiz-list"), state = list, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                            item {
                                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Caption("이 단어의 뜻은 무엇일까요?")
                                        Text(question.vocabulary.word, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("word"))
                                        SpeechControls(state, toggleSound, replay, voiceSettings)
                                    }
                                }
                            }
                            items(4) { index ->
                                val meaning = question.options[index]
                                val correct = state.answer != null && meaning == state.answer.correct
                                val wrong = state.answer != null && meaning == state.answer.selected && !state.answer.isCorrect
                                val colors = MaterialTheme.colorScheme
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = when { correct -> colors.primaryContainer; wrong -> colors.errorContainer; else -> colors.surface },
                                    border = BorderStroke(1.dp, when { correct -> colors.primary; wrong -> colors.error; else -> colors.outlineVariant }),
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("option-$index").selectable(
                                        selected = state.answer?.selected == meaning,
                                        enabled = state.answer == null && !state.busy, role = Role.RadioButton,
                                        onClick = { submit(index) },
                                    ),
                                ) {
                                    Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                                        Text("0${index + 1}", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.widthIn(min = 28.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (correct) Text("정답", color = colors.primary, style = MaterialTheme.typography.labelMedium)
                                            if (wrong) Text("내 선택 · 오답", color = colors.error, style = MaterialTheme.typography.labelMedium)
                                            Text(meaning, style = MaterialTheme.typography.bodyLarge)
                                        }
                                    }
                                }
                            }
                            if (state.answer != null) item {
                                val answer = state.answer
                                val colors = MaterialTheme.colorScheme
                                Surface(color = if (answer.isCorrect) colors.primaryContainer else colors.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.testTag("feedback").semantics { liveRegion = LiveRegionMode.Polite }) {
                                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(if (answer.isCorrect) "정답입니다!" else "다시 익혀볼까요? · 오답", style = MaterialTheme.typography.titleLarge, color = if (answer.isCorrect) colors.primary else colors.error)
                                        Text("내 선택: ${answer.selected}")
                                        Text("정답: ${answer.correct}", style = MaterialTheme.typography.titleMedium)
                                        Caption(when { !answer.isCorrect -> "오답노트에 저장했습니다."; state.review -> "오답노트에서 제거했습니다."; else -> "다음 단어로 이어가세요." })
                                    }
                                }
                            }
                            if (state.busy) item { Text("채점하고 기록을 저장하는 중…", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                        }
                        Action(if (state.index + 1 == state.total) "결과 보기" else "다음", "next", enabled = state.answer != null && !state.busy, onClick = next)
                    }
                }
                Screen.RESULT -> {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        item { Caption(if (state.review) "오답 복습 완료" else if (state.daily) "${state.sessionRound}회독 · DAY ${state.sessionDay} 완료" else "맞춤학습 완료"); Text(if (state.daily && state.sessionDay == 30) "${state.sessionRound}회독 완료" else "한 장을 마쳤어요", style = MaterialTheme.typography.headlineMedium) }
                        item {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp)) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Caption("정답")
                                    Text("${state.correctCount} / ${state.total}", style = MaterialTheme.typography.headlineLarge)
                                    Text("${state.total}문제 중 ${state.correctCount}문제를 맞혔습니다.", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        item { Text("오답노트에 ${state.notebookIds.size}개가 남아 있습니다.", style = MaterialTheme.typography.bodyLarge) }
                    }
                    if (state.daily) Action(if (state.sessionDay == 30) "${state.sessionRound + 1}회독 시작" else "다음 DAY 학습", "daily-next", enabled = !state.busy, onClick = startDaily)
                    Action("홈으로", "result-home", secondary = state.daily, onClick = home)
                    Action("오답노트 보기", "result-notebook", secondary = true, onClick = showNotebook)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeechControls(state: StudyUiState, toggleSound: () -> Unit, replay: () -> Unit, voiceSettings: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = toggleSound, enabled = !state.soundSaving,
            modifier = Modifier.heightIn(min = 48.dp).testTag("sound-toggle").semantics {
                contentDescription = if (state.soundOn) "자동 발음 끄기" else "자동 발음 켜기"
                stateDescription = if (state.soundOn) "소리 ON" else "소리 OFF"
            }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(painterResource(if (state.soundOn) R.drawable.ic_sound_on else R.drawable.ic_sound_off), contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (state.soundOn) "소리 ON" else "소리 OFF")
        }
        OutlinedButton(
            onClick = replay, enabled = state.speechStatus == SpeechStatus.READY,
            modifier = Modifier.heightIn(min = 48.dp).testTag("speech-replay").semantics { contentDescription = "현재 영단어 다시 듣기. 자동 발음 설정과 관계없이 재생합니다." },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(painterResource(R.drawable.ic_replay), contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp)); Text("다시 듣기")
        }
    }
    when (state.speechStatus) {
        SpeechStatus.LOADING -> Caption("영어 음성을 준비하는 중입니다. 학습은 계속할 수 있어요.")
        SpeechStatus.UNAVAILABLE -> {
            Text("영어 발음을 사용할 수 없습니다. 휴대폰 설정의 텍스트 음성 변환에서 영어 음성 데이터를 설치하거나 음성 엔진을 선택해 주세요.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            TextButton(onClick = voiceSettings, modifier = Modifier.heightIn(min = 48.dp).testTag("voice-settings")) { Text("음성 데이터 설치 안내") }
        }
        SpeechStatus.READY -> Unit
    }
}

private fun previewState() = StudyUiState(
    catalog = Catalog(listOf(Day(1, "채용", 1)), listOf(Vocabulary("a".repeat(24), 1, "credential", "신임장, 자격 증명서"))), busy = false,
    question = Question(Vocabulary("a".repeat(24), 1, "credential", "신임장, 자격 증명서"), listOf("신임장, 자격 증명서", "여러 부서와 협력하여 업무의 진행 상황을 확인하는 것", "우선적으로 처리하다", "상품을 진열하는 선반")), total = 1,
)
@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable private fun HomePreview() { Theme { StudyContent(previewState()) } }
@Preview(showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2f)
@Composable private fun LargeFontQuizPreview() { Theme { StudyContent(previewState().copy(screen = Screen.QUIZ)) } }
