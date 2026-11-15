package com.beff.app.data

import android.util.Log
import com.beff.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 카카오 로컬 API 키워드 검색으로 전국 장소를 찾는다(검색 탭의 「장소, 주소 검색」).
 * - 인증키는 BuildConfig.KAKAO_REST_KEY(빌드 때 환경변수/local.properties에서 주입). 없으면 [enabled]가 false이고 앱 안 무장애 데이터로만 찾는다.
 * - 검색어와 기준 위치(내 위치)가 카카오로 전송된다. 심사 기준상 외부 전송은 고지·동의가 필요하다(docs/sdk-notes.md §2) [미결].
 * - 결과는 무장애 정보가 없는 일반 장소다. 무장애 표시는 [PlaceRepository.barrierFreeMatch]로 앱 데이터와 맞춰 붙인다.
 */
object KakaoLocal {
    const val ID_PREFIX = "kakao-"
    const val SOURCE = "카카오 장소 검색"
    private const val TAG = "KakaoLocal"

    val enabled: Boolean get() = BuildConfig.KAKAO_REST_KEY.isNotBlank()

    /** 네트워크를 쓰므로 IO 스레드에서 부른다. 실패하면 null(빈 결과와 구분한다). 정확도 순, 기준 위치를 주면 가까운 곳을 앞에 둔다. */
    fun search(query: String, origin: GeoPoint, size: Int = 15): List<Place>? {
        if (!enabled || query.isBlank()) return null
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        return request("keyword.json?query=$q&x=${origin.lng}&y=${origin.lat}&size=$size")
    }

    /**
     * 프로필의 「가는 길에 들르는 곳」 칩(휴게소 · 주차장 · 편의점 · 카페 · 식당 · 약국)으로 [center] 반경 [radiusM] 안을 가까운 순으로 찾는다.
     * 휴게소는 카카오 분류가 없어 키워드로 찾는다. 모르는 칩이면 null.
     */
    fun nearbyFor(chip: String, center: GeoPoint, radiusM: Int, size: Int = 3): List<Place>? {
        if (!enabled) return null
        val where = "x=${center.lng}&y=${center.lat}&radius=$radiusM&sort=distance&size=$size"
        return when (val code = ROUTE_CATEGORY[chip]) {
            null -> if (chip == "휴게소") request("keyword.json?query=${URLEncoder.encode("휴게소", "UTF-8")}&$where") else null
            else -> request("category.json?category_group_code=$code&$where")
        }
    }

    /**
     * [center] 반경 [radiusM] 안에서 키워드로 찾고, 분류 마지막 단계가 [category]인 것만(예: "화장실" → 「가정,생활 > 화장실」).
     * 도착 화면 시설 지도용. 실패하면 null.
     */
    fun keywordNear(query: String, category: String, center: GeoPoint, radiusM: Int, size: Int = 15): List<Place>? {
        if (!enabled) return null
        val q = URLEncoder.encode(query, "UTF-8")
        return request("keyword.json?query=$q&x=${center.lng}&y=${center.lat}&radius=$radiusM&sort=distance&size=$size")
            ?.filter { it.kindLabel == category }
    }

    /** 칩 → 카카오 카테고리 그룹 코드(로컬 API 문서). */
    private val ROUTE_CATEGORY = mapOf("주차장" to "PK6", "편의점" to "CS2", "카페" to "CE7", "식당" to "FD6", "약국" to "PM9")

    private fun request(path: String): List<Place>? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://dapi.kakao.com/v2/local/search/$path")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000; readTimeout = 5000; useCaches = false
                setRequestProperty("Authorization", "KakaoAK ${BuildConfig.KAKAO_REST_KEY}")
            }
            if (conn.responseCode != 200) {
                Log.w(TAG, "${path.substringBefore('?')}: HTTP ${conn.responseCode}")   // 키·검색어는 로그에 남기지 않는다
                return null
            }
            val docs = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }).getJSONArray("documents")
            (0 until docs.length()).map { parse(docs.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.w(TAG, "${path.substringBefore('?')}: ${e.javaClass.simpleName}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parse(o: JSONObject): Place {
        val name = o.getString("place_name")
        val road = o.optString("road_address_name")
        val address = road.ifBlank { o.optString("address_name") }
        // "여행 > 관광,명소 > 공원" → "공원". 분류가 없으면(주소 검색 결과 등) 비운다.
        val category = o.optString("category_name").split('>').lastOrNull()?.trim().orEmpty()
        return Place(
            id = ID_PREFIX + o.getString("id"),
            name = name,
            kind = if ("휴게소" in name) PlaceKind.RestArea else PlaceKind.Attraction,
            facilities = emptyList(),
            lat = o.optString("y").toDoubleOrNull(),
            lng = o.optString("x").toDoubleOrNull(),
            address = address,
            kindLabel = category,
            region = address.substringBefore(' ').let { REGION_SHORT[it] ?: it },
            source = SOURCE,
        )
    }

    /** 카카오 주소의 시·도 표기를 앱 데이터(region)와 같은 짧은 이름으로. */
    private val REGION_SHORT = mapOf(
        "서울특별시" to "서울", "부산광역시" to "부산", "대구광역시" to "대구", "인천광역시" to "인천",
        "광주광역시" to "광주", "대전광역시" to "대전", "울산광역시" to "울산", "세종특별자치시" to "세종",
        "경기도" to "경기", "강원특별자치도" to "강원", "충청북도" to "충북", "충청남도" to "충남",
        "전북특별자치도" to "전북", "전라남도" to "전남", "경상북도" to "경북", "경상남도" to "경남", "제주특별자치도" to "제주",
    )
}
