package com.beff.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 하차 화면 그룹(06-2 · 06-3 · 06-4 · 07-1) 공용 조각. 수치는 Figma 스타일프레임에서 읽은 값이다. */
object UnloadColor {
    val Warn get() = Beff.Warning          // beff_warning (07-1 경고 화면)
    val WarnBg get() = Beff.WarningTint        // beff_warning_background
    val Line get() = Beff.Stroke          // 줄 구분선
    val SummaryFill get() = Beff.TealTint
    val SummaryLine = Color(0x59008E8C)   // #008E8C 35%
    val MapFill get() = Beff.Surface
    val SlotBase = Color(0xFFE3E8EA)
    val SlotLine = Color(0xFFCDD5D9)
    val Pillar get() = Beff.TextMuted
    val TealDark get() = Beff.ChipText
}

/** 제목 바 대신 X만. 호출 쪽이 위치를 정한다. */
@Composable
fun UnloadClose(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(80.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        BeffAsset("pict/close.svg", 48.dp)
    }
}

/**
 * 제목 바 자리(높이 80)에 X만 왼쪽 위로 둔다. 1113: 닫기 · 뒤로가기는 모든 화면에서 왼쪽 위로 통일(사용자 지시).
 * X 중심(24 + 40 = 64)은 [TitleBar] 뒤로 화살표 중심(26 + 38 = 64)과 같다.
 */
@Composable
fun CloseBar(onClose: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(80.dp)) {
        UnloadClose(onClose, Modifier.align(Alignment.CenterStart).padding(start = 24.dp))
    }
}

/** 840 폭 상단: 제목 바 자리(위 40 · 높이 80)에 X만 왼쪽에 둔다. */
@Composable
fun UnloadCloseBar(onClose: () -> Unit) {
    Box(Modifier.padding(top = 40.dp)) { CloseBar(onClose) }
}

/** 굵은 핵심어 56 ExtraBold + 나머지 40 SemiBold 한 줄 제목. */
@Composable
fun HeadlineText(bold: String, rest: String, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = Beff.TextStrong)) { append(bold) }
            withStyle(SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = Beff.TextSecondary)) { append(rest) }
        },
        style = BeffType.Title56.copy(lineHeight = 74.sp),
        modifier = modifier,
    )
}

/** 옅은 틸 요약 박스: 굵은 앞부분 + 보통 뒷부분 36. */
@Composable
fun SummaryBox(bold: String, rest: String, modifier: Modifier = Modifier, height: Dp = 108.dp) {
    val shape = RoundedCornerShape(36.dp)
    Box(
        modifier.height(height).clip(shape).background(UnloadColor.SummaryFill).border(2.dp, UnloadColor.SummaryLine, shape)
            .padding(horizontal = 36.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
                withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append(rest) }
            },
            style = BeffType.Body32.copy(fontSize = 36.sp, lineHeight = 48.sp, color = Beff.TextPrimary),
        )
    }
}

/** 줄 안의 이름(36 Regular) + 방향·거리(32 Bold) 두 줄 글. */
@Composable
// 1113: 보조 줄은 Figma처럼 32 Regular 회색(#4A545C)이 기본.
fun StepText(name: String, sub: String?, modifier: Modifier = Modifier, subColor: Color = Beff.TextSecondary, subBold: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name, style = BeffType.Body32.copy(fontSize = 36.sp, lineHeight = 48.sp, color = Beff.TextPrimary))
        if (sub != null) {
            Text(
                sub,
                style = BeffType.Body32.copy(
                    lineHeight = 42.sp, color = subColor, fontWeight = if (subBold) FontWeight.Bold else FontWeight.Normal,
                ),
            )
        }
    }
}

/** 순서 배지: 틸 원 40 + 흰 숫자 28 SemiBold. */
@Composable
fun StepBadge(n: Int, size: Dp = 40.dp, textSp: Int = 28) {
    Box(Modifier.size(size).clip(CircleShape).background(Beff.Teal), contentAlignment = Alignment.Center) {
        Text("$n", style = BeffType.Button36.copy(fontSize = textSp.sp, color = Color.White, lineHeight = (textSp * 1.3f).sp))
    }
}

data class StepLine(val badge: Int?, val illust: String, val name: String, val sub: String?)

/**
 * 흰 박스 + 제목 40 Bold + 줄 목록(그림 · 이름 · 방향·거리). 줄 사이 구분선 2.
 * 840: 그림 96 · 줄 96 · 간격 24. 2/3: 그림 120 · 줄 206 · 간격 24(07-1은 205 · 16).
 */
@Composable
fun StepsBox(
    title: String,
    lines: List<StepLine>,
    modifier: Modifier = Modifier,
    illust: Dp = 96.dp,
    rowHeight: Dp = 96.dp,
    gap: Dp = 24.dp,
    headerAction: (@Composable () -> Unit)? = null,
    titleVisible: Boolean = true,
    subtitle: String? = null,
) {
    BeffBox(modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(Modifier.padding(horizontal = 34.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            if (titleVisible) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 1113: 제목 40 Bold(#131417) 바로 아래 4 간격으로 틸 한 줄 32 SemiBold — 하차 동선 카드(G-1 · G-2)와 같은 위계.
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = BeffType.Box40.copy(color = Beff.TextStrong), maxLines = 1)
                    if (subtitle != null) Text(subtitle, style = BeffType.Body32.copy(lineHeight = 43.sp, color = Beff.TealText, fontWeight = FontWeight.SemiBold), maxLines = 1)
                }
                headerAction?.invoke()
            }
            lines.forEachIndexed { i, l ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(UnloadColor.Line))
                Row(Modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                    if (l.badge != null) { StepBadge(l.badge); Spacer(Modifier.width(20.dp)) }
                    BeffAsset(l.illust, illust)
                    StepText(l.name, l.sub, Modifier.padding(start = 20.dp).weight(1f))
                }
            }
        }
    }
}

/** 박스 제목 오른쪽 알약 「약도 보기」(171×72, #EEF4F4, 32 SemiBold). */
@Composable
fun HeaderPill(text: String, onClick: () -> Unit) {
    Box(
        Modifier.height(72.dp).clip(RoundedCornerShape(100.dp)).background(Beff.Surface).clickable(onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BeffType.Small30.copy(fontSize = 32.sp, color = Beff.TextPrimary), maxLines = 1) }
}

// ───────────────────────── 06-3 / 06-2(2/3) 평면 약도 ─────────────────────────

private fun Modifier.at(s: Float, x: Number, y: Number, w: Number, h: Number): Modifier =
    this.offset((x.toFloat() * s).dp, (y.toFloat() * s).dp).size((w.toFloat() * s).dp, (h.toFloat() * s).dp)

/**
 * 하차 약도. 좌표는 Figma 778×867 약도 박스 기준이며 s로 줄인다(840 폭에서 s = 폭/778). 글자 · 그림 크기는 줄이지 않는다.
 * 약도 데이터가 없어 지하 1층 주차장 평면과 동선은 전부 [가정] 예시다(현대오토에버 실내 지도 제휴 전제, 미확인).
 */
@Composable
fun UnloadMapBoard(s: Float, modifier: Modifier = Modifier) {
    val teal = Beff.Teal
    val shape = RoundedCornerShape((32 * s).dp)
    Box(modifier.size((778 * s).dp, (867 * s).dp).clip(shape).background(UnloadColor.MapFill).border(3.dp, teal, shape)) {
        // 주차면 두 줄 8칸 72×120 (칸 간격 84). 회색 = 이미 찬 칸, 흰색 = 빈 칸, 옅은 틸 = 유모차 주차면.
        val topFree = setOf(2, 5, 7)
        val botFree = setOf(2, 5)
        for (i in 0 until 8) {
            val x = 59 + 84 * i
            slot(s, x, 136, if (i in topFree) Color.White else UnloadColor.SlotBase, UnloadColor.SlotLine, 2)
            if (i == 1) slot(s, x, 707, UnloadColor.SummaryFill, teal, 3)
            else slot(s, x, 707, if (i in botFree) Color.White else UnloadColor.SlotBase, UnloadColor.SlotLine, 2)
        }
        // 기둥 24
        for (x in listOf(59, 271, 483, 695)) for (y in listOf(288, 664)) {
            Box(Modifier.at(s, x, y, 24, 24).background(UnloadColor.Pillar))
        }
        // 엘리베이터 홀 260×190
        val hall = RoundedCornerShape((20 * s).dp)
        Box(Modifier.at(s, 486, 404, 260, 190).clip(hall).background(UnloadColor.SlotBase).border(2.dp, UnloadColor.SlotLine, hall))
        BeffAsset("illust/elevator.svg", (96 * s).dp, Modifier.offset((568 * s).dp, (416 * s).dp))
        Text(
            "엘리베이터 B",
            style = BeffType.Body32.copy(fontWeight = FontWeight.Bold, color = Beff.TextPrimary, textAlign = TextAlign.Center),
            modifier = Modifier.offset((486 * s).dp, (522 * s).dp).width((260 * s).dp),
            maxLines = 1,
        )
        // 틸 점선 동선 6 / [16, 14]: 내 칸 위 → 위로 → 엘리베이터 홀
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / 778f
            drawLine(
                color = teal, start = Offset(179f * k, 704f * k), end = Offset(179f * k, 496f * k),
                strokeWidth = 6.dp.toPx(), cap = StrokeCap.Butt,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16.dp.toPx(), 14.dp.toPx())),
            )
            drawLine(
                color = teal, start = Offset(179f * k, 496f * k), end = Offset(486f * k, 496f * k),
                strokeWidth = 6.dp.toPx(), cap = StrokeCap.Butt,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16.dp.toPx(), 14.dp.toPx())),
            )
        }
        // 거리 태그 「20m」(동선 가운데) · 출구 태그
        Box(Modifier.offset((179 * s).dp, (462 * s).dp).width((307 * s).dp).height(68.dp), contentAlignment = Alignment.Center) { MapTag("20m") }
        Row(
            Modifier.offset(0.dp, (328 * s).dp).width((746 * s).dp),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically,
        ) {
            StepBadge(3, 48.dp, 34)
            Spacer(Modifier.width(12.dp))
            MapTag("↑ 1층 · 후문 입구 40m")
        }
        // 순서 배지 2(동선이 홀에 닿는 끝) · 1(내 칸 위)
        Box(Modifier.offset((462 * s).dp, (471 * s).dp)) { StepBadge(2, 48.dp, 34) }
        Box(Modifier.offset((155 * s).dp, (683 * s).dp)) { StepBadge(1, 48.dp, 34) }
        // 축척 10m: 왼쪽 빈 자리
        Text("10m", style = BeffType.Body32.copy(fontWeight = FontWeight.Bold, color = Beff.TextSecondary), modifier = Modifier.offset((32 * s).dp, (404 * s).dp))
        Box(Modifier.at(s, 32, 452, 100, 4).background(Beff.TextMuted))
        // 머리: 층 제목 + 층 알약(지하 1층 선택 · 1층)
        Row(
            Modifier.offset((34 * s).dp, (20 * s).dp).width((710 * s).dp).height(76.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("지하 1층 주차장", style = BeffType.Box40.copy(fontWeight = FontWeight.Normal), modifier = Modifier.weight(1f), maxLines = 1)
            FloorPill("지하 1층", selected = true)
            Spacer(Modifier.width(24.dp))
            FloorPill("1층", selected = false)
        }
    }
}

@Composable
private fun slot(s: Float, x: Int, y: Int, fill: Color, line: Color, w: Int) {
    val r = RoundedCornerShape((8 * s).dp)
    Box(Modifier.at(s, x, y, 72, 120).clip(r).background(fill).border(w.dp, line, r))
}

@Composable
private fun MapTag(text: String) {
    Box(
        Modifier.height(68.dp).clip(RoundedCornerShape(100.dp)).background(Beff.Card)
            .border(3.dp, Beff.Teal, RoundedCornerShape(100.dp)).padding(horizontal = 27.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BeffType.Body32.copy(fontWeight = FontWeight.Bold, color = Beff.TealText), maxLines = 1) }
}

/** 층 알약: 선택 = 옅은 틸 + 틸 테두리 3 + 진한 틸 글자, 아닌 쪽 = #EEF4F4. 높이 76. */
@Composable
private fun FloorPill(text: String, selected: Boolean) {
    val shape = RoundedCornerShape(38.dp)
    Box(
        Modifier.height(76.dp).clip(shape).background(if (selected) UnloadColor.SummaryFill else Beff.Surface)
            .then(if (selected) Modifier.border(3.dp, Beff.Teal, shape) else Modifier)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = BeffType.Small30.copy(fontSize = 32.sp, color = if (selected) UnloadColor.TealDark else Beff.TextPrimary), maxLines = 1)
    }
}

// ───────────────────────── 1110 G. 도착 · 하차 공용 조각 ─────────────────────────

/** 1110 G 화면 회색 타일 · 보조 버튼 색(Figma #EEF3F3 · #EDEEF2). */
object ArrivalColor {
    val Tile get() = Beff.Surface
    val Divider get() = Beff.Stroke
    val Secondary get() = Beff.SecondaryBtn
    val ToastBg get() = Beff.Toast       // #252B31 94%
    val Sub get() = Beff.TextMuted
}

/**
 * 1110 G-1 · G-2 · D-5 「하차 동선」 카드. 흰 90% r36, 안쪽 34, 제목 40 Bold, 줄 160(배지 48 · 그림 112 · 이름 36 · 방향 32 Bold), 구분선 2.
 * 840은 줄 136 · 그림 96.
 */
@Composable
fun ArrivalStepsCard(
    title: String,
    lines: List<StepLine>,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 160.dp,
    illust: Dp = 112.dp,
    empty: String = "이 장소는 하차 동선 원문이 없어요. 오른쪽 지도에서 시설 위치를 확인해 주세요",
) {
    // 1113: 흰 패널 위에서도 박스로 구분되게 회색 테두리.
    BeffBox(modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start, stroke = Beff.Stroke) {
        Column(Modifier.padding(34.dp)) {
            Text(title, style = BeffType.Box40.copy(color = Beff.TextStrong), maxLines = 1)
            Spacer(Modifier.height(24.dp))
            if (lines.isEmpty()) Text(empty, style = BeffType.Body32.copy(color = Beff.TextSecondary, lineHeight = 43.sp))
            lines.forEachIndexed { i, l ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(ArrivalColor.Divider))
                Row(Modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                    if (l.badge != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Beff.Teal), contentAlignment = Alignment.Center) {
                            Text("${l.badge}", style = BeffType.Body32.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 38.sp))
                        }
                        Spacer(Modifier.width(24.dp))
                    }
                    BeffAsset(l.illust, illust)
                    Column(Modifier.padding(start = 24.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // 4-14: 장소명 36 SemiBold #131417 > 안내 32 Regular #4A545C(보조 줄이 더 진하던 것 정리).
                        Text(l.name, style = BeffType.Item36Semi.copy(color = Beff.TextStrong), maxLines = 1)
                        if (!l.sub.isNullOrBlank()) Text(
                            l.sub,
                            style = BeffType.Body32.copy(color = Beff.TextSecondary),
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}

/** 핵심어(틸) + 나머지(검정) 2줄 제목 56 ExtraBold · 줄 76. 예: 「소양강스카이워크에 / 도착했어요」. */
@Composable
fun ArrivalTitle(teal: String, tail: String, second: String, modifier: Modifier = Modifier) {
    // 4-18b · 6-7: 화면 제목은 48 ExtraBold #131417 한 가지(틸 강조 · 줄바꿈 없음). 「국립세종수목원에 도착했어요」
    Text(
        "$teal$tail $second",
        style = BeffType.Title48.copy(color = Beff.TextStrong),
        maxLines = 2,
        modifier = modifier,
    )
}

/**
 * 1110 시스템 팝업(G-3 · G-6 · G-7, Figma ivi_system_popup): 어두운 막 55% + 720×576 흰 카드 r40 + 테두리 #131417 5%.
 * 위 40에 그림 200, 제목 40 ExtraBold, 설명 30, 아래 40에 버튼 2개(312×100 r32, 간격 16). 실제 화면 위에 띄운다.
 */
@Composable
fun ArrivalPopup(
    title: String,
    desc: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String,
    onSecondary: () -> Unit,
    illust: String = "illust/car.svg",
    illustSize: Dp = 200.dp,
) {
    Box(
        Modifier.fillMaxSize().fadeInOnce().background(Color(0x8C000000)).clickable(
            interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
        ) {},
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(40.dp)
        Column(
            Modifier.popIn().width(720.dp).heightIn(min = 576.dp).clip(shape).background(Beff.Popup).border(1.5.dp, Beff.PopupBorder, shape)
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            BeffAsset(illust, illustSize)
            Spacer(Modifier.height(33.dp))
            Text(title, style = BeffType.Title56.copy(fontSize = 40.sp, lineHeight = 53.sp, letterSpacing = 0.sp, color = Beff.TextStrong, textAlign = TextAlign.Center))
            Spacer(Modifier.height(24.dp))
            Text(
                desc,
                style = BeffType.Body32.copy(fontSize = 30.sp, lineHeight = 41.sp, letterSpacing = 0.sp, color = Color(0xD6131417), textAlign = TextAlign.Center),
                maxLines = 2,
            )
            Spacer(Modifier.height(40.dp))
            Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PopupAction(primary, true, onPrimary, Modifier.weight(1f))
                PopupAction(secondary, false, onSecondary, Modifier.weight(1f))
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PopupAction(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.fillMaxSize().clip(RoundedCornerShape(32.dp)).background(if (primary) Beff.Teal else Beff.PopupSecondary).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = BeffType.Button36.copy(fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = 0.sp, color = if (primary) Color.White else Beff.TextSecondary), maxLines = 1)
    }
}

/**
 * 음성 안내 토스트(4-10 · 6-15, E-2~E-5 · G-4 공통): 화면 위쪽에 둔다(호출 쪽이 위 40 · 와이드는 화면 가운데).
 * 바탕 Toast(라이트 짙은 회색 · 다크 흰색) r36 · 틸 글로우, 얼굴 원 72(#D6ECEB + #008E8C)를 가운데 위에, 문장은 한 문장씩 줄을 바꿔 가운데 정렬.
 */
@Composable
fun TtsToast(text: String, modifier: Modifier = Modifier) {
    androidx.compose.runtime.LaunchedEffect(text) { com.beff.app.device.Sfx.toast() } // 1113 효과음: 토스트가 뜨거나 문장이 바뀔 때
    val shape = RoundedCornerShape(36.dp)
    Column(
        modifier
            .shadow(28.dp, shape, ambientColor = Color(0x8000B3AE), spotColor = Color(0x8000B3AE))
            .clip(shape).background(Beff.Toast)
            .padding(horizontal = 40.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Beff.ToastFaceBg), contentAlignment = Alignment.Center) {
            BeffAsset("face/face_tilt.svg", 52.dp, tint = Beff.ToastFaceInk)
        }
        Text(
            ttsLines(text),
            style = BeffType.Item36Semi.copy(fontSize = 32.sp, lineHeight = 44.sp, color = Beff.ToastText, textAlign = TextAlign.Center),
            modifier = Modifier.widthIn(max = 680.dp),
        )
    }
}

/** 한 문장씩 줄바꿈: 「곧 안성 휴게소에 도착해요. 장애인 화장실이 있어요.」 → 두 줄. */
fun ttsLines(text: String): String =
    text.split(Regex("(?<=[.!?。])\\s+")).joinToString("\n") { it.trim() }

/** 도착 화면 TTS 토스트 상태. 화면 여러 곳(시트 카드 · 문 열림)이 같은 자리에 띄운다. */
object ArrivalToast {
    var text by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    private var seq = 0

    /** [ms] 뒤에 사라진다. 사이에 새 문장이 오면 그 문장 기준으로 다시 잰다. */
    private val scope = kotlinx.coroutines.MainScope()

    fun show(text: String, ms: Long = 3500) {
        this.text = text
        val my = ++seq
        scope.launch { kotlinx.coroutines.delay(ms); if (seq == my) this@ArrivalToast.text = null }
    }

    fun clear() { text = null; seq++ }
}

/** 1110 회색 보조 버튼(Figma Beff / Button #EDEEF2, 글 #4A545C 36 SemiBold). */
@Composable
fun ArrivalSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BeffButton(text, onClick, modifier, kind = ButtonKind.Secondary, containerColor = ArrivalColor.Secondary)
}

/**
 * 1110 G-1 · G-2 · D-5 · G-5 2/3 틀: 왼쪽 열(834, 여백 56) + 간격 32 + 오른쪽 흰 패널(834, 왼쪽 모서리 r36, 안쪽 좌우 58).
 * [titleBar]가 있으면 위 40에 제목 바(80)를 두고 왼쪽 내용은 y160부터, 없으면 y72부터. [tab]이 있으면 왼쪽 아래에 GNB(아래 40).
 * [overlay]는 왼쪽 열 위(토스트 자리)에 그린다.
 */
@Composable
fun ArrivalWideFrame(
    tab: com.beff.app.Tab?,
    onTab: (com.beff.app.Tab) -> Unit,
    titleBar: (@Composable () -> Unit)? = null,
    rightTop: Dp = 74.dp,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().beffBackground(base = Beff.BgCool)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(834f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize()) {
                    if (titleBar != null) {
                        Spacer(Modifier.height(40.dp))
                        titleBar()
                        Spacer(Modifier.height(40.dp))
                    } else Spacer(Modifier.height(72.dp))
                    Column(Modifier.weight(1f).fillMaxWidth().padding(start = WIDE_SIDE), content = left)
                    if (tab != null) {
                        Spacer(Modifier.height(17.dp))
                        BeffGnb(tab, onTab, Modifier.padding(start = WIDE_SIDE), sidePadding = 0.dp)
                    }
                    Spacer(Modifier.height(WIDE_BOTTOM))
                }
                Box(Modifier.fillMaxSize().padding(start = WIDE_SIDE, bottom = if (tab != null) 191.dp else 60.dp), contentAlignment = Alignment.BottomCenter, content = overlay)
            }
            Spacer(Modifier.weight(32f))
            val shape = RoundedCornerShape(topStart = 36.dp, bottomStart = 36.dp)
            Column(
                Modifier.weight(834f).fillMaxHeight()
                    .shadow(20.dp, shape, ambientColor = Beff.BoxShadow, spotColor = Beff.BoxShadow)
                    .clip(shape).background(Beff.Card)
                    .padding(start = 58.dp, end = 58.dp, top = rightTop, bottom = 57.dp),
                content = right,
            )
        }
    }
}
