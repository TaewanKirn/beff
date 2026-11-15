package com.beff.app.device

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

enum class SpeechState { Idle, Starting, Listening }

/**
 * 음성 검색(04 검색창 마이크). 정차 중에만 쓴다(Figma 「음성 (정차 중)」).
 * 1순위 SpeechToText SDK(`ON_DEVICE`: 앱 등록 · 클라이언트 키 불필요). 엔진 오류처럼 SDK가 못 쓰는 환경이면
 * 안드로이드 기본 인식(`SpeechRecognizer`, 차량 음성 서비스)으로 한 번 넘어간다.
 * 서버 모드(`HYBRID` · `SERVER`)는 콘솔 My Project 자격 증명이 있어야 해서 쓰지 않는다(키를 코드에 넣지 않는다).
 * 이 클래스에 `set*` 이름의 호출은 없다 — 그래서 SDK 인식 언어도 따로 지정하지 않는다 [미확인: 기본 언어가 한국어인지].
 */
class SpeechBridge(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    var state by mutableStateOf(SpeechState.Idle)
        private set

    /** 지금 쓰는 인식 경로("Pleos STT" · "Android"). 점검 표시용. */
    var engine by mutableStateOf<String?>(null)
        private set

    /** 사용자에게 한 줄로 보일 결과(못 들었어요 · 쓸 수 없어요 …). 새로 시작하면 지운다. */
    var message by mutableStateOf<String?>(null)
        private set

    private var stt: SpeechToText? = null
    private var sdkReady = false
    private var sdkListening = false
    private var recognizer: SpeechRecognizer? = null
    private var onPartial: ((String) -> Unit)? = null
    private var onFinal: ((String) -> Unit)? = null
    private var timeout: Runnable? = null
    private var gotText = false

    private val sdkListener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() { main.post { if (state == SpeechState.Starting) state = SpeechState.Listening } }
        override fun onUpdated(stt: String, completed: Boolean) {
            main.post {
                if (state == SpeechState.Idle) return@post
                if (stt.isNotBlank()) { gotText = true; onPartial?.invoke(stt) }
                if (completed) finish(stt)
            }
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() { main.post { if (state != SpeechState.Idle && gotText) finish(null) } }
        override fun onError(errorType: ErrorType) { main.post { onSdkError(errorType) } }
    }

    /** 음성 인식을 시작한다. 이미 듣는 중이면 무시. [partial]은 중간 결과, [final]은 최종 결과. */
    fun listen(partial: (String) -> Unit, final: (String) -> Unit) {
        if (state != SpeechState.Idle) return
        onPartial = partial
        onFinal = final
        message = null
        gotText = false
        state = SpeechState.Starting
        startSdk()
    }

    /** 사용자가 직접 멈춘다. 들은 게 있으면 그것을 최종으로 넘긴다. */
    fun stop() {
        if (state == SpeechState.Idle) return
        val had = gotText
        stopEngines()
        state = SpeechState.Idle
        if (!had) message = null
    }

    fun release() {
        stopEngines()
        stt?.let { s ->
            runCatching { s.removeListener(sdkListener) }
            runCatching { s.release() }
        }
        stt = null
        sdkReady = false
        state = SpeechState.Idle
    }

    // ── SDK 경로 ──
    private fun startSdk() {
        engine = "Pleos STT"
        armTimeout()
        runCatching {
            val s = stt ?: SpeechToText(appContext, Mode.ON_DEVICE).also { stt = it }
            if (sdkReady) { beginSdk(s); return }
            s.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        sdkReady = true
                        s.addListener(sdkListener) // SDK 규칙: request() 전에 리스너를 등록한다
                        beginSdk(s)
                    }
                }

                override fun onFailure(errorType: ErrorType) { main.post { onSdkError(errorType) } }
            })
        }.onFailure {
            Log.w(TAG, "STT SDK 시작 실패", it)
            main.post { onSdkError(ErrorType.UNKNOWN) }
        }
    }

    private fun beginSdk(s: SpeechToText) {
        if (state == SpeechState.Idle) return
        sdkListening = true
        runCatching { s.request() }.onFailure { onSdkError(ErrorType.UNKNOWN) }
    }

    private fun onSdkError(type: ErrorType) {
        if (state == SpeechState.Idle) return
        Log.w(TAG, "STT SDK 오류 $type")
        sdkListening = false
        runCatching { stt?.stop() }
        // 초기화 실패한 객체는 버려서 다음에 새로 만든다.
        if (!sdkReady) { runCatching { stt?.release() }; stt = null }
        when (type) {
            ErrorType.PERMISSION_DENIED -> fail("음성 인식 권한이 없어요")
            ErrorType.MIC_ERROR -> fail("마이크를 쓸 수 없어요")
            ErrorType.NETWORK_ERROR, ErrorType.TIMEOUT -> fail("잘 못 들었어요. 다시 말씀해 주세요")
            else -> startAndroid() // 엔진 오류 · 미지원 환경: 차량 음성 서비스로 한 번 더 시도
        }
    }

    // ── 안드로이드 기본 인식(차량 음성 서비스) 경로 ──
    private fun startAndroid() {
        engine = "Android"
        val ok = SpeechRecognizer.isRecognitionAvailable(appContext) &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!ok) { fail("이 환경에서는 음성 인식을 쓸 수 없어요"); return }
        recognizer?.destroy()
        recognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()?.apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { main.post { if (state != SpeechState.Idle) state = SpeechState.Listening } }
                override fun onPartialResults(partialResults: Bundle?) {
                    val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (t.isNotBlank()) main.post { gotText = true; onPartial?.invoke(t) }
                }
                override fun onResults(results: Bundle?) {
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    main.post { if (t.isBlank()) fail("잘 못 들었어요. 다시 말씀해 주세요") else { gotText = true; finish(t) } }
                }
                override fun onError(error: Int) { main.post { if (state != SpeechState.Idle) fail("잘 못 들었어요. 다시 말씀해 주세요") } }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
            )
        }
        if (recognizer == null) fail("이 환경에서는 음성 인식을 쓸 수 없어요")
    }

    // ── 공통 ──
    private fun armTimeout() {
        timeout?.let { main.removeCallbacks(it) }
        val r = Runnable { if (state != SpeechState.Idle) { if (gotText) finish(null) else fail("잘 못 들었어요. 다시 말씀해 주세요") } }
        timeout = r
        main.postDelayed(r, LISTEN_TIMEOUT_MS)
    }

    private fun finish(finalText: String?) {
        val cb = onFinal
        stopEngines()
        state = SpeechState.Idle
        finalText?.takeIf { it.isNotBlank() }?.let { cb?.invoke(it) }
    }

    private fun fail(msg: String) {
        stopEngines()
        state = SpeechState.Idle
        message = msg
        Log.i(TAG, "음성 인식 실패: $msg")
    }

    private fun stopEngines() {
        timeout?.let { main.removeCallbacks(it) }
        timeout = null
        if (sdkListening) { sdkListening = false; runCatching { stt?.stop() } }
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
    }

    private companion object {
        const val TAG = "BeffSpeech"
        const val LISTEN_TIMEOUT_MS = 12_000L
    }
}
