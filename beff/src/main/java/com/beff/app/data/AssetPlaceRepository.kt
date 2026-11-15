package com.beff.app.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** assets/places.json을 읽는 저장소. 파일이 없으면 빈 저장소로 동작한다(화면은 "찾지 못했어요"를 보여 준다). */
class AssetPlaceRepository(context: Context) : PlaceRepository {
    private val appContext: Context = context.applicationContext
    private val walkMaps = HashMap<String, WalkMap?>()
    private val all: List<Place> = load(context)
    private val byId: Map<String, Place> = all.associateBy { it.id }
    private val parking: List<ParkingSpot> = loadParking(context)

    /** 데이터에 실제로 있는 시설 칩과 그 장소 수. 테마 카드는 여기 있는 것만 만든다. */
    private val servable: Map<String, Int> =
        all.flatMap { it.facilities }.groupingBy { it }.eachCount()

    override val count get() = all.size

    /** 바깥 검색(카카오)에서 고른 장소. 이번 실행 동안만 둔다. */
    private val external = java.util.concurrent.ConcurrentHashMap<String, Place>()

    override fun place(id: String): Place = placeOrNull(id) ?: error("장소 없음: $id")
    override fun placeOrNull(id: String): Place? = byId[id] ?: external[id]
    override fun photoCredits(): List<Pair<String, String>> =
        all.mapNotNull { p -> p.imageCredit?.let { p.name to it } }.sortedBy { it.first }

    override fun register(place: Place) {
        if (place.id !in byId) external[place.id] = place
    }

    override fun barrierFreeMatch(place: Place): Place? {
        val la = place.lat ?: return null
        val lo = place.lng ?: return null
        val here = GeoPoint(la, lo)
        val n = norm(place.name)
        return all.mapNotNull { p ->
            val d = dist(here, p)
            if (d > MATCH_SAME_NAME_KM) return@mapNotNull null
            val m = norm(p.name)
            // 이름이 같으면 넓게(큰 공원은 데이터마다 대표 좌표가 수백 m 다르다 — 서울숲 약 390m),
            // 한쪽 이름이 다른 쪽을 품고 길이도 비슷하면 좁게 본다("서울숲 공영주차장"은 "서울숲"이 아니다).
            val short = minOf(n.length, m.length); val long = maxOf(n.length, m.length)
            val ok = when {
                short == 0 -> false
                n == m -> true
                (n.contains(m) || m.contains(n)) && short >= long * MATCH_NAME_RATIO -> d <= MATCH_SIMILAR_NAME_KM
                else -> false
            }
            if (ok) p to d else null
        }.minByOrNull { it.second }?.first
    }

    override fun search(query: String, origin: GeoPoint, limit: Int): List<Place> {
        val q = norm(query)
        if (q.isEmpty()) return emptyList()
        return all.mapNotNull { p ->
            val n = norm(p.name)
            val score = when {
                n == q -> 0
                n.startsWith(q) -> 1
                n.contains(q) -> 2
                norm(p.address).contains(q) -> 3
                else -> return@mapNotNull null
            }
            Triple(p, score, dist(origin, p))
        }.sortedWith(compareBy({ it.second }, { it.third })).take(limit).map { it.first }
    }

    override fun recommend(profile: Profile?, origin: GeoPoint, limit: Int): List<Recommendation> {
        val wanted = profile?.filters.orEmpty()
        fun score(p: Place, km: Double): Double =
            p.facilities.count { it in wanted } * 3.0 +             // 함께 타는 분에게 필요한 시설
                minOf(p.facilities.size, 4) * 0.5 +                  // 시설 정보가 풍부한 곳
                (if (p.image != null) 5.0 else 0.0) +                // 사진이 있는 곳(카드가 비지 않게 — 사진이 붙은 곳이 아직 일부라 크게 준다)
                maxOf(0.0, 3.0 - km / 15.0)                          // 가까운 곳(45km를 넘으면 가점 없음)
        fun candidates(maxKm: Double) = all.mapNotNull { p ->
            if (p.lat == null || p.lng == null || p.facilities.isEmpty()) return@mapNotNull null
            val km = dist(origin, p)
            if (km > maxKm) null else Triple(p, km, score(p, km))
        }
        // 기준 위치 근처에 데이터가 없으면(서울 · 경기 · 인천 밖) 거리 제한 없이 고른다.
        val pool = candidates(RECOMMEND_MAX_KM).ifEmpty { candidates(Double.MAX_VALUE) }
            .sortedByDescending { it.third }
        // 먼저 종류마다 RECOMMEND_PER_KIND곳까지 고르고, 모자라면 남은 곳을 점수 순으로 채운다.
        val perKind = HashMap<String, Int>()
        val varied = pool.filter { (p, _, _) ->
            val n = perKind[p.kindLabel] ?: 0
            (n < RECOMMEND_PER_KIND).also { if (it) perKind[p.kindLabel] = n + 1 }
        }
        val picked = (varied + (pool - varied.toSet())).take(limit)
        return picked.map { (p, km, _) ->
            val matched = p.facilities.filter { it in wanted }
            val reason = if (matched.isNotEmpty()) "${matched.take(2).joinToString(" · ")} 있어요"
            else "이동 편의 시설 ${p.facilities.size}가지"
            Recommendation(p, reason, km.takeIf { it < Double.MAX_VALUE })
        }
    }

    override fun alternativesNear(place: Place, profile: Profile?, radiusKm: Double, limit: Int): List<Place> {
        val la = place.lat ?: return emptyList()
        val lo = place.lng ?: return emptyList()
        val here = GeoPoint(la, lo)
        val wanted = profile?.filters.orEmpty()
        return all.asSequence()
            .filter { it.id != place.id && it.facilities.isNotEmpty() }
            .map { it to dist(here, it) }
            .filter { it.second <= radiusKm }
            .sortedWith(
                compareByDescending<Pair<Place, Double>> { (p, _) -> p.facilities.count { it in wanted } }
                    .thenByDescending { (p, _) -> p.kindLabel == place.kindLabel }
                    .thenBy { it.second },
            )
            .take(limit).map { it.first }.toList()
    }

    override fun nearbyWith(chip: String?, origin: GeoPoint, limit: Int): List<Place> =
        all.filter { chip == null || chip in it.facilities }
            .sortedBy { dist(origin, it) }
            .take(limit)

    override fun themesFor(profile: Profile?): List<Theme> {
        val wanted = profile?.filters.orEmpty()
        val ordered = THEME_ORDER.filter { it in wanted && (servable[it] ?: 0) > 0 } +
            THEME_ORDER.filter { it !in wanted && (servable[it] ?: 0) > 0 }
        return ordered.take(4).map { Theme(THEME_LABEL.getValue(it), THEME_ICON.getValue(it), it) }
    }

    override fun restSuggestions(profile: Profile?, origin: GeoPoint, dest: Place?): List<Place> {
        val d = dest ?: return emptyList()
        val dLat = d.lat ?: return emptyList()
        val dLng = d.lng ?: return emptyList()
        val a = origin
        val b = GeoPoint(dLat, dLng)
        val wanted = profile?.filters.orEmpty()
        return all.mapNotNull { p ->
            if (p.id == d.id || p.lat == null || p.lng == null) return@mapNotNull null
            val (off, t) = distanceToSegment(GeoPoint(p.lat, p.lng), a, b)
            if (off > MAX_DETOUR_KM || t < 0.12 || t > 0.88) return@mapNotNull null
            val match = p.facilities.count { it in wanted }
            if (match == 0) return@mapNotNull null
            Triple(p, match, off)
        }.sortedWith(compareByDescending<Triple<Place, Int, Double>> { it.second }.thenBy { it.third })
            .take(3).map { it.first }
    }

    override fun facilitiesFor(place: Place, profile: Profile?): List<String> {
        val wanted = profile?.filters.orEmpty()
        return place.facilities.filter { it in wanted }.ifEmpty { place.facilities.take(2) }
    }

    override fun unloadPlan(place: Place, profile: Profile?): UnloadPlan {
        // 원문(access)에 적힌 항목만 줄로 만든다. 방향·거리는 원본에 없어 비워 둔다.
        val rows = listOf(
            Triple("parking", "장애인 주차구역", "illust/parking.svg"),
            Triple("route", "출입구까지", "illust/door.svg"),
            Triple("elevator", "엘리베이터", "illust/elevator.svg"),
        ).mapNotNull { (k, label, illust) ->
            place.access[k]?.takeIf { it.isNotBlank() }?.let { UnloadStep(label, brief(it), illust, null) }
        }
        return UnloadPlan(place.name, rows)
    }

    override fun altSpots(place: Place): List<AltSpot> =
        parkingNear(place, radiusKm = 2.0, limit = 3).map { s ->
            val detail = listOfNotNull(s.spaces?.let { "주차 ${it}면" }, s.hours?.let { "평일 $it" }).joinToString(" · ")
            AltSpot(
                id = s.id, name = s.name, minutes = 0, distanceM = (s.distanceKm * 1000).toInt(),
                facilities = "장애인 전용주차구역 있음", illust = "illust/parking.svg",
                steps = listOf(UnloadStep("장애인 전용주차구역", detail.ifBlank { null }, "illust/parking.svg", null)),
                lat = s.lat, lng = s.lng, address = s.address, region = s.region,
            )
        }

    /** 원문 문장을 한 줄에 들어가도록 첫 문장만 줄여 쓴다. */
    private fun brief(text: String): String {
        val first = text.trim().split('.', '\n').firstOrNull { it.isNotBlank() }?.trim() ?: text.trim()
        return if (first.length > 26) first.take(25) + "…" else first
    }

    override fun walkMap(place: Place): WalkMap? =
        synchronized(walkMaps) { walkMaps.getOrPut(place.id) { loadWalkMap(appContext, place.id) } }

    override fun parkingNear(place: Place, radiusKm: Double, limit: Int): List<ParkingSpot> {
        val la = place.lat ?: return emptyList()
        val lo = place.lng ?: return emptyList()
        val here = GeoPoint(la, lo)
        return parking.map { it.copy(distanceKm = distanceKm(here, GeoPoint(it.lat, it.lng))) }
            .filter { it.distanceKm <= radiusKm }
            .sortedBy { it.distanceKm }
            .take(limit)
    }

    private fun dist(o: GeoPoint, p: Place): Double =
        if (p.lat == null || p.lng == null) Double.MAX_VALUE else distanceKm(o, GeoPoint(p.lat, p.lng))

    private fun norm(s: String) = s.lowercase().filterNot { it.isWhitespace() || it == '·' || it == '(' || it == ')' }

    private companion object {
        const val TAG = "PlaceRepo"
        const val MAX_DETOUR_KM = 8.0
        const val RECOMMEND_MAX_KM = 80.0
        const val RECOMMEND_PER_KIND = 2
        const val MATCH_SAME_NAME_KM = 1.0
        const val MATCH_SIMILAR_NAME_KM = 0.3
        const val MATCH_NAME_RATIO = 0.6

        val THEME_ORDER = listOf(
            // 1-4: 메인 타겟 휠체어 우선(1113). 유모차 시설은 뒤로.
            "장애인 화장실", "경사로", "엘리베이터", "장애인 주차", "턱 없는 입구", "휠체어 대여",
            "수유실", "기저귀 교환대", "유모차 대여", "유아 의자",
        )
        val THEME_LABEL = mapOf(
            "수유실" to "수유실\n있는 곳", "기저귀 교환대" to "기저귀 교환대\n있는 곳",
            "장애인 주차" to "장애인 주차\n있는 곳", "장애인 화장실" to "장애인 화장실\n있는 곳",
            "엘리베이터" to "엘리베이터\n있는 곳", "경사로" to "경사로\n있는 곳",
            "턱 없는 입구" to "턱 없는\n입구", "휠체어 대여" to "휠체어\n대여 가능",
            "유모차 대여" to "유모차\n대여 가능", "유아 의자" to "유아 의자\n있는 곳",
        )
        val THEME_ICON = mapOf(
            "수유실" to "illust/nursing.svg", "기저귀 교환대" to "illust/diaper.svg",
            "장애인 주차" to "illust/parking.svg", "장애인 화장실" to "illust/toilet.svg",
            "엘리베이터" to "illust/elevator.svg", "경사로" to "illust/ramp.svg",
            "턱 없는 입구" to "illust/door.svg", "휠체어 대여" to "illust/wheelchair.svg",
            "유모차 대여" to "illust/stroller.svg", "유아 의자" to "illust/badge.svg",
        )

        fun load(ctx: Context): List<Place> = runCatching {
            val txt = ctx.assets.open("places.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr: JSONArray = JSONObject(txt).getJSONArray("places")
            (0 until arr.length()).map { parse(arr.getJSONObject(it)) }
        }.onFailure { Log.w(TAG, "places.json을 읽지 못했어요: ${it.message}") }.getOrDefault(emptyList())

        fun loadWalkMap(ctx: Context, placeId: String): WalkMap? = runCatching {
            val txt = ctx.assets.open("osm/$placeId.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val o = JSONObject(txt)
            val c = o.getJSONObject("center")
            fun str(j: JSONObject, k: String): String? = if (j.isNull(k)) null else j.getString(k)
            val toilets = o.getJSONArray("toilets").let { a ->
                (0 until a.length()).map {
                    val t = a.getJSONObject(it)
                    Toilet(t.getDouble("lat"), t.getDouble("lng"), str(t, "name"), str(t, "wheelchair"), str(t, "access"))
                }
            }
            val ways = o.getJSONArray("ways").let { a ->
                (0 until a.length()).map {
                    val w = a.getJSONObject(it)
                    val p = w.getJSONArray("pts")
                    WalkWay(w.optString("kind"), (0 until p.length()).map { i -> p.getJSONArray(i).let { q -> GeoPoint(q.getDouble(0), q.getDouble(1)) } })
                }
            }
            WalkMap(placeId, GeoPoint(c.getDouble("lat"), c.getDouble("lng")), o.getInt("radiusM"), o.getString("source"), o.getString("fetched"), toilets, ways,
                o.optJSONObject("start")?.let { WalkStart(it.getDouble("lat"), it.getDouble("lng"), it.getString("label"), it.optBoolean("verified", false), str(it, "note")) })
        }.onFailure { if (it !is java.io.FileNotFoundException) Log.w(TAG, "walkmap $placeId: ${it.message}") }.getOrNull()

        fun loadParking(ctx: Context): List<ParkingSpot> = runCatching {
            val txt = ctx.assets.open("parking.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val arr = JSONObject(txt).getJSONArray("spots")
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ParkingSpot(
                    id = o.getString("id"), name = o.getString("name"), region = o.optString("region"),
                    address = o.optString("address"), lat = o.getDouble("lat"), lng = o.getDouble("lng"),
                    spaces = o.optInt("spaces", -1).takeIf { n -> n >= 0 },
                    hours = o.optString("hours").takeIf { h -> h.isNotBlank() && h != "null" },
                    reference = o.optString("reference").takeIf { r -> r.isNotBlank() && r != "null" },
                    distanceKm = 0.0,
                )
            }
        }.onFailure { Log.w(TAG, "parking.json을 읽지 못했어요: ${it.message}") }.getOrDefault(emptyList())

        fun parse(o: JSONObject): Place {
            val fac = o.getJSONArray("facilities").let { a -> (0 until a.length()).map { a.getString(it) } }
            val acc = o.optJSONObject("access")?.let { ob -> ob.keys().asSequence().associateWith { ob.getString(it) } }.orEmpty()
            val kindLabel = o.optString("kind")
            val name = o.getString("name")
            return Place(
                id = o.getString("id"),
                name = name,
                kind = when {
                    "휴게소" in name -> PlaceKind.RestArea
                    kindLabel == "문화시설" -> PlaceKind.Culture
                    else -> PlaceKind.Attraction
                },
                facilities = fac,
                lat = o.optDouble("lat").takeIf { !it.isNaN() },
                lng = o.optDouble("lng").takeIf { !it.isNaN() },
                address = o.optString("address"),
                kindLabel = kindLabel,
                region = o.optString("region"),
                access = acc,
                source = o.optString("source"),
                fetched = o.optString("fetched"),
                modified = o.optString("modified").takeIf { it.isNotBlank() && it != "null" },
                image = o.optString("image").takeIf { it.startsWith("https://") },
                imageCredit = o.optString("imageCredit").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }
}
