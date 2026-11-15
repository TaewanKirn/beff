package com.beff.app.data

import java.util.PriorityQueue

/**
 * 장소 주변 도식 지도(OpenStreetMap, tools/public_data/build_osm_walkmap.py가 assets/osm/<장소id>.json으로 만든다).
 * 화장실의 wheelchair 태그는 OSM에 비어 있는 곳이 많다 — null이면 "확인 안 됨"이다(접근 가능하다고 쓰지 않는다).
 */
data class Toilet(val lat: Double, val lng: Double, val name: String?, val wheelchair: String?, val access: String?)

data class WalkWay(val kind: String, val pts: List<GeoPoint>)

/** 사람이 고른 출발점(예: 항공사진에서 고른 장애인 주차 칸). verified=false면 현장 확인 전이다. */
data class WalkStart(val lat: Double, val lng: Double, val label: String, val verified: Boolean, val note: String?)

data class WalkMap(
    val placeId: String,
    val center: GeoPoint,
    val radiusM: Int,
    val source: String,
    val fetched: String,
    val toilets: List<Toilet>,
    val ways: List<WalkWay>,
    val start: WalkStart? = null,
    /** OSM 보행로를 못 받아 주차장 → 입구를 직선으로만 이은 지도(보행로 아님). */
    val direct: Boolean = false,
)

/** 시작점 → 화장실 보행 경로. [meters]는 보행로를 따라간 거리, [straightMeters]는 직선 거리. */
data class WalkRoute(
    val start: GeoPoint,
    val toilet: Toilet,
    val path: List<GeoPoint>,
    val meters: Int,
    val straightMeters: Int,
)

private const val SNAP_LIMIT_M = 120.0   // 시작점·화장실이 보행로에서 이만큼 넘게 떨어져 있으면 경로를 잇지 않는다

/** 화장실이 없는 곳에서 목적지로 쓰는 「입구(장소 대표 지점)」 표시. Toilet.access에 넣어 구분한다. */
const val ENTRANCE_TARGET = "entrance"

/** 도착 지점이 화장실이 아니라 장소 입구(대표 좌표)인가. */
val WalkRoute.toEntrance: Boolean get() = toilet.access == ENTRANCE_TARGET

/**
 * 미리 받아 둔 도식 지도(assets/osm)가 없는 장소용: 실행 중에 Overpass API(OSM 공개 서버, 키 없음)로 주변 보행로 · 화장실을 받는다.
 * 화장실이 없으면 장소 대표 좌표를 「입구」 목적지로 넣는다. 한 번 받은 곳은 앱이 켜져 있는 동안 기억한다. 실패하면 null.
 * 네트워크를 쓰므로 IO 스레드에서 부른다.
 */
object OsmLive {
    /** Overpass 공개 서버는 자주 502/504를 낸다 → 미러를 차례로 시도한다. */
    internal val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )
    private const val FOOT = "footway|path|pedestrian|steps|track|service|living_street|residential|cycleway|unclassified|tertiary"
    private val cache = java.util.concurrent.ConcurrentHashMap<String, WalkMap>()

    fun walkMap(place: Place, radiusM: Int = 400): WalkMap? {
        cache[place.id]?.let { return it }
        val lat = place.lat ?: return null
        val lng = place.lng ?: return null
        val q = """[out:json][timeout:25];
(
  node["amenity"="toilets"](around:$radiusM,$lat,$lng);
  way["highway"~"^($FOOT)$"](around:$radiusM,$lat,$lng);
);
out tags geom;"""
        val deadline = System.currentTimeMillis() + 25_000   // 미러를 다 돌아도 25초 안에는 포기하고 직선 연결로 넘긴다
        for (ep in ENDPOINTS) {
            if (System.currentTimeMillis() > deadline) break
            fetch(ep, q, place, lat, lng, radiusM)?.let { return it }
        }
        return null
    }

    /**
     * OSM 서버가 모두 응답하지 않을 때: 근처 주차장 [parking] → 장소 입구(대표 좌표)를 직선으로 잇는 지도.
     * 주차장을 못 찾으면 null(지어낸 출발점은 쓰지 않는다).
     */
    fun direct(place: Place, parking: GeoPoint, parkingName: String): WalkMap? {
        val lat = place.lat ?: return null
        val lng = place.lng ?: return null
        return WalkMap(
            placeId = place.id, center = GeoPoint(lat, lng), radiusM = 600,
            source = "직선 연결(보행로 정보 없음)", fetched = java.time.LocalDate.now().toString(),
            toilets = listOf(Toilet(lat, lng, "${place.name} 입구(대표 지점)", null, ENTRANCE_TARGET)),
            ways = emptyList(),
            start = WalkStart(parking.lat, parking.lng, "주차장(예시): $parkingName", verified = false, note = null),
            direct = true,
        )
    }

    private fun fetch(endpoint: String, q: String, place: Place, lat: Double, lng: Double, radiusM: Int): WalkMap? {
        var conn: java.net.HttpURLConnection? = null
        return try {
            conn = (java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 6000; readTimeout = 12000
                setRequestProperty("User-Agent", "BeffApp/0.1 (Pleos Connect accessibility trip app)")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            conn.outputStream.use { it.write(("data=" + java.net.URLEncoder.encode(q, "UTF-8")).toByteArray()) }
            if (conn.responseCode != 200) { android.util.Log.w("OsmLive", "HTTP ${conn.responseCode} @ $endpoint"); return null }
            val els = org.json.JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONArray("elements")
            val toilets = ArrayList<Toilet>(); val ways = ArrayList<WalkWay>()
            for (i in 0 until els.length()) {
                val e = els.getJSONObject(i)
                val tags = e.optJSONObject("tags")
                fun tag(k: String) = tags?.optString(k)?.takeIf { it.isNotBlank() }
                if (e.getString("type") == "node") {
                    toilets += Toilet(e.getDouble("lat"), e.getDouble("lon"), tag("name"), tag("wheelchair"), tag("access"))
                } else {
                    val g = e.optJSONArray("geometry") ?: continue
                    ways += WalkWay(tag("highway") ?: "", (0 until g.length()).map { j -> g.getJSONObject(j).let { GeoPoint(it.getDouble("lat"), it.getDouble("lon")) } })
                }
            }
            if (ways.isEmpty()) return null
            if (toilets.isEmpty()) toilets += Toilet(lat, lng, "${place.name} 입구(대표 지점)", null, ENTRANCE_TARGET)
            WalkMap(
                placeId = place.id, center = GeoPoint(lat, lng), radiusM = radiusM,
                source = "© OpenStreetMap contributors (ODbL), Overpass API",
                fetched = java.time.LocalDate.now().toString(), toilets = toilets, ways = ways,
            ).also { cache[place.id] = it }
        } catch (e: Exception) {
            android.util.Log.w("OsmLive", "${place.id}: ${e.javaClass.simpleName} @ $endpoint")
            null
        } finally {
            conn?.disconnect()
        }
    }
}

/** 하차 시뮬레이션 단계 한 줄. [at]은 경로(path)에서 이 안내를 말할 꼭짓점 번호. */
data class WalkStep(val at: Int, val text: String)

/**
 * 경로를 「출발 → ○m 걸어서 왼쪽/오른쪽 → … → 도착」 단계로 나눈다. 꺾이는 각이 [TURN_DEG]도 넘는 곳만 방향 안내로 쓰고,
 * 그 사이 거리는 합친다(짧은 꺾임은 무시). 방향은 지도 위 진행 방향 기준이다.
 */
fun walkSteps(route: WalkRoute, startLabel: String, endLabel: String): List<WalkStep> {
    val p = route.path
    if (p.size < 2) return emptyList()
    fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val y = Math.sin(Math.toRadians(b.lng - a.lng)) * Math.cos(Math.toRadians(b.lat))
        val x = Math.cos(Math.toRadians(a.lat)) * Math.sin(Math.toRadians(b.lat)) -
            Math.sin(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.cos(Math.toRadians(b.lng - a.lng))
        return Math.toDegrees(Math.atan2(y, x))
    }
    // 이름 뒤 조사는 붙이지 않는다(받침에 따라 틀리기 쉬움): 「출발 · ○○」 「도착 · ○○」 꼴로 쓴다.
    val steps = mutableListOf(WalkStep(0, "출발 · $startLabel"))
    var acc = 0.0
    for (i in 1 until p.size - 1) {
        acc += distanceKm(p[i - 1], p[i]) * 1000
        if (distanceKm(p[i], p[i + 1]) * 1000 < 2) continue                 // 같은 점이 겹친 곳
        var turn = bearing(p[i], p[i + 1]) - bearing(p[i - 1], p[i])
        while (turn > 180) turn -= 360
        while (turn < -180) turn += 360
        if (Math.abs(turn) < TURN_DEG || acc < MIN_STEP_M) continue
        steps += WalkStep(i, "${Math.round(acc)}m 가서 ${if (turn > 0) "오른쪽" else "왼쪽"}으로")
        acc = 0.0
    }
    acc += distanceKm(p[p.size - 2], p.last()) * 1000
    steps += WalkStep(p.size - 1, "${Math.round(acc)}m 더 가면 도착 · $endLabel")
    return steps
}

private const val TURN_DEG = 35.0
private const val MIN_STEP_M = 6.0

/**
 * 보행로 선을 그래프로 보고 [start]에서 가장 가까운(걸어서) 화장실까지 최단 경로를 찾는다.
 * 계단(steps)은 유모차·휠체어에 맞지 않아 뺀다. 노면 · 경사 · 폭은 OSM에 없어 판단하지 않는다 [미확인].
 * 경로를 이을 수 없으면 null.
 */
fun planWalk(map: WalkMap, start: GeoPoint): WalkRoute? {
    if (map.direct) {
        val t = map.toilets.firstOrNull() ?: return null
        val end = GeoPoint(t.lat, t.lng)
        val m = Math.round(distanceKm(start, end) * 1000).toInt()
        return WalkRoute(start = start, toilet = t, path = listOf(start, end), meters = m, straightMeters = m)
    }
    val index = HashMap<Long, Int>()
    val nodes = ArrayList<GeoPoint>()
    val adj = ArrayList<MutableList<Pair<Int, Double>>>()

    fun key(p: GeoPoint) = Math.round(p.lat * 1e6) * 1_000_000_000L + Math.round(p.lng * 1e6)
    fun idOf(p: GeoPoint): Int = index.getOrPut(key(p)) {
        nodes.add(p); adj.add(mutableListOf()); nodes.size - 1
    }

    for (w in map.ways) {
        if (w.kind == "steps") continue
        for (i in 0 until w.pts.size - 1) {
            val a = idOf(w.pts[i]); val b = idOf(w.pts[i + 1])
            if (a == b) continue
            val d = distanceKm(w.pts[i], w.pts[i + 1]) * 1000
            adj[a].add(b to d); adj[b].add(a to d)
        }
    }
    if (nodes.isEmpty()) return null

    fun nearest(p: GeoPoint): Pair<Int, Double> {
        var best = 0; var bd = Double.MAX_VALUE
        for (i in nodes.indices) {
            val d = distanceKm(p, nodes[i]) * 1000
            if (d < bd) { bd = d; best = i }
        }
        return best to bd
    }

    val (s, sd) = nearest(start)
    if (sd > SNAP_LIMIT_M) return null

    val dist = DoubleArray(nodes.size) { Double.MAX_VALUE }
    val prev = IntArray(nodes.size) { -1 }
    val pq = PriorityQueue<Pair<Int, Double>>(compareBy { it.second })
    dist[s] = 0.0; pq.add(s to 0.0)
    while (pq.isNotEmpty()) {
        val (u, du) = pq.poll()!!
        if (du > dist[u]) continue
        for ((v, w) in adj[u]) {
            val nd = du + w
            if (nd < dist[v]) { dist[v] = nd; prev[v] = u; pq.add(v to nd) }
        }
    }

    var bestToilet: Toilet? = null; var bestNode = -1; var bestTotal = Double.MAX_VALUE; var bestTd = 0.0
    for (t in map.toilets) {
        val (tn, td) = nearest(GeoPoint(t.lat, t.lng))
        if (td > SNAP_LIMIT_M || dist[tn] == Double.MAX_VALUE) continue
        val total = sd + dist[tn] + td
        if (total < bestTotal) { bestTotal = total; bestToilet = t; bestNode = tn; bestTd = td }
    }
    val toilet = bestToilet ?: return null

    val chain = ArrayList<GeoPoint>()
    var cur = bestNode
    while (cur != -1) { chain.add(nodes[cur]); cur = prev[cur] }
    chain.reverse()
    val path = listOf(start) + chain + GeoPoint(toilet.lat, toilet.lng)
    return WalkRoute(
        start = start, toilet = toilet, path = path,
        meters = Math.round(bestTotal).toInt(),
        straightMeters = Math.round(distanceKm(start, GeoPoint(toilet.lat, toilet.lng)) * 1000).toInt(),
    )
}
