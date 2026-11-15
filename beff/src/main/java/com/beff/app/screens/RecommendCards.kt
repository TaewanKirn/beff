package com.beff.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.beff.app.AppViewModel
import com.beff.app.data.Place
import com.beff.app.data.Recommendation
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffBox
import com.beff.app.ui.BeffType
import com.beff.app.ui.HeaderPill
import com.beff.app.ui.PageDots
import com.beff.app.ui.RoundArrow
import com.beff.app.ui.facilityIllust

/**
 * 03-1 · 04-4 오른쪽 「추천」 카드(Figma 「이번 주 기획」). 칩은 인기 지표(방문자 수) 대신 추천 이유를 쓴다.
 * 사진은 한국관광공사 대표 이미지(place.image), 없으면 시설 일러스트. [imageHeight]가 null이면 남은 높이를 사진이 채운다(홈).
 */
@Composable
fun RecommendHero(vm: AppViewModel, recs: List<Recommendation>, modifier: Modifier = Modifier, imageHeight: Dp? = null) {
    if (recs.isEmpty()) return
    RequestTrips(vm, recs.map { it.place })
    var index by remember(recs) { mutableIntStateOf(0) }
    val r = recs[index.coerceIn(recs.indices)]
    val open = { vm.openBriefing(r.place.id) }
    BeffBox(modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(
            Modifier.fillMaxWidth().clickable(onClick = open).padding(horizontal = 36.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ReasonChip(r.reason)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(r.place.name, style = BeffType.Box40.copy(color = Beff.TextPrimary), maxLines = 1)
                Text(subtitle(vm, r), style = BeffType.Body32.copy(color = Beff.TextSecondary), maxLines = 1)
            }
        }
        Box(
            Modifier.fillMaxWidth()
                .then(if (imageHeight != null) Modifier.height(imageHeight) else Modifier.weight(1f))
                .background(Beff.SecondaryBtn)
                .clickable(onClick = open),
        ) {
            PlacePhoto(r.place, Modifier.fillMaxSize(), illustSize = 160.dp)
            if (recs.size > 1) {
                RoundArrow(false, { index = (index - 1 + recs.size) % recs.size }, Modifier.align(Alignment.CenterStart).padding(start = 24.dp), onWhite = true)
                RoundArrow(true, { index = (index + 1) % recs.size }, Modifier.align(Alignment.CenterEnd).padding(end = 24.dp), onWhite = true)
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                        .clip(RoundedCornerShape(100.dp)).background(Color(0x73131417))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) { PageDots(recs.size, index, onDark = true) }
            }
        }
    }
}

/** 04-4 오른쪽 아래 「근처 함께 가기 좋은 곳」 목록(Figma 「코스」). 처음 3곳, 더보기로 나머지를 펼친다. */
@Composable
fun RecommendList(vm: AppViewModel, recs: List<Recommendation>, modifier: Modifier = Modifier) {
    if (recs.isEmpty()) return
    RequestTrips(vm, recs.map { it.place })
    val more = false // 1113: 「더보기」 뺌(6-11)
    BeffBox(modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(Modifier.padding(top = 32.dp, bottom = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("근처 함께 가기 좋은 곳", style = BeffType.Box40.copy(color = Beff.TextPrimary), modifier = Modifier.weight(1f), maxLines = 1)
            }
            recs.take(if (more) recs.size else LIST_FIRST).forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(Beff.Stroke))
                Row(
                    Modifier.fillMaxWidth().clickable { vm.openBriefing(r.place.id) }.padding(horizontal = 32.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(Beff.Surface), contentAlignment = Alignment.Center) {
                        PlacePhoto(r.place, Modifier.fillMaxSize(), illustSize = 72.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(r.place.name, style = BeffType.Item36Semi.copy(color = Beff.TextPrimary), maxLines = 1)
                        Text(subtitle(vm, r), style = BeffType.Caption28.copy(color = Beff.TextMuted), maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * B-1 오른쪽 위 추천 카드(Figma 「관광지 추천 카드」 725×520, r43): 사진 395 + 아래 띠 125.
 * 사진 오른쪽 위 시설 칩(최대 2), 사진 아래쪽 그라데이션 위에 한 줄 정보(28 #D9EEDC)와 장소 이름(48 ExtraBold 흰색).
 * 아래 띠는 흰 그라데이션 + 「상세보기 >」 36 Bold. 카드를 누르면 출발 브리핑.
 */
@Composable
fun HomeHeroCard(vm: AppViewModel, r: Recommendation, modifier: Modifier = Modifier) {
    RequestTrips(vm, listOf(r.place))
    val shape = RoundedCornerShape(43.dp)
    val open = { vm.openBriefing(r.place.id) }
    Column(
        modifier.fillMaxWidth().height(520.dp)
            .shadow(17.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
            .clip(shape).background(Beff.Card).border(2.dp, Beff.Stroke, shape).clickable(onClick = open),
    ) {
        Box(Modifier.fillMaxWidth().height(395.dp).background(Beff.SecondaryBtn)) {
            PlacePhoto(r.place, Modifier.fillMaxSize(), illustSize = 160.dp)
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
                    .background(Brush.verticalGradient(listOf(Color(0x00000000), Color(0x8C000000), Color(0xCC000000)))),
            )
            Row(Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 32.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                vm.places.facilitiesFor(r.place, vm.active).take(2).forEach { PhotoChip(it) }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 36.dp, end = 36.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(subtitle(vm, r), style = BeffType.Caption28.copy(color = Color(0xFFD9EEDC)), maxLines = 1)
                Text(
                    r.place.name,
                    style = BeffType.Title48.copy(color = Color.White, fontWeight = FontWeight.ExtraBold, lineHeight = 64.sp),
                    maxLines = 2,
                )
            }
        }
        Row(
            Modifier.fillMaxSize()
                .background(if (Beff.dark) Brush.verticalGradient(listOf(Beff.Card, Beff.Card)) else Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x99FFFFFF))))
                .padding(start = 43.dp, end = 43.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("상세보기", style = BeffType.Item36Semi.copy(color = Beff.TextStrong, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            BeffAsset("pict/chevron_right.svg", 48.dp)
        }
    }
}

/** 사진 위 칩(Figma 「Beff / Chip」): #F2F7F5 바탕 · #DAE7E2 테두리 2 · 높이 52 · 글자 28 SemiBold #006D68. */
@Composable
private fun PhotoChip(text: String) {
    val shape = RoundedCornerShape(100.dp)
    Box(
        Modifier.height(52.dp).clip(shape).background(Beff.Surface).border(2.dp, Beff.Stroke, shape).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BeffType.Caption28.copy(color = Beff.ChipText, fontWeight = FontWeight.SemiBold), maxLines = 1) }
}

/**
 * B-1 오른쪽 아래 「근처 함께 가기 좋은 곳」(Figma 「코스」 r36, 흰 90%): 제목 40 Bold + 「더보기 →」 32 SemiBold,
 * 한 줄 128(썸네일 88 r24 · 이름 36 SemiBold · 한 줄 정보 28 #66727B), 구분선 2 #E8ECEE.
 * 처음 3곳, 더보기를 누르면 박스 안에서 나머지를 스크롤로 본다.
 */
@Composable
fun HomeCourseList(vm: AppViewModel, recs: List<Recommendation>, modifier: Modifier = Modifier) {
    if (recs.isEmpty()) return
    // 1113: 「더보기」 뺌(6-11) — 목록은 박스 안에서 스크롤한다.
    val shown = recs
    RequestTrips(vm, shown.map { it.place })
    val shape = RoundedCornerShape(36.dp)
    // 1113: 오른쪽 패널(흰색) 위에서 박스 경계가 사라져 테두리를 회색 선(Stroke)으로. 위 추천 카드도 같다.
    Column(modifier.fillMaxWidth().clip(shape).background(Beff.BoxFill).border(2.dp, Beff.Stroke, shape)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, top = 34.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("근처 함께 가기 좋은 곳", style = BeffType.Box40.copy(color = Beff.TextPrimary, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f), maxLines = 1)
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            shown.forEachIndexed { i, r ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(Beff.Stroke))
                Row(
                    Modifier.fillMaxWidth().height(128.dp).clickable { vm.openBriefing(r.place.id) }.padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(Beff.Surface), contentAlignment = Alignment.Center) {
                        PlacePhoto(r.place, Modifier.fillMaxSize(), illustSize = 72.dp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(r.place.name, style = BeffType.Item36Semi.copy(color = Beff.TextPrimary, lineHeight = 48.sp), maxLines = 1)
                        Text(subtitle(vm, r), style = BeffType.Caption28.copy(color = Beff.TextMuted, lineHeight = 37.sp), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** 추천 카드(캐러셀)에 넣는 곳 수. 나머지는 목록으로 간다. */
const val HERO_COUNT = 3
private const val LIST_FIRST = 3

/** 칩(Beff / Chip): 옅은 틸 바탕 + 진한 틸 32 Regular. */
@Composable
private fun ReasonChip(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(100.dp)).background(Beff.TealSoft).padding(horizontal = 20.dp, vertical = 8.dp),
    ) { Text(text, style = BeffType.Body32.copy(color = Beff.TealText), maxLines = 1) }
}

/** 장소 사진. 주소가 없거나 받지 못하면 시설 일러스트(첫 시설, 없으면 장소 종류)를 가운데에 둔다. */
@Composable
internal fun PlacePhoto(place: Place, modifier: Modifier, illustSize: Dp, onShown: () -> Unit = {}) {
    val fallback: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(Beff.Surface), contentAlignment = Alignment.Center) {
            BeffAsset(place.facilities.firstOrNull()?.let { facilityIllust(it) } ?: place.kind.illust, illustSize)
        }
    }
    if (place.image == null) { Box(modifier) { fallback() }; return }
    val ctx = LocalContext.current
    // 위키미디어 사진 서버는 앱을 밝히는 User-Agent가 없으면 막는다(이용 정책).
    val request = remember(place.image) {
        ImageRequest.Builder(ctx).data(place.image).addHeader("User-Agent", PHOTO_USER_AGENT).crossfade(true).build()
    }
    // 리뷰 5: 다크 모드에선 사진을 25% 어둡게(막).
    Box(modifier) {
        SubcomposeAsyncImage(
            model = request,
            contentDescription = place.name,
            contentScale = ContentScale.Crop,
            loading = { fallback() },
            error = { fallback() },
            onSuccess = { onShown() },
            modifier = Modifier.fillMaxSize(),
        )
        if (Beff.dark) Box(Modifier.fillMaxSize().background(Beff.PhotoScrim))
    }
}

private const val PHOTO_USER_AGENT = "BeffApp/0.1 (Pleos Connect accessibility trip app)"

/** "관광지 · 서울 종로구 · 18분 · 12.3km" — 내비가 알려 준 도로 거리·시간이 있으면 그것, 없으면 "직선 3.2km". */
private fun subtitle(vm: AppViewModel, r: Recommendation): String = listOfNotNull(
    r.place.kindLabel.ifBlank { null }, r.place.regionLabel.ifBlank { null },
    tripText(vm, r.place, r.km),
).joinToString(" · ")

/** 카드·목록에 보일 장소 수만큼 내비에 도로 거리·시간을 묻는다(줄을 세워 하나씩, 연결이 되면 다시 시도). */
@Composable
internal fun RequestTrips(vm: AppViewModel, places: List<Place>) {
    val ids = places.joinToString { it.id }
    LaunchedEffect(ids, vm.nav.link) {
        if (vm.nav.link == com.beff.app.nav.NaviLink.Connected) places.forEach { vm.nav.requestTripTo(it) }
    }
}

/** 도로 기준 "18분 · 12.3km"(내비 값, 분은 올림). 내비 값이 없으면 "직선 3.2km"(근사). 좌표가 없으면 null. */
internal fun tripText(vm: AppViewModel, p: Place, straightKm: Double? = null): String? {
    vm.nav.trips[p.id]?.let { t ->
        return "${fmtMinutes((t.seconds + 59) / 60)} · ${String.format("%.1fkm", t.meters / 1000.0)}"
    }
    val km = straightKm ?: if (p.lat != null && p.lng != null)
        com.beff.app.data.distanceKm(vm.origin, com.beff.app.data.GeoPoint(p.lat, p.lng)) else null
    return km?.let { String.format("직선 %.1fkm", it) }
}
