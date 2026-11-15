package com.beff.app.device

import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ai.pleos.playground.tts.TextToSpeech as PleosTts

enum class VoiceLink { Idle, Connecting, Ready, Failed }

/**
 * 주행 중 음성 안내(말하기 전용). 주행 구역은 화면 조작 없이 음성으로만 다룬다.
 * TextToSpeech SDK 온디바이스 모드(앱 등록 · 클라이언트 키 불필요). 초기화 전에 온 문장은 한 번에 하나만 보관한다.
 * 언어는 SDK 기본값을 쓴다 — `set*` 호출을 피하기 위해 로케일을 따로 지정하지 않는다 [미확인: 기본이 한국어인지].
 */
class VoiceGuide(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: PleosTts? = null
    private var pending: String? = null
    private var lastText: String? = null
    private var lastAt = 0L

    var link by mutableStateOf(VoiceLink.Idle)
        private set

    /** 마지막으로 읽은 문장(상태 표시·확인용). */
    var lastSpoken by mutableStateOf<String?>(null)
        private set

    private val events = object : EventListener {
        override fun onStart() { Log.i(TAG, "재생 시작") }
        override fun onReady() = Unit
        override fun onDone() { Log.i(TAG, "재생 끝") }
        override fun onError(errorType: ErrorType) { Log.w(TAG, "오류 $errorType") }
        override fun onStop() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    fun start() {
        if (tts != null) return
        link = VoiceLink.Connecting
        runCatching {
            val t = PleosTts(appContext, Mode.ON_DEVICE).also { tts = it }
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    // SDK 규칙: initialize() 이후에 리스너를 붙인다
                    runCatching { t.addEventListener(events) }
                    main.post {
                        link = VoiceLink.Ready
                        Log.i(TAG, "TTS 준비됨")
                        pending?.let { speakNow(it) }
                        pending = null
                    }
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "TTS 초기화 실패 $errorType")
                    main.post { link = VoiceLink.Failed }
                }
            })
        }.onFailure {
            link = VoiceLink.Failed
            Log.w(TAG, "TTS 사용 불가", it)
        }
    }

    /**
     * 문장을 읽는다. 같은 문장이 8초 안에 다시 오면 무시한다. 음성이 안 되는 환경에서는 조용히 넘어간다.
     * [chime]이면 알림음(1113 효과음)을 먼저 울리고 차임이 거의 끝난 뒤 읽는다. 음성이 안 돼도 알림음은 울린다.
     */
    fun say(text: String, chime: Boolean = false) {
        val now = System.currentTimeMillis()
        if (text == lastText && now - lastAt < 8_000) return
        lastText = text
        lastAt = now
        if (chime) Sfx.chime()
        when (link) {
            VoiceLink.Ready -> if (chime) main.postDelayed({ speakNow(text) }, CHIME_LEAD_MS) else speakNow(text)
            VoiceLink.Connecting, VoiceLink.Idle -> pending = listOfNotNull(pending, text).joinToString(" ")
            VoiceLink.Failed -> Log.i(TAG, "음성 불가, 건너뜀: $text")
        }
    }

    private fun speakNow(text: String) {
        Log.i(TAG, "말하기: $text")
        lastSpoken = text
        runCatching { tts?.speak(text, true) }.onFailure { Log.w(TAG, "speak 실패", it) }
    }

    fun release() {
        runCatching { tts?.stop() }
        runCatching { tts?.release() }
        tts = null
        link = VoiceLink.Idle
    }

    private companion object {
        const val TAG = "BeffVoice"
        /** 알림음 3음이 다 나온 뒤 말하기 시작하는 간격. */
        const val CHIME_LEAD_MS = 450L
    }
}
