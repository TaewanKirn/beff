package com.beff.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beff.app.AppViewModel
import com.beff.app.nav.SyncState

// ── 여정(05) · 출발 브리핑(06-1) 공용 조각. 스타일프레임 값 그대로. ──

/** 경고 토큰: `beff_warning` #D32F2F · `beff_warning_background` #FDECEA (정책 「대체 지점」 행). 2/3 캡션은 #B71C1C. */
val WarnRed: Color get() = Beff.Warning
val WarnRedDeep = Color(0xFFB71C1C)
val WarnBg: Color get() = Beff.WarningTint

val TealCardBg: Color get() = Beff.TealTint
val TealCardBorder = Color(0x59008E8C)
val TealCaption: Color get() = Beff.ChipText
val GrayButtonBg: Color get() = Beff.SecondaryBtn

/** 여정 카드 틀: 옅은 틸(또는 경고색) 바탕 r36 · 안쪽 30 · 캡션 32 SemiBold + 20 간격. */
@Composable
fun JourneyCardFrame(
    caption: String,
    modifier: Modifier = Modifier,
    captionColor: Color = TealCaption,
    bg: Color = TealCardBg,
    border: Color = TealCardBorder,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(36.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(bg).border(2.dp, border, shape).padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            caption,
            style = BeffType.Body32.copy(color = captionColor, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
        )
        content()
    }
}

/** 카드 안 < > 둥근 버튼. 3분할 72, 2/3 전체 화면 76(터치 76). */
@Composable
fun JourneyArrow(forward: Boolean, onClick: () -> Unit, size: Dp = 72.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Beff.Card).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BeffAsset("pict/chevron_right.svg", 48.dp, if (forward) Modifier else Modifier.graphicsLayer(scaleX = -1f))
    }
}

/** 색을 직접 정하는 큰 버튼(100 · r32 · 36 SemiBold). 경고색 · 회색 버튼용 — 기본 틸은 BeffButton을 쓴다. */
@Composable
fun JourneyColorButton(text: String, onClick: () -> Unit, container: Color, content: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier.fillMaxWidth().height(100.dp).clip(shape).background(container).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BeffType.Button36.copy(color = content), maxLines = 1) }
}

/** 2/3 전체 화면 경유지 확인 표시. 왼쪽 열 GNB 위 24(호출하는 쪽이 위치를 정한다). MainScreen의 토스트와 같은 서식. */
@Composable
fun WideNotice(n: AppViewModel.Notice, vm: AppViewModel, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(36.dp)
    val sync = vm.nav.sync
    val ok = sync?.state == SyncState.Confirmed || sync?.state == SyncState.Sent
    val waiting = sync?.state == SyncState.Pending || sync?.state == SyncState.Late
    val good = ok || waiting
    // 1113 효과음: 뜰 때 토스트음, 내비 반영에 실패하면 경고음(MainScreen WaypointToast와 같다).
    androidx.compose.runtime.LaunchedEffect(n) { com.beff.app.device.Sfx.toast() }
    androidx.compose.runtime.LaunchedEffect(n, sync?.state) {
        if (sync?.state == SyncState.NotSent || sync?.state == SyncState.Failed) {
            kotlinx.coroutines.delay(350)
            com.beff.app.device.Sfx.warning()
        }
    }
    Box(
        modifier.fillMaxWidth()
            .shadow(16.dp, shape, ambientColor = Color(0x47004040), spotColor = Color(0x47004040))
            .clip(shape)
            .background(if (good) TealCardBg else Beff.WarningTint)
            .border(2.dp, if (good) TealCardBorder else Color(0x59C62828), shape)
            .clickable { vm.dismissNotice() },
    ) {
        Column(Modifier.padding(horizontal = 36.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(n.title, style = BeffType.Item36.copy(color = Beff.TextStrong), modifier = Modifier.padding(end = 80.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BeffAsset(
                    if (ok) "pict/verified.svg" else if (waiting) "pict/route.svg" else "pict/warning.svg",
                    40.dp, tint = if (ok) Beff.Teal else if (waiting) Beff.TextSecondary else Beff.Warning,
                )
                Text(sync?.message ?: "내비에 전달하지 않았어요", style = BeffType.Body32.copy(fontSize = 30.sp, color = Beff.TextSecondary))
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp).size(76.dp).clickable { vm.dismissNotice() },
            contentAlignment = Alignment.Center,
        ) { BeffAsset("pict/close.svg", 48.dp) }
    }
}

// ── 1110 F. 여정 탭 공용 틀 · 진행바 ──

/**
 * F-1 · F-2 · F-4 · F-5 와이드 틀: 왼쪽 열(여백 56, 폭 778, 위 48 · 아래 40, GNB 포함) + 오른쪽 흰 패널(x 866, 폭 834, 위아래 끝까지).
 * 패널은 왼쪽 모서리만 r36 + 옅은 그림자. [right]의 안쪽 여백(좌우 58, 위 74)은 호출하는 쪽이 정한다.
 * [gnb]가 null이면 GNB 없이 왼쪽 열 전체를 [left]가 쓴다.
 */
@Composable
fun JourneyPanelFrame(
    gnb: (@Composable () -> Unit)?,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(866f).fillMaxHeight().padding(start = WIDE_SIDE, top = WIDE_TOP, bottom = WIDE_BOTTOM)) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(end = 32.dp), content = left)
            if (gnb != null) {
                Spacer(Modifier.height(WIDE_GAP))
                gnb()
            }
        }
        val shape = RoundedCornerShape(topStart = 36.dp, bottomStart = 36.dp)
        Column(
            Modifier.weight(834f).fillMaxHeight()
                .shadow(20.dp, shape, ambientColor = Beff.BoxShadow, spotColor = Beff.BoxShadow)
                .clip(shape).background(Beff.Card),
            content = right,
        )
    }
}

/** 진행바 위 경유지 표시: 위치(0..1) · 이름(아래 28 SemiBold). [suggested]면 점선(아직 넣지 않은 쉴 곳). */
data class RouteStopMark(val at: Float, val label: String, val suggested: Boolean = false)

/**
 * F-1 · F-2 진행바(664×140, 왼쪽 여백 32는 호출하는 쪽): 선 636 · 출발 · 도착 고리 28(틸 6) · 내 차 64 · 경유지 50(옅은 틸 + 점선 틸) ·
 * 이름 28 SemiBold #006D68은 선 아래 77. 「도착」 이름은 끝 고리 아래.
 */
@Composable
fun JourneyRouteBar(progress: Float, stops: List<RouteStopMark>, modifier: Modifier = Modifier) {
    val p = progress.coerceIn(0f, 1f)
    val labelColor = Beff.ChipText
    Box(modifier.width(664.dp).height(140.dp)) {
        Box(Modifier.offset(14.dp, 38.dp).size(636.dp, 4.dp).background(Beff.Muted2))
        if (p > 0f) Box(Modifier.offset(14.dp, 37.dp).size((636 * p).dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(Beff.Teal))
        listOf(0, 636).forEach { x ->
            Box(Modifier.offset(x.dp, 26.dp).size(28.dp).clip(CircleShape).background(Beff.Card).border(6.dp, Beff.Teal, CircleShape))
        }
        stops.forEach { s ->
            val cx = 14 + 636 * s.at.coerceIn(0f, 1f)
            Box(
                Modifier.offset((cx - 25).dp, 15.dp).size(50.dp).clip(CircleShape).background(TealCardBg)
                    .then(if (s.suggested) Modifier.dashedRing(Beff.Teal) else Modifier.border(2.7.dp, Beff.Teal, CircleShape)),
                contentAlignment = Alignment.Center,
            ) { BeffAsset("pict/rest.svg", 36.dp, tint = Beff.TealText) }
            Text(
                s.label,
                style = BeffType.Caption28.copy(fontWeight = FontWeight.SemiBold, color = labelColor, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.offset((cx - 100).dp, 77.dp).width(200.dp),
            )
        }
        Text(
            "도착",
            style = BeffType.Caption28.copy(fontWeight = FontWeight.SemiBold, color = labelColor),
            modifier = Modifier.offset(626.dp, 77.dp),
        )
        Box(
            Modifier.offset((14 + 636 * p - 32).dp, 8.dp)
                .shadow(6.dp, CircleShape, ambientColor = Color(0x40004D4D), spotColor = Color(0x40004D4D))
                .size(64.dp).clip(CircleShape).background(Beff.Teal).border(4.dp, Beff.BoxBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) { BeffAsset("pict/car.svg", 48.dp, tint = Color.White) }
    }
}

private fun Modifier.dashedRing(color: Color): Modifier = this.drawBehind {
    drawCircle(
        color = color,
        radius = size.minDimension / 2 - 1.4.dp.toPx(),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2.7.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        ),
    )
}
