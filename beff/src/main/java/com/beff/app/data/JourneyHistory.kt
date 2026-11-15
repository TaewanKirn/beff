package com.beff.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

/**
 * 지난 여정 한 건(F-4 · F-5). 목적지에 도착했을 때(또는 하차를 마쳤을 때) 기기 안에만 남긴다.
 * [minutes]는 이번 실행에서 안내를 시작한 시각부터 도착까지 잰 값이다. 앱을 다시 켜서 이어 간 여정은 잴 수 없어 null.
 * [facilities]는 도착지에서 함께 탄 분이 쓸 수 있던 시설(프로필 필터 ∩ 장소 시설)이다. 실제로 썼는지는 모른다.
 */
data class PastJourney(
    val placeId: String,
    val name: String,
    val kind: PlaceKind,
    val lat: Double?,
    val lng: Double?,
    val at: Long,                 // 도착 시각(epoch ms)
    val profileLabel: String?,    // "동생" · "아이들" · "휠체어 1명"(일회용)
    val stopNames: List<String>,
    val minutes: Int?,
    val facilities: List<String>,
) {
    /** 다시 켠 뒤 id로 찾을 수 없는 장소(카카오 장소 등)를 다시 가기 위해 최소 정보로 되살린다. */
    fun toPlace(): Place = Place(placeId, name, kind, emptyList(), lat = lat, lng = lng)
}

/** 지난 여정 저장소. 최근 것이 앞, 최대 [MAX]건. 외부로 보내지 않는다. */
class JourneyHistory(context: Context) {
    private val prefs = context.getSharedPreferences("beff_history", Context.MODE_PRIVATE)

    /** 화면이 바로 읽는 목록(최근 것이 앞). */
    val items = mutableStateListOf<PastJourney>().also { it.addAll(load()) }

    /** 같은 날 같은 곳은 한 건으로 친다(도착 + 하차 마침이 겹쳐도 한 번). 나중 기록이 빈 값을 덮지 않게 합친다. */
    fun record(place: Place, profile: Profile?, stops: List<Place>, minutes: Int?, facilities: List<String>) {
        val now = System.currentTimeMillis()
        val label = profile?.let { if (it.id.isEmpty()) it.summary else it.name }
        val prev = items.firstOrNull { it.placeId == place.id && sameDay(it.at, now) }
        val entry = PastJourney(
            placeId = place.id, name = place.name, kind = place.kind, lat = place.lat, lng = place.lng,
            at = prev?.at ?: now,
            profileLabel = label ?: prev?.profileLabel,
            stopNames = stops.map { it.name }.ifEmpty { prev?.stopNames.orEmpty() },
            minutes = minutes ?: prev?.minutes,
            facilities = facilities.ifEmpty { prev?.facilities.orEmpty() },
        )
        if (prev != null) items.remove(prev)
        items.add(0, entry)
        while (items.size > MAX) items.removeAt(items.lastIndex)
        save()
    }

    fun clear() {
        items.clear()
        prefs.edit().clear().apply()
    }

    /** 화면 점검 전용: 저장하지 않고 목록만 채운다(앱을 다시 켜면 사라진다). */
    fun debugShow(list: List<PastJourney>) {
        items.clear()
        items.addAll(list)
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val z = java.time.ZoneId.systemDefault()
        return java.time.Instant.ofEpochMilli(a).atZone(z).toLocalDate() == java.time.Instant.ofEpochMilli(b).atZone(z).toLocalDate()
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach { p ->
            arr.put(JSONObject().apply {
                put("placeId", p.placeId); put("name", p.name); put("kind", p.kind.name)
                put("lat", p.lat ?: JSONObject.NULL); put("lng", p.lng ?: JSONObject.NULL)
                put("at", p.at)
                put("profile", p.profileLabel ?: JSONObject.NULL)
                put("stops", JSONArray(p.stopNames))
                put("minutes", p.minutes ?: JSONObject.NULL)
                put("facilities", JSONArray(p.facilities))
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<PastJourney> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                fun strs(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                PastJourney(
                    placeId = o.getString("placeId"),
                    name = o.getString("name"),
                    kind = runCatching { PlaceKind.valueOf(o.getString("kind")) }.getOrDefault(PlaceKind.Attraction),
                    lat = if (o.isNull("lat")) null else o.getDouble("lat"),
                    lng = if (o.isNull("lng")) null else o.getDouble("lng"),
                    at = o.getLong("at"),
                    profileLabel = if (o.isNull("profile")) null else o.getString("profile"),
                    stopNames = strs("stops"),
                    minutes = if (o.isNull("minutes")) null else o.getInt("minutes"),
                    facilities = strs("facilities"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY = "past_journeys"
        const val MAX = 20
    }
}
