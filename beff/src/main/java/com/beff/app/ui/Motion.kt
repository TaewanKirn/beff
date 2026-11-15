package com.beff.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 1113 화면 간 인터랙션. 짧고(200ms 안팎) 작게(가로 10% 미끄러짐 · 6% 확대) 움직인다.
 * - 앞으로(화면이 쌓임): 오른쪽에서 살짝 들어오며 나타남. 뒤로: 왼쪽에서. 같은 깊이(탭 전환 · 처음부터): 겹쳐 사라짐.
 * - 팝업: 바탕 막은 서서히, 카드는 살짝 커지며 나타남.
 * - 주행 중(기어 P가 아님 · 주행 제한)에는 움직이지 않는다(Pleos 기획 가이드 — 주행 중 애니메이션 최소화).
 */
object Motion {
    /** true면 모든 전환을 끈다. BeffApp이 차량 상태로 매번 맞춘다. */
    var reduced by mutableStateOf(false)

    private const val IN = 240
    private const val OUT = 160

    /** [dir] 1 = 앞으로, -1 = 뒤로, 0 = 같은 깊이. */
    fun <S> screen(scope: AnimatedContentTransitionScope<S>, dir: Int): ContentTransform = with(scope) {
        if (reduced) return EnterTransition.None togetherWith ExitTransition.None
        when (dir) {
            0 -> fadeIn(tween(IN, easing = LinearOutSlowInEasing)) togetherWith fadeOut(tween(OUT))
            else -> (slideInHorizontally(tween(IN, easing = FastOutSlowInEasing)) { w -> dir * w / 10 } + fadeIn(tween(IN))) togetherWith
                (slideOutHorizontally(tween(OUT)) { w -> -dir * w / 20 } + fadeOut(tween(OUT)))
        }
    }
}

/** 팝업 바탕 막: 처음 그릴 때 서서히 나타난다. */
fun Modifier.fadeInOnce(durationMs: Int = 180): Modifier = composed {
    val a = remember { Animatable(if (Motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(durationMs)) }
    graphicsLayer { alpha = a.value }
}

/** 팝업 카드: 처음 그릴 때 94% → 100%로 커지며 나타난다. */
fun Modifier.popIn(durationMs: Int = 220): Modifier = composed {
    val a = remember { Animatable(if (Motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
    graphicsLayer {
        alpha = a.value
        val s = 0.94f + 0.06f * a.value
        scaleX = s; scaleY = s
    }
}
