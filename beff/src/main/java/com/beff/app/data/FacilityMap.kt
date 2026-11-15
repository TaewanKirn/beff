package com.beff.app.data

import android.util.Log
import org.json.JSONObject

/**
 * 1110 도착 화면 지도: 「주차한 곳에서 필요한 시설까지 어떻게 가는지」.
 * 시설 위치(POI) · 보행로는 OpenStreetMap(Overpass, 실행 중 받음 · 저장 안 함). OSM에 없는 시설은 위치 없이 원문 문장만 보여 준다.
 * 경로는 OSM 보행로 위 최단 거리(계단 제외). 보행로로 이을 수 없으면 선을 긋지 않고 마커만 보여 준다(직선 금지).
 * 노면 · 경사 · 실내 층 이동은 판단하지 않는다 [미확인].
 */
enum class FacilityKind(val key: String, val label: String, val glyph: String, val illust: String, val accessKeys: List<String>) {
    Parking("parking", "장애인 주차", "P", "illust/parking.svg", listOf("parking")),
    Entrance("entrance", "입구", "🚪", "illust/door.svg", listOf("exit", "route")),
    Elevator("elevator", "엘리베이터", "🛗", "illust/elevator.svg", listOf("elevator")),
    Ramp("ramp", "경사로", "♿", "illust/ramp.svg", listOf("route", "exit")),
    Toilet("toilet", "화장실", "🚻", "illust/toilet.svg", listOf("restroom")),
    Changing("changing", "기저귀 교환대", "🍼", "illust/diaper.svg", listOf("infantsfamilyetc")),
    Nursing("nursing", "수유실", "🤱", "illust/nursing.svg", listOf("lactationroom")),
    Rest("rest", "쉼터 · 의자", "🪑", "illust/bench.svg", emptyList()),
    // 주의 표시(네이버 지도 도보 안내처럼 지도에 늘 깔아 둔다). 필요 시설 목록에는 넣지 않는다.
    Stairs("stairs", "계단", "⚠", "illust/stairs.svg", emptyList()),
    Slope("slope", "경사", "⛰", "illust/ramp.svg", emptyList());

    val caution: Boolean get() = this == Stairs || this == Slope
}

/** 오늘 함께 탄 사람(프로필)에게 필요한 시설만. 주차 · 입구는 항상, 나머지는 이동 수단 + 고른 칩으로 정한다. */
fun facilityNeeds(profile: Profile?): List<FacilityKind> {
    val s = mutableSetOf(FacilityKind.Parking, FacilityKind.Entrance)
    if (profile == null) s += FacilityKind.Toilet
    profile?.counts?.forEach { (m, n) ->
        if (n > 0) s += when (m) {
            Mobility.Wheelchair -> listOf(FacilityKind.Elevator, FacilityKind.Ramp, FacilityKind.Toilet)
            Mobility.Stroller -> listOf(FacilityKind.Elevator, FacilityKind.Toilet, FacilityKind.Changing, FacilityKind.Nursing)
            Mobility.Walker -> listOf(FacilityKind.Toilet, FacilityKind.Rest)
        }
    }
    profile?.filters?.forEach { CHIP_TO_KIND[it]?.let(s::add) }
    return FacilityKind.entries.filter { it in s }
}

private val CHIP_TO_KIND = mapOf(
    "장애인 주차" to FacilityKind.Parking, "엘리베이터" to FacilityKind.Elevator, "경사로" to FacilityKind.Ramp,
    "턱 없는 입구" to FacilityKind.Entrance, "장애인 화장실" to FacilityKind.Toilet, "가족 화장실" to FacilityKind.Toilet,
    "가까운 화장실" to FacilityKind.Toilet, "수유실" to FacilityKind.Nursing, "기저귀 교환대" to FacilityKind.Changing,
    "쉼터" to FacilityKind.Rest, "의자 있는 곳" to FacilityKind.Rest,
)

/** 지도 위 시설 하나. [note]는 OSM 태그에서 읽은 짧은 설명(휠체어 표시 등), [assumed]는 실제 위치가 아닌 대표 좌표. */
data class Poi(val kind: FacilityKind, val point: GeoPoint, val name: String?, val note: String? = null, val assumed: Boolean = false)

/** 기준점 → 시설 경로. [direct]면 보행로가 아니라 직선. */
data class FacilityRoute(val target: Poi, val path: List<GeoPoint>, val meters: Int, val direct: Boolean, val cautions: List<Poi> = emptyList()) {
    /** 걸리는 시간(분). 휠체어 · 유모차 기준 분당 약 67m(시속 4km)로 셈한다 [가정]. */
    val minutes: Int get() = maxOf(1, (meters + 66) / 67)
}

/**
 * OSRM 도보 길찾기(FOSSGIS routing.openstreetmap.de, OSM 기반 · 키 없음). Overpass가 막혔을 때의 대안.
 * 결과는 (경로 꼭짓점, 거리 m). 실패하면 null. IO 스레드에서.
 */
object FootRoute {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<List<GeoPoint>, Int>>()
    fun route(a: GeoPoint, b: GeoPoint): Pair<List<GeoPoint>, Int>? {
        val key = "%.5f,%.5f;%.5f,%.5f".format(a.lng, a.lat, b.lng, b.lat)
        cache[key]?.let { return it }
        var conn: java.net.HttpURLConnection? = null
        return try {
            conn = (java.net.URL("https://routing.openstreetmap.de/routed-foot/route/v1/foot/$key?overview=full&geometries=geojson")
                .openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 6000; readTimeout = 8000
                setRequestProperty("User-Agent", "BeffApp/0.1 (Pleos Connect accessibility trip app)")
            }
            if (conn.responseCode != 200) { Log.w("FootRoute", "HTTP ${conn.responseCode}"); return null }
            val r = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONArray("routes").getJSONObject(0)
            val cs = r.getJSONObject("geometry").getJSONArray("coordinates")
            val path = (0 until cs.length()).map { i -> cs.getJSONArray(i).let { GeoPoint(it.getDouble(1), it.getDouble(0)) } }
            (path to r.getDouble("distance").toInt()).also { cache[key] = it }
        } catch (e: Exception) {
            Log.w("FootRoute", e.javaClass.simpleName); null
        } finally { conn?.disconnect() }
    }
}

/** 경로 꼭짓점에서 [m]미터 안에 있는 계단 · 경사 표시. */
private fun cautionsOn(path: List<GeoPoint>, pois: List<Poi>, m: Double = 20.0): List<Poi> =
    pois.filter { p -> p.kind.caution && path.any { distanceKm(it, p.point) * 1000 <= m } }

data class FacilityPlan(
    val start: GeoPoint,
    val startLabel: String,
    val pois: List<Poi>,
    val routes: Map<FacilityKind, FacilityRoute>,
    /** OSM 보행로 상태: 받는 중 · 받음 · 못 받음 */
    val osm: OsmState,
    val fetched: String,
) {
    val osmOk: Boolean get() = osm == OsmState.Ok
}

enum class OsmState { Loading, Ok, Failed }

object FacilityMap {
    private const val RADIUS_M = 500
    private const val FOOT_MAX_M = 1000
    private const val FOOT = "footway|path|pedestrian|track|service|living_street|residential|cycleway|unclassified|tertiary|steps"
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<List<Poi>, List<WalkWay>>>()
    private val kakaoCache = java.util.concurrent.ConcurrentHashMap<String, List<Poi>>()

    /**
     * 카카오 장소 검색으로 화장실 · 주차장 위치(네이버 · 카카오 지도에 나오는 그 POI). 빠르고 안정적이라 먼저 보여 준다.
     * 엘리베이터 · 수유실은 카카오에 위치 POI가 없다. IO 스레드에서.
     */
    fun kakaoPois(place: Place): List<Poi> {
        kakaoCache[place.id]?.let { return it }
        val c = GeoPoint(place.lat ?: return emptyList(), place.lng ?: return emptyList())
        val out = ArrayList<Poi>()
        fun add(q: String, cat: String, kind: FacilityKind) {
            KakaoLocal.keywordNear(q, cat, c, 700).orEmpty().forEach { k ->
                if (k.lat != null && k.lng != null) out += Poi(kind, GeoPoint(k.lat, k.lng), k.name, "카카오 장소")
            }
        }
        add("화장실", "화장실", FacilityKind.Toilet)
        add("주차장", "주차장", FacilityKind.Parking)
        Log.i("FacilityMap", "${place.id}: 카카오 화장실 ${out.count { it.kind == FacilityKind.Toilet }} · 주차장 ${out.count { it.kind == FacilityKind.Parking }}")
        return out.also { if (it.isNotEmpty()) kakaoCache[place.id] = it }
    }

    /** OSM 보행로 · 계단 · 경사 · 기타 시설. 서버가 자주 느려 따로 받는다. 실패하면 null. IO 스레드에서. */
    fun osm(place: Place): Pair<List<Poi>, List<WalkWay>>? {
        cache[place.id]?.let { return it }
        val c = GeoPoint(place.lat ?: return null, place.lng ?: return null)
        return fetch(c)?.also { cache[place.id] = it }
    }

    /**
     * 시설 지도 한 장(경로 계산이 있어 IO 스레드에서). [osm]이 null이면 보행로 없이 마커만.
     * 기준점: 차가 장소 300m 안이면 지금 차 위치, 아니면 장소에서 가장 가까운 주차장(장애인 칸 표시 우선), 그것도 없으면 장소 대표 좌표.
     */
    fun plan(
        place: Place, registeredParking: List<ParkingSpot>, car: GeoPoint?,
        kakao: List<Poi>, osm: Pair<List<Poi>, List<WalkWay>>?, osmState: OsmState,
        fixedStart: Pair<GeoPoint, String>? = null,
    ): FacilityPlan? {
        val lat = place.lat ?: return null
        val lng = place.lng ?: return null
        val center = GeoPoint(lat, lng)
        val ways = osm?.second.orEmpty()
        val pois = ArrayList<Poi>(kakao)
        // OSM 시설은 카카오에 이미 있는 것(같은 종류 25m 안)은 뺀다.
        osm?.first.orEmpty().forEach { o ->
            if (pois.none { it.kind == o.kind && distanceKm(it.point, o.point) * 1000 < 25 }) pois += o
        }
        registeredParking.forEach { pois += Poi(FacilityKind.Parking, GeoPoint(it.lat, it.lng), it.name, "장애인 전용주차구역 등록") }
        if (pois.none { it.kind == FacilityKind.Entrance }) {
            pois += Poi(FacilityKind.Entrance, center, "${place.name} 대표 지점", "실제 입구 위치는 아니에요", assumed = true)
        }

        // 기준점
        val parkings = pois.filter { it.kind == FacilityKind.Parking }
            .sortedWith(compareBy({ if (it.note?.contains("장애인") == true) 0 else 1 }, { distanceKm(center, it.point) }))
            .filter { distanceKm(center, it.point) <= 0.6 }
        val (start, startLabel) = when {
            fixedStart != null -> fixedStart
            car != null && distanceKm(center, car) <= 0.3 -> car to "지금 차 위치"
            parkings.isNotEmpty() -> parkings.first().let { it.point to "주차장(예시): ${it.name ?: "이름 없는 주차장"}" }
            else -> center to "장소 대표 좌표(예시)"
        }

        // 종류별로 기준점에서 가장 가까운(걸어서) 시설까지
        val routes = HashMap<FacilityKind, FacilityRoute>()
        val fromParking = startLabel.startsWith("주차장")
        for (kind in FacilityKind.entries.filterNot { it.caution }) {
            // 1113: 주차장에서 출발하면 「주차장 → 다른 주차장」 길은 뜻이 없다. 주차 칸은 출발점 표시만 한다.
            if (kind == FacilityKind.Parking && fromParking) continue
            // 대표 좌표(실제 입구 아님)로는 길을 긋지 않는다 — 공원 한가운데로 1km 넘는 길이 나올 수 있다.
            val cands = pois.filter { it.kind == kind && !it.assumed && distanceKm(start, it.point) * 1000 > 15 }
                .sortedBy { distanceKm(start, it.point) }.take(8)
            if (cands.isEmpty()) continue
            var best: FacilityRoute? = null
            if (ways.isNotEmpty()) {
                for (p in cands) {
                    val m = WalkMap(place.id, center, RADIUS_M, "", "", listOf(Toilet(p.point.lat, p.point.lng, p.name, null, null)), ways)
                    val r = planWalk(m, start) ?: continue
                    if (best == null || r.meters < best.meters) best = FacilityRoute(p, r.path, r.meters, direct = false)
                }
            }
            // 1113: OSM 보행로(Overpass)를 못 받았으면 OSRM 도보 길찾기(routing.openstreetmap.de, 키 없음)로 가까운 두 곳을 잇는다.
            if (best == null) {
                for (p in cands.take(2)) {
                    val r = FootRoute.route(start, p.point) ?: continue
                    // 장소 밖으로 크게 돌아가는 길(1km 넘음)은 쓰지 않는다.
                    if (r.second > FOOT_MAX_M) continue
                    // OSRM은 양 끝을 가까운 길에 붙여 돌려준다. 출발점과 시설 위치까지 이어 그려 선이 마커에 닿게 한다.
                    if (best == null || r.second < best.meters) best = FacilityRoute(p, listOf(start) + r.first + p.point, r.second, direct = false)
                }
            }
            // 그래도 이을 수 없으면 선을 긋지 않는다(직선은 실제로 걸을 수 있는 길이 아니다). 마커만 강조한다.
            val chosen = best ?: continue
            routes[kind] = chosen.copy(cautions = cautionsOn(chosen.path, pois))
        }
        return FacilityPlan(start, startLabel, pois, routes, osmState, java.time.LocalDate.now().toString())
    }

    private fun fetch(c: GeoPoint): Pair<List<Poi>, List<WalkWay>>? {
        val a = "(around:$RADIUS_M,${c.lat},${c.lng})"
        val q = """[out:json][timeout:25];
(
  nwr["amenity"="toilets"]$a;
  nwr["amenity"="parking"]$a;
  nwr["parking_space"="disabled"]$a;
  node["highway"="elevator"]$a;
  node["entrance"]$a;
  nwr["ramp:wheelchair"="yes"]$a;
  nwr["changing_table"="yes"]$a;
  nwr["amenity"~"^(bench|shelter)$"]$a;
);
out center tags;
way["highway"~"^($FOOT)$"]$a;
out tags geom;"""
        val deadline = System.currentTimeMillis() + 45_000
        for (ep in OsmLive.ENDPOINTS) {
            if (System.currentTimeMillis() > deadline) break
            val json = request(ep, q) ?: continue
            return parse(json, c)
        }
        return null
    }

    private fun request(endpoint: String, q: String): JSONObject? {
        var conn: java.net.HttpURLConnection? = null
        return try {
            conn = (java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 8000; readTimeout = 30000
                setRequestProperty("User-Agent", "BeffApp/0.1 (Pleos Connect accessibility trip app)")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            conn.outputStream.use { it.write(("data=" + java.net.URLEncoder.encode(q, "UTF-8")).toByteArray()) }
            if (conn.responseCode != 200) { Log.w("FacilityMap", "HTTP ${conn.responseCode} @ $endpoint"); return null }
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            Log.w("FacilityMap", "${e.javaClass.simpleName} @ $endpoint"); null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parse(json: JSONObject, c: GeoPoint): Pair<List<Poi>, List<WalkWay>> {
        val els = json.getJSONArray("elements")
        val pois = ArrayList<Poi>(); val ways = ArrayList<WalkWay>()
        val footKinds = FOOT.split('|').toSet()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val tags = e.optJSONObject("tags")
            fun tag(k: String) = tags?.optString(k)?.takeIf { it.isNotBlank() }
            val geom = e.optJSONArray("geometry")
            if (geom != null) {
                val hw = tag("highway")
                if (hw != null && hw in footKinds) {
                    val pts = (0 until geom.length()).map { j -> geom.getJSONObject(j).let { GeoPoint(it.getDouble("lat"), it.getDouble("lon")) } }
                    ways += WalkWay(hw, pts)
                    val mid = pts[pts.size / 2]
                    // 계단 · 경사 표시(OSM highway=steps, incline 태그). 경사 값이 0이면 뺀다.
                    if (hw == "steps") pois += Poi(FacilityKind.Stairs, mid, tag("name"), tag("step_count")?.let { "${it}계단" })
                    else tag("incline")?.takeIf { it !in setOf("0", "0%", "no") }?.let { inc ->
                        pois += Poi(FacilityKind.Slope, mid, tag("name"), when (inc) { "up", "down", "yes" -> "경사 있음"; else -> "기울기 $inc" })
                    }
                }
                continue
            }
            val p = when {
                e.has("lat") -> GeoPoint(e.getDouble("lat"), e.getDouble("lon"))
                e.has("center") -> e.getJSONObject("center").let { GeoPoint(it.getDouble("lat"), it.getDouble("lon")) }
                else -> continue
            }
            val name = tag("name")
            val wc = when (tag("wheelchair")) { "yes" -> "휠체어 가능 표시"; "limited" -> "휠체어 제한적"; "no" -> "휠체어 불가 표시"; else -> null }
            val amenity = tag("amenity")
            when {
                amenity == "toilets" -> {
                    pois += Poi(FacilityKind.Toilet, p, name, wc)
                    if (tag("changing_table") == "yes") pois += Poi(FacilityKind.Changing, p, name, "화장실 안")
                }
                amenity == "parking" || tag("parking_space") == "disabled" -> {
                    val dis = tag("parking_space") == "disabled" || (tag("capacity:disabled")?.toIntOrNull() ?: 0) > 0 || tag("capacity:disabled") == "yes"
                    pois += Poi(FacilityKind.Parking, p, name ?: if (dis) "장애인 주차 칸" else null, if (dis) "장애인 칸 표시" else null)
                }
                tag("highway") == "elevator" -> pois += Poi(FacilityKind.Elevator, p, name, wc)
                tag("entrance") != null -> pois += Poi(FacilityKind.Entrance, p, name ?: if (tag("entrance") == "main") "주 출입구" else "출입구", wc)
                tag("ramp:wheelchair") == "yes" -> pois += Poi(FacilityKind.Ramp, p, name)
                tag("changing_table") == "yes" -> pois += Poi(FacilityKind.Changing, p, name)
                amenity == "bench" || amenity == "shelter" -> pois += Poi(FacilityKind.Rest, p, name ?: if (amenity == "bench") "벤치" else "쉼터")
            }
        }
        // 벤치 · 건물 입구는 너무 많을 수 있어 장소 가까운 순으로 줄인다.
        fun trim(k: FacilityKind, n: Int) = pois.filter { it.kind == k }.sortedBy { distanceKm(c, it.point) }.drop(n).toSet()
        val drop = trim(FacilityKind.Rest, 15) + trim(FacilityKind.Entrance, 20) + trim(FacilityKind.Stairs, 40) + trim(FacilityKind.Slope, 40)
        Log.i("FacilityMap", "POI ${pois.size - drop.size}개 · 보행로 ${ways.size}개")
        return pois.filter { it !in drop } to ways
    }
}
