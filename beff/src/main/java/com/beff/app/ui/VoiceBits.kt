package com.beff.app.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.beff.app.R

/**
 * 「듣고 있어요」 칩(`listening_chip.json`, 240×112, 얼굴이 좌우로 갸웃). 음성을 듣는 동안 반복한다.
 * 주행 제한 중에는 움직임을 멈춘다(플레오스 가이드: 운전 중 반복되는 장식 움직임 제한) — [playing]=false.
 */
@Composable
fun ListeningChip(height: Dp, playing: Boolean = true, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.listening_chip))
    LottieAnimation(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = playing,
        modifier = modifier.height(height).aspectRatio(240f / 112f),
    )
}
