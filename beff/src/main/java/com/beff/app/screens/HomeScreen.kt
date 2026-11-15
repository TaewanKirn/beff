package com.beff.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beff.app.AppViewModel
import com.beff.app.Tab
import com.beff.app.data.Place
import com.beff.app.data.Theme
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffBox
import com.beff.app.ui.BeffType
import com.beff.app.ui.FacilityTile
import com.beff.app.ui.LocalWide
import com.beff.app.ui.WidePanelTab
import com.beff.app.ui.facilityIllust
import androidx.compose.foundation.layout.offset
import com.beff.app.ui.PageDots
import com.beff.app.ui.RoundArrow
import com.beff.app.ui.SmallTagButton
import com.beff.app.ui.WhoHeader

/** 03 홈. 기본(큐레이션, 03-1) / 여정 중(03-2). 2/3 전체 화면은 2열(WideTab). */
@Composable
fun HomeScreen(vm: AppViewModel) {
    val journey = vm.journey
    if (LocalWide.current) {
        // 1110 수정 B-1~B-6: 홈은 여정 유무와 관계없이 같은 틀(검색 + 시설 타일 4 | 추천 카드 + 근처 코스).
        // 여정 중(B-2) · 경유지에서 쉬는 중(B-3) · 도착(B-4)은 GNB 위 짙은 배너로 알린다. 체크인(B-5)은 토스트.
        WidePanelTab(
            Tab.Home, vm::selectTab,
            left = {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                        HomeTitle(vm)
                        BigSearchBar(micEnabled = !vm.vehicle.drivingRestricted, onMic = vm::toggleVoiceSearch) { vm.selectTab(Tab.Search) }
                        ThemeGrid(vm.places.themesFor(vm.active), Modifier.weight(1f)) { t -> vm.openChipSearch(t.chip) }
                    }
                    val toast = vm.checkIn != null || vm.notice != null
                    if (journey != null && !toast) {
                        HomeBanner(vm, journey, Modifier.align(Alignment.BottomCenter))
                    }
                    WideToasts(vm, Tab.Home, Modifier.align(Alignment.BottomCenter))
                }
            },
            right = {
                val recs = vm.recommendations()
                Column(
                    Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 47.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    recs.firstOrNull()?.let { HomeHeroCard(vm, it) }
                    HomeCourseList(vm, recs.drop(1), Modifier.weight(1f))
                }
            },
        )
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 56.dp).padding(top = 24.dp, bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        if (journey == null) HomeDefault(vm) else HomeOnTrip(vm, journey.dest, journey.stops.firstOrNull())
    }
}

/** B-1 제목: 「어디로 갈까요?」 48 ExtraBold(6-6 · 6-7). 프로필 썸네일은 빼기로 했다(Figma 1113 — 프로필은 프로필 화면에서만). */
@Composable
internal fun HomeTitle(@Suppress("UNUSED_PARAMETER") vm: AppViewModel) {
    Text("어디로 갈까요?", style = BeffType.Title48.copy(color = Beff.TextStrong))
}

/**
 * B-1 시설 타일 2×2(370×280, 간격 32): 흰 90% · 흰 테두리 4.8 · r39 · 그림자.
 * 제목 36 Bold(두 줄) + 「더보기 >」 31 틸, 일러스트 170은 오른쪽 아래. 누르면 그 시설이 있는 곳을 검색한다.
 */
@Composable
internal fun ThemeGrid(themes: List<Theme>, modifier: Modifier = Modifier, onTheme: (Theme) -> Unit) {
    val items = themes.take(4)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(32.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                row.forEach { t -> ThemeTile(t, Modifier.weight(1f).fillMaxHeight()) { onTheme(t) } }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ThemeTile(t: Theme, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(39.dp)
    Box(
        modifier.shadow(24.dp, shape, ambientColor = Color(0x12004D4D), spotColor = Color(0x12004D4D))
            .clip(shape).background(Beff.BoxFill).border(4.8.dp, Beff.BoxBorder, shape).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(start = 40.dp, top = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 1113: 「더보기 >」 뺌(6-11). 제목 32 Bold(1/3 타일과 같음).
            Text(t.label, style = BeffType.Body32.copy(color = Beff.TextStrong, fontWeight = FontWeight.Bold, lineHeight = 42.sp), minLines = 2)
        }
        BeffAsset(t.illust, 170.dp, Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp))
    }
}

/**
 * B-2 · B-3 · B-4 홈 배너(765×154, #173535 r74): 제목 42 ExtraBold 흰색 + 아랫줄 32 #C9E1DD, 오른쪽 원 114.
 * 가는 중 = 남은 시간(민트 원) · 경유지에서 쉬는 중 = 회색 얼굴 · 도착 = 틸 웃는 얼굴. 누르면 여정 탭.
 */
@Composable
private fun HomeBanner(vm: AppViewModel, journey: com.beff.app.Journey, modifier: Modifier) {
    val stop = journey.stops.firstOrNull()
    val arrived = vm.nav.destinationArrived
    val resting = !arrived && stop != null && vm.nav.lastWaypointArrived != null && vm.vehicle.parked
    val (title, sub) = when {
        arrived -> "최종 목적지에 도착했어요" to "운전하느라 수고하셨어요"
        resting -> "${stop!!.name}에서 쉬는 중" to "다음 목적지 | ${journey.dest.name}"
        stop != null -> "${stop.name}${withRo(stop.name)} 가는 중" to "다음 목적지 | ${journey.dest.name}"
        else -> "${journey.dest.name}${withRo(journey.dest.name)} 가는 중" to "${accessPhrase(vm.active)}으로 가고 있어요"
    }
    val shape = RoundedCornerShape(74.dp)
    Box(
        modifier.padding(bottom = 8.dp).fillMaxWidth().height(154.dp)
            .shadow(23.dp, shape, ambientColor = Color(0x290F3633), spotColor = Color(0x290F3633))
            .clip(shape).background(Beff.Banner).clickable { vm.selectTab(Tab.Journey) }, // 다크 모드: 흰 배너(1113)
    ) {
        // 장식 원(Figma Ellipse 1: 지름 622, 선 #044846)
        Box(
            Modifier.padding(start = 187.dp).size(622.dp).offset(y = (-55).dp)
                .border(160.dp, Beff.BannerRing, CircleShape),
        )
        Row(
            Modifier.fillMaxSize().padding(start = 60.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = BeffType.Box40.copy(color = Beff.ToastText), maxLines = 1)
                Text(sub, style = BeffType.Body32.copy(color = Beff.ToastSub), maxLines = 1)
            }
            val remain = vm.nav.route?.remainSeconds ?: vm.nav.route?.totalSeconds
            when {
                arrived -> Box(Modifier.size(114.dp).clip(CircleShape).background(Beff.Teal), contentAlignment = Alignment.Center) {
                    BeffAsset("face/face_smile.svg", 88.dp, tint = Color.White)
                }
                resting -> Box(Modifier.size(114.dp).clip(CircleShape).background(Beff.TextMuted), contentAlignment = Alignment.Center) {
                    BeffAsset("face/face_spread.svg", 88.dp, tint = Color.White)
                }
                remain != null && remain > 0 -> Box(Modifier.size(114.dp).clip(CircleShape).background(Color(0xFFBFEBE3)), contentAlignment = Alignment.Center) {
                    Text(
                        fmtMinutes((remain + 59) / 60).replace(" ", "\n"),
                        style = BeffType.Caption28.copy(color = Color(0xFF173535), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 32.sp),
                    )
                }
            }
        }
    }
}

/** 03-1 위쪽(1113 B-1 1/3): 「어디로 갈까요?」 + 검색창. 「○○과 함께」 프로필 줄은 뺐다(6-6). */
@Composable
private fun HomeHeader(vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
        HomeTitle(vm)
        BigSearchBar(micEnabled = !vm.vehicle.drivingRestricted, onMic = vm::toggleVoiceSearch) { vm.selectTab(Tab.Search) }
    }
}

/** 03-1 3분할(840): 검색 · 테마 아래로 추천 카드와 근처 목록이 이어지고 화면 전체가 스크롤된다(1110 회의). */
@Composable
private fun HomeDefault(vm: AppViewModel) {
    HomeHeader(vm)
    Column(verticalArrangement = Arrangement.spacedBy(36.dp)) {
        ThemeCard(vm.places.themesFor(vm.active), compact = false) { t -> vm.openChipSearch(t.chip) }
        val recs = vm.recommendations()
        RecommendHero(vm, recs.take(HERO_COUNT), imageHeight = 360.dp)
        RecommendList(vm, recs.drop(HERO_COUNT))
    }
}

/**
 * 홈의 큰 검색 박스(03-1): 흰 바탕 + 틸 외곽선 4(INSIDE), 안내 글자 #4A545C.
 * 마이크 칸은 틸 바탕 + 흰 픽토(왼쪽), 검색 픽토는 틸(오른쪽). 마이크를 누르면 검색 탭에서 음성 인식을 시작한다(정차 중에만).
 */
@Composable
private fun BigSearchBar(micEnabled: Boolean, onMic: () -> Unit, onClick: () -> Unit) {
    // B-1 검색: 778×120 흰 바탕 · 틸 테두리 4 · r72 · 그림자 28, 마이크 원 80 · 안내 36 Regular #4A545C · 검색 픽토 48.
    val shape = RoundedCornerShape(72.dp)
    Row(
        Modifier.fillMaxWidth().height(120.dp)
            .shadow(14.dp, shape, ambientColor = Color(0x38004D4D), spotColor = Color(0x38004D4D))
            .clip(shape).background(Beff.Card).border(4.dp, Beff.Teal, shape).clickable(onClick = onClick)
            .padding(start = 20.dp, end = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier.size(80.dp).clip(CircleShape).background(Beff.Teal)
                .alpha(if (micEnabled) 1f else 0.4f).clickable(enabled = micEnabled, onClick = onMic),
            contentAlignment = Alignment.Center,
        ) { BeffAsset("pict/mic.svg", 48.dp, tint = Color.White) }
        Text("장소, 주소 검색", style = BeffType.Item36.copy(fontWeight = FontWeight.Normal, color = Beff.TextSecondary), modifier = Modifier.weight(1f), maxLines = 1)
        BeffAsset("pict/search.svg", 48.dp, tint = Beff.TealText)
    }
}

/**
 * "이런 곳은 어때요?" — 프로필 필터에 맞는 테마 3장(정책 56).
 * fill = 2/3 전체 화면: 박스가 열 높이를 채우고 카드 안 내용은 세로 가운데(아이콘 120).
 */
@Composable
fun ThemeCard(
    themes: List<Theme>, compact: Boolean,
    modifier: Modifier = Modifier, fill: Boolean = false, onMore: (() -> Unit)? = null, onTheme: (Theme) -> Unit,
) {
    BeffBox(modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(Modifier.padding(28.dp).then(if (fill) Modifier.fillMaxSize() else Modifier), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("이런 곳은 어때요?", style = BeffType.Box40.copy(color = Beff.TextPrimary))
                // 제목 오른쪽 알약(정책 75): 검색 탭에서 시설 칩 전체를 본다.
                if (onMore != null) SmallTagButton("더보기", onMore)
            }
            // 1113: 3분할(840)에서는 칸이 좁아 「장애인 화장실」이 글자 단위로 잘린다. 긴 줄은 띄어쓰기에서 나눠 단어가 깨지지 않게 하고, 칸 높이를 맞춘다.
            fun themeLabel(t: Theme) = if (fill) t.label else t.label.lines().joinToString("\n") { l -> if (l.length > 5 && ' ' in l) l.replace(' ', '\n') else l }
            val lineCount = themes.take(3).maxOfOrNull { themeLabel(it).lines().size } ?: 2
            Row(
                if (fill) Modifier.weight(1f).fillMaxWidth() else Modifier,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                themes.take(3).forEach { t ->
                    Column(
                        Modifier.weight(1f).then(if (fill) Modifier.fillMaxHeight() else Modifier)
                            .clip(RoundedCornerShape(32.dp)).background(Beff.Surface)
                            .clickable { onTheme(t) }
                            .padding(horizontal = 24.dp, vertical = if (compact) 20.dp else 24.dp),
                        verticalArrangement = if (fill) Arrangement.spacedBy(20.dp, Alignment.CenterVertically) else Arrangement.spacedBy(12.dp),
                    ) {
                        BeffAsset(t.illust, if (fill) 120.dp else if (compact) 72.dp else 96.dp)
                        Text(themeLabel(t), style = BeffType.Body32.copy(color = Beff.TextStrong, fontWeight = FontWeight.Bold, lineHeight = 42.sp), minLines = maxOf(2, lineCount))
                    }
                }
            }
        }
    }
}

/** 03-2 여정 중 다시 탔을 때. */
@Composable
private fun HomeOnTrip(vm: AppViewModel, dest: Place, stop: Place?) {
    TripHeader(vm, dest)
    TripStopBox(vm, stop, Modifier)
    TripArriveBox(vm, dest, Modifier)
}

/** 「○○과 함께」 + 「목적지 가는 중」 + 접근 안내 한 줄(정책 03-2). */
@Composable
private fun TripHeader(vm: AppViewModel, dest: Place) {
    Column {
        WhoHeader(vm.active, vm::openProfileSelect)
        DestinationTitle(dest.name, "가는 중")
        Column(Modifier.padding(start = 4.dp, top = 40.dp)) {
            BoldThenRegular(accessPhrase(vm.active), "으로 가고 있어요")
        }
    }
}

/**
 * 「가는 길에 쉬어갈 곳」 + [경유지 추가] [목적지 변경](1110 회의: 「수정」 대신 할 일을 바로 쓴다).
 * big = 2/3 전체 화면(열 높이를 채우고 휴게소 칸이 커짐).
 */
@Composable
private fun TripStopBox(vm: AppViewModel, stop: Place?, modifier: Modifier, big: Boolean = false) {
    BeffBox(modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(Modifier.padding(32.dp).then(if (big) Modifier.fillMaxSize() else Modifier), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("가는 길에 들를 만한 곳", style = BeffType.Box40.copy(color = Beff.TextPrimary))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 경유지 추가 = 여정 탭(쉬어갈 곳 추천 · 경유지로), 목적지 변경 = 검색 탭
                SmallTagButton("경유지 추가") { vm.selectTab(Tab.Journey) }
                SmallTagButton("목적지 변경") { vm.selectTab(Tab.Search) }
            }
            if (stop != null) {
                StopRow(stop, vm.places.facilitiesFor(stop, vm.active), big = big, modifier = if (big) Modifier.weight(1f) else Modifier)
                NaviStatusLine(vm.stopOnNavi(stop), vm.nav.sync?.takeIf { it.state == com.beff.app.nav.SyncState.NotSent || it.state == com.beff.app.nav.SyncState.Failed }?.message)
            } else {
                Text("아직 정한 쉴 곳이 없어요. 여정에서 추천을 받아 보세요", style = BeffType.Body32.copy(color = Beff.TextSecondary))
            }
        }
    }
}

/** 「도착하면 쓸 수 있어요」. big = 2/3: 시설 칸 2개를 세로로 쌓고 아이콘 200. */
@Composable
private fun TripArriveBox(vm: AppViewModel, dest: Place, modifier: Modifier, big: Boolean = false) {
    val items = vm.places.facilitiesFor(dest, vm.active).take(2)
    BeffBox(modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(Modifier.padding(32.dp).then(if (big) Modifier.fillMaxSize() else Modifier), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("도착하면 쓸 수 있어요", style = BeffType.Box40.copy(color = Beff.TextPrimary))
            if (big) {
                Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    items.forEach { label ->
                        Column(
                            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Beff.Surface),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
                        ) {
                            BeffAsset(facilityIllust(label), 200.dp)
                            Text(label, style = BeffType.Body32.copy(color = Beff.TextStrong, fontWeight = FontWeight.Bold), maxLines = 1)
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items.forEach { FacilityTile(it, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun StopRow(stop: Place, facilities: List<String>, big: Boolean = false, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(32.dp)
    val tile = if (big) 200.dp else 104.dp
    Row(
        modifier.fillMaxWidth().heightIn(min = 160.dp).clip(shape).background(Beff.TealTint)
            .border(2.dp, Color(0x59008E8C), shape)
            .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.size(tile).clip(RoundedCornerShape(tile * 0.27f)).background(Beff.Card), contentAlignment = Alignment.Center) {
            BeffAsset(stop.kind.illust, tile - if (big) 31.dp else 16.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stop.name, style = BeffType.Item36Semi.copy(color = Beff.TextStrong), maxLines = 1)
            FacilityChipsRow((facilities.take(1) + listOfNotNull(stop.note)).take(2))
        }
        BeffAsset("pict/chevron_right.svg", 48.dp)
    }
}
