package com.beff.app.data

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 장소 데이터 계층. 화면은 이 인터페이스만 본다.
 * 실제 구현은 AssetPlaceRepository(assets/places.json — 한국관광공사 무장애 여행정보를 tools/public_data로 수집·변환).
 * 시설(facilities)은 원문에 분명히 적힌 것만 켠다. 원문 문장은 access에 그대로 둔다.
 */
data class Place(
    val id: String,
    val name: String,
    val kind: PlaceKind,
    val facilities: List<String>,
    val minutes: Int = 0,           // 0 = 아직 모름(내비가 알려 주면 그 값을 쓴다)
    val km: Int = 0,
    val note: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val address: String = "",
    val kindLabel: String = "",     // 관광지 · 문화시설 · 레포츠 …
    val region: String = "",        // 서울 · 경기 · 인천
    val access: Map<String, String> = emptyMap(),  // 원문 문장(parking, route, exit, restroom …)
    val source: String = "",
    val fetched: String = "",       // 수집일(YYYY-MM-DD)
    val modified: String? = null,   // 원본 수정일(YYYYMMDD)
    val image: String? = null,      // 대표 사진 주소(https). 한국관광공사 firstimage 또는 위키미디어 공용. 없으면 일러스트로 대신한다.
    val imageCredit: String? = null, // 사진 출처 표기(라이선스 조건). 예: "Wikimedia Commons · 작성자 · CC BY-SA 4.0"
) {
    /** 원문에 지하 주차장이라고 적혀 있는가. 이런 곳은 하차 동선을 지상 지도(카카오맵)로 보여 줄 수 없다. */
    val undergroundParking: Boolean get() = access["parking"]?.contains("지하") == true

    /** 무장애 정보가 하나도 없는 곳(카카오에서만 찾은 곳 등). 이런 곳은 근처 대안을 보여 준다. */
    val hasNoAccessInfo: Boolean get() = facilities.isEmpty() && access.isEmpty()

    /** 목록에 보여 줄 지역. 주소에서 "서울 종로구", "성남시 분당구"까지 뽑고, 주소가 없으면 시·도(region)만. */
    val regionLabel: String get() = regionFromAddress(address).ifBlank { region }
}

private val METRO_SHORT = setOf("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종")

private val SIDO_SHORT = mapOf(
    "서울특별시" to "서울", "부산광역시" to "부산", "대구광역시" to "대구", "인천광역시" to "인천",
    "광주광역시" to "광주", "대전광역시" to "대전", "울산광역시" to "울산", "세종특별자치시" to "세종",
    "경기도" to "경기", "강원특별자치도" to "강원", "충청북도" to "충북", "충청남도" to "충남",
    "전북특별자치도" to "전북", "전라남도" to "전남", "경상북도" to "경북", "경상남도" to "경남", "제주특별자치도" to "제주",
)

/**
 * "경기도 성남시 분당구 …" → "성남시 분당구". 구가 딸린 시만 구까지, 군은 군까지. 주소를 못 읽으면 빈 문자열.
 * 도(경기 · 강원 …)는 떼고, 특별시·광역시는 구 이름이 겹치므로("중구" · "동구") 앞에 붙인다: "서울 종로구".
 */
fun regionFromAddress(address: String): String {
    val t = address.trim().split(' ').filter { it.isNotEmpty() }
    if (t.size < 2) return ""
    val sido = SIDO_SHORT[t[0]] ?: t[0]
    val sigungu = t[1]
    if (!(sigungu.endsWith("시") || sigungu.endsWith("군") || sigungu.endsWith("구"))) return sido
    val gu = t.getOrNull(2)?.takeIf { sigungu.endsWith("시") && it.endsWith("구") }
    // 서울특별시 · 부산광역시 … 또는 카카오 주소의 짧은 표기(서울 · 부산 …)
    val metro = t[0].endsWith("시") || sido in METRO_SHORT
    return listOfNotNull(sido.takeIf { metro }, sigungu, gu).joinToString(" ")
}

/** 추천 한 건: 장소 + 추천 이유(칩 문구) + 기준 위치에서의 직선 거리. 방문자 수 같은 인기 지표는 쓰지 않는다. */
data class Recommendation(val place: Place, val reason: String, val km: Double?)

enum class PlaceKind(val illust: String) {
    Attraction("illust/deck_path.svg"),
    RestArea("illust/rest_area.svg"),
    Culture("illust/home.svg"),
}

data class GeoPoint(val lat: Double, val lng: Double)

/** 두 지점 사이 직선 거리(km). 도로 거리가 아니다. */
fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
    val r = 6371.0
    val dLat = (b.lat - a.lat) * PI / 180
    val dLng = (b.lng - a.lng) * PI / 180
    val x = sin(dLat / 2) * sin(dLat / 2) +
        cos(a.lat * PI / 180) * cos(b.lat * PI / 180) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * atan2(sqrt(x), sqrt(1 - x))
}

/** 점 p가 선분 a→b에서 얼마나 떨어져 있는지(km)와 선분 위 위치 비율. 근거리용 평면 근사. */
fun distanceToSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): Pair<Double, Double> {
    val kx = 111.32 * cos(a.lat * PI / 180)
    val ky = 110.57
    val bx = (b.lng - a.lng) * kx
    val by = (b.lat - a.lat) * ky
    val px = (p.lng - a.lng) * kx
    val py = (p.lat - a.lat) * ky
    val len2 = bx * bx + by * by
    val t = if (len2 == 0.0) 0.0 else (px * bx + py * by) / len2
    val tc = max(0.0, min(1.0, t))
    val dx = px - tc * bx
    val dy = py - tc * by
    return sqrt(dx * dx + dy * dy) to t
}

data class Theme(val label: String, val illust: String, val chip: String)

data class RecentItem(val text: String, val date: String, val placeId: String?, val mobility: Mobility?)

/** 하차 브리핑(06-2)의 하차 동선 한 줄. label = 이름(36), sub = 방향 + 거리(32 볼드). */
data class UnloadStep(val label: String, val sub: String?, val illust: String, val meters: Int?)

/** 하차 브리핑 정보. 한국관광공사 무장애 여행정보 원문(access)에서 만든다. 원문에 없는 줄·거리는 만들지 않는다. */
data class UnloadPlan(
    val placeName: String,
    val steps: List<UnloadStep>,
)

/**
 * 대체 지점(07-1) 후보. 전국주차장정보표준데이터의 장애인 전용주차구역 보유 주차장에서 만든다. distanceM은 현재 위치에서 걷는 거리, facilities는 굵은 줄 문구,
 * steps는 2/3 화면 오른쪽 「{선택지}에서 내리면」 동선.
 */
data class AltSpot(
    val id: String, val name: String, val minutes: Int, val distanceM: Int,
    val facilities: String, val illust: String, val steps: List<UnloadStep>,
    val lat: Double? = null, val lng: Double? = null, val address: String = "", val region: String = "",
)

/**
 * 도착지 근처 장애인 주차장 후보(전국주차장정보표준데이터, 장애인 전용주차구역 보유 'Y'만).
 * spaces는 주차장 전체 구획 수이며 장애인 면수는 원본에 없다.
 */
data class ParkingSpot(
    val id: String,
    val name: String,
    val region: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val spaces: Int?,
    val hours: String?,
    val reference: String?,   // 원본 기준일
    val distanceKm: Double,   // 기준 장소에서의 직선 거리
)

interface PlaceRepository {
    val count: Int
    fun place(id: String): Place
    fun placeOrNull(id: String): Place?

    /** 앱에 들어 있는 사진의 출처 표기(장소 이름 → 작성자 · 라이선스). 저작권 화면용. */
    fun photoCredits(): List<Pair<String, String>>

    /** 이름 검색. 이름이 비슷한 순, 같으면 가까운 순. */
    fun search(query: String, origin: GeoPoint, limit: Int = 20): List<Place>

    /**
     * 바깥 검색(카카오) 결과와 같은 곳인 무장애 장소. 이름이 같으면 1km 안, 한쪽 이름이 다른 쪽을 품고 길이도 비슷하면 300m 안일 때만 같은 곳으로 본다 [가정].
     * 거리만으로는 맞추지 않는다(공원 대표 좌표 옆 편의점에 무장애 표시가 붙지 않게). 없으면 null.
     */
    fun barrierFreeMatch(place: Place): Place?

    /** 바깥 검색 결과를 이번 실행 동안 [place]/[placeOrNull]로 찾을 수 있게 둔다(저장하지 않는다). */
    fun register(place: Place)

    /**
     * 홈 · 검색 오른쪽 추천. 함께 타는 분의 필터와 맞는 시설 수, 가까운 정도, 사진 유무, 시설 정보의 풍부함으로 점수를 매긴다 [가정].
     * 같은 종류(관광지 · 문화시설 …)가 몰리지 않게 섞는다.
     */
    fun recommend(profile: Profile?, origin: GeoPoint, limit: Int = 6): List<Recommendation>

    /**
     * 무장애 정보가 없는 [place] 대신 갈 만한 근처(반경 [radiusKm]) 무장애 장소. 함께 타는 분의 필터에 맞는 시설이 많은 순,
     * 같으면 같은 종류(관광지 · 문화시설 …) · 가까운 순(1110 「정보 없음 → 근처 대안」).
     */
    fun alternativesNear(place: Place, profile: Profile?, radiusKm: Double = 3.0, limit: Int = 3): List<Place>

    /** [chip] 시설이 있는 장소를 가까운 순으로. chip이 null이면 전체. */
    fun nearbyWith(chip: String?, origin: GeoPoint, limit: Int = 20): List<Place>

    /** 프로필 필터 중 데이터로 실제 답할 수 있는 것만 테마 카드로 만든다. */
    fun themesFor(profile: Profile?): List<Theme>

    /** 출발지→목적지 경로(직선 근사) 근처에서 프로필 필터에 맞는 시설이 있는 장소. */
    fun restSuggestions(profile: Profile?, origin: GeoPoint, dest: Place?): List<Place>

    /** 도착하면 쓸 수 있는 시설: 프로필 필터와 장소 시설의 교집합. */
    fun facilitiesFor(place: Place, profile: Profile?): List<String>

    /** 하차 동선(06-2). 원문(access)에 적힌 항목만 줄로 만든다. 거리·방향은 원본에 없어 만들지 않는다. */
    fun unloadPlan(place: Place, profile: Profile?): UnloadPlan

    /** 대체 하차지(07-1). 근처 장애인 전용주차구역 보유 주차장에서 만든다. */
    fun altSpots(place: Place): List<AltSpot>

    /** 장소 주변 도식 지도(OSM 화장실 · 보행로). 수집해 둔 장소만 있고 없으면 null. */
    fun walkMap(place: Place): WalkMap?

    /** 기준 장소 반경 [radiusKm] 안의 장애인 전용주차구역 보유 주차장을 가까운 순으로. */
    fun parkingNear(place: Place, radiusKm: Double = 2.0, limit: Int = 5): List<ParkingSpot>
}
