package com.beff.app.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beff.app.AppViewModel
import com.beff.app.Journey
import com.beff.app.Tab
import com.beff.app.data.GeoPoint
import com.beff.app.data.Place
import com.beff.app.data.distanceKm
import com.beff.app.nav.NaviRoute
import com.beff.app.ui.Beff
import com.beff.app.ui.WIDE_GAP
import com.beff.app.ui.WIDE_BOTTOM
import com.beff.app.ui.WIDE_SIDE
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffBox
import com.beff.app.ui.BeffButton
import com.beff.app.ui.BeffGnb
import com.beff.app.ui.BeffType
import com.beff.app.ui.ButtonKind
import com.beff.app.ui.FacilityTile
import com.beff.app.ui.GrayButtonBg
import com.beff.app.ui.JourneyArrow
import com.beff.app.ui.JourneyCardFrame
import com.beff.app.ui.JourneyColorButton
import com.beff.app.ui.JourneyPanelFrame
import com.beff.app.ui.JourneyRouteBar
import com.beff.app.ui.LocalWide
import com.beff.app.ui.PageDots
import com.beff.app.ui.RouteStopMark
import com.beff.app.ui.WhoHeader
import com.beff.app.ui.WideNotice
import com.beff.app.ui.WideTab
import com.beff.app.ui.facilityIllust
import kotlinx.coroutines.delay

internal fun fmtMinutes(m: Int): String = if (m >= 60) "${m / 60}시간 ${m % 60}분" else "${m}분"

private val TitleInk get() = Beff.TextStrong

/** 한 번에 계산해 두는 여정 표시값. */
private class JourneyView(
    val j: Journey,
    val live: NaviRoute?,               // 내비가 알려 준 남은 시간 · 거리. 없으면 null(예시 숫자를 보여 주지 않는다).
    val progress: Float,
    val tripText: String,               // F-3 보조 줄: "13분(12km) 주행 중, 52분(50km) 남음"
    val suggestions: List<Place>,       // 가는 길 쉬어갈 곳(이미 넣은 곳 포함)
)

/**
 * F. 여정 탭. 여정이 있으면 F-1(경유지 없음) · F-2(경유지 있음), 오른쪽 「쉬어갈만한 곳」 카드를 누르면 F-3 쉬어가기 추천 캐러셀
 * (경유지로 추가하거나 뒤로 가면 F-1 · F-2로 돌아온다). 여정이 없으면 F-4 + 지난 여정, 지난 여정을 누르면 F-5.
 * 2/3 전체 화면(LocalWide)에서는 이 화면이 GNB까지 직접 그린다. 이 화면들에서는 GNB의 「여정」 탭이 켜진다.
 */
@Composable
fun JourneyScreen(vm: AppViewModel) {
    val wide = LocalWide.current
    val j = vm.journey
    if (j == null) { PastJourneysTab(vm); return }

    // 프로필의 「가는 길에 들르는 곳」으로 찾은 곳을 먼저(없으면 무장애 데이터).
    LaunchedEffect(j.dest.id, vm.active?.id) { vm.loadRouteStops(j.dest) }
    val suggestions = vm.restSuggestions()
    val live = vm.nav.route?.takeIf { vm.nav.routeActive && it.remainSeconds != null && it.remainMeters != null }
    val tripText = live?.let {
        val totalMin = it.totalSeconds / 60
        val remain = it.remainSeconds!! / 60
        val totalKm = it.totalMeters / 1000
        val remainKm = it.remainMeters!! / 1000
        if (totalMin - remain < 1) "${fmtMinutes(remain)}(${remainKm}km) 남음"   // 출발 전이면 「0분(0km) 주행 중」을 쓰지 않는다
        else "${fmtMinutes(totalMin - remain)}(${(totalKm - remainKm).coerceAtLeast(0)}km) 주행 중, ${fmtMinutes(remain)}(${remainKm}km) 남음"
    } ?: "내비가 경로를 계산하는 중이에요"
    // 제안 장소 · 경유지까지의 거리 · 시간은 내비에 조회한다(경로를 바꾸지 않는 조회).
    RequestTrips(vm, j.stops + suggestions)
    LaunchedEffect(vm.nav.link, j.dest.id) {
        while (vm.nav.link == com.beff.app.nav.NaviLink.Connected) { vm.nav.refresh(); delay(10_000) }
    }
    val v = JourneyView(j, live, live?.progress ?: 0f, tripText, suggestions)

    // F-3: 쉬어갈 곳 카드를 눌러 들어온 캐러셀. 경유지가 바뀌면(추가 확인) 여정 화면으로 돌아간다(Figma: 경유지로 추가 → BACK).
    var restFocus by remember(j.dest.id) { mutableStateOf(false) }
    LaunchedEffect(j.stops.size) { restFocus = false }
    BackHandler(enabled = restFocus) { restFocus = false }
    val openRest: (Place) -> Unit = { p ->
        val n = suggestions.size
        val idx = suggestions.indexOfFirst { it.id == p.id }
        if (n > 0 && idx >= 0) {
            vm.cycleSuggestion(idx - j.suggestionIndex.mod(n))
            restFocus = true
        }
    }
    // 1113: F-3 캐러셀은 타임라인 점선 카드로 들어갔다(4-16). 옛 캐러셀 화면은 쓰지 않는다.
    @Suppress("UNUSED_VARIABLE") val unusedCarousel = restFocus
    // 1113: 오른쪽 「근처 가볼만한 곳」 카드를 누르면 장소 상세(C-6 · D-2 틀)에서 보고 [경유지로 추가하기].
    var detail by remember(j.dest.id) { mutableStateOf<Place?>(null) }
    LaunchedEffect(j.stops.size) { detail = null }
    BackHandler(enabled = detail != null) { detail = null }
    detail?.let { p ->
        val full = j.stops.size >= 2
        PlaceDetail(
            vm, p,
            action = if (full) "경유지는 두 곳까지예요" else "경유지로 추가하기",
            onAction = { vm.askAddStop(p) },
            onBack = { detail = null },
            actionEnabled = !full,
        )
        return
    }
    val openDetail: (Place) -> Unit = { detail = it }
    if (wide) JourneyWide(vm, v, openDetail) else JourneyNarrow(vm, v, openDetail)
}

// ───────────────────────── F-1 · F-2 여정 중 ─────────────────────────

/** 오른쪽 「근처 가볼만한 곳」 카드 한 장. */
private class NearbyItem(val place: Place, val caption: String, val sub: String?, val rest: Boolean)

@Composable
private fun JourneyWide(vm: AppViewModel, v: JourneyView, openRest: (Place) -> Unit) {
    JourneyPanelFrame(
        gnb = { Box(Modifier.padding(end = 32.dp)) { BeffGnb(Tab.Journey, vm::selectTab, sidePadding = 0.dp) } },
        left = {
            Box(Modifier.fillMaxSize().padding(end = 50.dp)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 28.dp, bottom = 24.dp)) {
                    JourneyHeader(vm, v)
                    Spacer(Modifier.height(48.dp))
                    TimelineFor(vm, v)
                }
                vm.notice?.let { WideNotice(it, vm, Modifier.align(Alignment.BottomCenter).offset(y = 8.dp)) }
            }
        },
        right = {
            Column(Modifier.fillMaxSize().padding(start = 58.dp, end = 58.dp, top = 74.dp)) {
                Text("근처 가볼만한 곳을 찾아봤어요", style = BeffType.Box40.copy(color = TitleInk), maxLines = 1)
                Spacer(Modifier.height(24.dp))
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) { NearbyCards(vm, v, openRest) }
            }
        },
    )
}

/** 3분할(840, Figma 1/3 정본): 제목 + 세로 타임라인만. GNB · 토스트는 MainScreen이 그린다. */
@Composable
private fun JourneyNarrow(vm: AppViewModel, v: JourneyView, @Suppress("UNUSED_PARAMETER") openRest: (Place) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 56.dp).padding(top = 40.dp, bottom = 72.dp),
    ) {
        JourneyHeader(vm, v)
        Spacer(Modifier.height(48.dp))
        TimelineFor(vm, v)
    }
}

/** 4-15 · 4-16: 세로 타임라인. 경유지가 없고 쉬어갈 곳이 있으면 경유지 자리에 점선 추천 카드(F-3). */
@Composable
private fun TimelineFor(vm: AppViewModel, v: JourneyView) {
    val sug = if (v.j.stops.isEmpty() && v.suggestions.isNotEmpty()) v.current() else null
    JourneyTimeline(
        vm, v.j,
        suggestion = sug,
        suggestionLabel = "들를 만한 곳 추천 · ${v.j.suggestionIndex.mod(v.suggestions.size.coerceAtLeast(1)) + 1}/${v.suggestions.size}",
        onPrev = { vm.cycleSuggestion(-1) },
        onNext = { vm.cycleSuggestion(1) },
        onAdd = sug?.let { p -> { vm.askAddStop(p) } },
    )
}

/**
 * F-1 · F-2 머리: [아바타 80] 「○○ 가는 길」(56 ExtraBold + 40 SemiBold) · 둘째 줄 「n분 남았어요」 59 ExtraBold #006D68.
 * 경유지가 있고 내비가 그곳까지 시간을 알려 주면 「○○까지」(40 SemiBold 회색)를 앞에 붙여 다음 경유지까지 남은 시간을 쓴다.
 * 내비 값이 없으면 숫자 대신 「내비가 경로를 계산하는 중이에요」. 오른쪽 위 「여정 취소」(확인 창 → 여정 종료 + 내비 안내 종료).
 */
@Composable
private fun JourneyHeader(vm: AppViewModel, @Suppress("UNUSED_PARAMETER") v: JourneyView) {
    // 4-15 · 6-7: 「○○과 함께 가는 중이에요」 48 ExtraBold. 프로필 썸네일 뺌(6-6). [여정 취소]는 기능 유지(정책 #53, Figma 미정).
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(goingTitle(vm), style = BeffType.Title48.copy(color = Beff.TextStrong), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        com.beff.app.ui.SmallTagButton("여정 취소") { vm.askEndJourney = true }
    }
}

/** 이름 앞부분(괄호 · 공백 앞): "안성휴게소(부산방향)" → "안성휴게소". */
private fun shortName(p: Place): String = p.name.substringBefore(" ").substringBefore("(").ifBlank { p.name }

/**
 * 진행바. 경유지 위치는 내비가 알려 준 그곳까지 거리 ÷ 목적지까지 남은 거리로 놓고, 모르면 고르게 놓는다(위치 근사).
 */
@Composable
private fun StopsRoute(vm: AppViewModel, v: JourneyView, modifier: Modifier) {
    val p = v.progress
    val remainM = v.live?.remainMeters?.takeIf { it > 0 }
    val marks = v.j.stops.mapIndexed { i, s ->
        val trip = vm.nav.trips[s.id]
        val at = if (remainM != null && trip != null) p + (1 - p) * (trip.meters.toFloat() / remainM).coerceIn(0.05f, 0.95f)
        else p + (1 - p) * if (v.j.stops.size == 1) 0.5f else (if (i == 0) 0.35f else 0.7f)
        RouteStopMark(at, shortName(s))
    }
    JourneyRouteBar(p, marks, modifier)
}

/**
 * 경유지 · 목적지 아코디언(F-2). 접힘 = 시설 칩, 펼침 = 시설 칩 + (경유지: 내비 반영 · 경유지에서 빼기 / 목적지: 하차 동선).
 * 경유지가 없으면(F-1) 목적지가 펼쳐진 채로 시작한다.
 */
@Composable
private fun ColumnScope.PlaceAccordions(vm: AppViewModel, j: Journey) {
    val open = remember(j.dest.id) { mutableStateMapOf<String, Boolean>() }
    fun isOpen(id: String, default: Boolean) = open[id] ?: default
    j.stops.forEach { s ->
        PlaceAccordion(
            title = s.name, titleSize = 36, illust = s.kind.illust, illustSize = 88.dp,
            chips = chipsFor(vm, s), chipHeight = 112.dp,
            expanded = isOpen(s.id, false), onToggle = { open[s.id] = !isOpen(s.id, false) },
        ) {
            val reason = vm.nav.sync?.takeIf { it.state == com.beff.app.nav.SyncState.NotSent || it.state == com.beff.app.nav.SyncState.Failed }?.message
            NaviStatusLine(vm.stopOnNavi(s), reason)
            BeffButton("경유지에서 빼기", { vm.askRemoveStop(s) }, kind = ButtonKind.Muted)
        }
    }
    val destDefault = j.stops.isEmpty()
    PlaceAccordion(
        title = j.dest.name, titleSize = if (j.stops.isEmpty()) 40 else 36, illust = "illust/pin.svg", illustSize = 93.dp,
        chips = chipsFor(vm, j.dest), chipHeight = 112.dp,
        expanded = isOpen("dest", destDefault), onToggle = { open["dest"] = !isOpen("dest", destDefault) },
    ) {
        UnloadSteps(vm, j.dest)
        JourneyColorButton("하차 동선 미리 보기", { vm.openUnload(j.dest.id) }, GrayButtonBg, Beff.TextSecondary)
    }
}

/** 장소 칩: 함께 타는 분 필터 ∩ 장소 시설(2개). 시설 정보가 없는 곳(카카오)은 찾은 칩(휴게소 · 카페 …)만. */
private fun chipsFor(vm: AppViewModel, p: Place): List<String> =
    vm.places.facilitiesFor(p, vm.active).filter { it in p.facilities }.take(2).ifEmpty { listOfNotNull(p.note) }

/** 아코디언 카드: 흰 90% r36 · 안쪽 34 · 머리(흰 타일 104 r28 + 이름 + 꺾쇠) · 시설 칩 2칸(#EEF4F4 r32, 그림 72 + 32 Bold). */
@Composable
private fun PlaceAccordion(
    title: String,
    titleSize: Int,
    illust: String,
    illustSize: Dp,
    chips: List<String>,
    chipHeight: Dp,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BeffBox(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(Modifier.padding(34.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(104.dp).clip(RoundedCornerShape(28.dp)).background(Beff.Card), contentAlignment = Alignment.Center) {
                    BeffAsset(illust, illustSize)
                }
                Text(
                    title,
                    style = BeffType.Box40.copy(fontSize = titleSize.sp, color = Beff.TextPrimary, letterSpacing = 0.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 20.dp, end = 16.dp),
                )
                BeffAsset(
                    "pict/chevron_right.svg", 40.dp,
                    Modifier.padding(end = 4.dp).rotate(if (expanded) -90f else 90f), tint = Color(0xFF52656A),
                )
            }
            if (chips.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    chips.forEach { FacilityTile(it, Modifier.weight(1f).height(chipHeight)) }
                    if (chips.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (expanded) content()
        }
    }
}

/** 펼친 목적지의 하차 동선(F-1): 순서 배지 40 · 그림 96 · 이름 36 + 방향 32 Bold, 줄 사이 구분선. 장소 원문에서 만든다. */
@Composable
private fun UnloadSteps(vm: AppViewModel, dest: Place) {
    val rows = arrivalRows(vm, dest)
    if (rows.isEmpty()) {
        Text("이 장소는 하차 동선 정보가 적혀 있지 않아요", style = BeffType.Body32.copy(color = Beff.TextSecondary), modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        rows.forEachIndexed { i, r ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(Beff.Stroke))
            Row(Modifier.fillMaxWidth().height(126.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Beff.Teal), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", style = BeffType.Body32.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                }
                Spacer(Modifier.width(20.dp))
                BeffAsset(r.illust, 96.dp)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = BeffType.Body32.copy(fontSize = 36.sp, color = Beff.TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (r.line2.isNotBlank()) {
                        Text(r.line2, style = BeffType.Body32.copy(fontWeight = FontWeight.Bold, color = Beff.TextStrong), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * 오른쪽 「근처 가볼만한 곳」: 가는 길 쉬어갈 곳(아직 넣지 않은 것, 캡션 「쉬어갈만한 곳」, 누르면 F-3) + 목적지 근처(3km) 무장애 장소
 * (캡션 「근처 가볼만한 곳」, 목적지에서 직선 거리). 경유지는 둘까지라 꽉 차면 [경유지로 추가]를 숨긴다.
 */
@Composable
private fun NearbyCards(vm: AppViewModel, v: JourneyView, openRest: (Place) -> Unit) {
    val j = v.j
    val stopIds = j.stops.map { it.id }.toSet()
    val nearDest = remember(j.dest.id, vm.active?.id) { vm.places.alternativesNear(j.dest, vm.active, radiusKm = 3.0, limit = 3) }
    val rest = v.suggestions.filter { it.id !in stopIds }
    val items = rest.map { NearbyItem(it, "들를 만한 곳", restSub(vm, it), rest = true) } +
        nearDest.filter { p -> p.id !in stopIds && rest.none { it.id == p.id } }.map { p ->
            val km = if (p.lat != null && p.lng != null && j.dest.lat != null && j.dest.lng != null)
                distanceKm(GeoPoint(j.dest.lat, j.dest.lng), GeoPoint(p.lat, p.lng)) else null
            NearbyItem(p, "근처 가볼만한 곳", km?.let { String.format("목적지에서 직선 %.1fkm", it) }, rest = false)
        }
    if (items.isEmpty()) {
        Text("가는 길과 목적지 근처에서 함께 타는 분께 맞는 곳을 찾지 못했어요", style = BeffType.Body32.copy(color = Beff.TextSecondary))
        return
    }
    items.forEach { item -> NearbyCard(vm, item, onOpen = { openRest(item.place) }) }
}

/** 쉬어갈 곳 보조 줄: 내비 값 "22km · 23분 뒤 도착", 없으면 지금 위치에서 "직선 3.2km". */
private fun restSub(vm: AppViewModel, p: Place): String? {
    vm.nav.trips[p.id]?.let { t -> return "${t.meters / 1000}km · ${fmtMinutes((t.seconds + 59) / 60)} 뒤 도착" }
    if (p.lat == null || p.lng == null) return null
    return String.format("직선 %.1fkm", distanceKm(vm.origin, GeoPoint(p.lat, p.lng)))
}

/**
 * 카드 718×334 흰 r30 · 테두리 #DFE9E5 2. 왼쪽: 캡션 28 SemiBold #006D68 · 이름 40 Bold · 보조 26 SemiBold #506367 · 구분선 · 칩(52, #F2F7F5).
 * 오른쪽: 사진 322(없으면 일러스트) + 아래 [경유지로 추가] 흰 알약 235×74 r24(32 Bold #006D68).
 */
@Composable
private fun NearbyCard(vm: AppViewModel, item: NearbyItem, onOpen: () -> Unit) {
    // 6-13: 사진 168 왼쪽 · 라벨 · 이름 36 SemiBold · 거리 28 · 칩(카드색) + 오른쪽 [경유지로 추가] 주 버튼. 바탕 Surface r28.
    val shape = RoundedCornerShape(28.dp)
    val p = item.place
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Beff.Surface)
            .clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(Modifier.size(168.dp).clip(RoundedCornerShape(20.dp))) { PlacePhoto(p, Modifier.fillMaxSize(), illustSize = 96.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.caption, style = BeffType.Caption28.copy(fontWeight = FontWeight.SemiBold, color = Beff.ChipText), maxLines = 1)
            Text(p.name, style = BeffType.Item36Semi.copy(color = TitleInk), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.sub.orEmpty(), style = BeffType.Caption28.copy(color = Beff.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { chipsFor(vm, p).take(1).forEach { MiniChip(it) } }
        }
        // 1113: [경유지로 추가] 버튼 뺌(D-1과 같음) — 카드를 누르면 상세에서 경유지로 넣는다.
        BeffAsset("pict/chevron_right.svg", 48.dp, tint = Beff.TextSecondary)
    }
}

/** Beff / Chip 작은 칩: 52 · #F2F7F5 · 테두리 #DAE7E2 2 · 28 SemiBold #044846. */
@Composable
private fun MiniChip(text: String) {
    val shape = RoundedCornerShape(100.dp)
    Box(
        Modifier.height(46.dp).clip(RoundedCornerShape(12.dp)).background(Beff.Card).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BeffType.Caption28.copy(color = Beff.ChipText), maxLines = 1) }
}

// ───────────────────────── F-3 쉬어가기 추천 캐러셀 ─────────────────────────

/** 지금 캐러셀이 보여 주는 쉴 곳. */
private fun JourneyView.current(): Place = suggestions[j.suggestionIndex.mod(suggestions.size)]

/**
 * F-3 와이드: 왼쪽 = 「○○과 함께」 · 「○○ 가는 길」 · 주행/남은 줄 · 진행바(쉴 곳) · 「여기서 쉬어가는 걸 추천드려요」 캐러셀 + GNB,
 * 오른쪽 = 「도착하면 쓸 수 있어요」 카드 + 「도착하면 이렇게 내려요」.
 */
@Composable
private fun RestCarouselWide(vm: AppViewModel, v: JourneyView) {
    val j = v.j
    val facilities = vm.places.facilitiesFor(j.dest, vm.active).take(2)
    WideTab(
        Tab.Journey, vm::selectTab,
        left = {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
                    RestHeader(vm, v)
                    Spacer(Modifier.height(32.dp))
                    Box(Modifier.padding(horizontal = 32.dp).padding(top = 8.dp)) { RouteLine(v.progress, restMarkers(v), height = 140) }
                    Spacer(Modifier.weight(1f))
                    RestCard(vm, v, arrow = 76.dp)
                }
                vm.notice?.let { WideNotice(it, vm, Modifier.align(Alignment.BottomCenter).offset(y = 8.dp)) }
            }
        },
        right = {
            Column(Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                JourneyCardFrame("도착하면 쓸 수 있어요", Modifier.height(420.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { ArriveTiles(facilities) }
                }
                val arrival = arrivalRows(vm, j.dest)
                if (arrival.isNotEmpty()) ArrivalBox("도착하면 이렇게 내려요", arrival, illustSize = 96, rowHeight = 124, modifier = Modifier.weight(1f))
                else AccessInfoCard(j.dest, vm.active, modifier = Modifier.weight(1f))
            }
        },
    )
}

/** F-3 3분할: 머리 · 진행바 · 캐러셀 카드 한 열. */
@Composable
private fun RestCarouselNarrow(vm: AppViewModel, v: JourneyView) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 56.dp).padding(top = 24.dp, bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        RestHeader(vm, v)
        Box(Modifier.padding(horizontal = 32.dp).padding(top = 8.dp)) { RouteLine(v.progress, restMarkers(v), height = 140) }
        RestCard(vm, v, arrow = 72.dp)
    }
}

private fun restMarkers(v: JourneyView): List<RouteMarker> =
    v.j.stops.mapIndexed { i, s -> RouteMarker(if (i == 0) 0.45f else 0.7f, true, shortName(s)) } +
        RouteMarker(if (v.j.stops.isEmpty()) 0.55f else 0.85f, added = false, label = "쉴 곳")

@Composable
private fun RestHeader(vm: AppViewModel, v: JourneyView) {
    Column {
        // F-1 · F-2와 같은 머리(「○○ 가는 길 · n분 남았어요」). 옛 「n분(nkm) 주행 중, n분 남음」 줄은 쓰지 않는다.
        JourneyHeader(vm, v)
    }
}

/** 캐러셀 카드: < 쉴 곳 요약 > · [경유지로 추가](이미 넣었으면 흐리게) · 쪽 점. */
@Composable
private fun RestCard(vm: AppViewModel, v: JourneyView, arrow: Dp) {
    val place = v.current()
    val n = v.suggestions.size
    val added = v.j.stops.any { it.id == place.id }
    JourneyCardFrame("여기서 쉬어가는 걸 추천드려요") {
        Row(Modifier.fillMaxWidth().height(182.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            JourneyArrow(false, { vm.cycleSuggestion(-1) }, arrow)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PlaceSummary(vm, place) }
            JourneyArrow(true, { vm.cycleSuggestion(1) }, arrow)
        }
        when {
            added -> BeffButton("경유지에 넣었어요", {}, enabled = false)
            v.j.stops.size >= 2 -> BeffButton("경유지는 두 곳까지예요", {}, enabled = false)
            else -> BeffButton("경유지로 추가", { vm.askAddStop(place) })
        }
        PageDots(n, v.j.suggestionIndex.mod(n))
    }
}

@Composable
private fun PlaceSummary(vm: AppViewModel, place: Place) {
    val facilities = vm.places.facilitiesFor(place, vm.active).filter { it in place.facilities }
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(144.dp).clip(RoundedCornerShape(32.dp)).background(Beff.Card), contentAlignment = Alignment.Center) {
            BeffAsset(place.kind.illust, 120.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(shortName(place), style = BeffType.Brand44.copy(fontWeight = FontWeight.ExtraBold, color = Beff.TextStrong), maxLines = 1, overflow = TextOverflow.Ellipsis)
            restSub(vm, place)?.let { Text(it, style = BeffType.Body32.copy(color = Beff.TextSecondary), maxLines = 1) }
            val line = (facilities.take(1) + listOfNotNull(place.note)).joinToString(" · ")
            if (line.isNotBlank()) Text(line, style = BeffType.Body32.copy(color = Beff.TextPrimary, fontWeight = FontWeight.SemiBold), maxLines = 2)
        }
    }
}

/** 흰 타일 351×178 · r28 — 일러스트 88 위, 이름 32 Bold 아래. */
@Composable
private fun ArriveTile(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.height(178.dp).clip(RoundedCornerShape(28.dp)).background(Beff.Card),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        BeffAsset(facilityIllust(label), 88.dp)
        Text(label, style = BeffType.Body32.copy(color = Beff.TextStrong, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun ArriveTiles(facilities: List<String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        facilities.forEach { ArriveTile(it, Modifier.weight(1f)) }
    }
}

// ───────────────────────── F-4 여정 없음 ─────────────────────────

/** 여정이 없을 때: F-4(가는 곳 없음 + 지난 여정), 지난 여정을 누르면 F-5. 뒤로 가기는 F-5 → F-4. */
@Composable
private fun PastJourneysTab(vm: AppViewModel) {
    var openAt by rememberSaveable { mutableStateOf<Long?>(null) }
    val past = openAt?.let { at -> vm.history.items.firstOrNull { it.at == at } }
    BackHandler(enabled = past != null) { openAt = null }
    if (past != null) { PastJourneyDetail(vm, past, onBack = { openAt = null }); return }
    // 4-17: 지난 여정 목록 뺌. 회색 그라디언트 바탕(#F7F8F9 → #DCE1E5, 다크는 바탕 → 카드색), 빈 상태만 가운데.
    @Suppress("UNUSED_VARIABLE") val keepOpen = openAt
    val wide = LocalWide.current
    val grad = androidx.compose.ui.graphics.Brush.verticalGradient(
        if (Beff.dark) listOf(Beff.Bg, Beff.Card) else listOf(Color(0xFFF7F8F9), Color(0xFFDCE1E5)),
    )
    Box(Modifier.fillMaxSize().background(grad)) {
        Column(
            Modifier.align(Alignment.Center).then(if (wide) Modifier.fillMaxWidth() else Modifier.padding(horizontal = 56.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { EmptyContent(vm, buttonWidth = 272.dp) }
        // 1113: 다른 탭(WideTab)과 같은 폭 — 고정 778 대신 (화면 − 여백 56×2 − 간격 32) ÷ 2.
        if (wide) Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = WIDE_SIDE, end = WIDE_SIDE, bottom = WIDE_BOTTOM)) {
            Box(Modifier.weight(1f)) { BeffGnb(Tab.Journey, vm::selectTab, sidePadding = 0.dp) }
            Spacer(Modifier.width(WIDE_GAP))
            Spacer(Modifier.weight(1f))
        }
    }
}

/** 표정 160 · 제목 48 · 보조 32 · 「목적지 검색」 100 알약(→ 홈 검색). */
@Composable
private fun ColumnScope.EmptyContent(vm: AppViewModel, buttonWidth: Dp) {
    BeffAsset("face/face_neutral.svg", 160.dp, tint = Beff.Muted2)
    Text(
        "아직 가는 곳이 없어요",
        style = BeffType.Title48.copy(color = Beff.TextStrong, textAlign = TextAlign.Center),
        modifier = Modifier.padding(top = 40.dp),
    )
    Text(
        "목적지를 정하면 가는 길을 여기서 볼 수 있어요",
        style = BeffType.Body32.copy(color = Beff.TextSecondary, textAlign = TextAlign.Center),
        modifier = Modifier.padding(top = 12.dp),
    )
    val pill = RoundedCornerShape(100.dp)
    Box(
        Modifier.padding(top = 40.dp).width(buttonWidth).height(100.dp).clip(pill).background(TealButton).clickable { vm.selectTab(Tab.Search) },
        contentAlignment = Alignment.Center,
    ) { Text("목적지 검색", style = BeffType.Button36.copy(color = Color.White), maxLines = 1) }
}
