package com.beff.app

import androidx.compose.runtime.remember
import com.beff.app.ui.dismissImeOnTapOutside
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.beff.app.screens.FilterStepScreen
import com.beff.app.screens.MainScreen
import com.beff.app.screens.MobilityStepScreen
import com.beff.app.screens.PeopleStepScreen
import com.beff.app.screens.ProfileSelectScreen
import com.beff.app.screens.RegisterPopups
import com.beff.app.screens.ReplaceProfileScreen
import com.beff.app.screens.SplashScreen
import com.beff.app.screens.AlternativesScreen
import com.beff.app.screens.BriefingScreen
import com.beff.app.screens.UnloadScreen
import com.beff.app.screens.UnloadMapScreen
import com.beff.app.ui.ProvideSvgLoader
import com.beff.app.ui.ProvideWindowWidth

private const val LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    /** 차량 위치(FusedLocation) 권한. 거부해도 앱은 위치 없이 동작한다. */
    private val permissions = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { r ->
        r[LOCATION_PERMISSION]?.let { vm.onLocationPermission(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.beff.app.device.Sfx.init(this)
        setContent {
            // 1113 다크 모드: 차량 · 시스템 야간 모드를 따른다(configChanges에 uiMode가 있어 다시 만들지 않고 다시 그린다).
            com.beff.app.ui.Beff.dark = androidx.compose.foundation.isSystemInDarkTheme()
            // 1113 효과음: 모든 clickable의 눌림 표시에 탭 소리를 씌운다(기존 ripple은 그대로).
            val baseIndication = androidx.compose.foundation.LocalIndication.current as? androidx.compose.foundation.IndicationNodeFactory
            val tapIndication = androidx.compose.runtime.remember(baseIndication) { com.beff.app.device.TapSoundIndication(baseIndication) }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.LocalIndication provides tapIndication) {
            ProvideSvgLoader {
              // 1113: 입력창 바깥을 누르면 키보드를 내린다. 좌표가 화면 루트와 같도록 가장 바깥에 붙인다.
              androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().dismissImeOnTapOutside()) {
                val fw = debugForceWide
                if (fw == null) {
                    ProvideWindowWidth { BeffApp(vm) }
                } else {
                    // 화면 점검용: 창 크기와 상관없이 1700×1184 2열 레이아웃을 그리고 오프셋(0·441·883)만큼 밀어 창 폭만 보여 준다.
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().clipToBounds()) {
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .wrapContentSize(androidx.compose.ui.Alignment.TopStart, unbounded = true)
                                .requiredSize(1700.dp, 1184.dp)
                                .offset(x = (-(fw.toIntOrNull() ?: 0)).dp),
                        ) {
                            androidx.compose.runtime.CompositionLocalProvider(com.beff.app.ui.LocalWide provides true) { BeffApp(vm) }
                        }
                    }
                }
              }
                // 내비 안내 중에도 베프를 뒤로 보내지 않는다(1110: 뒤로 보내면 3분할이 깨진다). 가운데 칸에는 「길 안내 중이에요」만 남는다(BeffApp).
            }
            }
        }
        handleDebugIntent(intent)
        registerDebugReceiver()
        requestLocation()
    }

    /** 다크 모드 전환이 Compose 설정으로 안 들어오는 경우(Pleos 에뮬레이터 user 10)를 위해 직접 맞춘다. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.beff.app.ui.Beff.dark = (newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /** 화면 점검 전용: 실행 중인 앱에 `adb shell am broadcast -a com.beff.app.DEBUG --es debug <대상> --es gear <d|p|auto>`. */
    private fun registerDebugReceiver() {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, i: android.content.Intent) { handleDebugIntent(i) }
        }
        val filter = android.content.IntentFilter("com.beff.app.DEBUG")
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
        else registerReceiver(receiver, filter)
    }

    /** 차량 위치 · 마이크(음성 인식 대체 경로) 권한을 한 번에 요청한다. 거부해도 해당 기능만 빠진다. */
    private fun requestLocation() {
        val wanted = listOf(LOCATION_PERMISSION, android.Manifest.permission.RECORD_AUDIO)
        val missing = wanted.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (LOCATION_PERMISSION !in missing) vm.onLocationPermission(true) else vm.location.markWaiting()
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDebugIntent(intent)
    }

    /** 화면 점검용: `am start -n com.beff.app/.MainActivity --es debug <대상>` (AppViewModel.debugJump 참고). */
    private fun handleDebugIntent(intent: android.content.Intent?) {
        android.util.Log.i("BeffDebug", "intent extras: debug=${intent?.getStringExtra("debug")} gear=${intent?.getStringExtra("gear")}")
        debugForceWide = intent?.getStringExtra("forcewide")
        intent?.getStringExtra("gear")?.let { vm.debugJump("gear_$it") }
        intent?.getStringExtra("debug")?.let {
            android.util.Log.i("BeffDebug", "debugJump $it")
            vm.debugJump(it)
        }
        // `--es query <검색어>`: 검색 탭에 검색어를 넣는다(한글 입력이 어려운 에뮬레이터 점검용).
        intent?.getStringExtra("query")?.let { vm.searchChip = null; vm.searchQuery = it }
    }

    /** `--es forcewide <0|441|883>`: 1700 폭 2열 레이아웃을 강제로 그리고 그 x 오프셋(dp)부터 창 폭만큼 보여 준다(화면 점검 전용). */
    private var debugForceWide by androidx.compose.runtime.mutableStateOf<String?>(null)
}

@Composable
fun BeffApp(vm: AppViewModel) {
    BackHandler(enabled = vm.voiceChange) { vm.cancelVoiceChange() }
    BackHandler(enabled = vm.backStack.size > 1 || vm.askRegister || vm.deleteTarget != null || vm.pendingStop != null || vm.needDestination) { vm.back() }
    DebugStateLog(vm)
    // 1113: 주행 중에는 화면 전환 애니메이션을 끈다(Pleos 가이드).
    com.beff.app.ui.Motion.reduced = vm.vehicle.drivingRestricted || vm.vehicle.screenLocked
    if (vm.vehicle.screenLocked || vm.nav.guiding) {
        // 주행 구역(기어가 P가 아님 · 내비 안내 중): 베프 화면 대신 안내 문구만. P로 바꾸거나 안내가 끝나면(도착 · 취소) 원래 화면으로 돌아온다.
        com.beff.app.screens.DrivingLockScreen(
            guiding = vm.nav.guiding && !vm.vehicle.screenLocked,
            caption = vm.voice.lastSpoken.takeIf { vm.demoCaptions },
            vm = vm,
        )
        return
    }
    // 1113: 화면 간 전환 — 쌓이면 오른쪽에서, 돌아가면 왼쪽에서, 탭 · 처음부터는 겹쳐 사라짐(ui/Motion.kt).
    val depth = remember { intArrayOf(vm.backStack.size) }
    Box(Modifier.fillMaxSize()) {
      androidx.compose.animation.AnimatedContent(
        targetState = vm.route,
        transitionSpec = {
            val now = vm.backStack.size
            val dir = when {
                initialState == Route.Splash -> 0
                now > depth[0] -> 1
                now < depth[0] -> -1
                else -> 0
            }
            depth[0] = now
            com.beff.app.ui.Motion.screen(this, dir)
        },
        label = "route",
      ) { r ->
        when (r) {
            // 1115: 위치정보 동의를 아직 안 받았으면 동의 화면, 아니면 하던 여정(F) 또는 프로필 선택
            Route.Splash -> SplashScreen(onDone = { vm.afterSplash() })
            is Route.Consent -> com.beff.app.screens.ConsentScreen(vm, r.review)
            Route.ProfileSelect -> ProfileSelectScreen(vm)
            is Route.MobilityStep -> MobilityStepScreen(vm, r.flow)
            is Route.FilterStep -> FilterStepScreen(vm, r.flow)
            Route.PeopleStep -> PeopleStepScreen(vm)
            Route.ReplaceProfile -> ReplaceProfileScreen(vm)
            is Route.Main -> MainScreen(vm, r.tab)
            is Route.Briefing -> BriefingScreen(vm, r.placeId)
            is Route.Unload -> UnloadScreen(vm, r.placeId, r.preview)
            is Route.Alternatives -> AlternativesScreen(vm, r.placeId, r.manual, r.byUser)
            is Route.UnloadMap -> UnloadMapScreen(vm, r.placeId)
            Route.Credits -> com.beff.app.screens.CreditsScreen(vm)
            Route.DeviceStatus -> com.beff.app.screens.DeviceStatusScreen(vm)
            Route.ProfileEdit -> com.beff.app.screens.ProfileEditScreen(vm)
        }
      }
        RegisterPopups(vm)
    }
}

/**
 * 플로우 점검 페이지(tools/beff-admin)가 `adb logcat -s BeffDebug`로 지금 화면을 읽는다. 디버그 빌드에서만 남긴다.
 * 형식: `state {"route":"Main(tab=Home)", "gear":"Park", ...}`
 */
@Composable
private fun DebugStateLog(vm: AppViewModel) {
    val debuggable = androidx.compose.ui.platform.LocalContext.current.applicationInfo.flags and
        android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (!debuggable) return
    val route = vm.route
    val gear = vm.vehicle.gear
    val locked = vm.vehicle.screenLocked
    val guiding = vm.nav.guiding
    val dest = vm.journey?.dest?.name
    val stops = vm.journey?.stops?.size ?: 0
    // 1113: 점검 페이지가 위치를 옮길 좌표(목적지 · 경유지). 앱이 정한 장소를 그대로 따라가려고 로그에 싣는다.
    val destGeo = vm.journey?.dest?.let { p -> if (p.lat != null && p.lng != null) org.json.JSONObject().put("lat", p.lat).put("lng", p.lng) else null }
    val stopGeos = org.json.JSONArray().also { arr ->
        vm.journey?.stops?.forEach { p ->
            if (p.lat != null && p.lng != null) arr.put(org.json.JSONObject().put("name", p.name).put("lat", p.lat).put("lng", p.lng))
        }
    }
    val geoKey = destGeo.toString() + stopGeos.toString()
    val profile = vm.active?.name
    val popup = when {
        vm.deleteTarget != null -> "delete"
        vm.registeredDone -> "registered"
        vm.askRegister -> "register"
        vm.pendingStop != null -> "stop"
        vm.checkIn != null -> "checkin"
        vm.askEndJourney -> "endJourney"
        else -> null
    }
    LaunchedEffect(route, gear, locked, guiding, dest, stops, profile, popup, geoKey) {
        val json = org.json.JSONObject()
            .put("route", route.toString())
            .put("gear", gear.name)
            .put("locked", locked)
            .put("guiding", guiding)
            .put("dest", dest ?: org.json.JSONObject.NULL)
            .put("stops", stops)
            .put("destGeo", destGeo ?: org.json.JSONObject.NULL)
            .put("stopGeos", stopGeos)
            .put("profile", profile ?: org.json.JSONObject.NULL)
            .put("popup", popup ?: org.json.JSONObject.NULL)
        android.util.Log.i("BeffDebug", "state $json")
    }
}
