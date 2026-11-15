package com.beff.app.screens

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.viewinterop.AndroidView
import com.beff.app.AppViewModel
import com.beff.app.data.FacilityKind
import com.beff.app.data.FacilityMap
import com.beff.app.data.FacilityPlan
import com.beff.app.data.GeoPoint
import com.beff.app.data.KakaoMapPage
import com.beff.app.data.OsmState
import com.beff.app.data.Place
import com.beff.app.data.facilityNeeds
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffBox
import com.beff.app.ui.BeffType
import com.beff.app.ui.TealCardBg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val CautionOrange = Color(0xFFE8590C)

/**
 * 1110 도착 화면 지도(G-2 · G-1 · D-5 오른쪽 패널): 「주차장에서 ○○까지」 제목 + 보행로 · 직선 거리, 카카오맵(시설 POI), 아래 시설 타일 2열.
 * 타일을 누르면 그 마커가 커지고 틸로 바뀌며 보행로를 따라 경로선을 그리고 가운데로 맞춘다. 보행로로 이을 수 없으면 선은 긋지 않는다(직선 금지).
 * 위치 · 보행로는 OpenStreetMap, 화장실 · 주차장 위치는 카카오 장소 검색, 지도 배경은 카카오맵. 실내 층 이동 · 노면은 판단하지 않는다 [미확인].
 * 상자 없이 제목부터 그린다(호출하는 쪽이 흰 패널 · 스크롤을 정한다).
 */
@Composable
fun FacilityMapCard(vm: AppViewModel, place: Place, modifier: Modifier = Modifier, mapHeight: Dp = 420.dp, boxed: Boolean = false, tileColumns: Int = 2) {
    val needs = remember(vm.active) { facilityNeeds(vm.active).filterNot { it.caution } }
    var tried by remember(place.id) { mutableStateOf(false) }
    val plan by produceState<FacilityPlan?>(null, place.id) {
        val car = vm.location.location?.let { GeoPoint(it.latitude, it.longitude) }
        val parking = vm.places.parkingNear(place, radiusKm = 0.6, limit = 5)
        // 1) 카카오 화장실 · 주차장을 먼저 띄우고 2) OSM 보행로 · 계단 · 경사가 오면 경로선을 더한다.
        val kakao = withContext(Dispatchers.IO) { FacilityMap.kakaoPois(place) }
        value = withContext(Dispatchers.IO) { FacilityMap.plan(place, parking, car, kakao, null, OsmState.Loading) }
        tried = true
        // 미리 받아 둔 OSM 보행로(assets/osm, 지금은 서울숲)가 있으면 서버를 기다리지 않고 그것을 쓴다.
        val bundled = vm.places.walkMap(place)?.let { m ->
            m.toilets.filter { it.access != com.beff.app.data.ENTRANCE_TARGET }
                .map { com.beff.app.data.Poi(FacilityKind.Toilet, GeoPoint(it.lat, it.lng), it.name, null) } to m.ways
        }
        // 사람이 항공사진으로 골라 둔 장애인 주차 칸(서울숲 K8 등)이 있으면 그 칸을 출발점으로.
        val fixed = vm.places.walkMap(place)?.start?.let { GeoPoint(it.lat, it.lng) to it.label }
        val osm = bundled ?: withContext(Dispatchers.IO) { FacilityMap.osm(place) }
        value = withContext(Dispatchers.IO) {
            FacilityMap.plan(place, parking, car, kakao, osm, if (osm != null) OsmState.Ok else OsmState.Failed, fixed)
        }
    }
    Column(modifier.fillMaxWidth()) {
        val p = plan
        if (p == null) {
            MapCardFrame(boxed) {
                Text("주차장에서 필요한 시설까지", style = BeffType.Box40.copy(color = Beff.TextPrimary, lineHeight = 54.sp), maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (!tried) "주변 시설 위치와 보행로를 불러오는 중이에요…" else "주변 시설 정보를 받지 못했어요(인터넷 확인 필요)",
                    style = BeffType.Body32.copy(color = Beff.TextSecondary, lineHeight = 43.sp),
                )
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth().height(mapHeight).clip(RoundedCornerShape(36.dp)).background(MapBg))
            }
            Spacer(Modifier.height(24.dp))
            FacilityGrid(needs, null, vm.active, columns = tileColumns) {}
            return@Column
        }
        FacilityMapBody(vm, place, p, needs, mapHeight, boxed, tileColumns)
    }
}

/** 1113: 도착 화면(G-1 · G-2 · D-5)은 Figma처럼 제목 · 거리 · 지도를 흰 카드 하나로 묶는다. 장소 상세는 묶지 않는다. */
@Composable
private fun MapCardFrame(boxed: Boolean, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    if (!boxed) { Column(Modifier.fillMaxWidth(), content = content); return }
    com.beff.app.ui.BeffBox(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start, stroke = Beff.Stroke) {
        Column(Modifier.fillMaxWidth().padding(32.dp), content = content)
    }
}

/** 지도 바탕색(Figma #E3EFE1). 지도를 받기 전 · 못 받았을 때 보인다. */
private val MapBg = Color(0xFFE3EFE1)

/** 타일 · 마커 이름. 휠체어와 함께면 화장실은 「장애인 화장실」, 주차는 늘 「장애인 주차구역」. */
private fun tileLabel(k: FacilityKind, profile: com.beff.app.data.Profile?): String = when (k) {
    FacilityKind.Toilet -> if ((profile?.counts?.get(com.beff.app.data.Mobility.Wheelchair) ?: 0) > 0) "장애인 화장실" else "화장실"
    FacilityKind.Parking -> "장애인 주차구역"
    FacilityKind.Rest -> "쉼터"
    else -> k.label
}

@Composable
private fun FacilityMapBody(vm: AppViewModel, place: Place, p: FacilityPlan, needs: List<FacilityKind>, mapHeight: Dp, boxed: Boolean = false, columns: Int = 2) {
    var state by remember(p) { mutableStateOf(MapState.Loading) }
    var web by remember(p) { mutableStateOf<WebView?>(null) }
    var sky by remember { mutableStateOf(false) }
    var selected by remember(p) { mutableStateOf<FacilityKind?>(null) }
    val ctx = LocalContext.current

    fun select(k: FacilityKind?, speak: Boolean) {
        selected = k
        web?.evaluateJavascript(if (k == null) "resetFocus()" else "focusK('${k.key}')", null)
        val r = k?.let { p.routes[it] } ?: return
        if (speak) vm.voice.say(
            "${tileLabel(k, vm.active)}까지 걸어서 약 ${r.minutes}분, ${r.meters}미터예요" +
                (if (r.cautions.any { it.kind == FacilityKind.Slope }) ". 가는 길에 경사가 있어요" else ""),
        )
    }

    LaunchedEffect(p) {
        delay(LOAD_TIMEOUT_MS)
        if (state == MapState.Loading) state = MapState.Failed
    }
    // 처음에는 길이 있는 가장 가까운 시설(주차 제외)을 골라 둔다. 지도가 뜨면 그 마커를 강조한다.
    LaunchedEffect(p) {
        if (selected == null) {
            selected = needs.filter { it != FacilityKind.Parking && p.routes[it]?.target?.assumed == false }.minByOrNull { p.routes[it]!!.meters }
                ?: needs.firstOrNull { k -> k != FacilityKind.Parking && p.pois.any { it.kind == k && !it.assumed } }
        }
    }
    LaunchedEffect(state) { if (state == MapState.Ready) selected?.let { select(it, speak = false) } }

    // 제목: 「주차장에서 장애인 화장실까지」 40 Bold + 「보행로 기준 약 56m · 직선 37m」 32
    val k = selected
    val from = if (p.startLabel.startsWith("지금")) "지금 위치에서" else "주차장에서"
    MapCardFrame(boxed) {
        Text(
            when {
                k == null -> "$from 필요한 시설까지"
                // 1113: 주차장에서 출발할 때 주차 칸을 고르면 길이 아니라 출발한 주차장 위치를 보여 준다.
                k == FacilityKind.Parking && p.routes[k] == null && from == "주차장에서" -> "출발하는 주차장 위치"
                !hasLocation(p, k) -> "${tileLabel(k, vm.active)} 위치는 지도에 없어요"
                else -> "$from ${tileLabel(k, vm.active)}까지"
            },
            style = BeffType.Box40.copy(color = Beff.TextPrimary, lineHeight = 54.sp), maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (k == null) "아래에서 시설을 고르면 지도에 길을 보여 드려요" else statusOf(place, p, k),
            style = BeffType.Body32.copy(color = Beff.TextSecondary, lineHeight = 43.sp, letterSpacing = 0.sp), maxLines = 2,
        )
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(mapHeight).clip(RoundedCornerShape(36.dp)).background(MapBg)) {
            if (state == MapState.Loading || state == MapState.Ready) {
                // 보행로가 도착해 plan이 바뀌면 WebView를 새로 만든다(페이지 데이터가 바뀌므로).
                androidx.compose.runtime.key(p) {
                    FacilityWebMap(
                        html = remember(p) { poiStyled(ctx, com.beff.app.data.StyledMapPage.facilityHtml(p), needs.associate { it.key to tileLabel(it, vm.active) }) },
                        sky = sky,
                        onReady = { if (state == MapState.Loading) state = MapState.Ready },
                        onError = { state = MapState.Failed },
                        onCreated = { web = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (state == MapState.Failed || state == MapState.Off) {
                Text(
                    if (state == MapState.Off) "카카오맵 키가 없어 지도를 띄우지 못했어요" else "지도를 불러오지 못했어요",
                    style = BeffType.Body32.copy(color = Beff.TextSecondary), modifier = Modifier.align(Alignment.Center),
                )
            }
            // 1113: 위치가 없는 시설을 고르면 지도 위에 크게 알린다(지도는 처음 범위 그대로).
            if (state == MapState.Ready && k != null && !hasLocation(p, k)) {
                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 48.dp).clip(RoundedCornerShape(28.dp))
                        .background(Color(0xE6252B31)).padding(horizontal = 36.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${tileLabel(k, vm.active)} 위치 정보가 없어요", style = BeffType.Item36Semi.copy(color = Color.White), maxLines = 1)
                    Text("현장 안내판이나 직원에게 물어봐 주세요", style = BeffType.Caption28.copy(color = Color(0xFFDDE3E6)), maxLines = 1)
                }
            }
            if (state == MapState.Ready) {
                MapTypeToggle(sky, { sky = it }, Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 24.dp))
                MapZoom(web, Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp))
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    // 1113: 칸을 누를 때 음성 안내는 하지 않는다(길이 있는 칸만 말해 들쭉날쭉했음). 거리 · 시간은 제목 아래 글로.
    FacilityGrid(needs, selected, vm.active, available = { hasLocation(p, it) }, columns = columns) { t -> select(if (selected == t) null else t, speak = false) }
    Spacer(Modifier.height(20.dp))
    // 출처(카카오 · OpenStreetMap)는 설정 「저작권 · 출처」에 둔다. 화면에는 길을 못 그렸을 때만 짧게 알린다.
    if (p.osm == OsmState.Failed) {
        Text("걷는 길 정보를 받지 못해 길은 그리지 않았어요", style = BeffType.Caption28.copy(color = Beff.TextSecondary))
    }
}

/** 1113: 지도에 이 시설 위치가 있는지(대표 좌표로 만든 가짜 입구는 빼고). 기저귀 교환대 · 수유실은 화장실 안이라 화장실이 있으면 있음. */
private fun hasLocation(p: FacilityPlan, k: FacilityKind): Boolean =
    p.routes[k] != null || p.pois.any { it.kind == k && !it.assumed } ||
        ((k == FacilityKind.Changing || k == FacilityKind.Nursing) && p.pois.any { it.kind == FacilityKind.Toilet && !it.assumed })

/** 고른 시설의 한 줄: 보행로 거리 · 직선 거리 · 주의(경사 · 계단). 길이 없으면 지도에 있는지, 원문이 있는지. */
private fun statusOf(place: Place, p: FacilityPlan, k: FacilityKind): String {
    val count = p.pois.count { it.kind == k }
    val r = p.routes[k]
    val raw = k.accessKeys.firstNotNullOfOrNull { place.access[it] }
    return when {
        r != null -> buildString {
            append("걸어서 약 ${r.minutes}분 · ${r.meters}m")
            val slopes = r.cautions.count { it.kind == FacilityKind.Slope }
            val stairs = r.cautions.count { it.kind == FacilityKind.Stairs }
            if (slopes > 0) append(" · 경사 $slopes")
            if (stairs > 0) append(" · 근처 계단 $stairs")
        }
        k == FacilityKind.Parking && count > 0 -> "파란 점이 출발점이에요 · 근처 주차장 ${count}곳"
        count > 0 -> {
            // 1113: 걷는 길을 못 받았을 때도 가장 가까운 곳까지 직선 거리는 알린다.
            val m = p.pois.filter { it.kind == k }.minOf { com.beff.app.data.distanceKm(p.start, it.point) * 1000 }.toInt()
            "직선 약 ${m}m" + (if (count > 1) " · 근처 ${count}곳" else "") + " · 걷는 길 정보 없음"
        }
        raw != null -> raw
        else -> "관광공사 자료에도 위치 설명이 없어요"
    }
}

/** 시설 타일 2열(Figma 「시설 목록」): 104 높이 r32, 그림 64 + 이름 30 SemiBold, 간격 16. 선택 = #D6ECEB + 틸 테두리 3. */
@Composable
private fun FacilityGrid(
    needs: List<FacilityKind>, selected: FacilityKind?, profile: com.beff.app.data.Profile?,
    available: (FacilityKind) -> Boolean = { true }, columns: Int = 2, onClick: (FacilityKind) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        needs.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { k -> FacilityTile(k, tileLabel(k, profile), selected == k, Modifier.weight(1f), available(k)) { onClick(k) } }
                if (row.size < columns) Spacer(Modifier.weight((columns - row.size).toFloat()))
            }
        }
    }
}

@Composable
private fun FacilityTile(k: FacilityKind, label: String, on: Boolean, modifier: Modifier, available: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        // 1113: 위치가 없는 시설은 흐리게 + 「위치 정보 없음」. 누르면 지도 위에 안내만 띄운다.
        modifier.height(104.dp).alpha(if (available || on) 1f else 0.45f).clip(shape)
            .background(if (on) com.beff.app.ui.UnloadColor.SummaryFill else Beff.Card)
            // 1113: 버튼으로 읽히게 고르지 않은 칸에도 테두리를 둔다. 고른 칸은 틸 3.
            .border(if (on) 3.dp else 2.dp, if (on) Beff.TealMain else Beff.Stroke, shape)
            .clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PoiBadge(k, on && available)
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = BeffType.Small30.copy(lineHeight = 40.sp, color = if (on) com.beff.app.ui.UnloadColor.TealDark else Beff.TextStrong),
                maxLines = 1,
            )
            if (!available) Text("위치 정보 없음", style = BeffType.Caption28.copy(fontSize = 24.sp, lineHeight = 32.sp, color = Beff.TextSecondary), maxLines = 1)
        }
    }
}

/** 1113: 칸의 그림을 약도 마커와 같은 기호로 — 흰 원 + 틸 테두리 3 + 틸 선 아이콘, 고르면 틸 원 + 흰 테두리 + 흰 아이콘(poiCss와 같음). */
@Composable
private fun PoiBadge(k: FacilityKind, on: Boolean) {
    val teal = Color(0xFF00837F)
    val icon = POI_ICON[k.key]
    if (icon == null) { BeffAsset(k.illust, 64.dp); return }
    Box(
        Modifier.size(64.dp).clip(CircleShape).background(if (on) teal else Color.White)
            .border(3.dp, if (on) Color.White else teal, CircleShape),
        contentAlignment = Alignment.Center,
    ) { BeffAsset(icon, 36.dp, tint = if (on) Color.White else teal) }
}

/** 지도 종류 알약(Figma): 흰 262×60 r100 안에 123×48 칸, 선택 칸 틸 + 흰 글 24 SemiBold. */
@Composable
private fun MapTypeToggle(sky: Boolean, onSky: (Boolean) -> Unit, modifier: Modifier) {
    // 리뷰 2: 토글 높이 76(칸 64).
    Row(modifier.height(76.dp).clip(RoundedCornerShape(100.dp)).background(Beff.Card).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf("일반지도" to false, "스카이뷰" to true).forEach { (t, v) ->
            val on = sky == v
            Box(
                Modifier.width(140.dp).height(64.dp).clip(RoundedCornerShape(100.dp)).background(if (on) Beff.Teal else Color.Transparent).clickable { onSky(v) },
                contentAlignment = Alignment.Center,
            ) { Text(t, style = BeffType.Caption28.copy(fontWeight = FontWeight.SemiBold, color = if (on) Color.White else Beff.TextSecondary), maxLines = 1) }
        }
    }
}

/** 확대 · 축소: 흰 알약 r16 두 칸(+ / −), 36 SemiBold. */
@Composable
private fun MapZoom(web: WebView?, modifier: Modifier) {
    Column(modifier.width(76.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 원위치: 끌거나 확대한 지도를 처음 범위로 되돌린다.
        Box(
            Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(16.dp)).background(Beff.Card)
                .clickable { web?.evaluateJavascript("window.recenter&&recenter()", null) },
            contentAlignment = Alignment.Center,
        ) { BeffAsset("pict/here.svg", 32.dp, tint = Beff.TextPrimary) }
        // 1113: 확대 · 축소 되살림(리뷰에서 고정 약도로 잘못 보고 뺐음). 터치 76dp(AAOS).
        MapZoomButtons(web)
    }
}

@Composable
private fun MapZoomButtons(web: WebView?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Beff.Card)) {
        listOf("+" to "zoom(-1)", "−" to "zoom(1)").forEach { (t, js) ->
            Box(Modifier.fillMaxWidth().height(76.dp).clickable { web?.evaluateJavascript(js, null) }, contentAlignment = Alignment.Center) {
                Text(t, style = BeffType.Item36Semi.copy(color = Beff.TextPrimary))
            }
        }
    }
}

private const val LOAD_TIMEOUT_MS = 10_000L

private enum class MapState { Loading, Ready, Failed, Off }

/** 마커 그림(Figma 「지도 POI 마커」 · BEFF Pict 선 아이콘). 시설 종류 key → assets 경로. */
private val POI_ICON = mapOf(
    "parking" to "pict/parking.svg", "entrance" to "pict/door.svg", "elevator" to "pict/elevator.svg", "ramp" to "pict/ramp.svg",
    "toilet" to "pict/toilet.svg", "changing" to "pict/stroller.svg", "nursing" to "pict/nursing.svg", "rest" to "pict/rest.svg",
)

private fun svgData(ctx: android.content.Context, path: String): String? = runCatching {
    val bytes = ctx.assets.open(path).use { it.readBytes() }
    "data:image/svg+xml;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
}.getOrNull()

/** 마커 CSS(Figma POI): 흰 원 + 틸 테두리 3 + 틸 선 아이콘, 선택 = 틸 원 84 + 흰 테두리 4 + 흰 아이콘 + 아래 이름 알약. */
private fun poiCss(ctx: android.content.Context): String = buildString {
    append(
        ".poi{width:44px;height:44px;box-sizing:border-box;border:3px solid #00837F;background:#fff;position:relative;box-shadow:0 3px 8px rgba(0,60,60,.22);font-size:0}" +
            ".poi i{display:block;width:24px;height:24px;background:#00837F;-webkit-mask-size:contain;-webkit-mask-repeat:no-repeat;-webkit-mask-position:center}" +
            ".poi.on{background:#00837F;border:3px solid #fff;box-shadow:0 4px 12px rgba(0,60,60,.35)}" +
            ".poi.on i{background:#fff}" +
            ".poi b{display:none}" +
            ".poi.on b{display:block;position:absolute;top:62px;left:50%;transform:translateX(-50%);white-space:nowrap;background:#00837F;color:#fff;font:700 20px sans-serif;line-height:28px;padding:4px 14px;border-radius:100px;box-shadow:0 4px 10px rgba(0,60,60,.3)}" +
            ".pk{width:56px;height:56px;box-sizing:border-box;border-radius:50%;border:3px solid #00837F;background:#fff;display:flex;align-items:center;justify-content:center;box-shadow:0 3px 8px rgba(0,60,60,.22);transition:all .2s}" +
            ".pk i{display:block;width:32px;height:32px;background:#00837F;-webkit-mask-size:contain;-webkit-mask-repeat:no-repeat;-webkit-mask-position:center}" +
            ".pk.on{background:#00837F;border:3px solid #fff}.pk.on i{background:#fff}" +
            ".poi,.pk,.dest{transform:scale(var(--inv,1))}" +
            ".dest{padding:8px 16px;border-radius:100px;background:#252B31;color:#fff;font:700 22px sans-serif;white-space:nowrap;box-shadow:0 3px 8px rgba(0,0,0,.3)}",
    )
    POI_ICON.forEach { (k, path) ->
        svgData(ctx, path)?.let { append(".poi[data-k=$k] i,.pk[data-k=$k] i{-webkit-mask-image:url($it)}") }
    }
}

/**
 * 카카오 시설 지도 페이지(KakaoMapPage.facilityHtml)에 1110 마커 모양을 덧입힌다. 페이지 자체(데이터 · 경로 계산)는 바꾸지 않는다.
 * init을 감싸 마커의 글리프를 지우고 아이콘(i) · 이름 알약(b)을 넣는다. focusK의 on/dim 클래스는 그대로 쓴다.
 */
private fun poiStyled(ctx: android.content.Context, html: String, labels: Map<String, String>): String {
    val lb = org.json.JSONObject(labels).toString()
    val js = "<script>var LB=$lb;(function(){var _i=init;init=function(){_i();M.forEach(function(m){if(m.c)return;" +
        "m.el.textContent='';m.el.setAttribute('data-k',m.k);m.el.appendChild(document.createElement('i'));" +
        "var b=document.createElement('b');b.textContent=LB[m.k]||'';m.el.appendChild(b)})}})();</script>"
    return html.replace("</style>", poiCss(ctx) + "</style>").replace("</body>", "$js</body>")
}

/**
 * 1110 G-5 근처 주차장 지도: 목적지 이름표 + 주차장 P 마커. sel(i)로 고른 주차장 마커를 키우고 가운데로 옮긴다.
 * 카카오맵 JS API(키 · 도메인 조건은 KakaoMapPage와 같다).
 */
@Composable
fun ParkingMap(dest: Place, spots: List<com.beff.app.data.ParkingSpot>, selected: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var state by remember(dest.id) { mutableStateOf(if (dest.lat != null) MapState.Loading else MapState.Off) }
    var web by remember(dest.id) { mutableStateOf<WebView?>(null) }
    var sky by remember { mutableStateOf(false) }
    LaunchedEffect(dest.id) {
        delay(LOAD_TIMEOUT_MS)
        if (state == MapState.Loading) state = MapState.Failed
    }
    LaunchedEffect(state, selected) { if (state == MapState.Ready) web?.evaluateJavascript("sel($selected)", null) }
    Box(modifier.clip(RoundedCornerShape(36.dp)).background(MapBg)) {
        if (state == MapState.Loading || state == MapState.Ready) {
            FacilityWebMap(
                // 1113: Figma식 지도(StyledMapPage). 카카오 페이지(parkingHtml)는 남겨 둔다.
                html = remember(dest.id, spots) {
                    com.beff.app.data.StyledMapPage.parkingHtml(dest.lat ?: 0.0, dest.lng ?: 0.0, dest.name, spots.map { it.lat to it.lng }, poiCss(ctx))
                },
                sky = sky,
                onReady = { if (state == MapState.Loading) state = MapState.Ready },
                onError = { state = MapState.Failed },
                onCreated = { web = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (state == MapState.Failed || state == MapState.Off) {
            Text(
                if (state == MapState.Off) "목적지 좌표가 없어 지도를 띄우지 못했어요" else "지도를 불러오지 못했어요",
                style = BeffType.Body32.copy(color = Beff.TextSecondary), modifier = Modifier.align(Alignment.Center),
            )
        }
        if (state == MapState.Ready) {
            MapTypeToggle(sky, { sky = it }, Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 24.dp))
            MapZoom(web, Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 24.dp))
        }
    }
}

private fun parkingHtml(ctx: android.content.Context, dest: Place, spots: List<com.beff.app.data.ParkingSpot>): String {
    val data = org.json.JSONObject()
        .put("dest", org.json.JSONArray().put(dest.lat ?: 0.0).put(dest.lng ?: 0.0))
        .put("name", dest.name)
        .put("spots", org.json.JSONArray().apply { spots.forEach { put(org.json.JSONArray().put(it.lat).put(it.lng)) } })
    return """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<style>html,body,#map{margin:0;padding:0;width:100%;height:100%;overflow:hidden}${poiCss(ctx)}${com.beff.app.data.MapTiles.CSS}</style></head><body>${com.beff.app.data.MapTiles.SVG}<div id="map"></div>
<script>
var D=${data}, map=null, P=[], extra=1, B=null;
function fail(m){try{Android.onError(String(m))}catch(e){}}
window.onerror=function(m){fail(m)};
function ll(p){return new kakao.maps.LatLng(p[0],p[1])}
function init(){
  map=new kakao.maps.Map(document.getElementById('map'),{center:ll(D.dest),level:4,draggable:true,zoomable:true,scrollwheel:false,disableDoubleClickZoom:false,keyboardShortcuts:false});
  var b=new kakao.maps.LatLngBounds(); b.extend(ll(D.dest));
  var de=document.createElement('div'); de.className='dest'; de.textContent=D.name;
  new kakao.maps.CustomOverlay({map:map,position:ll(D.dest),content:de,xAnchor:0.5,yAnchor:1.2,zIndex:5});
  D.spots.forEach(function(s){var el=document.createElement('div'); el.className='pk'; el.setAttribute('data-k','parking'); el.appendChild(document.createElement('i'));
    var ov=new kakao.maps.CustomOverlay({map:map,position:ll(s),content:el,xAnchor:0.5,yAnchor:0.5,zIndex:2}); P.push({el:el,ov:ov}); b.extend(ll(s))});
  B=b; map.setBounds(b,90,90,90,90);
  window.addEventListener('resize',function(){map.relayout(); map.setBounds(b,90,90,90,90)});
  try{Android.onReady()}catch(e){}
}
function sel(i){P.forEach(function(p,j){p.el.className='pk'+(i===j?' on':''); p.ov.setZIndex(i===j?6:2)}); if(P[i]&&map){map.panTo(P[i].ov.getPosition())}}
function recenter(){if(map&&B)map.setBounds(B,90,90,90,90)}
function zoom(d){if(!map)return; var l=map.getLevel()+d; if(l>=1&&l<=9)map.setLevel(l)}
function whenSized(cb){var el=document.getElementById('map'); if(el.clientWidth>0&&el.clientHeight>0) cb(); else setTimeout(function(){whenSized(cb)},50)}
function setType(t){${com.beff.app.data.MapTiles.SKY_TOGGLE}if(map)map.setMapTypeId(t==='sky'?kakao.maps.MapTypeId.HYBRID:kakao.maps.MapTypeId.ROADMAP)}
var s=document.createElement('script');
s.src='https://dapi.kakao.com/v2/maps/sdk.js?appkey=${com.beff.app.BuildConfig.KAKAO_JS_KEY}&autoload=false';
s.onerror=function(){fail('sdk load failed')};
s.onload=function(){if(!window.kakao||!kakao.maps||!kakao.maps.load){fail('sdk unavailable (key/domain)');return} kakao.maps.load(function(){whenSized(init)})};
document.head.appendChild(s);
</script></body></html>
""".trimIndent()
}

/** 카카오맵 시설 지도 WebView. 끌어서 이동 · 두 손가락으로 확대 · 축소(+/− 버튼도 그대로). 지도 위 손가락은 바깥 스크롤이 가져가지 않는다. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FacilityWebMap(
    html: String, sky: Boolean, onReady: () -> Unit, onError: () -> Unit, onCreated: (WebView) -> Unit, modifier: Modifier,
) {
    val ready by rememberUpdatedState(onReady)
    val error by rememberUpdatedState(onError)
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                // 크기를 지정하지 않으면 페이지 안 높이가 0으로 잡혀 지도가 안 보인다(2026-11-09 확인).
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(AColor.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                // 지도 위에서 시작한 끌기 · 핀치는 지도가 받는다(바깥 세로 스크롤 · 화면 전환이 가로채지 않게).
                setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
                val main = Handler(Looper.getMainLooper())
                addJavascriptInterface(object {
                    @JavascriptInterface fun onReady() { main.post { ready() } }
                    @JavascriptInterface fun onError(msg: String) { Log.w("FacilityWebMap", msg); main.post { error() } }
                }, "Android")
                loadDataWithBaseURL(KakaoMapPage.BASE_URL, html, "text/html", "utf-8", null)
                onCreated(this)
            }
        },
        update = { it.evaluateJavascript("window.setType&&setType('${if (sky) "sky" else "road"}')", null) },
        onRelease = { it.destroy() },
    )
}
