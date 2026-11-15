package com.beff.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.beff.app.AppViewModel
import com.beff.app.Journey
import com.beff.app.data.Place
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffType
import com.beff.app.ui.facilityIllust

/**
 * 1113 E-1 · F-1~F-3 세로 타임라인(4-8 · 4-12 · 4-15 · 4-16). 페타 C05식.
 * 출발 · 경유 · 도착 줄. 지금 향하는 곳(첫 경유지, 없으면 도착)만 틸 테두리 강조 카드로 펼친다.
 * [suggestion]이 있으면(경유지 없음) 경유지 자리에 점선 추천 카드를 둔다(F-3).
 */
@Composable
internal fun JourneyTimeline(
    vm: AppViewModel,
    j: Journey,
    modifier: Modifier = Modifier,
    suggestion: Place? = null,
    suggestionLabel: String = "",
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
    onAdd: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(36.dp)
    val current = j.stops.firstOrNull() ?: j.dest
    Column(
        modifier.fillMaxWidth()
            .shadow(16.dp, shape, ambientColor = Beff.BoxShadow, spotColor = Beff.BoxShadow)
            .clip(shape).background(Beff.Card).border(1.5.dp, Beff.Stroke, shape)
            .padding(horizontal = 34.dp, vertical = 40.dp),
    ) {
        TimelineRow(first = true, last = false, passed = true, dot = DotKind.Origin) {
            PlainPoint("출발", "현재 위치", null, muted = true)
        }
        if (suggestion != null && j.stops.isEmpty()) {
            TimelineRow(first = false, last = false, passed = false, dot = DotKind.Dashed) {
                SuggestionCard(vm, suggestion, suggestionLabel, onPrev, onNext, onAdd)
            }
        }
        j.stops.forEach { s ->
            TimelineRow(first = false, last = false, passed = false, dot = DotKind.Point) {
                if (s.id == current.id) FocusCard(vm, s, "경유", remainText(vm, s, dest = false))
                else PlainPoint("경유", s.name, remainText(vm, s, dest = false))
            }
        }
        TimelineRow(first = false, last = true, passed = false, dot = DotKind.Point) {
            if (j.dest.id == current.id && j.stops.isEmpty() && suggestion == null) FocusCard(vm, j.dest, "도착", remainText(vm, j.dest, dest = true))
            else PlainPoint("도착", j.dest.name, remainText(vm, j.dest, dest = true))
        }
    }
}

private enum class DotKind { Origin, Point, Dashed }

/** 왼쪽 레일(폭 40): 위 · 아래 선 + 점. 지나온 선은 틸, 남은 선은 회색. */
@Composable
private fun TimelineRow(first: Boolean, last: Boolean, passed: Boolean, dot: DotKind, content: @Composable () -> Unit) {
    val teal = Beff.TealMain
    val gray = Beff.Muted2
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier.width(40.dp).fillMaxHeight().drawBehind {
                val x = size.width / 2
                val w = 4.dp.toPx()
                val cy = 34.dp.toPx()
                if (!first) drawLine(if (passed) teal else teal, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, cy), w)
                if (!last) drawLine(if (passed) teal else gray, androidx.compose.ui.geometry.Offset(x, cy), androidx.compose.ui.geometry.Offset(x, size.height), w)
            },
        ) {
            Box(
                Modifier.padding(top = 20.dp).align(Alignment.TopCenter).size(28.dp).clip(CircleShape)
                    .background(Beff.Card)
                    .then(
                        when (dot) {
                            DotKind.Origin -> Modifier.border(6.dp, gray, CircleShape)
                            DotKind.Point -> Modifier.border(6.dp, teal, CircleShape)
                            DotKind.Dashed -> Modifier.drawBehind {
                                drawCircle(teal, radius = size.minDimension / 2 - 2.dp.toPx(),
                                    style = Stroke(4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
                            }
                        },
                    ),
            )
        }
        Box(Modifier.weight(1f).padding(start = 16.dp, top = 12.dp, bottom = if (last) 0.dp else 32.dp)) { content() }
    }
}

/** 접힌 줄: 라벨 28 · 이름 36 SemiBold · 보조 28. */
@Composable
private fun PlainPoint(label: String, name: String, sub: String?, muted: Boolean = false) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(label, style = BeffType.Caption28.copy(color = Beff.TextSecondary), modifier = Modifier.padding(top = 6.dp))
        Column {
            Text(name, style = BeffType.Item36Semi.copy(color = if (muted) Beff.TextSecondary else Beff.TextStrong), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = BeffType.Caption28.copy(color = Beff.TextSecondary), maxLines = 1)
        }
    }
}

/** 지금 향하는 곳 강조 카드: 틸 테두리 3 · 옅은 틸 · 라벨 · 이름 40 Bold · 주소 32 · 「n분 | n km 남음」 36 틸 · 시설 2×2. */
@Composable
private fun FocusCard(vm: AppViewModel, p: Place, label: String, remain: String?) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Beff.TealSoft).border(3.dp, Beff.TealMain, shape).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = BeffType.Caption28.copy(fontWeight = FontWeight.SemiBold, color = Beff.TealTextOnTint))
        Text(p.name, style = BeffType.Box40.copy(color = Beff.TextStrong), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val addr = p.address.ifBlank { p.regionLabel }
        if (addr.isNotBlank()) Text(addr, style = BeffType.Body32.copy(color = Beff.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (remain != null) Text(remain, style = BeffType.Item36Semi.copy(color = Beff.TealTextOnTint), maxLines = 1)
        FacilityGrid(vm.places.facilitiesFor(p, vm.active).ifEmpty { p.facilities }.take(4))
    }
}

/** F-3 점선 추천 카드: 「쉬어갈 곳 추천 · 1/3」 · 이름 · 「n km 앞 · n분 뒤 도착」 · 시설 2×2 · ‹ [경유지로 추가] ›. */
@Composable
private fun SuggestionCard(vm: AppViewModel, p: Place, label: String, onPrev: () -> Unit, onNext: () -> Unit, onAdd: (() -> Unit)?) {
    val teal = Beff.TealMain
    Column(
        Modifier.fillMaxWidth().drawBehind {
            drawRoundRect(
                teal, cornerRadius = CornerRadius(28.dp.toPx()),
                style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))),
            )
        }.padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = BeffType.Caption28.copy(fontWeight = FontWeight.SemiBold, color = Beff.TealText))
        Text(p.name, style = BeffType.Box40.copy(color = Beff.TextStrong), maxLines = 1, overflow = TextOverflow.Ellipsis)
        vm.nav.trips[p.id]?.let { t ->
            Text("${t.meters / 1000}km 앞 · ${fmtMinutes((t.seconds + 59) / 60)} 뒤 도착", style = BeffType.Body32.copy(color = Beff.TextSecondary), maxLines = 1)
        }
        FacilityGrid(vm.places.facilitiesFor(p, vm.active).ifEmpty { p.facilities }.take(4), tile = Beff.Surface)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ArrowButton("‹", onPrev)
            Box(
                Modifier.weight(1f).height(80.dp).clip(RoundedCornerShape(24.dp)).background(TealButton)
                    .clickable(enabled = onAdd != null) { onAdd?.invoke() },
                contentAlignment = Alignment.Center,
            ) { Text(if (onAdd != null) "경유지로 추가" else "경유지는 두 곳까지예요", style = BeffType.Small30.copy(color = Color.White)) }
            ArrowButton("›", onNext)
        }
    }
}

@Composable
private fun ArrowButton(t: String, onClick: () -> Unit) {
    Box(
        Modifier.size(80.dp).clip(CircleShape).background(Beff.Surface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(t, style = BeffType.Box40.copy(color = Beff.TextStrong)) }
}

/** 시설 2×2: 카드색 타일 r24 · 그림 56 · 30 SemiBold. */
@Composable
internal fun FacilityGrid(items: List<String>, tile: Color = Beff.Card) {
    if (items.isEmpty()) return
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { f ->
                    Row(
                        Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(24.dp)).background(tile).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BeffAsset(facilityIllust(f), 56.dp)
                        Text(f, style = BeffType.Small30.copy(color = Beff.TextStrong), maxLines = 2)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 남은 시간 | 거리. 경유지는 내비 조회값, 도착은 내비 경로 남은 값(없으면 조회값). 모르면 null. */
internal fun remainText(vm: AppViewModel, p: Place, dest: Boolean): String? {
    val r = vm.nav.route
    if (dest && r?.remainSeconds != null && r.remainMeters != null) {
        return "${fmtMinutes((r.remainSeconds + 59) / 60)} | ${r.remainMeters / 1000}km 남음"
    }
    return vm.nav.trips[p.id]?.let { "${fmtMinutes((it.seconds + 59) / 60)} | ${it.meters / 1000}km 남음" }
}

/** 「동생과 함께 가는 중이에요」. 프로필이 없으면 「가는 중이에요」. */
internal fun goingTitle(vm: AppViewModel): String {
    val n = vm.active?.name ?: return "가는 중이에요"
    val last = n.lastOrNull() ?: return "가는 중이에요"
    val batchim = last in '가'..'힣' && (last - '가') % 28 != 0
    return "$n${if (batchim) "과" else "와"} 함께 가는 중이에요"
}

/** 4: 흰 글자를 얹는 틸 바탕은 대비를 위해 #00807C. */
internal val TealButton = Color(0xFF00807C)
