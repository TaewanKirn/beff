package com.beff.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 1110 H-1 말하는 속도. [rate]는 TTS에 넘길 배속 [가정] — 지금 TTS SDK(3.3.1)에는 속도를 바꾸는 API가 없다. */
enum class SpeechRate(val key: String, val label: String, val rate: Float) {
    Slow("slow", "느리게", 0.8f),
    Normal("normal", "보통", 1f),
    Fast("fast", "빠르게", 1.2f),
}

/** 1110 H-1 글자 크기. [scale]은 글자 배율 [가정] — 화면 전체에 아직 적용하지 않는다(설정만 저장). */
enum class TextSize(val key: String, val label: String, val scale: Float) {
    Normal("normal", "보통", 1f),
    Large("large", "크게", 1.15f),
    XLarge("xlarge", "아주 크게", 1.3f),
}

/**
 * 설정 탭 값(기기 안 저장). 프로필 저장소와 따로 두어 「앱 초기화」가 지우지 않는다.
 * 값이 바뀌면 화면이 바로 다시 그려지도록 Compose 상태로 들고 있다.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("beff_settings", Context.MODE_PRIVATE)

    var speechRate by mutableStateOf(
        SpeechRate.entries.firstOrNull { it.key == prefs.getString(KEY_RATE, null) } ?: SpeechRate.Normal,
    )
        private set

    /** 소리 크기 0..1. 기본 0.67(Figma H-1 막대 200/300). */
    var volume by mutableFloatStateOf(prefs.getFloat(KEY_VOLUME, 0.67f))
        private set

    var textSize by mutableStateOf(
        TextSize.entries.firstOrNull { it.key == prefs.getString(KEY_TEXT, null) } ?: TextSize.Normal,
    )
        private set

    /** 1113 효과음(탭 · 토스트 · 알림 · 경고) 켜기. 기본 켜짐. [com.beff.app.device.Sfx.enabled]와 맞춘다. */
    var soundEffects by mutableStateOf(prefs.getBoolean(KEY_SFX, true))
        private set

    init { com.beff.app.device.Sfx.enabled = soundEffects }

    fun updateSoundEffects(on: Boolean) {
        soundEffects = on
        com.beff.app.device.Sfx.enabled = on
        prefs.edit().putBoolean(KEY_SFX, on).apply()
    }

    fun updateSpeechRate(r: SpeechRate) {
        speechRate = r
        prefs.edit().putString(KEY_RATE, r.key).apply()
    }

    fun updateVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        prefs.edit().putFloat(KEY_VOLUME, volume).apply()
    }

    fun updateTextSize(t: TextSize) {
        textSize = t
        prefs.edit().putString(KEY_TEXT, t.key).apply()
    }

    private companion object {
        const val KEY_RATE = "speech_rate"
        const val KEY_VOLUME = "speech_volume"
        const val KEY_TEXT = "text_size"
        const val KEY_SFX = "sound_effects"
    }
}
