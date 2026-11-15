package com.beff.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.beff.app.data.KakaoLocal
import com.beff.app.data.FilterCatalog
import com.beff.app.data.MAX_PROFILES
import com.beff.app.data.Mobility
import com.beff.app.data.Person
import com.beff.app.device.LocationBridge
import com.beff.app.device.SpeechBridge
import com.beff.app.device.SpeechState
import com.beff.app.device.VehicleBridge
import com.beff.app.device.VoiceGuide
import com.beff.app.nav.NaviBridge
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull
import com.beff.app.ui.withEul
import com.beff.app.data.AssetPlaceRepository
import com.beff.app.data.GeoPoint
import com.beff.app.data.Place
import com.beff.app.data.PlaceRepository
import com.beff.app.data.RecentItem
import com.beff.app.data.Profile
import com.beff.app.data.ProfileStore
import java.util.UUID

/** 도착으로 보는 반경(m). 위치 기준 직선거리라 실제 주차장까지의 거리와 다르다 [가정]. */
private const val ARRIVAL_RADIUS_M = 150f

/** 안내를 시작한 뒤 이 시간 안에 오는 내비 취소 신호는 여정 종료로 보지 않는다(새 경로 요청 때 이전 경로가 지워지며 오는 신호). */
private const val CANCEL_GRACE_MS = 5000L
private const val DEBUG_CANCEL_IGNORE_MS = 20_000L

/** 다시 켰을 때 내비 경로 상태를 기다리는 시간. 이 안에 경로가 없으면 되살린 여정을 비운다. */
private const val RESTORE_CHECK_MS = 4000L

/** 경유지에 이만큼(직선 m) 가까워지면 음성으로 미리 알린다 [가정]. */
private const val STOP_NOTICE_M = 1000f

/** 내비가 알려 준 목적지까지 남은 거리(m)가 이만큼이면 음성으로 미리 알린다. */
private const val DEST_NOTICE_M = 3000f

/** 아직 넣지 않은 쉬어갈 곳에 이만큼(직선 m) 가까워지면 주행 중 음성으로 추천한다 [가정]. */
private const val SUGGEST_NOTICE_M = 2000f

/** 검색창 입력이 이만큼 멈추면 카카오 검색을 보낸다(글자마다 보내지 않게). */
private const val SEARCH_DEBOUNCE_MS = 350L

enum class Flow { Create, OneTime }
enum class Tab { Home, Search, Journey, Settings }

sealed interface Route {
    data object Splash : Route
    data object ProfileSelect : Route
    data class MobilityStep(val flow: Flow) : Route
    // 1115: 앉은 자리 선택(A-4)은 뺐다 — 공개 Pleos SDK에 승객 좌석 · 시트 제어 API가 없다.
    data class FilterStep(val flow: Flow) : Route
    data object PeopleStep : Route
    data object ReplaceProfile : Route
    data class Main(val tab: Tab) : Route
    data class Briefing(val placeId: String) : Route
    /** [preview] = 출발 전 하차 브리핑 미리보기(D-5). false면 도착 화면(G-2, 경유지면 G-1). */
    data class Unload(val placeId: String, val preview: Boolean = false) : Route
    data class Alternatives(val placeId: String, val manual: Boolean = false, val byUser: Boolean = false) : Route
    data class UnloadMap(val placeId: String) : Route
    /** 설정 > 저작권 · 출처: 데이터 출처와 사진 저작자 표기. */
    data object Credits : Route
    /** 위치정보 · 개인정보 동의(1115). [review] = 설정에서 다시 연 것. */
    data class Consent(val review: Boolean = false) : Route
    /** 설정 > 연동 상태(내비 · 위치 · 기어 · 음성). */
    data object DeviceStatus : Route
    /** 1110 H-2 설정 > 프로필 편집. 편집 중인 사본은 [AppViewModel.editProfile]. */
    data object ProfileEdit : Route
}

/** 프로필·일회용 이동 작성 중 상태. */
data class Draft(
    val counts: Map<Mobility, Int> = emptyMap(),
    val filters: Set<String> = emptySet(),
    val people: Map<Int, Person> = emptyMap(), // 탑승자 순번(0..) → 인물
    val seats: Map<Int, String> = emptyMap(),  // 탑승자 순번(0..) → 앉는 자리(SeatKind.key)
    val name: String = "우리 가족",
    val avatar: Int = 0,                       // 1110 A-6 프로필 이미지(얼굴 아바타 순번)
) {
    val total get() = counts.values.sum()

    /** 탑승자 목록: 유모차 1, 유모차 2, 휠체어 1 … */
    val riders: List<Pair<Mobility, Int>>
        get() = Mobility.entries.flatMap { m -> (1..(counts[m] ?: 0)).map { m to it } }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProfileStore(app)

    /** 기본 내비(NaviHelper) 연동. 연결은 앱 시작 때 한 번만 시도한다. */
    val nav = NaviBridge(app).also { it.connect() }

    /** 차량 위치(FusedLocation). 권한을 받은 뒤 [onLocationPermission]에서 시작한다. */
    init { com.beff.app.data.Consent.load(app) }
    val location = LocationBridge(app)

    /** 차량 상태 읽기(Vehicle 기어 · AAOS 주행 제한). 주차(P) 감지에 쓴다. */
    val vehicle = VehicleBridge(app).also { it.start() }

    /** 음성 안내(TextToSpeech). 주행 구역은 화면 조작 없이 음성으로만 다룬다. */
    val voice = VoiceGuide(app).also { it.start() }

    /** 음성 검색(SpeechToText). 마이크를 누를 때 처음 초기화한다. */
    val speech = SpeechBridge(app)

    /**
     * 검색창 마이크: 듣는 중이면 멈추고, 아니면 검색 탭으로 가서 듣기 시작한다.
     * 중간 결과는 검색창에 바로 채우고, 최종 결과가 오면 그 글자로 검색 결과가 나온다(자동 출발은 하지 않는다).
     */
    fun toggleVoiceSearch() {
        if (speech.state != SpeechState.Idle) { speech.stop(); return }
        if (vehicle.drivingRestricted) return // 정차 중에만
        if (route != Route.Main(Tab.Search)) selectTab(Tab.Search)
        searchQuery = ""
        speech.listen(partial = { searchQuery = it }, final = { searchQuery = it })
    }

    /** E-V 경유지 변경 · 음성 전용(4-9). [경유지 변경하기]를 누르면 듣기 시작한다. 주행 중에도 쓴다(음성 입력). */
    var voiceChange by mutableStateOf(false)
        private set

    private var voiceChangeJob: kotlinx.coroutines.Job? = null

    /**
     * 말이 없거나 못 알아들으면 저절로 닫는다(1113): 듣기가 끝났는데(Idle) 결과가 없으면 2초 뒤,
     * 아무 말 없이 [VOICE_CHANGE_TIMEOUT_MS]가 지나도 닫는다.
     */
    fun startVoiceChange() {
        if (journey == null) return
        voiceChange = true
        speech.listen(partial = {}, final = { handleVoiceChange(it) })
        voiceChangeJob?.cancel()
        voiceChangeJob = viewModelScope.launch {
            launch { delay(VOICE_CHANGE_TIMEOUT_MS); if (voiceChange) cancelVoiceChange() }
            snapshotFlow { speech.state }.collect { st ->
                if (st == SpeechState.Idle && voiceChange) { delay(2_000); if (voiceChange && speech.state == SpeechState.Idle) cancelVoiceChange() }
            }
        }
    }

    fun cancelVoiceChange() { voiceChange = false; speech.stop(); voiceChangeJob?.cancel(); voiceChangeJob = null }

    /** 「다른 쉼터로 바꿔 줘」 → 다음 후보로 바꿈 · 「쉬지 않고 갈래」 → 경유지 빼기 · 「취소」 → 닫기. 못 알아들으면 닫는다. */
    private fun handleVoiceChange(text: String) {
        val t = text.replace(" ", "")
        val j = journey
        when {
            j == null || "취소" in t -> {}
            "쉬지않" in t || "그냥" in t || "빼" in t -> j.stops.toList().forEach { removeRestStop(it) }
            "다른" in t || "바꿔" in t -> {
                val cur = j.stops.firstOrNull()
                val next = restSuggestions().firstOrNull { s -> j.stops.none { it.id == s.id } }
                if (cur != null) removeRestStop(cur)
                if (next != null) addRestStop(next)
            }
        }
        cancelVoiceChange()
    }

    override fun onCleared() {
        nav.release()
        location.release()
        vehicle.release()
        voice.release()
        speech.release()
        super.onCleared()
    }

    private var locationGranted = false

    /** 차량 위치 권한. 위치정보 동의(1115)가 있을 때만 FusedLocation을 켠다. */
    fun onLocationPermission(granted: Boolean) {
        locationGranted = granted
        if (granted && com.beff.app.data.Consent.locationAllowed) location.start()
    }

    /** 스플래시 다음: 위치정보 동의를 아직 안 받았으면 동의 화면(1115), 받았으면 하던 여정(F) 또는 프로필 선택. */
    fun afterSplash() {
        replaceAll(
            when {
                com.beff.app.data.Consent.state == com.beff.app.data.Consent.State.Unknown -> Route.Consent()
                journey != null -> Route.Main(Tab.Journey)
                else -> Route.ProfileSelect
            },
        )
    }

    /** 동의 화면에서 답한 뒤: 위치 사용을 켜거나 끄고, 첫 실행이면 다음 화면으로, 설정에서 열었으면 돌아간다. */
    fun onConsentChanged(review: Boolean) {
        if (com.beff.app.data.Consent.locationAllowed) { if (locationGranted) location.start() } else location.release()
        if (review) back() else replaceAll(if (journey != null) Route.Main(Tab.Journey) else Route.ProfileSelect)
    }

    val profiles = mutableStateListOf<Profile>().also { it.addAll(store.load()) }
    val backStack = mutableStateListOf<Route>(Route.Splash)
    val route: Route get() = backStack.last()

    /** 현재 여정에 쓰는 "함께 타는 사람"(저장 프로필이거나 일회용). */
    var active by mutableStateOf<Profile?>(null)
        private set

    var draft by mutableStateOf(Draft())
        private set

    var oneTimeFlowStarted by mutableStateOf(false)
        private set

    // 팝업 상태 (02-4 · 02-5 · 02-7)
    var askRegister by mutableStateOf(false)
    var registeredDone by mutableStateOf(false)
    var deleteTarget by mutableStateOf<Profile?>(null)

    fun push(r: Route) { backStack.add(r) }

    /** 화면 점검: 프로필 선택 → 이동 보조수단(1) → 시설(2) → 이미지 · 이름(3) 중 [depth]까지 쌓는다 */
    private fun stepStack(flow: Flow, depth: Int) {
        replaceAll(Route.ProfileSelect)
        listOf(Route.MobilityStep(flow), Route.FilterStep(flow), Route.PeopleStep).take(depth).forEach(::push)
    }
    fun replaceAll(r: Route) { backStack.clear(); backStack.add(r) }
    fun back(): Boolean {
        if (askRegister) { askRegister = false; return true }
        if (deleteTarget != null) { deleteTarget = null; return true }
        if (pendingStop != null) { pendingStop = null; return true }
        if (needDestination) { needDestination = false; return true }
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    // ── 프로필 선택(01) ──
    fun selectProfile(p: Profile) {
        active = p
        replaceAll(Route.Main(Tab.Home))
    }

    fun startCreate() {
        draft = Draft()
        push(Route.MobilityStep(Flow.Create))
    }

    fun startOneTime() {
        draft = Draft()
        push(Route.MobilityStep(Flow.OneTime))
    }

    // ── 수단 · 인원(01-4 · 02-1/2) ──
    fun toggleMobility(m: Mobility) {
        val cur = draft.counts[m] ?: 0
        draft = if (cur > 0) draft.copy(counts = draft.counts - m)
        else if (draft.total < 3) draft.copy(counts = draft.counts + (m to 1))
        else draft
    }

    fun changeCount(m: Mobility, delta: Int) {
        val cur = draft.counts[m] ?: 0
        val next = cur + delta
        if (delta > 0 && draft.total >= 3) return
        draft = if (next <= 0) draft.copy(counts = draft.counts - m) else draft.copy(counts = draft.counts + (m to next))
    }

    fun goFilterStep(flow: Flow) {
        // 고른 수단에 따라 칩 자동 선택(정책 29)
        val auto = draft.counts.keys.flatMap { FilterCatalog.forMobility(it).autoOn }.toSet()
        draft = draft.copy(filters = auto)
        push(Route.FilterStep(flow))
    }

    // ── 추천 장소 필터(01-5 · 02-3) ──
    fun toggleFilter(chip: String) {
        draft = draft.copy(filters = if (chip in draft.filters) draft.filters - chip else draft.filters + chip)
    }

    fun setAllFilters(on: Boolean) {
        val groups = draft.counts.keys.map { FilterCatalog.forMobility(it) } + FilterCatalog.route
        draft = draft.copy(filters = if (on) groups.flatMap { it.chips }.toSet() else emptySet())
    }

    fun goPeopleStep() { push(Route.PeopleStep) }

    // ── 프로필 이미지 · 이름(01-6) ──
    fun pickPerson(riderIndex: Int, person: Person) {
        draft = draft.copy(people = draft.people + (riderIndex to person))
    }

    fun setName(name: String) { draft = draft.copy(name = name.take(10)) }

    /** 1110 A-6: 프로필 이미지(얼굴 아바타) 고르기. */
    fun pickAvatar(index: Int) { draft = draft.copy(avatar = index) }

    fun saveCreatedProfile() {
        val d = draft
        val p = Profile(
            id = UUID.randomUUID().toString(),
            name = d.name.trim(),
            counts = d.counts,
            filters = d.filters,
            people = d.riders.indices.mapNotNull { d.people[it] },
            seats = d.seats,
            avatar = d.avatar,
        )
        profiles.add(p)
        store.save(profiles.toList())
        active = p
        replaceAll(Route.Main(Tab.Home))
    }

    // ── 일회용 이동(02) ──
    /** 02-3 완료 → 02-4 팝업. */
    fun finishOneTimeFilters() { askRegister = true }

    /** 괜찮아요: 저장 없이 홈으로(정책 16). */
    fun skipRegister() {
        askRegister = false
        active = oneTimeProfile()
        replaceAll(Route.Main(Tab.Home))
    }

    /** 프로필로 등록: 이름 · 이미지를 묻지 않고 「이동 N」으로 자동 저장(15). 4개면 지울 프로필 선택(19). */
    fun registerOneTime() {
        askRegister = false
        if (profiles.size >= MAX_PROFILES) {
            push(Route.ReplaceProfile)
        } else {
            commitOneTime()
        }
    }

    /** 01 프로필 편집(지우기) 모드. */
    var editingProfiles by mutableStateOf(false)
        private set

    fun toggleProfileEdit() { editingProfiles = !editingProfiles }

    fun confirmDelete() {
        val target = deleteTarget ?: return
        deleteTarget = null
        profiles.removeAll { it.id == target.id }
        if (active?.id == target.id) active = null
        if (route is Route.ReplaceProfile) {
            commitOneTime()   // 02-6: 하나 지우고 「이동 N」을 등록
        } else {
            store.save(profiles.toList())
            if (profiles.isEmpty()) editingProfiles = false
        }
    }

    fun skipFromReplace() {
        active = oneTimeProfile()
        replaceAll(Route.Main(Tab.Home))
    }

    private fun oneTimeProfile(): Profile {
        val d = draft
        return Profile("", "오늘의 이동", d.counts, d.filters, emptyList(), d.seats)
    }

    private fun commitOneTime() {
        val d = draft
        val p = Profile(
            id = UUID.randomUUID().toString(),
            name = "이동 ${store.nextAutoNumber()}",
            counts = d.counts,
            filters = d.filters,
            people = emptyList(),
            seats = d.seats,
        )
        profiles.add(p)
        store.save(profiles.toList())
        active = p
        registeredDone = true
    }

    /** 완료 팝업: 확인을 누르거나 3초가 지나면 홈으로(17). */
    fun finishRegisteredDone() {
        registeredDone = false
        replaceAll(Route.Main(Tab.Home))
    }

    // ───────── 03~06: 홈 · 검색 · 여정 · 출발 브리핑 ─────────

    val places: PlaceRepository = AssetPlaceRepository(app)

    /** 지난 여정(F-4 · F-5). 목적지에 도착하거나 하차를 마치면 [recordPastJourney]가 남긴다. */
    val history = com.beff.app.data.JourneyHistory(app)

    val recents = mutableStateListOf<RecentItem>().also { it.addAll(store.loadRecents().filter { r -> r.placeId == null || places.placeOrNull(r.placeId) != null }) }

    /** 진행 중인 여정. null이면 03-1(기본) · 05 빈 화면. */
    /** 05-2d 「도착 전에 알려드려요」 경고를 띄울지. 제보를 받는 서버가 아직 없어 디버그 직행으로만 켠다 [미확인]. */
    var warnBeforeArrival by mutableStateOf(false)

    var journey by mutableStateOf<Journey?>(null)
        private set

    /** 다가간다고 미리 알린 경유지 id(한 번만 말한다). 여정이 끝나면 비운다. init의 감시보다 먼저 만들어 둔다. */
    private val stopNoticed = mutableSetOf<String>()

    /** 다가간다고 미리 알린 목적지 id(한 번만 말한다). 여정이 끝나면 비운다. */
    private var destNoticedFor: String? = null

    /** 마지막으로 안내를 시작한 시각(elapsedRealtime). 직후에 오는 내비 취소 신호를 무시하는 데 쓴다. */
    private var journeyStartedAt = 0L
    /** 점검 페이지 화면 이동이 내비 안내를 끊을 때, 그 취소 신호로 여정이 지워지지 않게 무시하는 시각(elapsedRealtime). */
    private var ignoreNaviCancelUntil = 0L

    /** 주행 중 음성으로 추천한 쉬어갈 곳 id(한 번만 말한다). 여정이 끝나면 비운다. */
    private val suggestNoticed = mutableSetOf<String>()

    /** 데모 녹화용: 주행 중 가림 화면에 방금 말한 음성 안내를 자막으로 보인다(소리가 녹화되지 않아서). 점검용 직행 captions로만 켠다. */
    var demoCaptions by mutableStateOf(false)
        private set

    /**
     * 프로필 칩으로 찾은 경유지 후보(목적지 · 프로필 기준 키, 결과). 화면이 [loadRouteStops]로 채운다.
     * init 블록이 [restSuggestions]를 바로 부르므로 반드시 init보다 위에 선언한다(아래에 두면 복원된 여정이 있을 때 NPE).
     */
    private var routeStops by mutableStateOf<Pair<String, List<Place>>?>(null)

    init {
        // 시스템이 앱을 다시 시작해도 하던 여정을 이어 간다.
        store.loadSession()?.let { ss ->
            runCatching {
                active = ss.active
                journey = Journey(
                    dest = places.place(ss.destId),
                    // 카카오에서 찾은 경유지는 다시 켜면 id로 찾을 수 없어 빼고 이어 간다.
                    stops = ss.stopIds.mapNotNull { places.placeOrNull(it) },
                    page = ss.page,
                )
                // 시작 화면은 스플래시 그대로 두고, 끝난 뒤 홈으로 간다(MainActivity의 스플래시 onDone).
            }.onFailure { journey = null; active = null }
        }
        // observeDeviceEvents()는 클래스 맨 끝 init에서 시작한다(감시가 아래쪽 상태값을 읽으므로 모두 만들어진 뒤에).
    }

    // ── 기기 연동: 경유지 도착 음성 · 도착 + 주차(P) → 하차 공간 확인 ──
    /** 도착 안내(음성)를 한 목적지 id. 여정이 끝나면 비운다. */
    private var arrivalAnnouncedFor: String? = null

    /** 하차 화면을 연 목적지 id. */
    private var arrivalOpenedFor: String? = null

    /** 경유지 도착 화면(G-1)을 연 경유지 id. */
    private var stopOpenedFor: String? = null

    private data class ArrivalState(val destId: String?, val arrived: Boolean, val parked: Boolean)

    private fun arrivalState(): ArrivalState {
        val dest = journey?.dest
        // 도착 = 내비의 도착 이벤트, 또는 차량 위치가 목적지 좌표 반경 안. 직선거리라 [가정] 반경이다.
        val near = dest?.let { d ->
            val la = d.lat
            val lo = d.lng
            if (la != null && lo != null) location.metersTo(la, lo) else null
        }?.let { it <= ARRIVAL_RADIUS_M } == true
        return ArrivalState(dest?.id, nav.destinationArrived || near, vehicle.parked)
    }

    private fun observeDeviceEvents() {
        viewModelScope.launch {
            snapshotFlow { nav.lastWaypointArrived }.filterNotNull().collect { name ->
                // 1113: 경유지 도착은 장소 이름 없이 「경유지에 도착했어요」(사용자 요청). 목적지 도착 문구와 구분한다.
                voice.say("경유지에 도착했어요", chime = true)
            }
        }
        // 내비에서 안내를 취소하면(운전자가 [안내 종료]) 베프 여정도 끝낸다 → 홈은 목적지 없는 기본 화면으로 돌아간다.
        // 도착으로 끝난 경우(하차 흐름)는 그대로 두고, 안내를 막 시작한 직후의 취소(내비가 이전 경로를 지우며 보내는 신호)는 무시한다.
        viewModelScope.launch {
            snapshotFlow { nav.routeCancelCount }.drop(1).collect {
                val j = journey ?: return@collect
                if (nav.destinationArrived) return@collect
                if (android.os.SystemClock.elapsedRealtime() - journeyStartedAt < CANCEL_GRACE_MS) return@collect
                if (android.os.SystemClock.elapsedRealtime() < ignoreNaviCancelUntil) return@collect
                android.util.Log.i("BeffNavi", "내비 안내 취소 → 여정 종료(${j.dest.name})")
                journey = null
                persistSession()
                if (route is Route.Main) selectTab(Tab.Home)
            }
        }
        // 주행 중(기어가 P가 아님) 아직 넣지 않은 쉬어갈 곳에 [SUGGEST_NOTICE_M] 안으로 다가가면 음성으로 한 번 추천한다.
        // 주행 중에는 화면 조작을 요구하지 않으므로 「차를 세운 뒤」 넣으라고만 말한다(정책: 주행 구역 = 음성 전용).
        viewModelScope.launch {
            snapshotFlow {
                val j = journey ?: return@snapshotFlow null
                if (vehicle.parked || j.stops.size >= 2) return@snapshotFlow null
                restSuggestions().firstOrNull { s -> j.stops.none { it.id == s.id } && s.id !in suggestNoticed && s.lat != null && s.lng != null }
                    ?.let { s -> s to location.metersTo(s.lat!!, s.lng!!) }
            }.collect { pair ->
                if (journey == null) suggestNoticed.clear()
                val (s, m) = pair ?: return@collect
                if (m == null || m > SUGGEST_NOTICE_M) return@collect
                suggestNoticed += s.id
                val what = s.note ?: places.facilitiesFor(s, active).take(2).joinToString(", ").ifBlank { null }
                voice.say(
                    "가는 길 ${(SUGGEST_NOTICE_M / 1000).toInt()}킬로미터 앞에 들를 만한 곳이 있어요. ${s.name}" +
                        (what?.let { ", $it 있어요." } ?: ".") + " 들르려면 차를 세운 뒤 여정에서 경유지로 넣어 주세요.",
                    chime = true,
                )
            }
        }
        // 경유지에 다가가면(직선 [STOP_NOTICE_M] 안) 한 번 미리 알린다: 어디인지 + 있는 시설(또는 고른 칩).
        viewModelScope.launch {
            snapshotFlow {
                val j = journey ?: return@snapshotFlow null
                j.stops.firstOrNull { it.id !in stopNoticed && it.lat != null && it.lng != null }
                    ?.let { s -> s to location.metersTo(s.lat!!, s.lng!!) }
            }.collect { pair ->
                if (journey == null) stopNoticed.clear()
                val (s, m) = pair ?: return@collect
                if (m == null || m > STOP_NOTICE_M) return@collect
                stopNoticed += s.id
                val what = s.note ?: places.facilitiesFor(s, active).take(2).joinToString(", ").ifBlank { null }
                voice.say("곧 경유지 ${s.name}에 도착해요." + (what?.let { " $it 있어요." } ?: ""), chime = true)
            }
        }
        // 목적지까지 내비가 알려 준 남은 거리(onDestinationInfo)가 [DEST_NOTICE_M] 안으로 들어오면 한 번 미리 알린다. 주행 중이라 음성만 쓴다.
        // 내비 값이 아직 없을 때만 위치 기준 직선거리로 대신한다 [가정].
        viewModelScope.launch {
            snapshotFlow {
                val d = journey?.dest ?: return@snapshotFlow null
                val remain = nav.route?.remainMeters?.takeIf { it > 0 }?.toFloat()
                    ?: d.lat?.let { la -> d.lng?.let { lo -> location.metersTo(la, lo) } }
                remain?.let { Triple(d, it, nav.route?.totalMeters ?: 0) }
            }.collect { t ->
                if (journey == null) destNoticedFor = null
                val (d, m, total) = t ?: return@collect
                if (destNoticedFor == d.id || m > DEST_NOTICE_M || nav.destinationArrived) return@collect
                destNoticedFor = d.id
                val what = places.facilitiesFor(d, active).take(2).joinToString(", ").ifBlank { null }
                // 전체 경로가 3km보다 짧으면 출발하자마자 걸리므로 거리 숫자를 말하지 않는다.
                val head = if (total > DEST_NOTICE_M) "목적지 ${d.name}까지 ${(DEST_NOTICE_M / 1000).toInt()}킬로미터 남았어요." else "곧 목적지 ${d.name}에 도착해요."
                voice.say(head + (what?.let { " $it 있어요." } ?: "") + " 주차하면 내리는 길을 안내할게요.", chime = true)
            }
        }
        viewModelScope.launch {
            snapshotFlow { arrivalState() }.collect { onArrivalState(it) }
        }
        // 1110 G-1: 경유지 도착(내비 이벤트) 뒤 주차(P)하면 경유지 도착 화면을 연다. 경유지마다 한 번.
        viewModelScope.launch {
            snapshotFlow { Triple(nav.lastWaypointArrived, vehicle.parked, journey) }.collect { (name, parked, j) ->
                if (name == null || !parked || j == null) return@collect
                val stop = j.stops.firstOrNull { it.name.contains(name) || name.contains(it.name) } ?: return@collect
                if (stopOpenedFor == stop.id) return@collect
                stopOpenedFor = stop.id
                if (route is Route.Main) push(Route.Unload(stop.id))
            }
        }
    }

    private fun onArrivalState(s: ArrivalState) {
        val id = s.destId
        if (id == null) { arrivalAnnouncedFor = null; arrivalOpenedFor = null; return }
        if (!s.arrived) return
        val place = runCatching { places.place(id) }.getOrNull() ?: return
        // 한 번에 한 문장으로 말한다(도착 + 주차가 같이 올 수 있다). 주행 중에도 음성 안내는 한다(화면 조작 요구 없음).
        val lines = mutableListOf<String>()
        if (arrivalAnnouncedFor != id) {
            arrivalAnnouncedFor = id
            recordPastJourney()
            lines += "${place.name}에 도착했어요."
        }
        // 화면을 바꾸는 것은 주차(기어 P)된 뒤에만. 주행 중에는 화면을 건드리지 않고, 주차되면 다시 평가된다.
        var open = false
        if (s.parked && arrivalOpenedFor != id) {
            arrivalOpenedFor = id
            if (route is Route.Main) {
                lines += "주차를 마쳤어요. 내리는 길을 보여 드릴게요."
                open = true
            }
        }
        if (lines.isNotEmpty()) voice.say(lines.joinToString(" "), chime = true)
        // 1115: 하차 공간 확인(06-4, 어라운드뷰 · 측면 센서)은 뺐다 — 주차하면 바로 도착 분기(06-2 / 07-1)로 간다.
        if (open) openUnload(id, preview = false)
    }

    /** 앱이 아는 장소(id가 저장소에 있는 것)의 여정만 저장한다. */
    private fun persistSession() {
        val j = journey
        val known = j != null && runCatching { places.place(j.dest.id) }.isSuccess
        if (known && active != null) store.saveSession(active, j!!.dest.id, j.stops.map { it.id }, j.page)
        else store.saveSession(null, null, emptyList(), 0)
    }

    var searchQuery by mutableStateOf("")

    /** 테마 카드를 눌러 연 "시설이 있는 곳" 검색의 시설 칩. */
    var searchChip by mutableStateOf<String?>(null)

    /** 근처 조회의 기준 위치. 내비가 알려 준 현재 위치, 없으면 서울시청 [가정]. */
    val origin: GeoPoint get() = nav.location ?: DEFAULT_ORIGIN
    val originIsDefault: Boolean get() = nav.location == null

    /** 카카오 전국 검색 결과(무장애 장소와 맞춘 것은 그 장소로 바꿔 둔다). [searchHitsQuery]가 지금 검색어와 같을 때만 쓴다. */
    private var searchHits by mutableStateOf<List<Place>?>(null)
    private var searchHitsQuery by mutableStateOf("")

    /** 바깥 검색을 시도했는데 실패했는지(인터넷 끊김 등). 이때는 앱 안 무장애 데이터에서만 찾는다. */
    var searchOffline by mutableStateOf(false)
        private set

    /** 지금 검색어의 결과가 카카오 전국 검색에서 왔는지. false면 앱 안 무장애 데이터(서울·경기·인천)에서 찾은 것이다. */
    val searchIsNationwide: Boolean get() = searchHits != null && searchHitsQuery == searchQuery

    /** 검색 탭 결과: 카카오 결과가 도착했으면 그것, 아니면(입력 중 · 키 없음 · 실패) 앱 안 데이터. */
    val searchResults: List<Place>
        get() = searchHits?.takeIf { searchHitsQuery == searchQuery } ?: places.search(searchQuery, origin)

    init {
        // 입력이 잠깐 멈추면 카카오에 묻는다. 새 글자가 오면 앞의 요청은 버린다.
        viewModelScope.launch {
            snapshotFlow { searchQuery }.collectLatest { q ->
                if (q.isBlank() || !KakaoLocal.enabled) { searchHits = null; searchOffline = false; return@collectLatest }
                delay(SEARCH_DEBOUNCE_MS)
                // 위치정보 동의 전 · 거부면 내 위치 대신 기본 좌표(서울시청)를 보낸다
                val from = if (com.beff.app.data.Consent.locationAllowed) origin else DEFAULT_ORIGIN
                val hits = withContext(Dispatchers.IO) {
                    KakaoLocal.search(q, from)?.map { p -> places.barrierFreeMatch(p) ?: p.also { places.register(it) } }?.distinctBy { it.id }
                }
                searchOffline = hits == null
                searchHits = hits
                searchHitsQuery = q
            }
        }
    }

    fun openChipSearch(chip: String) {
        searchChip = chip
        searchQuery = ""
        selectTab(Tab.Search)
    }

    /** 홈 · 검색 추천(앞 [com.beff.app.screens.HERO_COUNT]곳은 추천 카드, 나머지는 목록). 함께 타는 분 · 내 위치 기준. */
    fun recommendations(): List<com.beff.app.data.Recommendation> =
        // 사진이 있는 곳을 앞(추천 카드)으로 — 아직 사진이 붙은 곳이 일부(위키미디어 공용 53곳)라서. 같은 조건에서는 추천 점수 순을 지킨다.
        places.recommend(active, origin, limit = 9).sortedByDescending { it.place.image != null }.let { recs ->
            // 1113: 홈 첫 추천 카드는 누구나 아는 곳(경복궁)으로. 사진이 가장 알아보기 쉽다.
            val f = places.placeOrNull(FEATURED_PLACE_ID) ?: return@let recs
            val wanted = active?.filters.orEmpty()
            val matched = f.facilities.filter { it in wanted }
            val km = if (f.lat != null && f.lng != null) com.beff.app.data.distanceKm(origin, com.beff.app.data.GeoPoint(f.lat, f.lng)) else null
            val top = recs.firstOrNull { it.place.id == f.id } ?: com.beff.app.data.Recommendation(
                f, if (matched.isNotEmpty()) "${matched.take(2).joinToString(" · ")} 있어요" else "이동 편의 시설 ${f.facilities.size}가지", km,
            )
            listOf(top) + recs.filter { it.place.id != f.id }.take(8)
        }

    /** 현재 여정의 목적지 가는 길에 들를 만한 곳. 프로필 필터에 맞는 시설이 있는 곳만. */
    fun restSuggestions(): List<Place> = journey?.dest?.let { restSuggestionsFor(it) } ?: emptyList()

    /**
     * [dest]까지 가는 길에 쉬어갈 곳(최대 4곳). 1순위는 함께 타는 분이 실제로 내릴 수 있는 곳 — 프로필 필터에 맞는 무장애 시설이 있는 곳(앱 데이터).
     * 그다음(또는 그런 곳이 없으면) 프로필에서 고른 「가는 길에 들르는 곳」(휴게소 · 카페 …)으로 카카오에서 찾은 곳. 이 곳은 무장애 정보가 없다.
     */
    fun restSuggestionsFor(dest: Place): List<Place> {
        val accessible = places.restSuggestions(active, origin, dest)
        val chips = routeStops?.takeIf { it.first == routeKey(dest) }?.second.orEmpty()
        return (accessible + chips).distinctBy { it.id }.take(4)
    }

    private fun routeKey(dest: Place) = "${dest.id}|${active?.id}"

    /** 프로필 칩마다 경로(직선 근사) 위 두 지점 근처를 카카오에서 찾는다. 칩 하나에 한 곳, 최대 4곳. 이미 찾았으면 다시 찾지 않는다. */
    suspend fun loadRouteStops(dest: Place) {
        val key = routeKey(dest)
        if (routeStops?.first == key) return
        val chips = active?.filters.orEmpty().filter { it in FilterCatalog.route.chips }
        val la = dest.lat; val lo = dest.lng
        // 경로 위 지점은 내 위치에서 나오므로 위치정보 동의가 있을 때만 찾는다
        if (chips.isEmpty() || la == null || lo == null || !KakaoLocal.enabled || !com.beff.app.data.Consent.locationAllowed) { routeStops = key to emptyList(); return }
        val from = origin
        val found = withContext(Dispatchers.IO) {
            // 출발 쪽 40% · 도착 쪽 70% 지점(너무 가까우면 가운데 한 곳). 반경은 경로 길이에 맞춰 1~5km.
            val km = com.beff.app.data.distanceKm(from, GeoPoint(la, lo))
            val ts = if (km < 8) listOf(0.5) else listOf(0.4, 0.7)
            val radius = (km * 1000 / 6).toInt().coerceIn(1000, 5000)
            val seen = HashSet<String>()
            chips.mapNotNull { chip ->
                ts.firstNotNullOfOrNull { t ->
                    val c = GeoPoint(from.lat + (la - from.lat) * t, from.lng + (lo - from.lng) * t)
                    KakaoLocal.nearbyFor(chip, c, radius)?.firstOrNull { seen.add(it.id) }
                }?.copy(note = chip)
            }.take(4)
        }
        found.forEach { places.register(it) }
        routeStops = key to found
    }

    fun openBriefing(placeId: String) { push(Route.Briefing(placeId)) }

    fun selectTab(tab: Tab) {
        replaceAll(Route.Main(tab))
    }

    /** 프로필 선택 화면. 이미 프로필을 고른 상태면 쌓아서(뒤로 가면 보던 화면으로), 처음 진입이면 비우고 연다. */
    fun openProfileSelect() {
        if (active != null && route !is Route.ProfileSelect) push(Route.ProfileSelect) else replaceAll(Route.ProfileSelect)
    }

    /**
     * 설정 탭 "앱 초기화": 플로우 점검용. 저장한 프로필 · 최근 검색 · 여정 · 체크인을 모두 지우고
     * 스플래시부터 다시 시작한다(처음 설치한 상태와 같다). 기기 권한은 건드리지 않는다.
     */
    fun resetApp() {
        speech.stop()
        nav.cancelRoute()
        store.clearAll()
        profiles.clear()
        recents.clear()
        journey = null
        active = null
        draft = Draft()
        oneTimeFlowStarted = false
        askRegister = false; registeredDone = false; deleteTarget = null
        editingProfiles = false
        searchQuery = ""; searchChip = null; searchRecentOpen = false; searchRecentEdit = false
        searchHits = null; searchHitsQuery = ""; searchOffline = false; routeStops = null
        pendingStop = null; needDestination = false; notice = null
        checkIn = null; entranceReportToday = false; warnBeforeArrival = false
        arrivalAnnouncedFor = null; arrivalOpenedFor = null; stopOpenedFor = null
        history.clear()
        // 1115: 처음 상태 = 위치정보 동의도 다시 묻는다(스플래시 다음 동의 화면)
        com.beff.app.data.Consent.set(getApplication(), com.beff.app.data.Consent.State.Unknown)
        location.release()
        replaceAll(Route.Splash)
    }

    /**
     * 06 "안내 시작". 기본 내비로 넘기는 NaviHelper 연동은 아직 없다.
     * 연동 지점: NaviHelper `requestRoute()`(사용자가 이 버튼을 눌렀을 때만 — 무동의 자동 전환 금지) [미확인].
     */
    fun startJourney(place: Place, chosenStops: List<Place>? = null) {
        // 출발 브리핑에서 고른 경유지, 없으면 같은 목적지로 정해 둔 경유지를 함께 보낸다.
        val stops = chosenStops ?: journey?.takeIf { it.dest.id == place.id }?.stops.orEmpty()
        journey = Journey(dest = place, stops = stops)
        journeyStartedAt = android.os.SystemClock.elapsedRealtime()
        voice.say("${place.name}, 안내를 시작할게요")
        // 주행 중 음성 추천에 쓸 쉬어갈 곳 후보를 미리 찾아 둔다(화면이 가려져도 쓸 수 있게).
        viewModelScope.launch { loadRouteStops(place) }
        // 좌표가 있는 장소만 실제 경로를 요청한다. 없거나 내비가 연결되지 않으면 앱 안 여정으로만 동작한다.
        nav.startRoute(place, stops)
        persistSession()
        selectTab(Tab.Home)
    }

    /** 하차 브리핑에서 "내리기": 여정을 끝내고, 다음에 열 때 지난 여행 체크인을 묻는다(03-3). */
    fun finishUnload() {
        recordPastJourney()
        val dest = journey?.dest
        journey = null
        nav.cancelRoute()
        persistSession()
        if (dest != null) checkIn = CheckIn(dest.name, "11월 7일")
        selectTab(Tab.Home)
    }

    /** 지금 여정을 지난 여정으로 남긴다. 걸린 시간은 이번 실행에서 안내를 시작했을 때만 잰다. */
    private fun recordPastJourney() {
        val j = journey ?: return
        val minutes = journeyStartedAt.takeIf { it > 0L }?.let { ((android.os.SystemClock.elapsedRealtime() - it) / 60_000L).toInt() }
        history.record(j.dest, active, j.stops, minutes, places.facilitiesFor(j.dest, active).filter { it in j.dest.facilities })
    }

    fun endJourney() {
        journey = null
        nav.cancelRoute()
        persistSession()
        selectTab(Tab.Home)
    }

    fun addRestStop(place: Place) {
        val j = journey ?: return
        if (j.stops.any { it.id == place.id } || j.stops.size >= 2) return
        val after = j.stops + place
        journey = j.copy(stops = after, page = j.stops.size)
        persistSession()
        if (nav.routeActive) {
            // 내비 경로가 이미 있으면 내비에 전달하고 결과를 화면 확인 표시로 알린다.
            nav.syncAddStop(j.dest, after, place, j.stops.size)
            showNotice("${withEul(place.name)} 경유지로 추가했어요")
        } else {
            showPlanBriefing(j.dest.id)   // 경로 전: 앱 안에서 계획만 고치고 출발 브리핑으로 보여 준다
        }
    }

    fun removeRestStop(place: Place) {
        val j = journey ?: return
        val idx = j.stops.indexOfFirst { it.id == place.id }
        journey = j.copy(stops = j.stops.filterNot { it.id == place.id }, page = 0)
        persistSession()
        if (nav.routeActive) {
            if (idx >= 0) nav.syncRemoveStop(idx, place)
            showNotice("${withEul(place.name)} 경유지에서 뺐어요")
        } else {
            showPlanBriefing(j.dest.id)
        }
    }

    /** 경유지까지 정해진 계획을 출발 브리핑(06-1)으로 보여 준다. "안내 시작"에서 내비로 한 번에 넘긴다. */
    private fun showPlanBriefing(destId: String) {
        val cur = route
        if (cur is Route.Briefing && cur.placeId == destId) return
        push(Route.Briefing(destId))
    }

    /** 이 경유지가 내비에 실제로 있는가. null = 알 수 없음(연결 안 됨 · 아직 내비 응답 없음). */
    fun stopOnNavi(place: Place): Boolean? {
        val names = nav.waypointNames ?: return null
        if (nav.link != com.beff.app.nav.NaviLink.Connected) return null
        return names.any { it.contains(place.name) || place.name.contains(it) }
    }

    /** 경유지 추가·삭제를 내비에 보내기 전에 묻는 확인. */
    sealed interface StopAction {
        val place: Place
        data class Add(override val place: Place) : StopAction
        data class Remove(override val place: Place) : StopAction
    }

    var pendingStop by mutableStateOf<StopAction?>(null)
        private set

    /** 목적지 없이 경유지를 바꾸려 할 때 띄우는 안내(목적지부터 정하게 한다). */
    var needDestination by mutableStateOf(false)

    /** 여정 탭 [여정 취소]를 눌러 확인 창을 띄운 상태. */
    var askEndJourney by mutableStateOf(false)

    fun askAddStop(place: Place) {
        if (journey == null) { needDestination = true; return }
        pendingStop = StopAction.Add(place)
    }

    fun askRemoveStop(place: Place) {
        if (journey == null) { needDestination = true; return }
        pendingStop = StopAction.Remove(place)
    }

    fun goPickDestination() {
        needDestination = false
        selectTab(Tab.Search)
    }
    fun cancelPendingStop() { pendingStop = null }

    fun confirmPendingStop() {
        val a = pendingStop ?: return
        pendingStop = null
        when (a) {
            is StopAction.Add -> addRestStop(a.place)
            is StopAction.Remove -> removeRestStop(a.place)
        }
    }

    /** 경유지 추가·삭제 확인 표시. 내비 반영 결과는 nav.sync가 실시간으로 채운다. */
    data class Notice(val title: String)

    var notice by mutableStateOf<Notice?>(null)
        private set

    private fun showNotice(title: String) {
        val n = Notice(title)
        notice = n
        viewModelScope.launch {
            delay(30_000)
            if (notice === n) notice = null
        }
    }

    fun dismissNotice() { notice = null }

    fun setJourneyPage(page: Int) { journey = journey?.copy(page = page) }

    /** 쉴 곳 제안(05-1)을 앞뒤로 넘긴다. */
    fun cycleSuggestion(delta: Int) {
        val j = journey ?: return
        val n = restSuggestions().size.coerceAtLeast(1)
        journey = j.copy(suggestionIndex = (j.suggestionIndex + delta).mod(n))
    }

    /**
     * 오늘 이 목적지의 입구 · 엘리베이터 불가 제보가 있는가(07-1 도착 분기). 제보 서버가 없어 [미확인] 기본 false.
     * 03-3 체크인의 「맞아요 / 아니에요」가 쌓이면 이 값을 채운다. 지금은 debugJump(arrive_report)로만 켠다.
     */
    var entranceReportToday by mutableStateOf(false)
        private set

    /** 도착 분기(정책 07-1): 제보가 있으면 06-2 대신 07-1, 없으면 06-2. */
    fun openUnload(placeId: String, preview: Boolean = true) {
        push(if (entranceReportToday) Route.Alternatives(placeId) else Route.Unload(placeId, preview))
    }

    /** 06-2 「다른 곳 찾기」: 제목이 「다른 곳을 찾아볼게요」로 바뀐다. */
    fun openAlternatives(placeId: String) { push(Route.Alternatives(placeId, manual = true)) }

    /** 도착 화면(G-2) [주차가 어려워요]: 근처 주차장으로 안내하는 화면(D-6과 같은 화면)을 연다. */
    fun openParkingHelp(placeId: String) { push(Route.Alternatives(placeId, byUser = true)) }
    fun openUnloadMap(placeId: String) { push(Route.UnloadMap(placeId)) }

    /** 03-3 체크인 토스트: 답하거나 X를 누를 때까지 유지(정책 58). */
    var checkIn by mutableStateOf<CheckIn?>(null)
        private set

    fun answerCheckIn(correct: Boolean) {
        // 서버가 없어 제보는 저장하지 않는다. "달라요"의 음성 한 마디 제보 흐름은 디자인에 없다(S12 미작성).
        checkIn = null
    }

    fun rememberSearch(text: String, placeId: String?) {
        if (text.isBlank()) return
        recents.removeAll { it.text == text }
        val d = java.time.LocalDate.now()
        // 카카오 장소는 앱을 다시 켜면 id로 찾을 수 없어 검색어로만 남긴다(누르면 다시 검색).
        val keepId = placeId?.takeUnless { it.startsWith(KakaoLocal.ID_PREFIX) }
        recents.add(0, RecentItem(text, "%02d.%02d.".format(d.monthValue, d.dayOfMonth), keepId, active?.counts?.keys?.firstOrNull()))
        if (recents.size > 8) recents.removeAt(recents.lastIndex)
        store.saveRecents(recents.toList())
    }

    fun removeRecent(item: RecentItem) {
        recents.remove(item)
        store.saveRecents(recents.toList())
    }

    /** 04 검색 최근 검색 펼침·편집 상태(화면 점검용 직행이 켤 수 있도록 여기에 둔다). */
    var searchRecentOpen by mutableStateOf(false)
    var searchRecentEdit by mutableStateOf(false)

    /**
     * 화면 점검용 직행(저장하지 않음, 내비 호출 없음). 사용:
     * `adb shell am start -n com.beff.app/.MainActivity --es debug <대상>`
     * 대상: splash · consent · profile0 · profile · profile4 · mobility · mobility_one · filter · filter_one · people ·
     * register(02-4) · registered(02-5) · replace(02-6) · delete(02-7) ·
     * gear_p · gear_d(기어만 덮어씀, 화면은 그대로) · arrive(도착 + 현재 기어) · unload_under(지하 주차장 하차 약도) ·
     * home · home_w · home_trip · home_trip_w · home_checkin · unload_preview(D-5) · stop_arrive(G-1) · gear_unknown(G-6) · door(G-4) · search · search_recent · search_edit · journey · journey_empty · briefing · unload · alt · alt_manual · arrive_report(주차 → 07-1 분기) · map ·
     * settings · credits · device · profile_edit · place(C-6) · place_noinfo(C-5) · journey_cancel · alt_user
     * 새 화면을 만들면 여기에 대상 한 줄을 더하고, 플로우 점검 페이지(tools/beff-admin/index.html)의 버튼 목록에도 더한다.
     */
    fun debugJump(target: String) {
        // 기어 덮어쓰기(앱 안에서만): 주행 중엔 화면이 안 바뀌고 P가 되면 열리는지 확인한다.
        if (target == "gear_p") { vehicle.debugForceGear(com.beff.app.device.GearState.Park); return }
        if (target == "gear_d") { vehicle.debugForceGear(com.beff.app.device.GearState.Drive); return }
        if (target == "gear_auto") { vehicle.debugForceGear(null); return }
        // 기어 정보 없음(SDK 실패와 같은 상태) 점검. 「도착하셨나요?」 팝업(G-6)은 1112에 뺐다.
        if (target == "gear_unknown") { vehicle.debugForceGear(com.beff.app.device.GearState.Unknown); return }
        // 1110 G-4 점검: 문 열림 신호를 앱 안에서만 흉내 낸다(도착 화면이 시설 안내를 말한다).
        if (target == "door") { vehicle.debugDoorOpen(); return }
        // 1113: 내비 도착 이벤트 흉내. 실제 흐름(목적지 설정 → 안내 시작)에서 안내 중에 눌러 도착 화면으로 넘어간다. 여정은 그대로 둔다.
        if (target == "navi_arrived") { nav.debugArrived(); return }
        if (target == "navi_stop_arrived") {
            journey?.stops?.firstOrNull { it.id != stopOpenedFor }?.let { nav.debugWaypointArrived(it.name) }
            return
        }
        // 1113: 내비 안내가 남아 있으면 앱 전체가 주행 잠금 화면으로 덮이고(BeffApp), 이전 기어 덮어쓰기(gear_d)가 남아 있으면 실제 기어(P)를 무시한다.
        // 그러면 점검 페이지에서 어느 화면으로 가도 안 넘어가므로, 화면 이동 때 둘 다 푼다. 장면(SCENES)은 이동 뒤에 gear_p · gear_d를 따로 보내 다시 덮는다.
        vehicle.debugForceGear(null)
        if (nav.guiding || nav.route != null) {
            // 내비 안내 취소는 여정도 끝내므로(observeDeviceEvents), 취소 신호가 늦게 와도 이번 점검 여정이 지워지지 않게 한동안 무시한다.
            ignoreNaviCancelUntil = android.os.SystemClock.elapsedRealtime() + DEBUG_CANCEL_IGNORE_MS
            nav.cancelRoute()
        }
        // 점검용 직행은 이전 여정을 이어받지 않는다(앞선 점검의 여정이 여정 탭 · 홈에 남지 않게). 여정이 필요한 대상은 아래에서 다시 만든다.
        // 이전 시험의 도착 상태(내비 도착 신호 · 이미 안내한 도착 · 이미 연 경유지 도착 화면)도 지워 새 여정이 처음부터 도착을 판정하게 한다.
        nav.debugClearArrival()
        arrivalAnnouncedFor = null; arrivalOpenedFor = null; stopOpenedFor = null
        stopNoticed.clear(); suggestNoticed.clear(); destNoticedFor = null
        journey = null
        persistSession()
        val kids = Profile(
            "dbg1", "아이들", mapOf(Mobility.Stroller to 2), setOf("수유실", "기저귀 교환대", "가족 화장실", "카페", "편의점"), listOf(Person.Boy, Person.Girl),
        )
        // 1-3: 예시 프로필은 휠체어 우선(동생 · 휠체어 1명).
        val wheel = Profile(
            "dbg2", "동생", mapOf(Mobility.Wheelchair to 1), setOf("장애인 화장실", "장애인 주차", "엘리베이터", "경사로", "측면 승하차 공간"), listOf(Person.AdultMan),
        )
        val extra = listOf(
            Profile("dbg3", "할머니", mapOf(Mobility.Walker to 1), setOf("쉼터"), listOf(Person.Grandma)),
            Profile("dbg4", "이동 1", mapOf(Mobility.Stroller to 1, Mobility.Wheelchair to 1), setOf("수유실", "엘리베이터"), listOf(Person.Boy, Person.AdultWoman)),
        )
        profiles.clear()
        when (target) {
            "profile0" -> Unit
            "profile4" -> profiles.addAll(listOf(wheel, kids) + extra)
            "replace" -> profiles.addAll(listOf(wheel, kids) + extra)
            else -> profiles.addAll(listOf(wheel, kids))
        }
        askRegister = false; registeredDone = false; deleteTarget = null; checkIn = null; askEndJourney = false
        entranceReportToday = target == "arrive_report"
        journey = null; warnBeforeArrival = false
        active = if (target.endsWith("_k")) kids else wheel
        val draftKids = Draft(counts = mapOf(Mobility.Stroller to 2), filters = kids.filters, people = mapOf(0 to Person.Boy, 1 to Person.Girl))
        // 점검용 목적지: 서울숲(contentid 128611, 도식 지도 예시 수집 장소). 데이터에 없으면 가장 가까운 장소.
        val sejong = places.placeOrNull("tour-128611") ?: places.nearbyWith(null, origin, 1).first()
        // 점검용 쉬어갈 곳. 위에서 여정을 비웠으므로 restSuggestions()(지금 여정 기준)는 늘 비어 있다 — 목적지 기준으로 찾는다.
        // 가는 길에 맞는 곳이 없으면 목적지 근처 대체 장소, 그것도 없으면 경유지 없이 둔다(예전 .first()는 여기서 앱이 죽었다).
        val debugStop = restSuggestionsFor(sejong).firstOrNull()
            ?: places.alternativesNear(sejong, active ?: kids, radiusKm = 30.0, limit = 1).firstOrNull()
        when (target) {
            "splash" -> replaceAll(Route.Splash)
            "consent" -> replaceAll(Route.Consent())   // 1115 위치정보 · 개인정보 동의
            "profile0", "profile", "profile4" -> replaceAll(Route.ProfileSelect)
            // 프로필 만들기 단계는 실제 흐름과 같은 순서로 앞 화면을 쌓는다(점검 페이지에서 바로 열어도 「뒤로」가 동작하게, 1113)
            "mobility" -> { draft = Draft(); stepStack(Flow.Create, 1) }
            "mobility_one" -> { draft = Draft(); stepStack(Flow.OneTime, 1) }
            "filter" -> { draft = draftKids; stepStack(Flow.Create, 2) }
            "filter_one" -> { draft = draftKids; stepStack(Flow.OneTime, 2) }
            "people" -> { draft = draftKids; stepStack(Flow.Create, 3) }
            "register" -> { draft = draftKids; stepStack(Flow.OneTime, 2); askRegister = true }
            "registered" -> { draft = draftKids; stepStack(Flow.OneTime, 2); askRegister = true; registeredDone = true }
            "replace" -> { draft = draftKids; replaceAll(Route.ProfileSelect); push(Route.ReplaceProfile) }
            "delete" -> { draft = draftKids; replaceAll(Route.ProfileSelect); push(Route.ReplaceProfile); deleteTarget = profiles.first() }
            "home", "home_w" -> replaceAll(Route.Main(Tab.Home))
            "home_trip", "home_trip_w" -> { journey = Journey(dest = sejong); replaceAll(Route.Main(Tab.Home)) }
            "home_checkin" -> { checkIn = CheckIn("국립세종수목원", "11월 7일"); replaceAll(Route.Main(Tab.Home)) }
            "settings" -> replaceAll(Route.Main(Tab.Settings))
            // 설정 하위 화면(플로우 점검 페이지 tools/beff-admin에서 쓴다)
            "credits" -> { replaceAll(Route.Main(Tab.Settings)); push(Route.Credits) }
            "device" -> { replaceAll(Route.Main(Tab.Settings)); push(Route.DeviceStatus) }
            "profile_edit" -> { replaceAll(Route.Main(Tab.Settings)); openProfileEdit(kids) }
            "search" -> { searchRecentOpen = false; searchRecentEdit = false; replaceAll(Route.Main(Tab.Search)) }
            "search_recent" -> { searchRecentOpen = true; searchRecentEdit = false; replaceAll(Route.Main(Tab.Search)) }
            "search_edit" -> { searchRecentOpen = true; searchRecentEdit = true; replaceAll(Route.Main(Tab.Search)) }
            "journey" -> { journey = Journey(dest = sejong); replaceAll(Route.Main(Tab.Journey)) }
            "journey_empty" -> replaceAll(Route.Main(Tab.Journey))
            // F-4 · F-5 점검: 지난 여정 목록을 저장하지 않고 채운다(앱 데이터의 실제 장소, 날짜 · 시간은 점검용 값).
            "journey_past" -> {
                val day = 24L * 60 * 60 * 1000
                val now = System.currentTimeMillis()
                val others = places.alternativesNear(sejong, kids, radiusKm = 30.0, limit = 2)
                history.debugShow((listOf(sejong) + others).mapIndexed { i, p ->
                    com.beff.app.data.PastJourney(
                        p.id, p.name, p.kind, p.lat, p.lng, now - (i * 7 + 4) * day,
                        if (i == 0) wheel.name else kids.name,
                        restSuggestionsFor(p).take(1).map { it.name }, if (i == 1) null else 40 + i * 30,
                        places.facilitiesFor(p, if (i == 0) wheel else kids).filter { it in p.facilities }.take(2),
                    )
                })
                replaceAll(Route.Main(Tab.Journey))
            }
            "reset" -> replaceAll(Route.Main(Tab.Home))   // 여정 없이 홈(위에서 여정을 비웠다)
            "captions" -> demoCaptions = true
            "captions_off" -> demoCaptions = false
            // 주행 데모: 서울숲 여정(두 번째 추천을 경유지로 넣음, 첫 번째는 주행 중 음성 추천 대상). 좌표는 로그로 알린다(adb emu geo fix용).
            "drive" -> {
                val recs = restSuggestionsFor(sejong)
                journey = Journey(dest = sejong, stops = listOfNotNull(recs.getOrNull(1)))
                recs.forEachIndexed { i, p -> android.util.Log.i("BeffDebug", "drive suggestion $i ${p.name} ${p.lng} ${p.lat}") }
                replaceAll(Route.Main(Tab.Journey))
            }
            "journey_stop", "journey_warn" -> {
                journey = Journey(dest = sejong, stops = listOfNotNull(debugStop), page = 0)
                warnBeforeArrival = target == "journey_warn"
                replaceAll(Route.Main(Tab.Journey))
            }
            "briefing_stop" -> { journey = Journey(dest = sejong, stops = listOfNotNull(debugStop)); replaceAll(Route.Main(Tab.Home)); push(Route.Briefing(sejong.id)) }
            "arrive" -> { journey = Journey(dest = sejong.copy(lat = 37.4503, lng = 127.1)); replaceAll(Route.Main(Tab.Home)) } // 에뮬레이터 고정 위치(37.45, 127.10) 근처 → 도착 + 주차 연동 확인
            "briefing" -> { journey = Journey(dest = sejong); replaceAll(Route.Main(Tab.Home)); push(Route.Briefing(sejong.id)) }
            // C-6 장소 상세: 여정 없이 Briefing을 열면 상세 단계부터 시작한다(BriefingScreen BriefMemo).
            "place" -> { replaceAll(Route.Main(Tab.Search)); push(Route.Briefing(sejong.id)) }
            // C-5 정보 없음: 무장애 정보가 없는 장소의 상세(가까운 곳부터 찾는다). 없으면 C-6과 같다.
            "place_noinfo" -> {
                val p = places.nearbyWith(null, origin, 1000).firstOrNull { it.hasNoAccessInfo } ?: sejong
                replaceAll(Route.Main(Tab.Search)); push(Route.Briefing(p.id))
            }
            // F 여정 취소 확인 팝업
            "journey_cancel" -> { journey = Journey(dest = sejong); replaceAll(Route.Main(Tab.Journey)); askEndJourney = true }
            "unload" -> { journey = Journey(dest = sejong); replaceAll(Route.Main(Tab.Home)); push(Route.Unload(sejong.id)) }
            // 1110 D-5 하차 브리핑 미리보기 · G-1 경유지 도착(첫 쉬어갈 곳을 경유지로)
            "unload_preview" -> { journey = Journey(dest = sejong); replaceAll(Route.Main(Tab.Home)); push(Route.Briefing(sejong.id)); push(Route.Unload(sejong.id, preview = true)) }
            "stop_arrive" -> {
                val stop = debugStop ?: sejong
                journey = Journey(dest = sejong, stops = listOf(stop)); replaceAll(Route.Main(Tab.Journey)); push(Route.Unload(stop.id))
            }
            // 지하 주차장 장소(광화문광장, 없으면 지하 주차장인 첫 곳)의 하차 브리핑 → 예시 약도
            "unload_under" -> {
                val under = places.placeOrNull("tour-775394")?.takeIf { it.undergroundParking }
                    ?: places.nearbyWith(null, origin, 1000).firstOrNull { it.undergroundParking } ?: sejong
                journey = Journey(dest = under); replaceAll(Route.Main(Tab.Home)); push(Route.Unload(under.id))
            }
            "alt" -> { journey = Journey(dest = sejong); replaceAll(Route.Main(Tab.Home)); push(Route.Alternatives(sejong.id)) }
            "alt_manual" -> { journey = Journey(dest = sejong); replaceAll(Route.Main(Tab.Home)); push(Route.Alternatives(sejong.id, manual = true)) }
            // G-2 [주차가 어려워요] → D-6과 같은 화면(제목만 「근처 주차장으로 안내할게요」)
            "alt_user" -> { journey = Journey(dest = sejong); replaceAll(Route.Main(Tab.Home)); push(Route.Unload(sejong.id)); push(Route.Alternatives(sejong.id, byUser = true)) }
            // 주차 직후 도착 분기: 오늘 입구 제보가 있으면 07-1(대체 하차지), 없으면 06-2
            "arrive_report" -> { journey = Journey(dest = sejong); replaceAll(Route.Main(Tab.Home)); openUnload(sejong.id, preview = false) }
            "map" -> { journey = Journey(dest = sejong); replaceAll(Route.Main(Tab.Home)); push(Route.Unload(sejong.id)); push(Route.UnloadMap(sejong.id)) }
            // unload:<장소id> — 아무 장소의 하차 브리핑(현장 OSM 동선 · 시뮬레이션 점검용)
            else -> places.placeOrNull(target.removePrefix("unload:"))?.takeIf { target.startsWith("unload:") }?.let { p ->
                journey = Journey(dest = p); replaceAll(Route.Main(Tab.Home)); push(Route.Unload(p.id))
            }
        }
    }

    // 기기 연동 감시는 맨 마지막에 시작한다. viewModelScope는 Main.immediate라 launch가 바로 첫 값을 읽는데,
    // 그때 아래쪽에 선언된 상태값(routeStops 등)이 아직 만들어지지 않았으면 NPE로 앱이 시작하자마자 죽는다(2026-11-11 재현: 저장된 여정이 있을 때).
    init {
        observeDeviceEvents()
        dropStaleRestoredJourney()
    }

    /**
     * 다시 켰을 때 되살린 여정이 내비에는 이미 없으면(운전자가 그사이 내비에서 안내를 끝냄) 여정을 비운다.
     * 내비가 연결되고 경로 상태를 물은 뒤 [RESTORE_CHECK_MS] 동안 경로 · 안내 · 도착 신호가 하나도 없을 때만 비운다(도착 후 하차 흐름은 유지).
     */
    private fun dropStaleRestoredJourney() {
        val restored = journey ?: return
        viewModelScope.launch {
            snapshotFlow { nav.link }.first { it == com.beff.app.nav.NaviLink.Connected || it == com.beff.app.nav.NaviLink.Failed }
            if (nav.link != com.beff.app.nav.NaviLink.Connected) return@launch
            nav.refresh()
            delay(RESTORE_CHECK_MS)
            if (journey === restored && !nav.routeActive && !nav.guiding && !nav.destinationArrived) {
                android.util.Log.i("BeffNavi", "내비에 경로 없음 → 되살린 여정 비움(${restored.dest.name})")
                journey = null
                persistSession()
            }
        }
    }

    // ───────── 1110 H. 설정: 음성 · 글자 크기 설정, 프로필 편집(H-2) ─────────

    /** 설정 탭의 말하는 속도 · 소리 크기 · 글자 크기. 기기 안에 저장한다(앱 초기화와 별개). */
    val settings = com.beff.app.data.SettingsStore(app)

    /** H-2에서 편집 중인 프로필 사본. [saveProfileEdit] 전에는 저장된 프로필을 바꾸지 않는다. */
    var editProfile by mutableStateOf<Profile?>(null)
        private set

    /** H-2 「이 프로필로 갈 때」 항목 → 다시 쓰는 프로필 만들기 단계 화면. */
    enum class EditStep { Mobility, Filter }

    /** 편집하려고 연 단계 화면. 그 화면의 「다음」이 다음 단계로 넘어가려 하면 값만 받아 편집 화면으로 돌아온다. */
    private var editStep: EditStep? = null

    fun openProfileEdit(p: Profile) {
        editProfile = p
        editStep = null
        push(Route.ProfileEdit)
    }

    fun renameEditProfile(name: String) {
        editProfile = editProfile?.copy(name = name.take(com.beff.app.data.MAX_NAME))
    }

    /** 단계 화면(A-1 수단 · A-3 필터)을 편집 중인 값으로 채워 연다. */
    fun openEditStep(step: EditStep) {
        val p = editProfile ?: return
        draft = Draft(
            counts = p.counts, filters = p.filters,
            people = p.people.withIndex().associate { it.index to it.value },
            seats = p.seats, name = p.name,
        )
        editStep = step
        push(
            when (step) {
                EditStep.Mobility -> Route.MobilityStep(Flow.Create)
                EditStep.Filter -> Route.FilterStep(Flow.Create)
            },
        )
    }

    /** 단계 화면이 다음 단계 화면을 쌓으면(=「다음」) 고친 값만 편집 사본에 넣고 편집 화면으로 되돌린다. */
    private fun onEditStack(top: Route?) {
        val step = editStep ?: return
        if (top == Route.ProfileEdit) { editStep = null; return }   // 뒤로 나왔다: 바꾸지 않는다
        val done = when (step) {
            EditStep.Mobility -> top is Route.FilterStep
            EditStep.Filter -> top == Route.PeopleStep
        }
        if (!done) return
        val d = draft
        val p = editProfile ?: return
        editProfile = when (step) {
            // 인원이 줄면 범위 밖 탑승자의 인물은 뺀다.
            EditStep.Mobility -> p.copy(counts = d.counts, seats = emptyMap(), people = p.people.take(d.total))
            EditStep.Filter -> p.copy(filters = d.filters)
        }
        editStep = null
        while (backStack.size > 1 && backStack.last() != Route.ProfileEdit) backStack.removeAt(backStack.lastIndex)
    }

    /** H-2 [저장]: 같은 id의 프로필을 바꾸고 저장한다. 지금 쓰는 프로필이면 함께 바꾼다. */
    fun saveProfileEdit() {
        val p = editProfile?.let { it.copy(name = it.name.trim()) } ?: return
        if (p.name.isEmpty()) return
        val i = profiles.indexOfFirst { it.id == p.id }
        if (i >= 0) profiles[i] = p
        store.save(profiles.toList())
        if (active?.id == p.id) { active = p; persistSession() }
        editProfile = null
        back()
    }

    /** H-2 「프로필 지우기」: 기존 지우기 확인 창(02-7)을 띄운다. 지운 뒤 처리는 [onEditedProfileDeleted]. */
    fun askDeleteEditProfile() {
        val id = editProfile?.id ?: return
        deleteTarget = profiles.firstOrNull { it.id == id }
    }

    /** 편집하던 프로필이 지워졌을 때: 지금 쓰던 프로필이었으면 프로필 선택으로, 아니면 설정으로 돌아간다. */
    fun onEditedProfileDeleted() {
        editProfile = null
        editStep = null
        if (active == null) replaceAll(Route.ProfileSelect) else if (route == Route.ProfileEdit) back()
    }

    init {
        viewModelScope.launch { snapshotFlow { backStack.lastOrNull() }.collect { onEditStack(it) } }
    }
}

private val DEFAULT_ORIGIN = GeoPoint(37.5665, 126.9780)  // 서울시청 [가정] — 내비 위치를 받기 전 기본값

data class Journey(val dest: Place, val stops: List<Place> = emptyList(), val page: Int = 0, val suggestionIndex: Int = 0)

data class CheckIn(val placeName: String, val dateText: String)

/** E-V 음성 팝업이 말 없이 기다리는 최대 시간. */
private const val VOICE_CHANGE_TIMEOUT_MS = 10_000L
private const val FEATURED_PLACE_ID = "tour-126508" // 경복궁
