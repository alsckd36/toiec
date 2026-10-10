package com.toiec.vocabulary.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.toiec.vocabulary.analytics.*
import java.text.NumberFormat
import java.time.format.DateTimeFormatter

private fun percentage(value: Double?): String = value?.let {
    NumberFormat.getNumberInstance().apply { maximumFractionDigits=1 }.format(it)+"%"
} ?: "—"
private val dateLabel=DateTimeFormatter.ofPattern("MM/dd")
private val detailDate=DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val wrongColor=Color(0xFFA9B2AD)

@Composable
fun AnalyticsCard(summary: AnalyticsSummary, accuracy: Boolean) {
    val name=if(accuracy) "정답률" else "푼 문제 수"
    val tag=if(accuracy) "accuracy" else "count"
    val today=summary.today
    var selectedDate by rememberSaveable { mutableStateOf(today.date.toString()) }
    val selected=summary.days.firstOrNull { it.date.toString()==selectedDate } ?: today
    Surface(shape=RoundedCornerShape(20.dp),border=androidx.compose.foundation.BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth().testTag("analytics-$tag")) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(name,style=MaterialTheme.typography.titleLarge)
            Text("오늘 · ${today.date.format(detailDate)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if(accuracy) percentage(today.accuracy) else "${today.total}개",style=MaterialTheme.typography.headlineLarge,modifier=Modifier.testTag("today-$tag"))
            if(!accuracy) Text("맞힌 문제 ${today.correct}개 · 틀린 문제 ${today.wrong}개",style=MaterialTheme.typography.bodySmall)
            Text("최근 7일 · 날짜를 누르면 상세 수치를 볼 수 있어요",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(!accuracy) Legend()
            SevenDayChart(summary.days,accuracy,selectedDate) { selectedDate=it }
            Text(
                "${selected.date.format(detailDate)} · " + if(accuracy) {
                    if(selected.total==0) "답변 없음 · 정답률 —" else "정답률 ${percentage(selected.accuracy)} · ${selected.correct}/${selected.total}개"
                } else "전체 ${selected.total}개 · 맞힌 문제 ${selected.correct}개 · 틀린 문제 ${selected.wrong}개",
                style=MaterialTheme.typography.bodyMedium,modifier=Modifier.testTag("$tag-detail").semantics { liveRegion=LiveRegionMode.Polite },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Legend() {
    val dark=MaterialTheme.colorScheme.onSurface
    val mint=MaterialTheme.colorScheme.primary
    FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(16.dp,20.dp)) { drawLine(dark,Offset(0f,size.height/2),Offset(size.width,size.height/2),strokeWidth=2.dp.toPx()) }
            Text("전체 푼 문제",style=MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { Box(Modifier.padding(top=6.dp).size(10.dp).background(mint,RoundedCornerShape(2.dp))); Text("맞힌 문제",style=MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { Box(Modifier.padding(top=6.dp).size(10.dp).background(wrongColor,RoundedCornerShape(2.dp))); Text("틀린 문제",style=MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun SevenDayChart(days: List<DayStats>, accuracy: Boolean, selected: String, select: (String)->Unit) {
    val mint=MaterialTheme.colorScheme.primary
    val dark=MaterialTheme.colorScheme.onSurface
    val rule=MaterialTheme.colorScheme.outlineVariant
    val scale=LocalDensity.current.fontScale.coerceAtLeast(1f)
    val maximum=if(accuracy) 100 else ((days.maxOf { it.total }+3)/4).coerceAtLeast(1)*4
    val tag=if(accuracy) "accuracy" else "count"
    val scroll=rememberScrollState()
    LaunchedEffect(scroll.maxValue,days.last().date) { scroll.scrollTo(scroll.maxValue) }
    val ticks=(4 downTo 0).map { maximum*it/4 }
    Text(if(accuracy) "정답률 (%)" else "문제 수 (개)",style=MaterialTheme.typography.bodySmall)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val viewportWidth=(maxWidth-56.dp*scale).coerceAtLeast(1.dp)
        val visibleDates=(viewportWidth/(48.dp*scale)).toInt().coerceIn(1,7)
        val plotWidth=viewportWidth/visibleDates*7
        Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(56.dp*scale).height(180.dp*scale),verticalArrangement=Arrangement.SpaceBetween) {
                    ticks.forEach { Text(if(accuracy) "$it%" else "$it",style=MaterialTheme.typography.bodySmall,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Start) }
                }
                Column(Modifier.weight(1f).horizontalScroll(scroll).testTag("$tag-chart-scroll")) {
                    Column(Modifier.width(plotWidth)) {
                    Canvas(Modifier.fillMaxWidth().height(180.dp*scale).testTag("$tag-chart").pointerInput(days,accuracy) {
                        detectTapGestures { point -> select(days[(point.x/(size.width/7f)).toInt().coerceIn(0,6)].date.toString()) }
                    }) {
                        val top=(12.dp*scale).toPx(); val bottom=size.height-top
                        val step=size.width/7f
                        fun x(i: Int)=(i+.5f)*step
                        fun y(value: Double)=bottom-(bottom-top)*(value/maximum).toFloat()
                        ticks.forEach { drawLine(rule,Offset(0f,y(it.toDouble())),Offset(size.width,y(it.toDouble())),strokeWidth=1.dp.toPx()) }
                        days.forEachIndexed { i,day ->
                            val prev=days.getOrNull(i-1)
                            if(accuracy) {
                                day.accuracy?.let { value ->
                                    if(prev?.accuracy!=null) drawLine(mint,Offset(x(i-1),y(prev.accuracy!!)),Offset(x(i),y(value)),strokeWidth=3.dp.toPx(),cap=StrokeCap.Round)
                                    drawCircle(mint,4.dp.toPx(),Offset(x(i),y(value)))
                                    if(selected==day.date.toString()) drawCircle(dark,6.dp.toPx(),Offset(x(i),y(value)),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                                }
                            } else {
                                val width=step*.48f
                                if(day.correct>0) drawRect(mint,Offset(x(i)-width/2,y(day.correct.toDouble())),Size(width,bottom-y(day.correct.toDouble())))
                                if(day.wrong>0) drawRect(wrongColor,Offset(x(i)-width/2,y(day.total.toDouble())),Size(width,y(day.correct.toDouble())-y(day.total.toDouble())))
                                if(prev!=null) drawLine(dark,Offset(x(i-1),y(prev.total.toDouble())),Offset(x(i),y(day.total.toDouble())),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round)
                                drawCircle(dark,3.dp.toPx(),Offset(x(i),y(day.total.toDouble())))
                                if(selected==day.date.toString()) drawCircle(dark,6.dp.toPx(),Offset(x(i),y(day.total.toDouble())),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        days.forEach { day ->
                            TextButton(onClick={select(day.date.toString())},modifier=Modifier.weight(1f).heightIn(min=48.dp).testTag("$tag-date-${day.date}").semantics {
                                this.selected=selected==day.date.toString()
                                contentDescription= "${day.date.format(detailDate)}, " + if(accuracy) "정답률 ${percentage(day.accuracy)}" else "전체 ${day.total}개, 맞힌 문제 ${day.correct}개, 틀린 문제 ${day.wrong}개"
                            },contentPadding=PaddingValues(horizontal=0.dp,vertical=8.dp)) { Text(day.date.format(dateLabel),style=MaterialTheme.typography.bodySmall,modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center) }
                        }
                    }
                }
            }
        }
    }
    Text("작은 화면에서는 그래프를 좌우로 밀어 날짜를 확인하세요",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
