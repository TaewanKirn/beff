package com.beff.app.device

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class GearState { Unknown, Park, Reverse, Neutral, Drive }

/**
 * 차량 상태 읽기: Vehicle SDK의 기어 선택(`CAR_DRIVE`)과 AAOS 주행 제한(`CarUxRestrictionsManager`).
 * 읽기·구독만 한다. 차량 제어(`set*`) 호출은 없다 — 심사 즉시 탈락 규칙.
 * 주차(Parked) 판정은 기어 P(플랫폼 API)로 하고, 속도값으로 상태를 만들지 않는다.
 */
class VehicleBridge(context: Context) {
    private val appContext = context.applicationContext
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var ux: CarUxRestrictionsManager? = null

    var gear by mutableStateOf(GearState.Unknown)
        private set

    /** 주행 중 화면 조작 제한이 걸려 있는가(AAOS). 알 수 없으면 false. */
    var drivingRestricted by mutableStateOf(false)
        private set

    var gearAvailable by mutableStateOf<Boolean?>(null)
        private set
    var uxAvailable by mutableStateOf<Boolean?>(null)
        private set

    val parked: Boolean get() = gear == GearState.Park

    /**
     * 1110 G-4: 승객 쪽 문(조수석 · 뒷좌석)이 닫힘 → 열림으로 바뀐 횟수. 도착 화면이 이 값이 늘 때 시설 안내를 말한다.
     * Vehicle SDK 문 위치 구독(읽기만, CAR_DOORS). 지원하지 않는 차량이면 늘지 않는다.
     */
    var doorOpenCount by mutableStateOf(0)
        private set

    /** 화면 점검 전용: 문 열림을 앱 안에서만 흉내 낸다. */
    fun debugDoorOpen() { doorOpenCount++ }

    private val doorOpen = HashMap<DoorValue, Boolean>()
    private val doorListeners = DoorValue.entries.filter { it in DOOR_AREAS }.associateWith { v ->
        object : DoorPositionListener {
            override fun onPositionUpdated(pos: Int?) {
                val open = (pos ?: 0) > 0
                if (open && doorOpen[v] == false) doorOpenCount++
                doorOpen[v] = open
            }
            override fun onFailed(e: Exception) { Log.w(TAG, "문 위치 구독 실패($v): ${e.message}") }
        }
    }

    /**
     * 베프 화면을 가려야 하는가(주행 구역 = 음성 전용). 기어를 읽으면 P가 아닐 때(R · N · D 모두),
     * 기어를 못 읽으면(SDK 실패 · 아직 모름) AAOS 주행 제한으로 판단한다. 속도값으로 만들지 않는다.
     */
    val screenLocked: Boolean get() = if (gear != GearState.Unknown) gear != GearState.Park else drivingRestricted

    /**
     * 화면 점검 전용: 기어 상태를 앱 안에서만 고정한다(차량에는 아무것도 보내지 않는다).
     * null이면 해제하고 마지막으로 받은 실제 기어로 돌아간다(예전에는 다음 기어 이벤트가 올 때까지 덮어쓴 값이 남았다).
     */
    fun debugForceGear(g: GearState?) {
        forcedGear = g
        gear = g ?: realGear
    }

    private var forcedGear: GearState? = null
    /** 덮어쓰는 동안에도 실제 기어는 기억해 둔다 */
    private var realGear = GearState.Unknown

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            realGear = toGear(gear)
            if (forcedGear != null) return // 화면 점검 중에는 덮어쓴 값을 유지한다
            this@VehicleBridge.gear = realGear
            Log.i(TAG, "기어 $gear → ${this@VehicleBridge.gear}")
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "기어 구독 실패: ${e.message}")
        }
    }

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r ->
        drivingRestricted = r.isRequiresDistractionOptimization
        Log.i(TAG, "주행 제한 ${r.isRequiresDistractionOptimization}")
    }

    fun start() {
        if (vehicle != null) return
        // 기어: 도메인마다 권한·지원이 달라 실패해도 나머지(주행 제한)는 계속 시도한다.
        runCatching {
            val v = Vehicle(appContext).also { vehicle = it }
            v.initialize()
            v.getGear().apply {
                getCurrentGearSelectionState({ g -> gearListener.onSelected(g) }, { e -> gearListener.onFailed(e) })
                registerGearSelection(gearListener)
            }
            gearAvailable = true
        }.onFailure {
            gearAvailable = false
            Log.w(TAG, "Vehicle SDK 기어 시작 실패: ${it.message}")
        }
        // 문 위치: 처음 값은 닫힘으로 두고(열린 채 시작하면 다음 열림부터 센다) 구독한다. 실패해도 나머지는 계속.
        runCatching {
            val d = (vehicle ?: return@runCatching).getDoor()
            doorListeners.forEach { (v, l) -> doorOpen[v] = false; d.registerDoorPosition(DoorArea(v), l) }
        }.onFailure { Log.w(TAG, "Vehicle SDK 문 위치 시작 실패: ${it.message}") }
        runCatching {
            val c = Car.createCar(appContext) ?: error("Car service unavailable")
            car = c
            ux = (c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager)?.also { m ->
                m.registerListener(uxListener)
                m.currentCarUxRestrictions?.let { uxListener.onUxRestrictionsChanged(it) }
            }
            uxAvailable = ux != null
        }.onFailure {
            uxAvailable = false
            Log.w(TAG, "주행 제한 시작 실패: ${it.message}")
        }
    }

    fun release() {
        vehicle?.let { v ->
            runCatching { v.getGear().unregisterGearSelection(gearListener) }
            runCatching { doorListeners.forEach { (a, l) -> v.getDoor().unregisterDoorPosition(DoorArea(a), l) } }
            runCatching { v.release() }
        }
        vehicle = null
        runCatching { ux?.unregisterListener() }
        runCatching { car?.disconnect() }
        ux = null
        car = null
    }

    private fun toGear(g: VehicleGear?): GearState = when (g) {
        VehicleGear.GEAR_PARK -> GearState.Park
        VehicleGear.GEAR_REVERSE -> GearState.Reverse
        VehicleGear.GEAR_NEUTRAL -> GearState.Neutral
        null, VehicleGear.GEAR_UNKNOWN -> GearState.Unknown
        else -> GearState.Drive
    }

    private companion object {
        const val TAG = "BeffVehicle"
        val DOOR_AREAS = setOf(DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)
    }
}
