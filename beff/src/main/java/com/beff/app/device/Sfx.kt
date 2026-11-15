package com.beff.app.device

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import com.beff.app.R
import kotlinx.coroutines.launch

/**
 * 1113 효과음. 소리는 직접 합성했다(`tools/beff-sfx/gen.py`, 외부 음원 · 라이선스 없음).
 * - [Kind.Tap] 버튼 · 카드를 누를 때(아래 [TapSoundIndication]이 모든 clickable에 붙인다)
 * - [Kind.Toast] 토스트 · 확인 표시가 뜰 때
 * - [Kind.Notify] 도착 · 접근 같은 안내 음성 앞 차임
 * - [Kind.Warning] 내비 반영 실패 · 하차 어려움 같은 경고
 * 차량 시스템 효과음 볼륨(ASSISTANCE_SONIFICATION)을 따른다. 소리가 안 되는 환경에서는 조용히 넘어간다.
 */
object Sfx {
    enum class Kind(val res: Int, val volume: Float) {
        Tap(R.raw.sfx_tap, 0.6f),
        Toast(R.raw.sfx_toast, 0.8f),
        Notify(R.raw.sfx_notify, 0.9f),
        Warning(R.raw.sfx_warning, 1f),
    }

    /** 효과음 끄기(설정 등에서 쓸 수 있게 열어 둔다). */
    @Volatile var enabled = true

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<Kind, Int>()
    private val loaded = mutableSetOf<Int>()
    private var lastTapAt = 0L
    private var lastAlertAt = mutableMapOf<Kind, Long>()

    fun init(context: Context) {
        if (pool != null) return
        runCatching {
            val p = SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
            p.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(loaded) { loaded += id } }
            Kind.entries.forEach { ids[it] = p.load(context.applicationContext, it.res, 1) }
            pool = p
        }.onFailure { Log.w(TAG, "효과음 준비 실패", it) }
    }

    fun play(kind: Kind) {
        if (!enabled) return
        val p = pool ?: return
        val id = ids[kind] ?: return
        if (synchronized(loaded) { id !in loaded }) return
        val now = SystemClock.elapsedRealtime()
        if (kind == Kind.Tap) {
            if (now - lastTapAt < 60) return          // 겹친 clickable이 한 번 누름에 두 번 울리지 않게
            lastTapAt = now
        } else {
            if (now - (lastAlertAt[kind] ?: 0L) < 400) return
            lastAlertAt[kind] = now
        }
        runCatching { p.play(id, kind.volume, kind.volume, 1, 0, 1f) }
    }

    fun tap() = play(Kind.Tap)
    fun toast() = play(Kind.Toast)
    fun chime() = play(Kind.Notify)
    fun warning() = play(Kind.Warning)

    fun release() {
        runCatching { pool?.release() }
        pool = null; ids.clear(); synchronized(loaded) { loaded.clear() }
    }

    private const val TAG = "BeffSfx"
}

/**
 * 기존 눌림 표시(ripple 등)는 그대로 두고, 누름이 클릭으로 끝날 때(PressInteraction.Release) 탭 소리를 낸다.
 * MainActivity가 LocalIndication에 씌워서 앱의 모든 clickable(indication을 따로 끄지 않은 것)에 적용된다.
 */
class TapSoundIndication(private val base: IndicationNodeFactory?) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        TapSoundNode(interactionSource, base?.create(interactionSource))

    override fun equals(other: Any?) = other is TapSoundIndication && other.base == base
    override fun hashCode() = base.hashCode()
}

private class TapSoundNode(private val source: InteractionSource, inner: DelegatableNode?) : DelegatingNode() {
    init { if (inner != null) delegate(inner) }

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { if (it is PressInteraction.Release) Sfx.tap() }
        }
    }
}
