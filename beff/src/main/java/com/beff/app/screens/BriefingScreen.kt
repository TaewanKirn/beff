package com.beff.app.screens

import com.beff.app.ui.fadeInOnce
import com.beff.app.ui.popIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beff.app.AppViewModel
import com.beff.app.Route
import com.beff.app.data.GeoPoint
import com.beff.app.data.Place
import com.beff.app.data.distanceKm
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffType
import com.beff.app.ui.BottomBar
import com.beff.app.ui.FacilityTile
import com.beff.app.ui.LocalWide
import com.beff.app.ui.ScreenFrame
import com.beff.app.ui.TitleBar
import com.beff.app.ui.facilityIllust
import kotlinx.coroutines.delay

/** 출발 브리핑 화면 안의 단계: 장소 상세(C-5 · C-6) → 출발 브리핑(D-1 · D-3 · D-4) → 쉬어갈 곳 상세(D-2). */
private sealed interface BriefStep {
    data object Detail : BriefStep
    data object Brief : BriefStep
    data class Rest(val placeId: String) : BriefStep
}

/**
 * 1110 검색 결과 · 추천 → 장소 상세 → [여기로 출발] → 출발 브리핑.
 * 같은 Route(Briefing) 안에서 단계만 바꾼다(라우트 추가 없이). 이미 이 목적지로 계획(여정)이 있으면 바로 출발 브리핑부터.
 * 자동 시작은 하지 않는다: 쉬어갈 곳을 먼저 고르고 「안내 시작」을 누르면 「안내를 시작할까요?」(D-7)로 한 번 더 묻는다.
 */
@Composable
fun BriefingScreen(vm: AppViewModel, placeId: String) {
    val place = vm.places.placeOrNull(placeId) ?: vm.journey?.dest ?: return
    // 하차 미리 보기 · 근처 주차장에 다녀와도 보던 단계와 고른 경유지가 남도록 라우트가 스택에 있는 동안 기억한다.
    val memo = remember(placeId) {
        briefMemos.getOrPut(placeId) {
            val planned = vm.journey?.takeIf { it.dest.id == place.id }
            BriefMemo(startsAtBrief = planned != null, step = if (planned != null) BriefStep.Brief else BriefStep.Detail, stops = planned?.stops.orEmpty())
        }
    }
    DisposableEffect(placeId) {
        onDispose { if (vm.backStack.none { it is Route.Briefing && it.placeId == placeId }) briefMemos.remove(placeId) }
    }
    val startsAtBrief = memo.startsAtBrief
    var step by remember(placeId) { mutableStateOf(memo.step) }
    // 뒤로: 쉬어갈 곳 상세 → 출발 브리핑 → (상세에서 왔으면) 장소 상세 → 이전 화면.
    BackHandler(enabled = step is BriefStep.Rest || (step == BriefStep.Brief && !startsAtBrief)) {
        step = if (step is BriefStep.Rest) BriefStep.Brief else BriefStep.Detail
    }

    // 이 화면에서 고르는 경유지(최대 2곳). 같은 목적지로 이미 정해 둔 경유지가 있으면 그것부터.
    var stops by remember(placeId) { mutableStateOf(memo.stops) }
    var focusId by remember(placeId) { mutableStateOf(memo.focusId) }
    SideEffect { memo.step = step; memo.stops = stops; memo.focusId = focusId }
    val toggleStop = { p: Place ->
        if (stops.any { it.id == p.id }) stops = stops.filterNot { it.id == p.id }
        else if (stops.size < MAX_STOPS) { stops = stops + p; focusId = p.id }
    }
    // 목적지까지의 시간·거리는 내비에 조회한다(경로를 바꾸지 않는 조회).
    LaunchedEffect(place.id, vm.nav.link) { vm.nav.requestTripTo(place) }

    // 1113: 단계 전환도 화면 전환처럼(상세 → 브리핑 → 들를 곳 상세는 앞으로, 반대는 뒤로).
    fun order(x: BriefStep) = when (x) { BriefStep.Detail -> 0; BriefStep.Brief -> 1; is BriefStep.Rest -> 2 }
    androidx.compose.animation.AnimatedContent(
        targetState = step,
        transitionSpec = { com.beff.app.ui.Motion.screen(this, (order(targetState) - order(initialState)).coerceIn(-1, 1)) },
        label = "brief",
    ) { s -> when (s) {
        BriefStep.Detail ->
            if (place.hasNoAccessInfo) NoInfoDetail(vm, place, onGo = { step = BriefStep.Brief }, onBack = { vm.back() })
            else PlaceDetail(vm, place, "여기로 출발", onAction = { step = BriefStep.Brief }, onBack = { vm.back() })
        is BriefStep.Rest -> {
            val p = vm.restSuggestionsFor(place).firstOrNull { it.id == s.placeId } ?: stops.firstOrNull { it.id == s.placeId }
                ?: vm.places.placeOrNull(s.placeId)
            if (p == null) { LaunchedEffect(s) { step = BriefStep.Brief }; return@AnimatedContent }
            val on = stops.any { it.id == p.id }
            PlaceDetail(
                vm, p,
                action = if (on) "경유지에서 빼기" else if (stops.size >= MAX_STOPS) "경유지는 ${MAX_STOPS}곳까지" else "경유지로 추가하기",
                onAction = { toggleStop(p); step = BriefStep.Brief },
                onBack = { step = BriefStep.Brief },
                actionEnabled = on || stops.size < MAX_STOPS,
            )
        }
        BriefStep.Brief -> BriefBody(
            vm, place, stops, focusId,
            onFocus = { focusId = it },
            onToggle = toggleStop,
            onOpenRest = { step = BriefStep.Rest(it.id) },
            onBack = { if (startsAtBrief) vm.back() else step = BriefStep.Detail },
        )
    } }
}

private class BriefMemo(val startsAtBrief: Boolean, var step: BriefStep, var stops: List<Place>, var focusId: String? = null)

/** 장소 id → 출발 브리핑 화면 상태. 그 Briefing 라우트가 스택에서 빠지면 지운다. */
private val briefMemos = HashMap<String, BriefMemo>()

/** 출발 브리핑에서 고를 수 있는 경유지 수(여정 탭과 같은 규칙). */
private const val MAX_STOPS = 2

/**
 * D-1 출발 브리핑 · D-3 경유지 추가 후 · D-4 가는 길에 쉼터가 없을 때 · D-7 안내 시작 팝업.
 * 와이드: 왼쪽 제목(○○으로 출발할게요 + 소요 시간) · 선형 경로 · 목적지(또는 고른 경유지) 카드 · [안내 시작] · [하차 브리핑 미리 보기],
 * 오른쪽 흰 패널 「가는 길에 쉬어갈 곳」 사진 카드 목록(D-3은 목적지 카드가 위로 올라간다).
 */
@Composable
private fun BriefBody(
    vm: AppViewModel,
    place: Place,
    stops: List<Place>,
    focusId: String?,
    onFocus: (String) -> Unit,
    onToggle: (Place) -> Unit,
    onOpenRest: (Place) -> Unit,
    onBack: () -> Unit,
) {
    // 프로필의 「가는 길에 들르는 곳」(카페 · 휴게소 …)으로 먼저 찾고, 없으면 무장애 시설이 맞는 곳.
    LaunchedEffect(place.id, vm.active?.id) { vm.loadRouteStops(place) }
    val suggestions = vm.restSuggestionsFor(place)
    RequestTrips(vm, stops + suggestions)

    var asking by remember(place.id) { mutableStateOf(false) }
    var started by remember(place.id) { mutableStateOf(false) }
    fun start() { if (!started) { started = true; asking = false; vm.startJourney(place, stops) } }
    // D-7 팝업이 떠 있으면 뒤로 가기는 팝업만 닫는다(단계 이동 · 화면 뒤로보다 나중에 등록돼 먼저 받는다).
    BackHandler(enabled = asking) { asking = false }
    val previewUnload = { vm.openUnload(place.id) }
    val focus = stops.firstOrNull { it.id == focusId } ?: stops.lastOrNull()
    val noRest = stops.isEmpty() && suggestions.isEmpty()

    val leftTop: @Composable ColumnScope.() -> Unit = {
        BriefHeader(vm, place, stops)
        Box(Modifier.fillMaxWidth().height(192.dp).padding(start = 32.dp, top = 52.dp)) {
            BriefRouteLine(vm, place, stops, onFocus)
        }
    }
    val rightList: @Composable ColumnScope.() -> Unit = {
        if (stops.isNotEmpty()) {
            DestCard(vm, place, Modifier.height(479.dp))
            Spacer(Modifier.height(24.dp))
        }
        // 넣은 곳도 목록에 「넣었어요」로 남겨 다시 누르면 뺄 수 있게 한다(D-3).
        val rest = (stops + suggestions).distinctBy { it.id }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (stops.isEmpty()) "가는 길에 들를 만한 곳을 알아봤어요" else "가는 길에 들를 만한 곳",
                style = BeffType.Box40.copy(color = InkTitle), modifier = Modifier.weight(1f), maxLines = 1,
            )
            if (stops.isNotEmpty()) Text("${stops.size}/$MAX_STOPS", style = BeffType.Body32.copy(color = Beff.TextSecondary))
        }
        Spacer(Modifier.height(24.dp))
        if (rest.isEmpty()) {
            Text("가는 길 근처에 들를 만한 곳을 찾지 못했어요", style = BeffType.Body32.copy(color = Beff.TextSecondary))
        }
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            rest.forEach { p ->
                RestCard(vm, p, on = stops.any { it.id == p.id }, full = stops.size >= MAX_STOPS, onToggle = { onToggle(p) }, onOpen = { onOpenRest(p) })
            }
        }
    }

    if (LocalWide.current) {
        Box(Modifier.fillMaxSize()) {
            BriefWideFrame(
                panel = !noRest,
                left = {
                    // 1113: 2/3에도 「< 출발 브리핑」 뒤로 가기(Figma D-1).
                    Spacer(Modifier.height(40.dp))
                    TitleBar("출발 브리핑", onBack)
                    Column(Modifier.fillMaxWidth().weight(1f).padding(start = 56.dp, end = 82.dp, top = 12.dp, bottom = 45.dp)) {
                        leftTop()
                        if (focus != null) StopCard(vm, focus, onRemove = { onToggle(focus) }, onOpen = { onOpenRest(focus) }, modifier = Modifier.weight(1f))
                        else DestCard(vm, place, Modifier.weight(1f))
                        Spacer(Modifier.height(32.dp))
                        BigButton("안내 시작", { asking = true })
                        Spacer(Modifier.height(12.dp))
                        WhiteBigButton("하차 브리핑 미리 보기", previewUnload)
                    }
                },
                right = {
                    if (noRest) NoRestBlock(vm, place, Modifier.fillMaxSize().padding(horizontal = 58.dp))
                    else Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 58.dp, end = 58.dp, top = 74.dp, bottom = 48.dp),
                        content = rightList,
                    )
                },
            )
            if (asking) StartConfirmPopup(place, stops, onStart = { start() }, onCancel = { asking = false })
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        ScreenFrame(
            top = { Box(Modifier.padding(top = 40.dp)) { TitleBar("출발 브리핑", onBack) } },
            bottom = {
                BottomBar {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) { BigButton("안내 시작", { asking = true }, height = 100.dp) }
                        Box(Modifier.weight(1f)) { WhiteBigButton("하차 미리 보기", previewUnload) }
                    }
                }
            },
        ) { mod ->
            Column(mod.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 56.dp).padding(top = 24.dp, bottom = 24.dp)) {
                leftTop()
                if (focus != null) StopCard(vm, focus, onRemove = { onToggle(focus) }, onOpen = { onOpenRest(focus) }, modifier = Modifier.height(479.dp))
                else DestCard(vm, place, Modifier.height(479.dp))
                Spacer(Modifier.height(40.dp))
                if (noRest) NoRestBlock(vm, place, Modifier.fillMaxWidth()) else rightList()
            }
        }
        if (asking) StartConfirmPopup(place, stops, onStart = { start() }, onCancel = { asking = false })
    }
}

/**
 * 제목 두 줄: 「(프로필) {목적지}으로 출발할게요」(이름 56 ExtraBold + 접미 40 SemiBold) / 「목적지까지 52분 소요돼요」(40 SemiBold + 59 ExtraBold 틸).
 * 경유지를 넣으면 「경유지를 추가했어요」 / 「첫 경유지까지 …」. 시간은 내비가 알려 준 값만, 없으면 직선 거리라고 밝힌다.
 */
@Composable
private fun BriefHeader(vm: AppViewModel, place: Place, stops: List<Place>) {
    // 4-2: 프로필 썸네일 뺌. 4-18b · 6-7: 제목 한 가지(48 ExtraBold #131417), 아랫줄 32 Regular 회색.
    Text(
        if (stops.isNotEmpty()) "경유지를 추가했어요" else "${place.name}${withRo(place.name)} 출발할게요",
        style = BeffType.Title48.copy(color = Beff.TextStrong),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
    val first = stops.firstOrNull()
    val target = first?.takeIf { vm.nav.trips[it.id] != null } ?: place
    val label = if (target === place) (if (stops.isEmpty()) "목적지까지 " else "최종 목적지까지 ") else "첫 경유지까지 "
    val trip = vm.nav.trips[target.id]
    Text(
        when {
            trip != null -> "$label${fmtMinutes((trip.seconds + 59) / 60)} 소요돼요"
            place.lat != null && place.lng != null -> label + String.format("직선 %.1fkm", distanceKm(vm.origin, GeoPoint(place.lat, place.lng)))
            else -> "내비가 경로를 계산하고 있어요"
        },
        style = BeffType.Body32.copy(color = Beff.TextSecondary),
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

/**
 * 선형 경로(664×140): 출발 · 도착 원 28(틸 테두리 6), 남은 길 #AEB8BF 4, 내 차 64(출발 전이라 출발점 옆).
 * 경유지는 점선 원 50(#D6ECEB · 아이콘 36) + 이름 28 SemiBold 틸. 이름을 누르면 왼쪽 카드가 그 경유지로 바뀐다.
 * 위치는 출발지 → 경유지 → 목적지 직선 거리 비율(근사).
 */
@Composable
private fun BriefRouteLine(vm: AppViewModel, place: Place, stops: List<Place>, onFocus: (String) -> Unit) {
    val ats = stopPositions(vm, place, stops)
    // 4-6: 막대 위 오른쪽에 「목적지까지 n km」(내비 남은 거리, 없으면 직선).
    val km = vm.nav.trips[place.id]?.meters?.let { it / 1000.0 }
        ?: if (place.lat != null && place.lng != null) distanceKm(vm.origin, GeoPoint(place.lat, place.lng)) else null
    Box(Modifier.width(664.dp).height(140.dp)) {
        if (km != null) Text(
            "목적지까지 ${String.format(if (km >= 10) "%.0fkm" else "%.1fkm", km)}",
            style = BeffType.Caption28.copy(fontWeight = FontWeight.SemiBold, color = Beff.TealText, textAlign = TextAlign.End),
            modifier = Modifier.offset(264.dp, (-34).dp).width(400.dp), maxLines = 1,
        )
        Box(Modifier.offset(14.dp, 38.dp).size(636.dp, 4.dp).background(Beff.Muted2))
        Box(Modifier.offset(14.dp, 37.dp).size(58.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(Beff.Teal))
        RouteDot(Modifier.offset(0.dp, 26.dp))
        RouteDot(Modifier.offset(636.dp, 26.dp))
        stops.forEachIndexed { i, s ->
            val cx = 14 + 636 * ats[i]
            Box(
                Modifier.offset((cx - 25).dp, 15.dp).size(50.dp).clip(CircleShape).background(Beff.TealTint).dashedRing(Beff.Teal),
                contentAlignment = Alignment.Center,
            ) { BeffAsset("pict/rest.svg", 36.dp, tint = Beff.TealText) }
            Text(
                s.name, style = BeffType.Caption28.copy(fontWeight = FontWeight.SemiBold, color = TealInk, textAlign = TextAlign.Center),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset((cx - 110).dp, 77.dp).width(220.dp).clickable { onFocus(s.id) },
            )
        }
        Box(
            Modifier.offset(40.dp, 8.dp)
                .shadow(6.dp, CircleShape, ambientColor = Color(0x40004D4D), spotColor = Color(0x40004D4D))
                .size(64.dp).clip(CircleShape).background(Beff.Teal).border(4.dp, Beff.BoxBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) { BeffAsset("pict/car.svg", 48.dp, tint = Color.White) }
        Text(
            "도착", style = BeffType.Caption28.copy(fontWeight = FontWeight.SemiBold, color = TealInk, textAlign = TextAlign.Center),
            modifier = Modifier.offset(600.dp, 77.dp).width(100.dp),
        )
    }
}

/** 경유지의 경로 위 위치(0.3~0.8). 두 곳이 겹치면 0.38 · 0.68로 벌린다. */
private fun stopPositions(vm: AppViewModel, place: Place, stops: List<Place>): List<Float> {
    val raw = stops.map { s ->
        if (s.lat == null || s.lng == null || place.lat == null || place.lng == null) 0.5f
        else {
            val p = GeoPoint(s.lat, s.lng)
            val a = distanceKm(vm.origin, p); val b = distanceKm(p, GeoPoint(place.lat, place.lng))
            if (a + b <= 0.0) 0.5f else (a / (a + b)).toFloat().coerceIn(0.3f, 0.8f)
        }
    }
    if (raw.size == 2 && kotlin.math.abs(raw[0] - raw[1]) < 0.25f) return if (raw[0] <= raw[1]) listOf(0.38f, 0.68f) else listOf(0.68f, 0.38f)
    return raw
}

@Composable
private fun RouteDot(modifier: Modifier) {
    Box(modifier.size(28.dp).clip(CircleShape).background(Beff.Card).border(6.dp, Beff.Teal, CircleShape))
}

private fun Modifier.dashedRing(color: Color): Modifier = this.drawBehind {
    drawCircle(
        color = color,
        radius = size.minDimension / 2 - 1.4.dp.toPx(),
        style = Stroke(width = 2.7.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))),
    )
}

/** 목적지 카드(728×479 · 흰 · 테두리 #E0EAE6 · r32): 핀 + 이름 54 ExtraBold, 맞는 시설 한 줄 28 SemiBold, 사진 r22. */
@Composable
private fun DestCard(vm: AppViewModel, place: Place, modifier: Modifier = Modifier) {
    val facilities = vm.places.facilitiesFor(place, vm.active).ifEmpty { place.facilities }.take(3)
    BriefPlaceCard(
        icon = { BeffAsset("illust/pin.svg", 58.dp) },
        name = place.name,
        subIcon = null,
        sub = if (facilities.isEmpty()) listOf(place.kindLabel, place.regionLabel).filter { it.isNotBlank() }.joinToString(" · ") else "",
        chips = facilities,
        modifier = modifier,
    ) { PlaceImage(place, Modifier.fillMaxSize(), illustSize = 160.dp) }
}

/** D-3 왼쪽: 고른 경유지 카드(휴게소 그림 + 이름 + 시설 + 사진). 오른쪽 위 X로 뺀다. 카드를 누르면 쉬어갈 곳 상세(D-2). */
@Composable
private fun StopCard(vm: AppViewModel, stop: Place, onRemove: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val facilities = if (stop.note == null) vm.places.facilitiesFor(stop, vm.active).ifEmpty { stop.facilities }.take(3) else emptyList()
    BriefPlaceCard(
        icon = { BeffAsset(stop.note?.let { ROUTE_ILLUST[it] } ?: "illust/rest_area.svg", 56.dp) },
        name = stop.name,
        subIcon = null,
        sub = if (facilities.isEmpty()) listOfNotNull(stop.note, tripText(vm, stop)).joinToString(" · ") else "",
        chips = facilities,
        modifier = modifier.clickable(onClick = onOpen),
        trailing = {
            Box(Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                BeffAsset("pict/close.svg", 40.dp)
            }
        },
    ) { PlaceImage(stop, Modifier.fillMaxSize(), illustSize = 160.dp) }
}

@Composable
private fun BriefPlaceCard(
    icon: @Composable () -> Unit,
    name: String,
    subIcon: String?,
    sub: String,
    modifier: Modifier,
    trailing: @Composable () -> Unit = {},
    chips: List<String> = emptyList(),
    photo: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Beff.Card).border(2.dp, Beff.Stroke, shape)
            .padding(start = 32.dp, end = 32.dp, top = 48.dp, bottom = 32.dp),
    ) {
        Row(Modifier.height(66.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(66.dp), contentAlignment = Alignment.Center) { icon() }
            Text(
                name, style = BeffType.Box40.copy(color = InkTitle), // 6-7: 카드 제목 40 Bold
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            trailing()
        }
        // 4-5 · 6-8: 시설은 C-6과 같은 칩(옅은 틸 · r12 · 28 Regular).
        Row(Modifier.padding(top = 8.dp, start = 10.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subIcon?.let { BeffAsset(it, 38.dp) }
            chips.forEach { FacilityChip(it) }
            if (sub.isNotBlank()) Text(sub, style = BeffType.Caption28.copy(color = InkSub), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(30.dp))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(22.dp))) { photo() }
    }
}

/**
 * 「가는 길에 쉬어갈 곳」 카드(6-13): 바탕 Surface(#F1F6F6) · r28 · 안쪽 20/24. 사진 168 왼쪽 · 라벨 28 틸 · 이름 36 SemiBold · 거리 28 · 칩(흰 바탕),
 * 오른쪽 [경유지로 추가] 주 버튼(사진 위 버튼은 없앰). 카드를 누르면 쉬어갈 곳 상세(D-2).
 */
@Composable
private fun RestCard(vm: AppViewModel, p: Place, on: Boolean, full: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val chips = if (p.note == null) vm.places.facilitiesFor(p, vm.active).ifEmpty { p.facilities }.take(2) else listOf("시설 정보 없음")
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Beff.Surface).clickable(onClick = onOpen)
            .padding(start = 20.dp, end = 24.dp, top = 20.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Box(Modifier.size(168.dp).clip(RoundedCornerShape(20.dp))) { PlaceImage(p, Modifier.fillMaxSize(), illustSize = 96.dp) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 무장애 데이터의 곳(= 함께 타는 분이 내릴 수 있는 곳)과 프로필 칩(카페 · 휴게소 …)으로 찾은 곳을 구분한다.
            Text(if (on) "경유지로 넣었어요" else if (p.note == null) "들를 만한 곳" else "가는 길 ${p.note}", style = BeffType.Caption28.copy(fontWeight = FontWeight.SemiBold, color = Beff.ChipText), maxLines = 1)
            Text(p.name, style = BeffType.Item36Semi.copy(color = Beff.TextStrong), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(tripText(vm, p), p.regionLabel.ifBlank { null }).joinToString(" · "),
                style = BeffType.Caption28.copy(color = Beff.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips.take(1).forEach { RestChip(it) } }
        }
        // 1113: [경유지로 추가] 버튼 뺌 — 카드를 누르면 상세(D-2)에서 정보를 보고 경유지로 넣는다.
        BeffAsset("pict/chevron_right.svg", 48.dp, tint = Beff.TextSecondary)
    }
}

/** 카드 위 시설 칩(6-13): 흰(다크는 카드색) 바탕 · r12 · 28 Regular 틸. */
@Composable
private fun RestChip(text: String) {
    Box(
        Modifier.height(46.dp).clip(RoundedCornerShape(12.dp)).background(Beff.Card).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BeffType.Caption28.copy(color = Beff.ChipText), maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** 시설 칩(6-8): #EDF6F1 · r12 · 28 Regular #00706C, 테두리 없음. */
@Composable
internal fun FacilityChip(text: String) {
    Box(
        Modifier.height(46.dp).clip(RoundedCornerShape(12.dp)).background(Beff.ChipBg).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BeffType.Caption28.copy(color = Beff.ChipText), maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/**
 * D-4 가는 길에 쉼터가 없을 때: 벤치 그림 223 · 「가는 길엔 정보가 확인된 쉼터가 없어요」 48 ExtraBold.
 * 대신 목적지에서 쓸 수 있는 시설을 보여 준다(설명 카드 「목적지 주변 시설 안내로 대체」).
 */
@Composable
private fun NoRestBlock(vm: AppViewModel, place: Place, modifier: Modifier) {
    val facilities = vm.places.facilitiesFor(place, vm.active).ifEmpty { place.facilities }.take(4)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // 6-15: F-4와 같은 표정 그래픽(face_neutral 회색), 문구 40 Bold.
        BeffAsset("face/face_neutral.svg", 160.dp, tint = Beff.Muted2)
        Spacer(Modifier.height(24.dp))
        Text("가는 길엔 정보가\n확인된 쉼터가 없어요", style = BeffType.Box40.copy(color = Beff.TextStrong, textAlign = TextAlign.Center))
        Spacer(Modifier.height(12.dp))
        Text(
            if (facilities.isEmpty()) "바로 출발해도 돼요" else "${place.name}에서 쓸 수 있는 시설을 알려 드릴게요",
            style = BeffType.Body32.copy(color = Beff.TextSecondary, textAlign = TextAlign.Center),
        )
        if (facilities.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                facilities.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { FacilityTile(it, Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * D-7 「○○으로 안내를 시작할까요?」(720 · r40 · 자동차 그림 200 · 40 ExtraBold · 30 Regular · [안내 시작][취소] 312×100).
 * 창이 뜨면 [START_AUTO_MS]ms 뒤 자동으로 안내를 시작한다(1110 요청). [취소]를 누르면 멈춘다.
 */
/** D-7 안내 시작 팝업이 뜬 뒤 저절로 안내를 시작하기까지(ms). */
private const val START_AUTO_MS = 5_000L

@Composable
private fun StartConfirmPopup(place: Place, stops: List<Place>, onStart: () -> Unit, onCancel: () -> Unit) {
    // 4-7: 5초 자동 시작 · 카운트다운 없앰. [안내 시작] · [취소]만.
    // 1113: 카운트다운 표시를 없애면서 자동 시작까지 빠져 있었다(플로우 D-7 「5초 자동」). [취소]로 팝업이 사라지면 이 효과도 같이 멈춘다.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(START_AUTO_MS)
        onStart()
    }
    Box(
        Modifier.fillMaxSize().fadeInOnce().background(Color(0x8C000000)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(40.dp)
        Column(
            Modifier.popIn().width(720.dp).clip(shape).background(Beff.Popup).border(1.5.dp, Beff.PopupBorder, shape)
                .padding(start = 40.dp, end = 40.dp, top = 40.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BeffAsset("illust/car.svg", 200.dp)
            Spacer(Modifier.height(33.dp))
            Text(
                "${place.name}${withRo(place.name)} 안내를 시작할까요?",
                style = BeffType.Box40.copy(color = Beff.TextStrong, textAlign = TextAlign.Center),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                (if (stops.isEmpty()) "" else "${stops.joinToString(" · ") { it.name }}에 들렀다 가요\n") + "시작하면 기본 내비게이션으로 바뀌어요",
                style = BeffType.Body32.copy(fontSize = 30.sp, color = Beff.TextSecondary, textAlign = TextAlign.Center, lineHeight = 41.sp),
            )
            Spacer(Modifier.height(45.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val btn = RoundedCornerShape(32.dp)
                Box(Modifier.weight(1f).height(100.dp).clip(btn).background(Beff.Teal).clickable(onClick = onStart), contentAlignment = Alignment.Center) {
                    Text("안내 시작", style = BeffType.Small30.copy(color = Color.White), maxLines = 1)
                }
                Box(Modifier.weight(1f)) { com.beff.app.ui.BeffButton("취소", onCancel, kind = com.beff.app.ui.ButtonKind.Secondary, containerColor = Beff.PopupSecondary) }
            }
        }
    }
}

