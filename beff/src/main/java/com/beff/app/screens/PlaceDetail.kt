package com.beff.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beff.app.AppViewModel
import com.beff.app.Tab
import com.beff.app.data.GeoPoint
import com.beff.app.data.Mobility
import com.beff.app.data.Place
import com.beff.app.data.distanceKm
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffBox
import com.beff.app.ui.BeffGnb
import com.beff.app.ui.BeffType
import com.beff.app.ui.LocalWide
import com.beff.app.ui.ScreenFrame
import com.beff.app.ui.TitleBar
import com.beff.app.ui.beffBackground
import com.beff.app.ui.facilityIllust

// ── 1110 C-5 · C-6 · D-2 장소 상세. 출발 브리핑(BriefingScreen) 안의 한 단계로 쓴다. ──
// Figma 「1110 수정」 C. 검색 · D. 출발 준비. px = dp 1:1.

internal val InkTitle: Color get() = Beff.TextStrong     // 카드 제목
internal val InkSub: Color get() = Beff.TextSecondary       // 카드 보조 글
internal val TealInk: Color get() = Beff.ChipText      // 라벨 · 강조 글
private val CardLine: Color get() = Beff.Stroke      // 카드 테두리
private val TileGray: Color get() = Beff.Surface      // 시설 타일
private val TileOn: Color get() = Beff.Selected        // 맞는 시설 타일
private val MutedButton: Color get() = Beff.SecondaryBtn   // 회색 버튼(그래도 여기로 가기 · 취소 · 근처 주차장)

/**
 * 1110 와이드 틀(C-5 · C-6 · D-1~4): 왼쪽 866 + 오른쪽 위아래 끝까지 붙은 흰 패널 834(왼쪽 모서리 r36 · 그림자).
 * [right]가 null이면(D-4) 패널 없이 배경 위에 둔다.
 */
@Composable
internal fun BriefWideFrame(
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
    panel: Boolean = true,
) {
    Row(Modifier.fillMaxSize().beffBackground()) {
        Column(Modifier.weight(866f).fillMaxSize(), content = left)
        val shape = RoundedCornerShape(topStart = 36.dp, bottomStart = 36.dp)
        Column(
            Modifier.weight(834f).fillMaxSize()
                .then(
                    if (panel) Modifier.shadow(20.dp, shape, ambientColor = Beff.BoxShadow, spotColor = Beff.BoxShadow)
                        .clip(shape).background(Beff.Card)
                    else Modifier
                ),
            content = right,
        )
    }
}

/** 함께 타는 분의 대표 이동 수단(유모차 → 휠체어 → 보행 도움 순). 없으면 null. */
internal fun mainMobility(vm: AppViewModel): Mobility? =
    vm.active?.counts?.filterValues { it > 0 }?.keys?.minByOrNull { it.ordinal }

/** 카드 · 상세의 소요 시간: 내비 값이면 "1시간 5분 소요", 없으면 "직선 3.2km", 좌표도 없으면 null. */
internal fun tripLabel(vm: AppViewModel, p: Place): String? {
    vm.nav.trips[p.id]?.let { return "${fmtMinutes((it.seconds + 59) / 60)} 소요" }
    return tripText(vm, p)
}

/** 사진 칸. 카카오에서 찾은 「가는 길에 들르는 곳」(사진 없음)은 그 칩 그림으로 대신한다. */
@Composable
internal fun PlaceImage(place: Place, modifier: Modifier, illustSize: Dp) {
    val chipIllust = place.note?.let { ROUTE_ILLUST[it] }
    if (place.image == null && chipIllust != null) {
        Box(modifier.background(Beff.Surface), contentAlignment = Alignment.Center) { BeffAsset(chipIllust, illustSize) }
    } else PlacePhoto(place, modifier, illustSize)
}

/** 「가는 길에 들르는 곳」 칩 → 그림. 맞는 그림이 없는 칩은 위치 핀. */
internal val ROUTE_ILLUST = mapOf(
    "휴게소" to "illust/rest_area.svg", "주차장" to "illust/parking_sign.svg", "카페" to "illust/cafe.svg",
    "식당" to "illust/cafe.svg", "약국" to "illust/hospital.svg", "편의점" to "illust/pin.svg",
)

/** 시설 근거 칩(C-6): #EDF6F1 바탕 · r12 · 높이 46 · 26 Regular 틸. */
@Composable
private fun EvidenceChip(text: String) {
    Box(
        Modifier.height(46.dp).clip(RoundedCornerShape(12.dp)).background(Beff.ChipBg).padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BeffType.Caption28.copy(color = Beff.ChipText), maxLines = 1) }
}

/**
 * C-6 · D-2 장소 상세. 왼쪽: 「< 장소 상세보기」 · 장소 카드(이름 · 소요 · 근거 칩 · 사진 · 출처) · 주 버튼 · GNB.
 * 오른쪽: 주차한 곳에서 시설까지 지도(FacilityMapCard) + 이곳 시설 그리드(원문 펼쳐 보기는 1113에 뺌).
 * [action]은 C-6 「여기로 출발」, D-2 「경유지로 추가하기」.
 */
@Composable
internal fun PlaceDetail(
    vm: AppViewModel,
    place: Place,
    action: String,
    onAction: () -> Unit,
    onBack: () -> Unit,
    actionEnabled: Boolean = true,
) {
    LaunchedEffect(place.id, vm.nav.link) { vm.nav.requestTripTo(place) }
    if (LocalWide.current) {
        BriefWideFrame(
            left = {
                Spacer(Modifier.height(40.dp))
                TitleBar("장소 상세보기", onBack)
                Spacer(Modifier.height(12.dp))
                DetailCard(vm, place, Modifier.weight(1f).padding(horizontal = 31.dp))
                Spacer(Modifier.height(24.dp))
                Box(Modifier.padding(start = 56.dp, end = 56.dp)) { BigButton(action, onAction, enabled = actionEnabled) }
                Spacer(Modifier.height(25.dp))
                BeffGnb(Tab.Home, vm::selectTab, sidePadding = 56.dp)
                Spacer(Modifier.height(29.dp))
            },
            right = {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(start = 58.dp, end = 58.dp, top = 74.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) { DetailFacilities(vm, place) }
            },
        )
        return
    }
    ScreenFrame(
        top = { Box(Modifier.padding(top = 40.dp)) { TitleBar("장소 상세보기", onBack) } },
        bottom = {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 29.dp)) {
                Box(Modifier.padding(horizontal = 56.dp)) { BigButton(action, onAction, enabled = actionEnabled) }
                Spacer(Modifier.height(24.dp))
                BeffGnb(Tab.Home, vm::selectTab)
            }
        },
    ) { mod ->
        Column(
            mod.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 31.dp).padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            DetailCard(vm, place, Modifier.height(746.dp))
            Column(Modifier.padding(horizontal = 25.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) { DetailFacilities(vm, place) }
        }
    }
}

/** C-6 왼쪽 장소 카드(778×746 · 흰 · 테두리 #E0EAE6 · r32). 사진은 대표 사진 1장이 남은 높이를 채운다. */
@Composable
private fun DetailCard(vm: AppViewModel, place: Place, modifier: Modifier) {
    val shape = RoundedCornerShape(32.dp)
    val chips = vm.places.facilitiesFor(place, vm.active).ifEmpty { place.facilities }.take(3)
    Column(
        modifier.fillMaxWidth().clip(shape).background(Beff.Card).border(2.dp, CardLine, shape)
            .padding(start = 56.dp, end = 45.dp, top = 50.dp, bottom = 40.dp),
    ) {
        // 4-1 · 6-9: 이름 48 ExtraBold → 주소 → 시간 · 거리(내비 값, 없으면 직선) → 시설 칩. C-6 · D-2 같은 틀.
        Column(Modifier.padding(start = 15.dp, end = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                place.name, style = BeffType.Title48.copy(color = InkTitle),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val addr = place.address.ifBlank { listOf(place.kindLabel, place.regionLabel).filter { it.isNotBlank() }.joinToString(" · ") }
            if (addr.isNotBlank()) Text(addr, style = BeffType.Body32.copy(fontSize = 30.sp, lineHeight = 41.sp, color = InkSub), maxLines = 1, overflow = TextOverflow.Ellipsis)
            tripText(vm, place)?.let {
                Text(it, style = BeffType.Small30.copy(color = Beff.TextStrong), maxLines = 1)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(start = 15.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { EvidenceChip(it) }
        }
        Spacer(Modifier.height(30.dp))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(22.dp))) {
            PlaceImage(place, Modifier.fillMaxSize(), illustSize = 160.dp)
        }
        // 출처 · 사진 저작자는 설정 「저작권 · 출처」에 모은다.
    }
}

/**
 * C-6 오른쪽: 지도(주차한 곳 → 필요한 시설, FacilityMapCard) + 이곳 시설 그리드(351×196 · 그림 112 · 32 SemiBold).
 * 함께 타는 분에게 맞는 시설은 옅은 틸 + 틸 테두리로 먼저 둔다. 아래에 근처 주차장.
 */
@Composable
private fun ColumnScope.DetailFacilities(vm: AppViewModel, place: Place) {
    // 1113: C-6 · D-2 같은 틀 — 「이곳에 있는 시설」 그리드는 뺐다.
    // 2/3: 스크롤 없이 한 화면에 — 지도 560 + 시설 2×3. 근처 주차장 버튼은 Figma에 없어 뺀다(도착 화면 G-2에 있음).
    // 1/3: 화면 전체가 스크롤되므로 지도 아래 한 줄 목록 + 근처 주차장.
    if (LocalWide.current) {
        FacilityMapCard(vm, place, mapHeight = 560.dp, tileColumns = 2)
    } else {
        FacilityMapCard(vm, place, mapHeight = 600.dp, tileColumns = 1)
        if (place.lat != null && place.lng != null) {
            MutedBigButton("근처 주차장") { vm.openAlternatives(place.id) }
        }
    }
}

@Composable
private fun FacilityGridTile(label: String, on: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier.height(196.dp).clip(shape).background(if (on) TileOn else Beff.Card)
            .border(if (on) 3.dp else 1.5.dp, if (on) Beff.Teal else Beff.Stroke, shape) // 6-10: 흰 타일 + 테두리, 선택만 #B3DEDA
            .padding(top = 18.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BeffAsset(facilityIllust(label), 112.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            label, style = BeffType.Body32.copy(fontWeight = FontWeight.SemiBold, color = Beff.TextStrong),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 1110 주 버튼(728×96 · r26 · 35 Bold). */
@Composable
internal fun BigButton(text: String, onClick: () -> Unit, enabled: Boolean = true, height: Dp = 96.dp) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier.fillMaxWidth().height(height).clip(shape)
            .background(if (enabled) Beff.Teal else Beff.Teal.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BeffType.Button36.copy(fontSize = 35.sp, fontWeight = FontWeight.Bold, color = Color.White), maxLines = 1) }
}

/** 1110 흰 보조 버튼(「하차 브리핑 미리 보기」: 흰 바탕 · 테두리 #DFE9E7 · 틸 35 Bold). */
@Composable
internal fun WhiteBigButton(text: String, onClick: () -> Unit, height: Dp = 100.dp) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier.fillMaxWidth().height(height).clip(shape).background(Beff.Card)
            .border(2.dp, Color(0xFFDFE9E7), shape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BeffType.Button36.copy(fontSize = 35.sp, fontWeight = FontWeight.Bold, color = TealInk), maxLines = 1) }
}

/** 회색 버튼(Beff / Button 회색: #EDEEF2 · r32 · 30 SemiBold #4A545C). */
@Composable
internal fun MutedBigButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier.fillMaxWidth().height(100.dp).clip(shape).background(MutedButton).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BeffType.Small30.copy(color = Beff.SecondaryBtnText), maxLines = 1) }
}

/**
 * C-5 정보 없음 → 근처 대안. 왼쪽: 장소 이름 · 분류 · 「휠체어 정보가 아직 없어요」 · [그래도 여기로 가기].
 * 오른쪽: 목적지 주변 3km 안에서 정보가 확인된 곳(맞는 시설이 많은 순). 누르면 그곳의 장소 상세.
 */
@Composable
internal fun NoInfoDetail(vm: AppViewModel, place: Place, onGo: () -> Unit, onBack: () -> Unit) {
    val alts = remember(place.id, vm.active?.id) { vm.places.alternativesNear(place, vm.active) }
    if (LocalWide.current) {
        BriefWideFrame(
            left = {
                Spacer(Modifier.height(40.dp))
                TitleBar("장소 상세보기", onBack)
                Spacer(Modifier.height(24.dp))
                NoInfoCard(vm, place, onGo, Modifier.weight(1f).padding(horizontal = 56.dp))
                Spacer(Modifier.height(19.dp))
                BeffGnb(Tab.Home, vm::selectTab, sidePadding = 56.dp)
                Spacer(Modifier.height(29.dp))
            },
            right = {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 58.dp, end = 58.dp, top = 74.dp, bottom = 48.dp),
                ) { AltList(vm, place, alts) }
            },
        )
        return
    }
    ScreenFrame(
        top = { Box(Modifier.padding(top = 40.dp)) { TitleBar("장소 상세보기", onBack) } },
        bottom = { Box(Modifier.padding(top = 24.dp, bottom = 29.dp)) { BeffGnb(Tab.Home, vm::selectTab) } },
    ) { mod ->
        Column(
            mod.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 56.dp).padding(top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            NoInfoCard(vm, place, onGo, Modifier.height(860.dp))
            Column { AltList(vm, place, alts) }
        }
    }
}

@Composable
private fun NoInfoCard(vm: AppViewModel, place: Place, onGo: () -> Unit, modifier: Modifier) {
    val m = mainMobility(vm)
    val who = m?.label ?: "시설"
    BeffBox(modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 42.dp)) {
            // 6-9: 이름 앞 핀 아이콘 뺌, 이름 48 ExtraBold. 4-1: 분류 · 지역 아래 시간 · 거리.
            Text(place.name, style = BeffType.Title48.copy(color = InkTitle), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOf(place.kindLabel, place.regionLabel).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = BeffType.Body32.copy(fontSize = 30.sp, color = Beff.TextSecondary), maxLines = 1)
            tripText(vm, place)?.let { Text(it, style = BeffType.Small30.copy(color = Beff.TextStrong), maxLines = 1) }
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                BeffAsset(m?.illust ?: "illust/wheelchair.svg", 176.dp)
                Spacer(Modifier.height(24.dp))
                Text(
                    "$who 정보가\n아직 없어요",
                    style = BeffType.Box40.copy(color = Beff.TextStrong, textAlign = TextAlign.Center), // 6-7: 빈 상태 문구 40 Bold
                )
                Spacer(Modifier.height(12.dp))
                Text("열린관광에 등록되지 않았어요", style = BeffType.Body32.copy(color = Beff.TextSecondary, textAlign = TextAlign.Center))
            }
            MutedBigButton("그래도 여기로 가기", onClick = onGo)
        }
    }
}

/** C-5 오른쪽 「근처에 정보가 있는 곳」 목록(줄 126 · 핀 타일 72 · 이름 36 SemiBold · 한 줄 28). */
@Composable
private fun AltList(vm: AppViewModel, place: Place, alts: List<Place>) {
    val m = mainMobility(vm)
    val wanted = vm.active?.filters.orEmpty()
    Text("근처에 정보가 있는 곳", style = BeffType.Box40.copy(color = Beff.TextPrimary))
    Spacer(Modifier.height(4.dp))
    Text(
        // 대안은 내 위치가 아니라 목적지 주변에서 찾는다(1110).
        "${place.name} 주변 3km · ${m?.label ?: "시설"} 정보가 많은 순",
        style = BeffType.Body32.copy(color = Beff.TextSecondary), maxLines = 2,
    )
    Spacer(Modifier.height(24.dp))
    if (alts.isEmpty()) {
        Text("주변 3km 안에도 정보가 확인된 곳이 없어요. 그대로 가려면 「그래도 여기로 가기」를 눌러 주세요", style = BeffType.Body32.copy(color = Beff.TextSecondary))
        return
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Beff.Card)) {
        alts.forEachIndexed { i, a ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(Beff.Stroke))
            val matched = a.facilities.filter { it in wanted }
            val km = if (place.lat != null && place.lng != null && a.lat != null && a.lng != null)
                distanceKm(GeoPoint(place.lat, place.lng), GeoPoint(a.lat, a.lng)) else null
            Row(
                Modifier.fillMaxWidth().height(126.dp).clickable { vm.openBriefing(a.id) }.padding(start = 32.dp, end = 32.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(72.dp, 74.dp).clip(RoundedCornerShape(36.dp)).background(Beff.Surface), contentAlignment = Alignment.Center) {
                    BeffAsset("pict/pin.svg", 48.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = BeffType.Item36Semi.copy(color = Beff.TextPrimary, lineHeight = 48.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (listOfNotNull(km?.let { String.format("%.1fkm", it) }) + matched.ifEmpty { a.facilities }.take(2)).joinToString(" · "),
                        style = BeffType.Caption28.copy(color = Beff.TextMuted, lineHeight = 37.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (matched.size <= 1) LessInfoTag() else BeffAsset(m?.illust ?: "illust/wheelchair.svg", 64.dp)
            }
        }
    }
}

/** 「정보 적음」 회색 알약(#EEF0F2 · 24 SemiBold #6B7280). */
@Composable
private fun LessInfoTag() {
    Box(
        Modifier.height(45.dp).clip(RoundedCornerShape(100.dp)).background(Beff.Surface).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text("정보 적음", style = BeffType.Caption28.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6B7280)), maxLines = 1) }
}
