package com.beff.app.nav

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import com.beff.app.data.GeoPoint
import com.beff.app.data.Place

/** 기본 내비와의 연결 상태. */
enum class NaviLink { Idle, Connecting, Connected, Failed }

/** 내비가 알려 준 현재 경로 상태. 값은 전부 내비 이벤트에서 온 것이다. */
data class NaviRoute(
    val totalSeconds: Int,
    val totalMeters: Int,
    val remainSeconds: Int? = null,
    val remainMeters: Int? = null,
) {
    /** 0..1, 남은 값이 아직 없으면 0 */
    val progress: Float
        get() = if (remainMeters != null && totalMeters > 0) (1f - remainMeters.toFloat() / totalMeters).coerceIn(0f, 1f) else 0f
}

/** 경유지 변경을 내비에 전달한 결과. 화면 확인 표시에 그대로 쓴다. */
enum class SyncState { Pending, Late, Sent, Confirmed, NotSent, Failed }

data class WaypointSync(val state: SyncState, val message: String)

/** 현재 위치에서 어떤 지점까지의 거리·예상 소요(내비 trip 계산 결과). */
data class TripEstimate(val meters: Int, val seconds: Int)

/**
 * NaviHelper 연동. 경로를 바꾸는 호출(requestRoute · addWaypoint · removeWaypoint · cancelRoute)은
 * 사용자가 화면에서 직접 버튼을 눌렀을 때만 부른다(무동의 자동 전환 금지 — 개발자 가이드).
 * 이 클래스에 `set*` 이름의 호출은 없다.
 */
class NaviBridge(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var helper: NaviHelper? = null

    var link by mutableStateOf(NaviLink.Idle)
        private set
    var route by mutableStateOf<NaviRoute?>(null)
        private set
    var destinationArrived by mutableStateOf(false)
        private set

    /**
     * 내비가 경로 안내 중이다(`NavigationStatus.ROUTE_DRIVE`). 주행이 끝날 때까지 베프 화면을 보이지 않는 데 쓴다.
     * 차량 주행 상태(Parked/Idling/Moving)가 아니라 내비 상태이므로, `CarDrivingStateManager` 연동 전까지의 임시 기준이다 [가정].
     */
    var guiding by mutableStateOf(false)
        private set
    var lastWaypointArrived by mutableStateOf<String?>(null)
        private set

    /** 내비가 경로 안내를 취소했다고 알려 온 횟수(운전자가 내비에서 [안내 종료]를 누른 경우 등). 베프가 여정을 비우는 신호로 쓴다. */
    var routeCancelCount by mutableStateOf(0)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    /**
     * 내비에 실제로 잡힌 경로가 있다. `route` 값이 있어도 거리가 0이면(내비가 빈 상태로 답한 경우) 경로가 아니다.
     * 이 판정이 틀리면 경로가 없는 내비에 경유지 변경을 보내 "경로 탐색 오류 - Invalid Parameter"가 난다.
     */
    val routeActive: Boolean
        get() = route?.let { it.totalMeters > 0 || (it.remainMeters ?: 0) > 0 } == true

    /** 내비가 알려 준 현재 위치. null이면 아직 모른다(근처 조회는 기본 위치를 쓴다). */
    var location by mutableStateOf<GeoPoint?>(null)
        private set

    /** 내비가 알려 준 현재 경유지 이름 목록. null이면 아직 모른다. 화면의 "내비 반영" 표시는 이 값으로 정한다. */
    var waypointNames by mutableStateOf<List<String>?>(null)
        private set

    /** 가장 최근 경유지 전달 결과. null이면 아직 전달한 적 없음. */
    var sync by mutableStateOf<WaypointSync?>(null)
        private set

    /** 장소 id → 현재 위치 기준 거리·시간(내비 계산값). */
    val trips = mutableStateMapOf<String, TripEstimate>()
    private var pendingTripPlace: String? = null

    // onTripInfo에는 요청 식별자가 없다(페타와 같은 정책): 한 번에 하나만 보내고 답을 기다린다. 나머지는 줄을 세운다.
    private val tripQueue = ArrayDeque<Place>()
    private val tripGaveUp = HashSet<String>()      // 답이 없던 장소는 다시 묻지 않는다
    private var tripTimeout: Runnable? = null
    private var tripFailStreak = 0                  // 연달아 답이 없으면(에뮬레이터 등) 더 묻지 않는다

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            Log.i(TAG, "onRouteStarted duration=${routeStartInfo.duration}s distance=${routeStartInfo.distance}m")
            route = NaviRoute(routeStartInfo.duration.toInt(), routeStartInfo.distance.toInt())
            destinationArrived = false
            if (sync?.state == SyncState.Sent) sync = WaypointSync(SyncState.Confirmed, "내비에서 안내를 시작했어요")
        }

        override fun onRouteCancelled() {
            Log.i(TAG, "onRouteCancelled")
            route = null
            guiding = false
            routeCancelCount++
        }

        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            val remainM = destinationInfo.remainDistance.toInt()
            val remainS = destinationInfo.remainTime.toInt()
            Log.i(TAG, "onDestinationInfo remain=${remainM}m ${remainS}s")
            if (remainM <= 0 && !routeActive) return   // 경로가 없을 때 내비가 0으로 답한 것: 경로로 치지 않는다
            route = (route ?: NaviRoute(remainS, remainM)).copy(remainSeconds = remainS, remainMeters = remainM)
        }

        override fun onWayPointInfo(waypointInfo: List<WaypointInfo>) {
            val names = waypointInfo.joinToString { it.name }
            Log.i(TAG, "onWayPointInfo ${waypointInfo.size}개: $names")
            waypointNames = waypointInfo.map { it.name }
            if (awaiting() && pendingConfirm?.invoke(waypointNames.orEmpty()) == true) {
                confirm("내비에 반영됐어요")
            }
        }

        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {
            Log.i(TAG, "onWaypointChanged index=${waypointChangedInfo.index} cmd=${waypointChangedInfo.cmdType}")
            if (awaiting()) confirm("내비에 반영됐어요")
            ready()?.getWaypointInfo()
        }

        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            Log.i(TAG, "onCurrentLocationInfo ${currentLocationInfo.latitude},${currentLocationInfo.longitude}")
            location = GeoPoint(currentLocationInfo.latitude, currentLocationInfo.longitude)
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            Log.i(TAG, "onNavigationStatus $status")
            guiding = status == NavigationStatus.ROUTE_DRIVE
            if (status == NavigationStatus.ROUTE_DRIVE && sync?.state == SyncState.Confirmed) {
                sync = WaypointSync(SyncState.Confirmed, "내비에 반영됐어요 · 안내가 이어지고 있어요")
            }
        }

        override fun onTripInfo(tripInfo: TripInfo) {
            val id = pendingTripPlace ?: return
            Log.i(TAG, "onTripInfo $id distance=${tripInfo.distance}m eta=${tripInfo.eta}s")
            trips[id] = TripEstimate(tripInfo.distance, tripInfo.eta)
            tripFailStreak = 0
            finishTrip()
        }

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            Log.i(TAG, "onDestinationArrived ${destinationArrivedInfo.name}")
            destinationArrived = true
            guiding = false
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            Log.i(TAG, "onWayPointArrived ${waypointArrivedInfo.name}")
            lastWaypointArrived = waypointArrivedInfo.name
        }

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "onError $errorCode")
            lastError = errorCode.name
            if (awaiting()) { sync = WaypointSync(SyncState.Failed, "내비가 오류를 알려 왔어요 · ${errorCode.name}"); clearPending() }
        }
    }

    fun connect() {
        if (helper != null) return
        link = NaviLink.Connecting
        val h = NaviHelper(appContext)
        helper = h
        h.initialize(
            onConnected = {
                h.addListener(listener)
                link = NaviLink.Connected
                tripFailStreak = 0; tripGaveUp.clear()
                Log.i(TAG, "연결됨")
                h.getCurrentLocationInfo()
            },
            onFailed = { t ->
                link = NaviLink.Failed
                Log.w(TAG, "연결 실패: ${t.message}")
            },
        )
    }

    fun release() {
        helper?.let {
            runCatching { it.removeListener(listener) }
            runCatching { it.release() }
        }
        helper = null
        link = NaviLink.Idle
    }

    /** 내비 응답을 기다리는 중이다(응답이 늦어져 Late가 되어도 계속 기다린다). */
    private fun awaiting() = sync?.state == SyncState.Pending || sync?.state == SyncState.Late

    private fun ready(): NaviHelper? = helper?.takeIf { link == NaviLink.Connected }

    private fun Place.toRouteInfo(): RouteInfo? {
        val la = lat ?: return null
        val lo = lng ?: return null
        return RouteInfo(
            latitude = la,
            longitude = lo,
            displayLongitude = null,
            displayLatitude = null,
            poiName = name,
            poiId = "",                  // 우리 id는 내비 POI DB에 없다. "beff-…"를 보내면 내비가 상세 조회에 실패해 오류 창을 띄운다(2026-11-08 에뮬레이터)
            poiSubId = "",
            address = address,
        )
    }

    private fun Place.toWaypoint(index: WaypointIndex): RequestWaypointInfo? {
        val la = lat ?: return null
        val lo = lng ?: return null
        return RequestWaypointInfo(
            latitude = la, longitude = lo, poiName = name, waypointIndex = index,
            poiId = "", address = address, poiSubId = "",
        )
    }

    private fun idx(i: Int) = if (i == 0) WaypointIndex.FIRST else WaypointIndex.SECOND

    /** "안내 시작": 목적지(와 이미 정한 경유지)로 경로를 요청하고 내비를 앞으로 가져온다. */
    fun startRoute(dest: Place, stops: List<Place>): Boolean {
        val h = ready() ?: return false
        val destInfo = dest.toRouteInfo() ?: return false
        val waypoints = stops.mapIndexedNotNull { i, s -> s.toWaypoint(idx(i)) }
        if (waypoints.isEmpty()) h.requestRoute(destInfo)
        else h.requestRouteWithWaypoints(RouteWithWaypointsRequest(destination = destInfo, waypoints = waypoints))
        h.requestNavigationScreen(NavigationScreenRequest.Foreground)
        return true
    }

    /**
     * 경유지 추가를 내비에 전달하고 결과를 [sync]에 남긴다.
     * - 경로가 이미 잡혀 있을 때만 부른다(addWaypoint). 경로 전에는 앱 안에서 계획만 고치고 "안내 시작"에서 한 번에 보낸다.
     * - 좌표가 없는 장소는 전달할 수 없다(좌표를 임의로 만들지 않는다).
     */
    fun syncAddStop(dest: Place, stopsAfter: List<Place>, added: Place, index: Int) {
        val h = ready()
        when {
            h == null -> report(SyncState.NotSent, "내비에 연결되지 않아 앱 안에서만 추가했어요")
            dest.lat == null -> report(SyncState.NotSent, "목적지 좌표가 없어 내비에는 전달하지 못했어요")
            added.lat == null -> report(SyncState.NotSent, "${added.name} 좌표가 없어 내비에는 전달하지 못했어요")
            else -> {
                report(SyncState.Pending, "내비에 전달했어요 · 반영을 기다리는 중")
                h.addWaypoint(added.toWaypoint(idx(index))!!)
                startWaiting { names -> names.any { it.contains(added.name) || added.name.contains(it) } }
            }
        }
    }

    fun syncRemoveStop(index: Int, removed: Place) {
        val h = ready()
        when {
            h == null -> report(SyncState.NotSent, "내비에 연결되지 않아 앱 안에서만 뺐어요")
            removed.lat == null -> report(SyncState.NotSent, "내비에는 없던 경유지라 앱 안에서만 뺐어요")
            !routeActive -> report(SyncState.NotSent, "내비 안내 전이라 앱 안에서만 뺐어요")
            else -> {
                report(SyncState.Pending, "내비에서 경유지를 빼는 중")
                h.removeWaypoint(idx(index))
                startWaiting { names -> names.none { it.contains(removed.name) || removed.name.contains(it) } }
            }
        }
    }

    private fun report(state: SyncState, message: String) {
        Log.i(TAG, "sync $state: $message")
        sync = WaypointSync(state, message)
    }


    /** 반영 여부를 판정하는 조건(내비가 알려 준 경유지 이름 목록 → 반영됐는가). */
    private var pendingConfirm: ((List<String>) -> Boolean)? = null
    private var waitToken = 0

    /**
     * 내비의 확인 응답은 요청 후 8초 가까이 걸린다(에뮬레이터 실측: ADD 요청 → onWaypointChanged 약 8초 뒤).
     * 그래서 (1) 3초마다 경유지 목록을 직접 물어 확인하고, (2) 20초가 지나도 응답이 없으면 "늦어요"로 알리되
     * (3) 그 뒤 60초까지 계속 확인해서 늦게 와도 반영 결과를 보여 준다.
     */
    private fun startWaiting(confirmed: (List<String>) -> Boolean) {
        pendingConfirm = confirmed
        val token = ++waitToken
        for (t in 1..20) main.postDelayed({
            if (token == waitToken && awaiting()) ready()?.getWaypointInfo()
        }, t * 3000L)
        main.postDelayed({
            if (token == waitToken && sync?.state == SyncState.Pending) {
                sync = WaypointSync(SyncState.Late, "내비 응답이 늦어요 · 계속 확인하는 중")
            }
        }, 20_000)
        main.postDelayed({
            if (token == waitToken && awaiting()) {
                sync = WaypointSync(SyncState.NotSent, "내비가 응답하지 않았어요 · 내비 화면에서 경유지를 확인해 주세요")
                clearPending()
            }
        }, 60_000)
    }

    private fun confirm(message: String) {
        sync = WaypointSync(SyncState.Confirmed, message)
        clearPending()
    }

    private fun clearPending() { pendingConfirm = null; waitToken++ }

    fun cancelRoute() {
        ready()?.cancelRoute()
        route = null
    }

    /**
     * 점검 전용(AppViewModel.debugJump `navi_arrived`): 내비의 도착 이벤트를 흉내 낸다. 에뮬레이터 내비는 위치가 고정이라 도착 신호를 보내지 않는다.
     * 내비에는 아무것도 보내지 않고 이 객체의 상태만 바꾼다.
     */
    fun debugArrived() {
        destinationArrived = true
        guiding = false
    }

    /** 점검 전용: 흉내 낸(또는 이전 시험의) 도착 신호를 지운다. 실제 내비는 새 경로를 시작할 때(onRouteStarted) 같은 값을 비운다. */
    fun debugClearArrival() {
        destinationArrived = false
        lastWaypointArrived = null
    }

    /** 점검 전용(`navi_stop_arrived`): 경유지 도착 이벤트를 흉내 낸다. 도착 뒤 안내가 멈춘다고 보고 guiding도 끈다 [가정]. */
    fun debugWaypointArrived(name: String) {
        lastWaypointArrived = name
        guiding = false
    }

    /** 남은 거리·시간을 내비에 묻는다. 답은 onDestinationInfo로 온다. */
    fun refresh() { ready()?.let { it.getDestinationInfo(); it.getWaypointInfo(); it.getCurrentLocationInfo() } }

    /**
     * 현재 위치에서 [place]까지 도로 거리·시간을 묻는다(조회만 하며 경로를 바꾸지 않는다). 답은 onTripInfo → [trips].
     * 여러 곳을 연달아 불러도 줄을 세워 하나씩 보낸다. 5초 안에 답이 없으면 포기하고, 연속 2회 없으면 이번 연결에서는 더 묻지 않는다.
     */
    fun requestTripTo(place: Place) {
        if (place.lat == null || place.lng == null) return
        if (trips.containsKey(place.id) || place.id in tripGaveUp || place.id == pendingTripPlace) return
        if (tripQueue.any { it.id == place.id } || tripFailStreak >= TRIP_MAX_FAILS) return
        tripQueue.addLast(place)
        pumpTrips()
    }

    private fun pumpTrips() {
        if (pendingTripPlace != null) return
        val h = ready() ?: return
        val place = tripQueue.removeFirstOrNull() ?: return
        pendingTripPlace = place.id
        val timeout = Runnable {
            if (pendingTripPlace == place.id) {
                Log.w(TAG, "requestTrip 답 없음: ${place.id}")
                tripGaveUp += place.id
                tripFailStreak++
                if (tripFailStreak >= TRIP_MAX_FAILS) tripQueue.clear()
                finishTrip()
            }
        }
        tripTimeout = timeout
        main.postDelayed(timeout, TRIP_TIMEOUT_MS)
        h.requestTrip(TripRequest(place.lat!!, place.lng!!, "", place.name, ""))   // poiId는 비운다: 우리 id는 내비 POI DB에 없다
    }

    private fun finishTrip() {
        tripTimeout?.let { main.removeCallbacks(it) }
        tripTimeout = null
        pendingTripPlace = null
        pumpTrips()
    }

    private companion object {
        const val TAG = "BeffNavi"
        const val TRIP_TIMEOUT_MS = 5_000L
        const val TRIP_MAX_FAILS = 2
    }
}
