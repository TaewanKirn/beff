package com.beff.app.device

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.content.Context
import android.location.Location
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class LocationLink { Idle, Waiting, Connected, Unsupported, Failed }

/**
 * FusedLocation SDK 연동(차량 위치). 읽기 전용: 마지막 위치 조회 + 위치 변경 구독만 쓴다.
 * 권한 `pleos.car.permission.FUSED_LOCATION`(dangerous)을 받은 뒤 [start]를 부른다. 거부되면 앱은 위치 없이 동작한다.
 * 이 클래스에 `set*` 이름의 호출은 없다.
 */
class LocationBridge(context: Context) {
    private val appContext = context.applicationContext
    private var fused: FusedLocation? = null

    var link by mutableStateOf(LocationLink.Idle)
        private set
    var location by mutableStateOf<LocationData?>(null)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            if (location != null) this@LocationBridge.location = location
        }

        override fun onError(exception: Exception) {
            Log.w(TAG, "위치 오류 ${exception.javaClass.simpleName}: ${exception.message}")
            lastError = exception.message ?: exception.javaClass.simpleName
        }
    }

    fun markWaiting() { if (link == LocationLink.Idle) link = LocationLink.Waiting }

    fun start() {
        if (fused != null) return
        runCatching {
            val f = FusedLocation(appContext)
            // 가이드: isSupported()를 먼저 확인하고, 지원하지 않으면 위치 기능을 쓰지 않는다.
            if (!f.isSupported()) {
                link = LocationLink.Unsupported
                Log.i(TAG, "FusedLocation 미지원 환경")
                return
            }
            f.initialize()
            fused = f
            f.getLastKnownLocation(listener)
            f.registerFusedLocationCallback(listener)
            link = LocationLink.Connected
            Log.i(TAG, "FusedLocation 연결됨")
        }.onFailure {
            link = LocationLink.Failed
            lastError = it.message
            Log.w(TAG, "FusedLocation 시작 실패", it)
        }
    }

    fun release() {
        fused?.let { f ->
            runCatching { f.unregisterFusedLocationCallback(listener) }
            runCatching { f.release() }
        }
        fused = null
        link = LocationLink.Idle
        location = null // 위치정보 동의 철회(1115) 뒤에는 마지막 위치도 쓰지 않는다
    }

    /** 현재 위치에서 (lat, lng)까지 직선거리(m). 위치를 아직 모르면 null. 주행 거리가 아니다. */
    fun metersTo(lat: Double, lng: Double): Float? {
        val here = location ?: return null
        val out = FloatArray(1)
        Location.distanceBetween(here.latitude, here.longitude, lat, lng, out)
        return out[0]
    }

    private companion object { const val TAG = "BeffLocation" }
}
