package com.beff.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 위치정보 · 개인정보 동의 (2026-11-15). 기기 안에만 저장한다.
 * 동의 전 · 거부 상태에서는 차의 현재 위치를 쓰지 않는다 — FusedLocation을 켜지 않고(도착은 내비 이벤트로만),
 * 카카오 장소 검색에는 내 위치 대신 기본 좌표(서울시청)를 보내며, 가는 길 쉬어갈 곳 찾기는 하지 않는다.
 * 약관이 바뀌면 [VERSION]을 올려 다시 묻는다.
 */
object Consent {
    enum class State { Unknown, Agreed, Declined }

    const val VERSION = 1
    /** 동의 화면 QR이 여는 처리방침 (제출용 저장소 TaewanKirn/beff의 docs/PRIVACY.md) */
    const val POLICY_URL = "https://github.com/TaewanKirn/beff/blob/main/docs/PRIVACY.md"
    private const val PREFS = "beff_consent"

    var state by mutableStateOf(State.Unknown)
        private set

    val locationAllowed: Boolean get() = state == State.Agreed

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        state = if (p.getInt("version", 0) < VERSION) State.Unknown
        else State.entries.getOrElse(p.getInt("state", 0)) { State.Unknown }
    }

    fun set(context: Context, value: State) {
        state = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("version", VERSION).putInt("state", value.ordinal).apply()
    }

    fun label(): String = when (state) {
        State.Agreed -> "동의함"
        State.Declined -> "동의 안 함"
        State.Unknown -> "확인 전"
    }
}
