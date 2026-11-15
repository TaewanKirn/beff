package com.beff.app.screens

import com.beff.app.ui.imeField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.beff.app.Tab
import com.beff.app.ui.LocalWide
import com.beff.app.ui.WideTab
import com.beff.app.ui.WidePanelTab
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beff.app.AppViewModel
import com.beff.app.data.Place
import com.beff.app.data.RecentItem
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffBox
import com.beff.app.ui.BeffType
import com.beff.app.ui.SmallTagButton
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 04 검색. 최근 검색은 접힘이 기본(정책 60), 즐겨찾기는 없다(59).
 * 3분할(840)은 한 열 스크롤 + 스크롤해도 검색창 고정(04-3). 2/3 전체 화면은 2열 스크롤 없음(04-1 · 04-2, 정책 65).
 */
@Composable
fun SearchScreen(vm: AppViewModel) {
    val expanded = vm.searchRecentOpen
    val editing = vm.searchRecentEdit
    val query = vm.searchQuery
    val chip = vm.searchChip
    // 검색어 결과는 카카오 전국 검색(무장애 장소와 맞춘 것은 그 장소), 못 쓰면 assets/places.json(한국관광공사 무장애 여행정보).
    // 시설 칩 결과는 무장애 데이터에서만 찾는다(카카오에는 시설 정보가 없다).
    val results: List<Place> = when {
        chip != null -> vm.places.nearbyWith(chip, vm.origin, 15)
        query.isNotBlank() -> vm.searchResults
        else -> emptyList()
    }
    val active = chip != null || query.isNotBlank()
    val wide = LocalWide.current

    @Composable
    fun Title() = Text("어디로 갈까요?", style = BeffType.Title56.copy(color = Beff.TextStrong, lineHeight = 74.sp), modifier = Modifier.padding(start = 4.dp))

    @Composable
    fun Field() = SearchField(
        value = query,
        onChange = { vm.searchChip = null; vm.searchQuery = it },
        listening = vm.speech.state != com.beff.app.device.SpeechState.Idle,
        micEnabled = !vm.vehicle.drivingRestricted,
        message = vm.speech.message,
        onMic = vm::toggleVoiceSearch,
        onSubmit = {
            results.firstOrNull()?.let { vm.rememberSearch(query, it.id); vm.openBriefing(it.id) }
        },
    )

    @Composable
    fun Results() {
        BeffBox(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 3-2: 시설 타일로 들어오면 타일 이름 그대로 + 일러스트. 3-3: [변경] · [✕ 닫기].
                    val theme = chip?.let { c -> vm.places.themesFor(vm.active).firstOrNull { it.chip == c } }
                    if (theme != null) BeffAsset(theme.illust, 56.dp)
                    Text(
                        theme?.label?.replace("\n", " ") ?: if (chip != null) "$chip 있는 곳" else "‘$query’ 검색 결과",
                        style = BeffType.Box40.copy(color = Beff.TextStrong), modifier = Modifier.weight(1f), maxLines = 1,
                    )
                    if (chip != null) {
                        SmallTagButton("변경") { vm.searchChip = null }
                        CloseChip { vm.searchChip = null }
                    }
                }
                Text(
                    when {
                        chip == null && vm.searchIsNationwide -> "전국 장소 · 시설 표시는 한국관광공사 열린관광 정보 기준"
                        chip == null && vm.searchOffline -> "인터넷에 연결되지 않아 앱에 저장된 장소(서울 · 경기 · 인천)에서만 찾았어요"
                        vm.originIsDefault -> "서울시청 기준 · 내비 위치를 받으면 내 위치로 바뀌어요"
                        else -> "내 위치에서 가까운 순"
                    },
                    style = BeffType.Caption28.copy(color = Beff.TextSecondary),
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                )
                if (results.isEmpty()) {
                    Text(
                        "찾지 못했어요. 이름이나 지역을 바꿔 보세요",
                        style = BeffType.Body32.copy(color = Beff.TextSecondary),
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                    )
                }
                RequestTrips(vm, results.take(TRIP_MAX_ROWS))
                results.forEach { p ->
                    PlaceRow(p, subtitle = resultLine(vm, p), picts = mobilityPicts(p)) {
                        vm.rememberSearch(p.name, p.id)
                        vm.openBriefing(p.id)
                    }
                }
            }
        }
    }

    @Composable
    fun Recent() =
        // 1113: 3분할은 접기 없이 펼쳐 둔다 — 검색창을 눌러 들어온 화면에서 최근 검색이 바로 보이게. 기록이 없으면 카드도 없다.
        if (!wide && vm.recents.isEmpty()) Unit
        else RecentCard(
            vm, expanded || !wide, { vm.searchRecentOpen = !vm.searchRecentOpen }, editing, { vm.searchRecentEdit = !vm.searchRecentEdit },
            maxItems = if (wide) 4 else 5, collapsible = wide,
        )

    /** 04-4 오른쪽: 추천 카드(사진 267) + 「근처 함께 가기 좋은 곳」 목록. */
    @Composable
    fun Recommended() {
        val recs = vm.recommendations()
        RecommendHero(vm, recs.take(HERO_COUNT), imageHeight = 267.dp)
        RecommendList(vm, recs.drop(HERO_COUNT))
    }

    @Composable
    fun Themes(modifier: Modifier = Modifier, fill: Boolean = false) =
        ThemeCard(vm.places.themesFor(vm.active), compact = true, modifier = modifier, fill = fill) { t -> vm.openChipSearch(t.chip) }

    if (wide) {
        // 1110 수정 C-1 · C-3: 왼쪽은 홈과 같은 틀(검색창은 입력 상태), 오른쪽 흰 패널에 최근 검색 / 검색 결과.
        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        androidx.compose.runtime.LaunchedEffect(Unit) { if (chip == null) runCatching { focus.requestFocus() } }
        WidePanelTab(
            Tab.Search, vm::selectTab,
            left = {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                        HomeTitle(vm)
                        Box(Modifier.focusRequester(focus)) { Field() }
                        ThemeGrid(vm.places.themesFor(vm.active), Modifier.weight(1f)) { t ->
                            // 시설 타일을 누르면 입력을 끝내고(키보드 내림) 그 시설이 있는 곳을 보여 준다.
                            focusManager.clearFocus(); keyboard?.hide()
                            vm.openChipSearch(t.chip)
                        }
                    }
                    WideToasts(vm, Tab.Search, Modifier.align(Alignment.BottomCenter))
                }
            },
            right = {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 16.dp, top = 79.dp, bottom = 40.dp)) {
                    if (active) PanelResults(vm, chip, query, results) else PanelRecent(vm, editing)
                }
            },
        )
    } else {
        // 04-3: 제목은 스크롤로 올라가고, 검색창은 위(본문 맨 위)에 붙어 고정된다.
        val scroll = rememberScrollState()
        val titleBlock = with(LocalDensity.current) { (24 + 74 + 32).dp.toPx() }
        val pinned = scroll.value > titleBlock
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll)
                    .padding(horizontal = 56.dp).padding(top = 24.dp, bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                Title()
                Spacer(Modifier.height(120.dp)) // 검색창 자리(아래 Box가 겹쳐 그린다)
                if (active) Results() else { Recent(); Themes(); Recommended() }
            }
            if (pinned) {
                Box(
                    Modifier.fillMaxWidth().height(152.dp)
                        .background(Brush.verticalGradient(listOf(Beff.Bg.copy(alpha = 0.95f), Beff.Bg.copy(alpha = 0f)))),
                )
            }
            Box(
                Modifier.padding(horizontal = 56.dp)
                    .offset { IntOffset(0, kotlin.math.max(0f, titleBlock - scroll.value).toInt()) },
            ) { Field() }
        }
    }
}

/** C-1 오른쪽 패널: 「최근 검색」 40 Bold + 한 줄 128(장소 = 핀, 검색어 = 틸 20% 바탕), 구분선 #E8ECEE. */
@Composable
private fun PanelRecent(vm: AppViewModel, editing: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = 38.dp, end = 16.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("최근 검색", style = BeffType.Box40.copy(color = Beff.TextPrimary, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        if (vm.recents.isNotEmpty()) SmallTagButton(if (editing) "완료" else "편집") { vm.searchRecentEdit = !vm.searchRecentEdit }
    }
    if (vm.recents.isEmpty()) {
        Text("아직 검색한 곳이 없어요", style = BeffType.Body32.copy(color = Beff.TextSecondary), modifier = Modifier.padding(horizontal = 38.dp, vertical = 16.dp))
    }
    vm.recents.take(8).forEachIndexed { i, r ->
        if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(Beff.Stroke))
        val place = r.placeId?.let { vm.places.placeOrNull(it) }
        if (place != null) {
            PlaceRow(place, subtitle = r.date, picts = mobilityPicts(place), editing = editing, onRemove = { vm.removeRecent(r) }) {
                vm.openBriefing(place.id)
            }
        } else {
            TermRow(r.text, r.date, editing = editing, onRemove = { vm.removeRecent(r) }) { vm.searchQuery = r.text }
        }
    }
}

/** C-3 오른쪽 패널: 「'○○' 검색 결과」(또는 「수유실 있는 곳」) 40 Bold + 장소 줄. 줄 끝 픽토 = 이동 보조수단 정보가 있는 곳. */
@Composable
private fun PanelResults(vm: AppViewModel, chip: String?, query: String, results: List<Place>) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        // 6-11: 와이드는 왼쪽 타일 선택으로 바꾸므로 [변경] · [닫기]를 두지 않는다. 머리말은 타일 이름 + 일러스트(3-2).
        val theme = chip?.let { c -> vm.places.themesFor(vm.active).firstOrNull { it.chip == c } }
        if (theme != null) { BeffAsset(theme.illust, 56.dp); Spacer(Modifier.width(12.dp)) }
        Text(
            theme?.label?.replace("\n", " ") ?: if (chip != null) "$chip 있는 곳" else "‘$query’ 검색 결과",
            style = BeffType.Box40.copy(color = Beff.TextStrong, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f), maxLines = 1,
        )
    }
    val note = when {
        chip == null && vm.searchOffline -> "인터넷에 연결되지 않아 앱에 저장된 장소(서울 · 경기 · 인천)에서만 찾았어요"
        vm.originIsDefault -> "서울시청 기준 · 내비 위치를 받으면 내 위치로 바뀌어요"
        else -> null
    }
    if (note != null) Text(note, style = BeffType.Caption28.copy(color = Beff.TextSecondary), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    if (results.isEmpty()) {
        Text("찾지 못했어요. 이름이나 지역을 바꿔 보세요", style = BeffType.Body32.copy(color = Beff.TextSecondary), modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))
    }
    RequestTrips(vm, results.take(TRIP_MAX_ROWS))
    results.forEachIndexed { i, p ->
        if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(Beff.Stroke))
        PlaceRow(p, subtitle = resultLine(vm, p), picts = mobilityPicts(p)) {
            vm.rememberSearch(p.name, p.id)
            vm.openBriefing(p.id)
        }
    }
}

/** 검색 결과 한 줄 정보(2-1): 위치 · 시간 · 거리만 — "서울 성동구 · 18분 · 15.2km". */
private fun resultLine(vm: AppViewModel, p: Place): String = listOfNotNull(
    p.regionLabel.ifBlank { null }, tripText(vm, p),
).joinToString(" · ")

/**
 * 1-1: 장소 시설로 판단한 이동 보조수단 픽토를 모두(휠체어 → 유모차 → 보행 도움). 많이 겹칠수록 접근성이 좋다는 뜻.
 * 판단은 원문에 적힌 시설만 쓴다(추정하지 않음).
 */
private fun mobilityPicts(p: Place): List<String> {
    val f = p.facilities.joinToString(" ")
    return buildList {
        if (listOf("장애인 화장실", "휠체어", "장애인 주차", "엘리베이터", "경사로", "턱 없는").any { it in f }) add("pict/wheelchair.svg")
        if (listOf("수유실", "기저귀", "유모차", "유아").any { it in f }) add("pict/stroller.svg")
        if (listOf("경사로", "엘리베이터", "턱 없는", "쉼터", "의자").any { it in f }) add("pict/cane.svg")
    }
}

/** 겹친 픽토(원 64, 24씩 겹침). 맨 앞(왼쪽)이 위에 온다. */
@Composable
private fun PictStack(picts: List<String>) {
    Box(Modifier.width((64 + (picts.size - 1).coerceAtLeast(0) * 40).dp).height(64.dp)) {
        picts.asReversed().forEachIndexed { ri, p ->
            val i = picts.size - 1 - ri
            Box(
                Modifier.offset(x = (i * 40).dp).size(64.dp).clip(RoundedCornerShape(32.dp))
                    .background(Beff.Card).border(2.dp, Beff.TealSoft, RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center,
            ) { BeffAsset(p, 44.dp, tint = Beff.TealText) }
        }
    }
}

/** 3-3: ✕ + 「닫기」 회색 채움 버튼. */
@Composable
private fun CloseChip(onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(100.dp)).background(Beff.SecondaryBtn).clickable(onClick = onClick)
            .padding(start = 20.dp, end = 28.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BeffAsset("pict/close.svg", 36.dp, tint = Beff.SecondaryBtnText)
        Text("닫기", style = BeffType.Small30.copy(color = Beff.SecondaryBtnText))
    }
}

private fun mobilityPict(m: com.beff.app.data.Mobility): String = when (m) {
    com.beff.app.data.Mobility.Stroller -> "pict/stroller.svg"
    com.beff.app.data.Mobility.Wheelchair -> "pict/wheelchair.svg"
    com.beff.app.data.Mobility.Walker -> "pict/cane.svg"
}

private fun mobilityOfProfile(profile: com.beff.app.data.Profile?): com.beff.app.data.Mobility = when {
    profile == null -> com.beff.app.data.Mobility.Stroller
    (profile.counts[com.beff.app.data.Mobility.Wheelchair] ?: 0) > 0 -> com.beff.app.data.Mobility.Wheelchair
    (profile.counts[com.beff.app.data.Mobility.Stroller] ?: 0) > 0 -> com.beff.app.data.Mobility.Stroller
    else -> com.beff.app.data.Mobility.Walker
}

/** 04 검색창(정책 10절): 흰 바탕 + 틸 외곽선 4(INSIDE), 안내 글자 #4A545C. 마이크 칸은 틸 바탕 + 흰 픽토, 검색 픽토는 틸. */
@Composable
private fun SearchField(
    value: String, onChange: (String) -> Unit, onSubmit: () -> Unit,
    listening: Boolean, micEnabled: Boolean, message: String?, onMic: () -> Unit,
) {
    val shape = RoundedCornerShape(72.dp)
    Row(
        Modifier.fillMaxWidth().height(120.dp)
            .shadow(12.dp, shape, ambientColor = Color(0x38004D4D), spotColor = Color(0x38004D4D))
            .clip(shape).background(Beff.Card).border(4.dp, Beff.Teal, shape)
            .padding(start = 16.dp, end = 32.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (listening) {
            // 듣는 중: 「듣고 있어요」 칩. 누르면 멈춘다.
            com.beff.app.ui.ListeningChip(88.dp, playing = micEnabled, modifier = Modifier.clickable(onClick = onMic))
        } else {
            Box(
                Modifier.size(80.dp).clip(RoundedCornerShape(40.dp)).background(Beff.Teal)
                    .alpha(if (micEnabled) 1f else 0.4f).clickable(enabled = micEnabled, onClick = onMic),
                contentAlignment = Alignment.Center,
            ) { BeffAsset("pict/mic.svg", 48.dp, tint = Color.White) }
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty() && !listening) Text(message ?: "장소, 주소 검색", style = BeffType.Item36.copy(fontWeight = FontWeight.Normal, color = if (message != null) Beff.TextPrimary else Beff.TextSecondary))
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = BeffType.Item36.copy(fontWeight = FontWeight.Bold, color = Beff.TextStrong), // 2-2: 입력한 검색어는 Bold
                cursorBrush = SolidColor(Beff.Teal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().imeField(),
            )
        }
        Box(Modifier.size(48.dp).clickable(onClick = onSubmit)) { BeffAsset("pict/search.svg", 48.dp, tint = Beff.TealText) }
    }
}

@Composable
private fun RecentCard(vm: AppViewModel, expanded: Boolean, onToggle: () -> Unit, editing: Boolean, onEdit: () -> Unit, maxItems: Int, collapsible: Boolean = true) {
    BeffBox(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(Modifier.padding(top = 32.dp, bottom = if (expanded && LocalWide.current) 20.dp else 32.dp)) {
            Row(
                Modifier.fillMaxWidth().then(if (collapsible) Modifier.clickable(onClick = onToggle) else Modifier)
                    .padding(start = if (collapsible) 20.dp else 32.dp, end = 32.dp, bottom = if (expanded) 12.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (collapsible) Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    // 접힘 = 오른쪽 화살표, 펼침 = 아래 화살표
                    BeffAsset("pict/chevron_right.svg", 48.dp, Modifier.graphicsLayer(rotationZ = if (expanded) 90f else 0f))
                }
                Text("최근 검색", style = BeffType.Box40.copy(color = Beff.TextPrimary), modifier = Modifier.weight(1f))
                if (expanded && vm.recents.isNotEmpty()) SmallTagButton(if (editing) "완료" else "편집", onEdit)
            }
            if (expanded) {
                if (vm.recents.isEmpty()) {
                    Text(
                        "아직 검색한 곳이 없어요",
                        style = BeffType.Body32.copy(color = Beff.TextSecondary),
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                    )
                }
                vm.recents.take(maxItems).forEachIndexed { i, r ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(Beff.Stroke))
                    val place = r.placeId?.let { vm.places.placeOrNull(it) }
                    if (place != null) {
                        PlaceRow(place, subtitle = r.date, picts = mobilityPicts(place), editing = editing, onRemove = { vm.removeRecent(r) }) {
                            vm.openBriefing(place.id)
                        }
                    } else {
                        TermRow(r.text, r.date, editing = editing, onRemove = { vm.removeRecent(r) }) { vm.searchQuery = r.text }
                    }
                }
            }
        }
    }
}

/**
 * 장소 한 줄. [mark]가 true면 무장애 장소(이름 옆 「무장애」 배지 + 원문에 적힌 시설), false면 「무장애 정보 없음」, null이면 표시하지 않는다(최근 검색).
 */
@Composable
private fun PlaceRow(
    place: Place, subtitle: String? = null, trailing: String? = null, mark: Boolean? = null, picts: List<String> = emptyList(),
    editing: Boolean = false, onRemove: () -> Unit = {}, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 32.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(Beff.Surface), contentAlignment = Alignment.Center) {
            BeffAsset("pict/pin.svg", 48.dp)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(place.name, style = BeffType.Item36Semi.copy(color = Beff.TextPrimary), maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (mark == true) BarrierFreeBadge()
            }
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = BeffType.Caption28.copy(color = Beff.TextMuted), maxLines = 1)
            when (mark) {
                true -> place.facilities.take(3).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), style = BeffType.Caption28.copy(color = Beff.ChipText, fontWeight = FontWeight.SemiBold), maxLines = 1)
                }
                false -> Text("시설 정보 없음", style = BeffType.Caption28.copy(color = Beff.TextSecondary), maxLines = 1)
                null -> {}
            }
        }
        if (editing) RemoveX(onRemove) else if (picts.isNotEmpty()) PictStack(picts) else if (trailing != null) BeffAsset(trailing, 48.dp, tint = Beff.TealText)
    }
}

/** 「무장애」 배지: 옅은 틸 바탕 + 틸 글자(검색 결과 이름 옆). */
@Composable
private fun BarrierFreeBadge() {
    Box(
        Modifier.clip(RoundedCornerShape(100.dp)).background(com.beff.app.ui.TealCardBg)
            .border(2.dp, com.beff.app.ui.TealCardBorder, RoundedCornerShape(100.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text("시설 정보", style = BeffType.Caption28.copy(color = Beff.ChipText, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
private fun TermRow(text: String, date: String?, editing: Boolean = false, onRemove: () -> Unit = {}, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0x33008E8C)).clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(Beff.Card), contentAlignment = Alignment.Center) {
            BeffAsset("pict/search.svg", 48.dp, tint = Beff.TealText)
        }
        Column(Modifier.weight(1f)) {
            Text(text, style = BeffType.Item36Semi.copy(color = Beff.ChipText), maxLines = 1)
            if (date != null) Text(date, style = BeffType.Caption28.copy(color = Beff.TextSecondary))
        }
        if (editing) RemoveX(onRemove)
    }
}

@Composable
private fun RemoveX(onClick: () -> Unit) {
    Box(Modifier.size(76.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) { // 리뷰 1: 닫기 누름 76
        BeffAsset("pict/close.svg", 48.dp, tint = Beff.TextSecondary)
    }
}

/** "관광지 · 서울 종로구 · 18분 · 12.3km"(내비 값 없으면 "직선 3.2km") */
private fun subtitle(vm: AppViewModel, p: Place): String = listOfNotNull(
    p.kindLabel.ifBlank { null }, p.regionLabel.ifBlank { null }, tripText(vm, p),
).joinToString(" · ")

/** 검색 결과 중 앞쪽 몇 곳만 내비에 도로 거리·시간을 묻는다(페타와 같은 상한 정책). */
private const val TRIP_MAX_ROWS = 6
