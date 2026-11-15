package com.beff.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beff.app.data.Mobility
import com.beff.app.data.Profile
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffBox
import com.beff.app.AppViewModel
import com.beff.app.ui.BeffType

/** "유모차 접근이 쉬운 주차장" — 프로필의 주된 이동 보조수단에 따라 달라지는 안내 문구. */
fun accessPhrase(profile: Profile?): String = when {
    profile == null -> "접근이 쉬운 주차장"
    (profile.counts[Mobility.Wheelchair] ?: 0) > 0 -> "휠체어 접근이 쉬운 주차장"
    (profile.counts[Mobility.Stroller] ?: 0) > 0 -> "유모차 접근이 쉬운 주차장"
    else -> "계단 없는 주차장"
}

/** 굵은 부분 + 보통 부분 한 줄 문장. */
@Composable
fun BoldThenRegular(bold: String, regular: String, size: Int = 32, boldColor: Color = Beff.TextStrong, regularColor: Color = Beff.TextSecondary) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) { append(bold) }
            withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = regularColor)) { append(regular) }
        },
        style = BeffType.Body32.copy(fontSize = size.sp),
    )
}

/** 목적지 줄: "국립세종수목원 가는 길" — 이름 56 ExtraBold + 접미 40 SemiBold. */
@Composable
fun DestinationTitle(name: String, suffix: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = Beff.TextStrong)) { append("$name ") }
            withStyle(SpanStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = Beff.TextSecondary)) { append(suffix) }
        },
        style = BeffType.Title56.copy(lineHeight = 74.sp),
        maxLines = 1,
    )
}

/** 경로 위 경유지 표시. 제안(점선, "쉴 곳")과 추가됨(흰 원 + 회색 테두리, 장소명)을 구분한다. */
data class RouteMarker(val at: Float, val added: Boolean, val label: String)

/**
 * 선형 경로(정책 67): 출발 · 도착은 작은 원, 경유지만 아이콘, 내 차 위치 표시, 지나온 길은 틸.
 * progress 0..1.
 */
@Composable
fun RouteLine(progress: Float, markers: List<RouteMarker>, height: Int) {
    Box(Modifier.width(664.dp).height(height.dp)) {
        Box(Modifier.offset(14.dp, 38.dp).size(636.dp, 4.dp).background(Beff.Muted2))
        if (progress > 0f) {
            Box(Modifier.offset(14.dp, 37.dp).size((636 * progress).dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(Beff.Teal))
        }
        Dot(Modifier.offset(0.dp, 26.dp), passed = progress > 0f)
        Dot(Modifier.offset(636.dp, 26.dp), passed = false)
        markers.forEach { m ->
            val cx = 14 + 636 * m.at
            if (m.added) {
                Box(
                    Modifier.offset((cx - 36).dp, 4.dp).size(72.dp).clip(CircleShape).background(Beff.Card)
                        .border(3.6.dp, Beff.Muted2, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { BeffAsset("pict/rest.svg", 48.dp) }
            } else {
                Box(
                    Modifier.offset((cx - 36).dp, 4.dp).size(72.dp).clip(CircleShape).background(Beff.TealTint)
                        .dashedCircle(Beff.Teal),
                    contentAlignment = Alignment.Center,
                ) { BeffAsset("pict/rest.svg", 48.dp, tint = Beff.TealText) }
            }
            if (height >= 140) {
                Text(
                    m.label,
                    style = BeffType.Body32.copy(color = if (m.added) Beff.TextPrimary else Beff.ChipText),
                    maxLines = 1,
                    modifier = Modifier.offset((cx - 110).dp, 92.dp).width(220.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        val carX = 14 + 636 * progress - 32
        Box(
            Modifier.offset(carX.dp, 8.dp)
                .shadow(6.dp, CircleShape, ambientColor = Color(0x40004D4D), spotColor = Color(0x40004D4D))
                .size(64.dp).clip(CircleShape).background(Beff.Teal).border(4.dp, Beff.BoxBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) { BeffAsset("pict/car.svg", 48.dp, tint = Color.White) }
    }
}

private fun Modifier.dashedCircle(color: Color): Modifier = this.drawBehind {
    drawCircle(
        color = color,
        radius = size.minDimension / 2 - 1.8.dp.toPx(),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 3.6.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        ),
    )
}

@Composable
private fun Dot(modifier: Modifier, passed: Boolean) {
    Box(
        modifier.size(28.dp).clip(CircleShape).background(Beff.Card)
            .border(5.dp, if (passed) Beff.Teal else Beff.Muted2, CircleShape),
    )
}

/** 시설 칩(흰 배경 + 진한 틸 글자) — 쉴 곳 행 안. */
@Composable
fun FacilityChipsRow(items: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { t ->
            Box(Modifier.clip(RoundedCornerShape(100.dp)).background(Beff.Card).padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(t, style = BeffType.Body32.copy(color = Beff.ChipText), maxLines = 1)
            }
        }
    }
}

/** 경유지가 내비에 실제로 반영됐는지 보여 주는 한 줄. reflected=null이면 내비 상태를 모른다. */
@Composable
fun NaviStatusLine(reflected: Boolean?, reason: String?) {
    val (icon, tint, text) = when (reflected) {
        true -> Triple("pict/verified.svg", Beff.Teal, "내비에 반영됨")
        false -> Triple("pict/warning.svg", Beff.Warning, reason ?: "내비에는 아직 없어요")
        null -> Triple("pict/warning.svg", Beff.TextSecondary, reason ?: "내비 연결 상태를 확인하는 중이에요")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BeffAsset(icon, 40.dp, tint = tint)
        Text(text, style = BeffType.Body32.copy(fontSize = 30.sp, color = if (reflected == true) Beff.TealText else Beff.TextSecondary), maxLines = 2)
    }
}

/** "으로 / 로": 받침이 없거나 ㄹ 받침이면 "로". */
fun withRo(name: String): String {
    val c = name.lastOrNull() ?: return "으로"
    if (c !in '가'..'힣') return "으로"
    val jong = (c - '가') % 28
    return if (jong == 0 || jong == 8) "로" else "으로"
}

/** 「도착하면 이렇게 내려요」 한 줄(정책 「하차 동선」): 이름 + 방향·거리. */
data class ArrivalRow(val name: String, val line2: String, val illust: String)

/**
 * 하차 동선 줄. 이름·그림·문구는 장소 원문(unloadPlan ← access)에서 만든다. 방향 · 거리는 원본에 없어 쓰지 않는다.
 */
fun arrivalRows(vm: AppViewModel, place: com.beff.app.data.Place): List<ArrivalRow> =
    vm.places.unloadPlan(place, vm.active).steps.map { s ->
        ArrivalRow(s.label, s.sub.orEmpty(), s.illust)
    }

/**
 * 하차 동선 박스(흰 90%). 05-1 · 06-1의 2/3 전체 화면 오른쪽. 제목 40 Bold · 줄마다 순서 배지 40 + 그림 + 이름 36 + 방향 32 Bold.
 * 05-1: 그림 96 · 줄 124. 06-1: 그림 120 · 줄 179.
 */
@Composable
fun ArrivalBox(title: String, rows: List<ArrivalRow>, illustSize: Int, rowHeight: Int, modifier: Modifier = Modifier) {
    BeffBox(modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(Modifier.padding(horizontal = 34.dp, vertical = 36.dp)) {
            Text(title, style = BeffType.Box40.copy(color = Beff.TextPrimary), maxLines = 1)
            rows.forEachIndexed { i, r ->
                Spacer(Modifier.height(24.dp))
                if (i > 0) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(Beff.Stroke))
                    Spacer(Modifier.height(24.dp))
                }
                Row(Modifier.fillMaxWidth().height(rowHeight.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Beff.Teal), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = BeffType.Body32.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                    }
                    BeffAsset(r.illust, illustSize.dp)
                    Column {
                        Text(r.name, style = BeffType.Body32.copy(fontSize = 36.sp, color = Beff.TextPrimary), maxLines = 1)
                        Text(r.line2, style = BeffType.Body32.copy(fontWeight = FontWeight.Bold, color = Beff.TextStrong), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** 장소의 접근 정보 원문. 요약 칩과 달리 원문 문장을 그대로 보여 주고 출처·수집일을 붙인다. */
@Composable
fun AccessInfoCard(place: com.beff.app.data.Place, profile: com.beff.app.data.Profile? = null, title: String = "시설 안내 (원문)", modifier: Modifier = Modifier) {
    val labels = listOf(
        "parking" to "주차", "route" to "출입구까지", "exit" to "출입구", "elevator" to "엘리베이터",
        "restroom" to "화장실", "lactationroom" to "수유실", "wheelchair" to "휠체어", "stroller" to "유모차",
        "babysparechair" to "유아 의자", "infantsfamilyetc" to "영유아 가족", "handicapetc" to "기타 편의",
    )
    // 함께 타는 분에게 중요한 항목부터: 휠체어 → 주차·출입구·엘리베이터·화장실, 유모차 → 수유실·출입구·엘리베이터, 보행 도움 → 화장실·출입구
    val priority = buildList {
        val c = profile?.counts.orEmpty()
        if ((c[com.beff.app.data.Mobility.Wheelchair] ?: 0) > 0) addAll(listOf("parking", "route", "exit", "elevator", "restroom", "wheelchair"))
        if ((c[com.beff.app.data.Mobility.Stroller] ?: 0) > 0) addAll(listOf("lactationroom", "route", "elevator", "stroller", "babysparechair", "infantsfamilyetc"))
        if ((c[com.beff.app.data.Mobility.Walker] ?: 0) > 0) addAll(listOf("restroom", "route", "exit", "elevator", "handicapetc"))
    }.distinct()
    val rows = labels.sortedBy { (k, _) -> priority.indexOf(k).let { if (it < 0) 99 else it } }
        .mapNotNull { (k, label) -> place.access[k]?.takeIf { it.isNotBlank() }?.let { label to it } }
    com.beff.app.ui.BeffBox(modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = BeffType.Box40.copy(color = Beff.TextPrimary))
            if (rows.isEmpty()) {
                Text("이 장소는 이동 편의 시설 정보가 적혀 있지 않아요", style = BeffType.Body32.copy(color = Beff.TextSecondary))
            }
            rows.forEach { (label, text) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = BeffType.Caption28.copy(color = Beff.TextSecondary))
                    Text(text, style = BeffType.Body32.copy(color = Beff.TextStrong))
                }
            }
        }
    }
}
