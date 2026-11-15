package com.beff.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.beff.app.R
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffType
import kotlinx.coroutines.delay

/**
 * 00-1 스플래시. 앱 실행 → 스플래시 → 프로필 선택(정책 1). 바탕 #008E8C 한 색.
 * 표정(434) 위 y250, 「베프」 120 Medium y740, 문구 44 Regular y888은 840 · 1700 폭 모두 가운데 정렬(Figma 00-1).
 * 문구는 슬로건 「이동 약자를 위한 / 차량용 Barrier-Free 파트너」 두 줄(2026-11-15 확정). Lottie의 tagline 층도 같은 글을
 * Pretendard Regular 44 · 자간 -3% · 줄 간격 62로 윤곽선화해 바꿨다.
 * Lottie는 840×1184 기준이라 폭이 넓어도 가운데에 비율대로 놓고, 읽지 못하면 같은 배치를 정적으로 그린다.
 * 에뮬레이터 재생은 [미확인].
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.splash))
    LaunchedEffect(Unit) {
        delay(2200)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF008E8C)), contentAlignment = Alignment.TopCenter) {
        if (composition != null) {
            LottieAnimation(
                composition = composition,
                iterations = 1,
                modifier = Modifier.fillMaxHeight().aspectRatio(840f / 1184f),
            )
        } else {
            Box(Modifier.fillMaxHeight().aspectRatio(840f / 1184f)) {
                BeffAsset("face/face_smile.svg", 434.dp, Modifier.offset(203.dp, 250.dp))
                Text(
                    "베프",
                    style = BeffType.Title56.copy(fontSize = 120.sp, lineHeight = 132.sp, fontWeight = FontWeight.Medium, color = Color.White, textAlign = TextAlign.Center),
                    modifier = Modifier.offset(0.dp, 740.dp).wrapContentSize().align(Alignment.TopCenter),
                )
                Text(
                    "이동 약자를 위한\n차량용 Barrier-Free 파트너",
                    style = BeffType.Caption28.copy(fontSize = 44.sp, lineHeight = 52.sp, color = Color.White, textAlign = TextAlign.Center),
                    modifier = Modifier.offset(0.dp, 888.dp).align(Alignment.TopCenter),
                )
            }
        }
    }
}
