package com.beff.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.beff.app.Route
import com.beff.app.Tab
import com.beff.app.data.GeoPoint
import com.beff.app.data.ParkingSpot
import com.beff.app.data.distanceKm
import com.beff.app.ui.ArrivalColor
import com.beff.app.ui.ArrivalPopup
import com.beff.app.ui.ArrivalSecondaryButton
import com.beff.app.ui.ArrivalStepsCard
import com.beff.app.ui.ArrivalTitle
import com.beff.app.ui.ArrivalToast
import com.beff.app.ui.ArrivalWideFrame
import com.beff.app.ui.BeffGnb
import com.beff.app.ui.TitleBar
import com.beff.app.ui.TtsToast
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.beff.app.AppViewModel
import com.beff.app.data.AltSpot
import com.beff.app.data.Mobility
import com.beff.app.data.Place
import com.beff.app.data.PlaceKind
import com.beff.app.data.Profile
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffButton
import androidx.compose.foundation.layout.width
import com.beff.app.ui.BeffType
import com.beff.app.ui.BottomBar
import com.beff.app.ui.ButtonKind
import com.beff.app.ui.HeaderPill
import com.beff.app.ui.HeadlineText
import com.beff.app.ui.LocalWide
import com.beff.app.ui.ScreenFrame
import com.beff.app.ui.StepLine
import com.beff.app.ui.StepsBox
import com.beff.app.ui.StepText
import com.beff.app.ui.SummaryBox
import com.beff.app.ui.UnloadCloseBar
import com.beff.app.ui.CloseBar
import com.beff.app.ui.UnloadColor
import com.beff.app.ui.UnloadMapBoard
import com.beff.app.ui.WIDE_SIDE
import com.beff.app.ui.WIDE_TOP
import com.beff.app.ui.beffBackground
import androidx.compose.ui.unit.sp

/** 2/3 전체 화면 틀: 배경 + 왼쪽 위 X 바 + 2열(왼쪽 = 운전석 쪽) + 하단 바. 1113: X는 왼쪽 위(닫기 · 뒤로 통일). */
@Composable
private fun WideUnloadFrame(
    onClose: () -> Unit,
    bottom: @Composable () -> Unit,
    tint: Color = Color(0xFF008F8C),
    left: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    right: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().beffBackground(tint, base = Beff.BgCool)) {
        Column(Modifier.fillMaxSize()) {
            UnloadCloseBar(onClose)
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(start = WIDE_SIDE, end = WIDE_SIDE, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxSize(), content = left)
                Column(Modifier.weight(1f).fillMaxSize(), content = right)
            }
            bottom()
        }
    }
}

/** 프로필에 따른 보조수단 이름. 예시 문구(유모차)가 기본이다. */
private fun mobilityOf(profile: Profile?): Mobility = when {
    profile == null -> Mobility.Stroller
    (profile.counts[Mobility.Wheelchair] ?: 0) > 0 -> Mobility.Wheelchair
    (profile.counts[Mobility.Stroller] ?: 0) > 0 -> Mobility.Stroller
    else -> Mobility.Walker
}

private fun mobilityNoun(m: Mobility) = when (m) {
    Mobility.Stroller -> "유모차"
    Mobility.Wheelchair -> "휠체어"
    Mobility.Walker -> "보행 보조기"
}

/** 06-2 하차 동선 줄: 1번 줄 이름만 프로필에 맞춘다. */
private fun stepLines(vm: AppViewModel, place: Place): List<StepLine> =
    vm.places.unloadPlan(place, vm.active).steps.mapIndexed { i, st ->
        StepLine(i + 1, st.illust, st.label, st.sub)
    }

/**
 * 1110 하차 화면 한 벌(Route.Unload).
 * - D-5 하차 브리핑 미리보기([preview], 출발 브리핑 · 여정에서): 「< 하차 브리핑」 + 「도착하면 이렇게 내려요」 + 하차 동선, 오른쪽 지도 · 시설 · [근처 주차장].
 * - G-2 목적지 도착: 「○○에 도착했어요」 + 하차 동선 + GNB(여정), 오른쪽 지도 · 시설 · [주차가 어려워요](→ D-6) [내리기]. 문 열림 음성(G-4).
 * - G-1 경유지 도착(여정의 경유지): 「경유지에 도착했어요」 + [다시 출발].
 * 하차 동선은 한국관광공사 무장애 여행정보 원문(access)에서 만든다. 원문에 없는 방향 · 거리는 만들지 않는다.
 */
@Composable
fun UnloadScreen(vm: AppViewModel, placeId: String, preview: Boolean = false) {
    val place = vm.places.placeOrNull(placeId) ?: vm.journey?.dest ?: return
    val wide = LocalWide.current
    val lines = stepLines(vm, place)
    val isStop = !preview && vm.journey?.let { j -> j.dest.id != place.id && j.stops.any { it.id == place.id } } == true
    val back = { vm.back(); Unit }
    val finish = { vm.finishUnload() }
    val nearby = { vm.openAlternatives(place.id) }
    val hardToPark = { vm.openParkingHelp(place.id) }
    val toast = ArrivalToast.text
    // G-4: 도착 화면에서 승객 쪽 문이 열리면 시설 안내를 말한다(Vehicle SDK 문 상태, 읽기만).
    if (!preview) DoorOpenGuide(vm, place)
    val title: @Composable (Modifier) -> Unit = { m ->
        when {
            preview -> Column(m) {
                Text("도착하면 이렇게 내려요", style = BeffType.Title48.copy(color = Beff.TextStrong, lineHeight = 65.sp, letterSpacing = 0.sp), maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${place.name} · ${mobilityNoun(mobilityOf(vm.active))} 기준",
                    style = BeffType.Body32.copy(fontSize = 30.sp, lineHeight = 41.sp, letterSpacing = 0.sp, color = Beff.TextSecondary), maxLines = 1,
                )
            }
            isStop -> ArrivalTitle("경유지", "에", "도착했어요", m) // 1113: 「○○에서 쉬어 가세요」 → 「경유지에 도착했어요」(사용자 요청)
            else -> ArrivalTitle(place.name, "에", "도착했어요", m)
        }
    }
    val buttons: @Composable () -> Unit = {
        when {
            preview -> ArrivalSecondaryButton("근처 주차장", nearby)
            isStop -> BeffButton("다시 출발", back)
            // 4-20: [내리기](주 · 여정 종료) + [근처 주차장] 가로 2개. 주차가 어려울 때(D-6)는 제보 분기(openUnload)로 들어간다.
            else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BeffButton("내리기", finish, Modifier.weight(1f))
                ArrivalSecondaryButton("근처 주차장", nearby, Modifier.weight(1f))
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unusedHard = hardToPark
    val mapSection: @Composable (Dp) -> Unit = { h ->
        if (place.undergroundParking) UndergroundMapCard(place) else FacilityMapCard(vm, place, mapHeight = h, boxed = true)
    }
    // 4-20: 도착 화면(G) · 하차 미리보기(D-5)는 GNB를 두지 않는다. 4-18: 바탕 #F1F6F6.
    Box(Modifier.fillMaxSize()) {
        if (wide) {
            // 4-23: 왼쪽(운전석 쪽) 약도 먼저, 오른쪽 하차 동선.
            ArrivalWideFrame(
                tab = null,
                onTab = { vm.selectTab(it) },
                // 1113: 닫기 · 뒤로는 왼쪽 위로 통일 — 미리보기는 「< 하차 브리핑」, 도착(G-1 · G-2)은 ✕.
                titleBar = if (preview) ({ TitleBar("하차 브리핑", back) }) else ({ CloseBar(back) }),
                overlay = {},
                left = {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        title(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(if (preview) 32.dp else 36.dp))
                        mapSection(420.dp)
                        Spacer(Modifier.height(24.dp))
                    }
                    Spacer(Modifier.height(24.dp))
                    buttons()
                },
                right = {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                        // 1113: 「시설 안내 (원문)」 뺌 — 하차 동선 박스만.
                        ArrivalStepsCard("하차 동선", lines)
                        Spacer(Modifier.height(16.dp))
                    }
                },
            )
            // 6-15: 와이드 음성 토스트는 화면(1700) 가운데 위.
            if (toast != null) TtsToast(toast, Modifier.align(Alignment.TopCenter).padding(top = 40.dp))
        } else {
            Column(Modifier.fillMaxSize().beffBackground(base = Beff.BgCool)) {
                Spacer(Modifier.height(40.dp))
                if (preview) TitleBar("하차 브리핑", back) else CloseBar(back)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 56.dp)
                        .padding(top = 32.dp, bottom = 32.dp),
                ) {
                    title(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(32.dp))
                    // 4-23: 약도(시설 강조 + 시설 칸) 먼저 → 하차 동선.
                    mapSection(400.dp)
                    Spacer(Modifier.height(32.dp))
                    ArrivalStepsCard("하차 동선", lines, rowHeight = 136.dp, illust = 96.dp)
                }
                Box(Modifier.padding(horizontal = 56.dp).padding(top = 16.dp, bottom = 40.dp)) { buttons() }
            }
            if (toast != null) TtsToast(toast, Modifier.align(Alignment.TopCenter).padding(top = 40.dp, start = 56.dp, end = 56.dp))
        }
        // 1115: G-3 시트 팝업(옮겨 타기 시트)은 뺐다 — 공개 SDK에 시트 제어 · 승객 좌석 API가 없다.
    }
}

/**
 * G-4 문 열림 안내: 승객 쪽 문이 열리면(Vehicle SDK 문 위치, 읽기만) 「문이 열렸어요」 + 함께 탄 분에게 필요한 원문 한 줄을 말하고 토스트로 보인다.
 * 원문(access)이 없으면 하차 동선 안내만 말한다.
 */
@Composable
private fun DoorOpenGuide(vm: AppViewModel, place: Place) {
    val count = vm.vehicle.doorOpenCount
    val start = remember(place.id) { count }
    androidx.compose.runtime.LaunchedEffect(count) {
        if (count == start) return@LaunchedEffect
        val text = doorText(place, vm.active)
        vm.voice.say(text)
        ArrivalToast.show(text, 5000)
    }
}

private fun doorText(place: Place, profile: Profile?): String {
    val c = profile?.counts.orEmpty()
    val keys = buildList {
        if ((c[Mobility.Wheelchair] ?: 0) > 0) addAll(listOf("wheelchair" to "휠체어", "restroom" to "화장실"))
        if ((c[Mobility.Stroller] ?: 0) > 0) addAll(listOf("stroller" to "유모차", "lactationroom" to "수유실"))
        add("restroom" to "화장실")
    }
    val line = keys.firstNotNullOfOrNull { (k, label) ->
        place.access[k]?.trim()?.takeIf { it.isNotBlank() }?.let { raw ->
            val first = raw.split('.', '\n').firstOrNull { it.isNotBlank() }?.trim() ?: raw
            "$label: " + if (first.length > 30) first.take(29) + "…" else first
        }
    }
    return "문이 열렸어요.\n" + (line ?: "화면의 하차 동선을 따라 천천히 내리세요")
}

/**
 * 지하 주차장 하차 약도(06-3 평면 약도). 지하는 지상 지도(카카오맵) · OSM 보행로로 동선을 그릴 수 없어 Figma 예시 도식을 쓴다.
 * 층 · 엘리베이터 위치 · 거리는 실내 지도 데이터가 없어 전부 [가정] 예시다. 원문의 주차 문장은 위에 그대로 보여 준다.
 */
@Composable
private fun UndergroundMapCard(place: Place) {
    com.beff.app.ui.BeffBox(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("주차장에서 입구까지 (예시)", style = BeffType.Box40.copy(color = Beff.TextPrimary))
            place.access["parking"]?.let { Text(it, style = BeffType.Body32.copy(color = Beff.TextStrong)) }
            BoxWithConstraints(Modifier.fillMaxWidth()) { UnloadMapBoard(maxWidth.value / 778f) }
            Text(
                "지하 주차장은 지상 지도로 동선을 그릴 수 없어 예시 도식으로 보여 드려요. 실제 층 · 엘리베이터 위치와 거리는 다를 수 있어요 [가정].",
                style = BeffType.Caption28.copy(color = Beff.TextSecondary),
            )
        }
    }
}

/** 06-3 하차 약도(840). 2/3에서는 06-2 왼쪽 열에 통합되어 별도 화면이 없으므로 06-2를 그대로 보여 준다. */
@Composable
fun UnloadMapScreen(vm: AppViewModel, placeId: String) {
    if (LocalWide.current) { UnloadScreen(vm, placeId); return }
    ScreenFrame(
        top = { UnloadCloseBar { vm.back() } },
        // [미확인] 폰 전송 수단 미정: 동작 없음.
        bottom = { BottomBar { BeffButton("폰으로 보내기", {}) } },
    ) { mod ->
        BoxWithConstraints(mod.fillMaxWidth().padding(horizontal = 56.dp).padding(top = 24.dp)) {
            UnloadMapBoard(maxWidth.value / 778f)
        }
    }
}

private const val ALT_META = "도착지 반경 2km · 가까운 순 · 출처 전국주차장정보표준데이터"

/**
 * Route.Alternatives.
 * - manual(도착 화면 [근처 주차장]) → 1110 G-5 근처 주차장.
 * - byUser(도착 화면 G-2 [주차가 어려워요]) → D-6과 같은 화면, 제목만 「근처 주차장으로 안내할게요」.
 * - 제보 분기(정책 07-1) → 1110 D-6 「원활한 하차가 어려워요」: 대체 하차지 + 선택한 곳의 하차 동선.
 * 후보는 전국주차장정보표준데이터에서 장애인 전용주차구역 보유로 확인된 근처 주차장뿐이다(없으면 비어 있다).
 */
@Composable
fun AlternativesScreen(vm: AppViewModel, placeId: String, manual: Boolean = false, byUser: Boolean = false) {
    val place = vm.places.placeOrNull(placeId) ?: vm.journey?.dest ?: return
    if (manual) { NearbyParkingScreen(vm, place); return }
    // 1113 효과음: 제보로 들어온 「원활한 하차가 어려워요」만 경고음(운전자가 직접 고른 경우는 탭 소리만).
    if (!byUser) androidx.compose.runtime.LaunchedEffect(placeId) { com.beff.app.device.Sfx.warning() }
    val wide = LocalWide.current
    val spots = vm.places.altSpots(place)
    var sel by remember { mutableIntStateOf(0) }
    val spot = spots.getOrNull(sel.coerceIn(0, (spots.size - 1).coerceAtLeast(0)))
    val guide = { spot?.let { guide(vm, it) }; Unit }
    val finish = { vm.finishUnload() }
    val close = { vm.back(); Unit }
    val list: @Composable (Modifier) -> Unit = { m ->
        SpotsBox(spots, sel, wide, m) { sel = it }
    }
    val titleBlock: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth()) {
            if (byUser) {
                // 도착 화면 [주차가 어려워요]로 들어온 경우: 제보가 아니라 운전자가 직접 고른 것이므로 제보 문구를 쓰지 않는다.
                Text("근처 주차장으로 안내할게요", style = BeffType.Title56.copy(color = Beff.TextStrong, lineHeight = 74.sp), maxLines = 1)
                Text("장애인 전용주차구역이 있는 주차장 · 도착지에서 가까운 순", style = BeffType.Body32.copy(color = Beff.TextSecondary, lineHeight = 42.sp), maxLines = 1)
            } else {
                Text("원활한 하차가 어려워요", style = BeffType.Title56.copy(color = Beff.TextStrong, lineHeight = 74.sp), maxLines = 1)
                // 제보 서버가 없어 제보 내용은 받지 못한다 [미확인]. 07-1 분기가 켜졌다는 사실만 적는다.
                Text("입구 · 엘리베이터를 쓰기 어렵다는 제보가 있어요 · 사용자 제보 · 오늘", style = BeffType.Body32.copy(color = Beff.TextSecondary, lineHeight = 42.sp), maxLines = 1)
            }
        }
    }
    if (wide) {
        WideUnloadFrame(
            onClose = close,
            tint = UnloadColor.Warn,
            bottom = {
                BottomBar {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Box(Modifier.weight(1f)) { BeffButton("대체 구역으로 안내", guide, containerColor = UnloadColor.Warn) }
                        Box(Modifier.weight(1f)) { BeffButton("여기서 내리기", finish, kind = ButtonKind.Ghost) }
                    }
                }
            },
            left = {
                titleBlock()
                Spacer(Modifier.height(24.dp))
                list(Modifier.weight(1f))
            },
            right = {
                Spacer(Modifier.height(140.dp))
                if (spot != null) StepsBox(
                    spot.name,
                    spot.steps.mapIndexed { i, st -> StepLine(i + 1, st.illust, st.label, st.sub) },
                    Modifier.weight(1f).fillMaxWidth(), illust = 120.dp, rowHeight = 205.dp, gap = 16.dp,
                )
            },
        )
        return
    }
    ScreenFrame(
        top = { UnloadCloseBar(close) },
        tint = UnloadColor.Warn,
        bottom = {
            BottomBar {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(1f)) { BeffButton("대체 구역으로 안내", guide, containerColor = UnloadColor.Warn) }
                    Box(Modifier.weight(1f)) { BeffButton("여기서 내리기", finish, kind = ButtonKind.Ghost) }
                }
            }
        },
    ) { mod ->
        Column(
            mod.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 56.dp).padding(top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            titleBlock()
            list(Modifier)
        }
    }
}

/** 「대체 구역으로 안내」: 그 지점을 새 목적지로 여정을 다시 시작한다(NaviHelper 호출은 startJourney 안, 바꾸지 않음). */
private fun guide(vm: AppViewModel, spot: AltSpot) {
    vm.startJourney(
        Place(
            id = spot.id, name = spot.name, kind = PlaceKind.Attraction, facilities = listOf("장애인 주차"),
            lat = spot.lat, lng = spot.lng, address = spot.address, region = spot.region, kindLabel = "주차장",
        ),
    )
}

private fun meters(m: Int): String = if (m < 1000) "${m}m" else String.format("%.1fkm", m / 1000.0)

@Composable
private fun SpotsBox(spots: List<AltSpot>, sel: Int, wide: Boolean, modifier: Modifier, onSelect: (Int) -> Unit) {
    // 박스 제목 「대체 하차지」 40 Bold, 줄 간격 16(2/3도 같음)
    com.beff.app.ui.BeffBox(modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(Modifier.padding(horizontal = 34.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("대체 하차지", style = BeffType.Box40.copy(color = Beff.TextPrimary), maxLines = 1)
            if (spots.isEmpty()) {
                Text("공공데이터에 장애인 전용주차구역이 있다고 적힌 주차장이 반경 2km 안에 없어요", style = BeffType.Body32.copy(color = Beff.TextSecondary))
            }
            spots.forEachIndexed { i, s ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(UnloadColor.Line))
                SpotRow(s, selected = i == sel, wide = wide) { onSelect(i) }
            }
            Text(ALT_META, style = BeffType.Caption28.copy(color = Beff.TextSecondary), maxLines = 2)
        }
    }
}

@Composable
private fun SpotRow(spot: AltSpot, selected: Boolean, wide: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val illust = if (wide) 120 else 96
    Box(
        Modifier.fillMaxWidth()
            .then(if (wide) Modifier.heightIn(min = if (selected) 209.dp else 203.dp) else Modifier)
            .clip(shape)
            .clickable(onClick = onClick)
            .then(if (selected) Modifier.background(UnloadColor.WarnBg).border(3.dp, UnloadColor.Warn, shape).padding(3.dp) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BeffAsset(spot.illust, illust.dp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(spot.name, style = BeffType.Body32.copy(fontSize = 36.sp, lineHeight = 48.sp, color = Beff.TextPrimary), maxLines = 1)
                Text("도착지에서 ${meters(spot.distanceM)}", style = BeffType.Body32.copy(lineHeight = 42.sp, color = Beff.TextSecondary), maxLines = 1)
                Text(spot.facilities, style = BeffType.Body32.copy(lineHeight = 42.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Beff.TextStrong), maxLines = 1)
            }
        }
    }
}

// ───────────────────────── 1110 G-5 근처 주차장 ─────────────────────────

/**
 * 1110 G-5 근처 주차장(도착 화면 [근처 주차장]). 전국주차장정보표준데이터에서 장애인 전용주차구역이 있다고 적힌 주차장을 도착지에서 가까운 순으로.
 * 줄을 누르면 오른쪽 지도에서 그 주차장이 커지고, [안내]는 그 주차장을 새 목적지로 내비 안내를 시작한다.
 * 장애인 면수는 원본에 없어 「장애인 주차구역 있음」만 적는다. 거리는 도착지에서 직선거리.
 */
@Composable
fun NearbyParkingScreen(vm: AppViewModel, place: Place) {
    val wide = LocalWide.current
    val spots = remember(place.id) { vm.places.parkingNear(place, radiusKm = 2.0, limit = 5) }
    var sel by remember(place.id) { mutableIntStateOf(0) }
    val back = { vm.back(); Unit }
    val head: @Composable () -> Unit = {
        Text("가까운 순", style = BeffType.Box40.copy(color = Beff.TextPrimary, lineHeight = 54.sp))
        Spacer(Modifier.height(4.dp))
        Text("전국주차장정보표준데이터 기준", style = BeffType.Body32.copy(fontSize = 30.sp, lineHeight = 41.sp, letterSpacing = 0.sp, color = Beff.TextSecondary), maxLines = 1)
        Spacer(Modifier.height(24.dp))
    }
    val list: @Composable () -> Unit = { ParkingList(spots, sel, { sel = it }) { guideTo(vm, it) } }
    if (wide) {
        // 1113: Figma G-5 — GNB 없음, 「가까운 순」 + 목록을 흰 카드 하나로.
        ArrivalWideFrame(
            tab = null,
            onTab = { vm.selectTab(it) },
            titleBar = { TitleBar("근처 주차장", back) },
            left = {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) { ParkingCard { head(); list() } }
            },
            right = { ParkingMap(place, spots, sel, Modifier.fillMaxSize()) },
        )
        return
    }
    Column(Modifier.fillMaxSize().beffBackground(base = Beff.BgCool)) {
        Spacer(Modifier.height(40.dp))
        TitleBar("근처 주차장", back)
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 56.dp).padding(top = 32.dp)) {
            ParkingCard { head(); list() }
            Spacer(Modifier.height(24.dp))
            ParkingMap(place, spots, sel, Modifier.fillMaxWidth().weight(1f).heightIn(min = 240.dp))
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ParkingCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    com.beff.app.ui.BeffBox(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start, stroke = Beff.Stroke) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp), content = content)
    }
}

/** 주차장 목록(Figma 「주차장 리스트」): #FCFDFD r28, 줄 126, 선택 줄 #EEF6F5, 아이콘 칸 72 · 이름 36 SemiBold · 거리 28 · [안내] 89×64. */
@Composable
private fun ParkingList(spots: List<ParkingSpot>, sel: Int, onSelect: (Int) -> Unit, onGuide: (ParkingSpot) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Beff.Card)) {
        if (spots.isEmpty()) {
            Text(
                "반경 2km 안에 장애인 전용주차구역이 있다고 적힌 주차장이 없어요",
                style = BeffType.Body32.copy(color = Beff.TextSecondary), modifier = Modifier.padding(32.dp),
            )
        }
        spots.forEachIndexed { i, s ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(UnloadColor.Line))
            Row(
                Modifier.fillMaxWidth().height(126.dp).clip(RoundedCornerShape(24.dp)).background(if (i == sel) UnloadColor.SummaryFill else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(72.dp, 74.dp).clip(RoundedCornerShape(36.dp)).background(Beff.Surface), contentAlignment = Alignment.Center) {
                    BeffAsset("pict/parking.svg", 48.dp)
                }
                Column(Modifier.weight(1f).padding(start = 16.dp, end = 16.dp)) {
                    Text(s.name, style = BeffType.Item36Semi.copy(lineHeight = 48.sp, color = Beff.TextPrimary), maxLines = 1)
                    Text(
                        "${meters((s.distanceKm * 1000).toInt())} · 장애인 주차구역 있음", // 4-22: 면 수 데이터 없음
                        style = BeffType.Caption28.copy(lineHeight = 37.sp, color = ArrivalColor.Sub), maxLines = 1,
                    )
                }
                // 리뷰 1: Beff Button Medium(높이 80).
                Box(Modifier.width(132.dp)) { BeffButton("안내", { onGuide(s) }, kind = ButtonKind.Secondary, height = 80.dp) }
            }
        }
    }
}

/** G-5 [안내]: 그 주차장을 새 목적지로 여정을 다시 시작한다(NaviHelper 호출은 startJourney 안). */
private fun guideTo(vm: AppViewModel, s: ParkingSpot) {
    vm.startJourney(
        Place(
            id = s.id, name = s.name, kind = PlaceKind.Attraction, facilities = listOf("장애인 주차"),
            lat = s.lat, lng = s.lng, address = s.address, region = s.region, kindLabel = "주차장",
        ),
    )
}
