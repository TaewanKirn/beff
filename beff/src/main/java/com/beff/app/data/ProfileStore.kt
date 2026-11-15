package com.beff.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 기기 안 로컬 저장(외부 API 없음). 정책 12: 수단별 인원 · 추천 장소 필터 · 탑승자별 인물 · 이름. */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("beff_profiles", Context.MODE_PRIVATE)

    fun load(): List<Profile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> arr.getJSONObject(i).toProfile() }
        }.getOrDefault(emptyList())
    }

    fun save(profiles: List<Profile>) {
        val arr = JSONArray()
        profiles.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    fun saveRecents(items: List<RecentItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("text", it.text); put("date", it.date)
                put("placeId", it.placeId ?: JSONObject.NULL)
                put("mobility", it.mobility?.key ?: JSONObject.NULL)
            })
        }
        prefs.edit().putString(KEY_RECENTS, arr.toString()).apply()
    }

    fun loadRecents(): List<RecentItem> {
        val raw = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RecentItem(
                    o.getString("text"), o.getString("date"),
                    if (o.isNull("placeId")) null else o.getString("placeId"),
                    if (o.isNull("mobility")) null else Mobility.of(o.getString("mobility")),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** 진행 중인 여정 + 함께 타는 사람. 시스템이 앱을 다시 시작해도 이어 가려고 저장한다. */
    data class Session(val active: Profile, val destId: String, val stopIds: List<String>, val page: Int)

    fun saveSession(active: Profile?, destId: String?, stopIds: List<String>, page: Int) {
        if (active == null || destId == null) {
            prefs.edit().remove(KEY_SESSION).apply(); return
        }
        val o = JSONObject().apply {
            put("active", active.toJson())
            put("dest", destId)
            put("stops", JSONArray(stopIds))
            put("page", page)
        }
        prefs.edit().putString(KEY_SESSION, o.toString()).apply()
    }

    fun loadSession(): Session? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val st = o.getJSONArray("stops")
            Session(o.getJSONObject("active").toProfile(), o.getString("dest"), (0 until st.length()).map { st.getString(it) }, o.optInt("page", 0))
        }.getOrNull()
    }

    /** 저장한 프로필 · 최근 검색 · 여정 · 자동 이름 번호를 모두 지운다(설정 탭 "앱 초기화"). */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /** 자동 이름 번호는 순서대로 늘리고 지운 번호는 다시 쓰지 않는다(정책 18). */
    fun nextAutoNumber(): Int {
        val n = prefs.getInt(KEY_AUTO_NO, 0) + 1
        prefs.edit().putInt(KEY_AUTO_NO, n).apply()
        return n
    }

    private fun Profile.toJson() = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("counts", JSONObject().also { o -> counts.forEach { (m, c) -> o.put(m.key, c) } })
        put("filters", JSONArray(filters.toList()))
        put("people", JSONArray(people.map { it.key }))
        put("seats", JSONObject().also { o -> seats.forEach { (i, s) -> o.put(i.toString(), s) } })
        put("avatar", avatar)
    }

    private fun JSONObject.toProfile(): Profile {
        val c = getJSONObject("counts")
        val f = getJSONArray("filters")
        val p = getJSONArray("people")
        return Profile(
            id = getString("id"),
            name = getString("name"),
            counts = Mobility.entries.associateWith { c.optInt(it.key, 0) }.filterValues { it > 0 },
            filters = (0 until f.length()).map { f.getString(it) }.toSet(),
            people = (0 until p.length()).map { Person.of(p.getString(it)) },
            // 좌석은 1110에 추가됐다. 예전 저장분에는 없으므로 비어 있으면 빈 값.
            seats = optJSONObject("seats")?.let { s -> s.keys().asSequence().associate { it.toInt() to s.getString(it) } }.orEmpty(),
            avatar = optInt("avatar", -1),   // 1110 A-6. 예전 저장분에는 없다.
        )
    }

    private companion object {
        const val KEY_PROFILES = "profiles"
        const val KEY_AUTO_NO = "auto_no"
        const val KEY_SESSION = "session"
        const val KEY_RECENTS = "recents"
    }
}
