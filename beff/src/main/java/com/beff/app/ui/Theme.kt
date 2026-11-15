package com.beff.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 스타일프레임_261105 토큰 + 1113 다크 모드. 화면 기준 3분할 840×1184(dp 1:1).
 *
 * [dark]는 Compose 상태라서, 이 값을 읽는 컴포저블은 모드가 바뀌면 다시 그려진다.
 * MainActivity가 `isSystemInDarkTheme()`(차량 · 시스템 야간 모드)로 매번 맞춘다.
 * 다크 값은 Figma `[최종] 1112 검증` 다크 섹션과 같은 토큰이다. 사진 · 컬러 일러스트는 바꾸지 않는다.
 */
object Beff {
    var dark by mutableStateOf(false)

    private fun c(light: Long, dark: Long): Color = if (this.dark) Color(dark) else Color(light)

    /** 주 버튼 · 브랜드 채움. 두 모드 같음. */
    /** 리뷰 4: 흰 글자를 얹는 틸은 대비를 위해 #00807C(Figma #00837F · #008E8C에서 조정). */
    val Teal = Color(0xFF00807C)
    val TealMain get() = c(0xFF008E8C, 0xFF3CC8C1)      // 테두리 · 강조선
    val TealSoft get() = c(0x1F008E8C, 0x333CC8C1)      // 12%
    val TealTrack get() = c(0x24008280, 0x3D3CC8C1)     // 진행 막대 트랙
    /** 틸 글자 · 아이콘(다크에선 밝은 틸). */
    val TealText get() = c(0xFF00837F, 0xFF3CC8C1)
    /** 선택 · 강조 카드(옅은 틸 바탕) 안의 강조 글자. 다크에선 틸 글자가 틸 바탕에 묻혀 흰색(1113 피드백). */
    val TealTextOnTint get() = c(0xFF00837F, 0xFFF1F4F5)
    val TextStrong get() = c(0xFF131417, 0xFFF1F4F5)
    val TextPrimary get() = c(0xFF252B31, 0xFFDDE3E6)
    val TextSecondary get() = c(0xFF4A545C, 0xFFA9B4BA)
    /** 더 옅은 회색 글자 · 아이콘(66727B). */
    val TextMuted get() = c(0xFF66727B, 0xFF8C979E)
    /** 비활성 · 옅은 선(AEB8BF). */
    val Muted2 get() = c(0xFFAEB8BF, 0xFF5C686E)

    /** 화면 바탕. */
    val Bg get() = c(0xFFFFFFFF, 0xFF0F1416)
    /** 여정(F) · 도착(G) · 주행(E) 바탕(4-18 · 6-15). */
    val BgCool get() = c(0xFFF1F6F6, 0xFF0F1416)
    /** 카드 · 패널 · GNB · 팝업. */
    val Card get() = c(0xFFFFFFFF, 0xFF1B2226)
    /** 박스 안 회색 요소 · 회색 카드(EEF4F4 · F1F6F6). */
    val Surface get() = c(0xFFEEF4F4, 0xFF242C30)
    val SurfaceHigh get() = c(0xFFE3E9EC, 0xFF2E383C)
    /** 카드 테두리 · 구분선(E1EAEA 계열). */
    val Stroke get() = c(0xFFE1EAEA, 0xFF2E383C)
    val DashBorder get() = c(0xFFB6BFC5, 0xFF46535A)
    val Divider get() = c(0x1A131417, 0x24FFFFFF)
    val BoxFill get() = c(0xE6FFFFFF, 0xFF1B2226)     // 흰색 90%
    /** 박스 흰 테두리(다크에선 선). */
    val BoxBorder get() = c(0xFFFFFFFF, 0xFF2E383C)
    val BoxShadow get() = c(0x12004D4D, 0x33000000)   // rgba(0,77,77,.07)

    /** 시설 칩(6-8). */
    val ChipBg get() = c(0xFFEDF6F1, 0xFF1E3A38)
    val ChipText get() = c(0xFF00706C, 0xFF7FDCD6)
    /** 선택 타일 · 줄(6-2 · 6-10). */
    val Selected get() = c(0xFFB3DEDA, 0xFF255652)
    /** 옅은 틸 바탕(D6ECEB · DFF1EF). */
    val TealTint get() = c(0xFFD6ECEB, 0xFF1F3F3D)

    val Warning get() = c(0xFFD32F2F, 0xFFFF6B6B)
    val WarningTint get() = c(0xFFFDECEA, 0xFF3A2326)
    /**
     * 토스트 · 말풍선(음성 토스트 E-2~E-5 · 홈 배너 B-2~B-4 · 체크인 B-5). 1113 결정: 다크 모드에선 흰색으로 뒤집는다.
     */
    val Toast get() = c(0xF0252B31, 0xFFFFFFFF)
    val ToastText get() = c(0xFFFFFFFF, 0xFF131417)
    val ToastSub get() = c(0xFFC9E1DD, 0xFF4A545C)
    /** 홈 배너 바탕(라이트 #173535). */
    val Banner get() = c(0xFF173535, 0xFFFFFFFF)
    val BannerRing get() = c(0xFF044846, 0xFFEDF6F5)
    /** 토스트 안 얼굴 원(다크: #D6ECEB 원 + #008E8C 선). */
    val ToastFaceBg get() = c(0xFFD6ECEB, 0xFFD6ECEB)
    val ToastFaceInk get() = c(0xFF008E8C, 0xFF008E8C)
    /** 토스트 안 보조 버튼(다크: #EDEEF2 + #131417). */
    val ToastSecondaryBtn get() = c(0xFFEDEEF2, 0xFFEDEEF2)
    val ToastSecondaryText get() = c(0xFF131417, 0xFF131417)
    /** 팝업 카드(리뷰 5: 다크 #242C30 + 테두리 #3A4448 1.5) · 팝업 안 보조 버튼(다크 #343E43). */
    val Popup get() = c(0xFFFFFFFF, 0xFF242C30)
    val PopupBorder get() = c(0x0D131417, 0xFF3A4448)
    val PopupSecondary get() = c(0xFFEDEEF2, 0xFF343E43)
    /** 사진 어둡게(리뷰 5: 다크 25%). */
    val PhotoScrim get() = c(0x00000000, 0x40000000)
    /** 보조 버튼(회색). */
    val SecondaryBtn get() = c(0xFFEDEEF2, 0xFF2A3238)
    val SecondaryBtnText get() = c(0xFF252B31, 0xFFDDE3E6)
}

object BeffType {
    /** 6-1: 자간 -2% 통일. */
    private fun t(size: Int, w: FontWeight, lh: Int? = null) =
        TextStyle(
            fontSize = size.sp,
            fontWeight = w,
            color = Beff.TextPrimary,
            lineHeight = (lh?.sp ?: (size * 1.32f).sp),
            letterSpacing = (-0.02f).em,
        )

    // 글자 위계(6-5 · 6-7): 화면 제목 48 ExtraBold · 카드 제목 40 Bold · 줄 제목 36 SemiBold · 보조 32 Regular · 작은 버튼 30 SemiBold · 라벨 28.
    /** 1113부터 화면 제목은 48 한 가지 — 56은 48로 맞춘다(이름은 호환용). */
    val Title56 get() = t(48, FontWeight.ExtraBold, lh = 64)
    val Title48 get() = t(48, FontWeight.ExtraBold, lh = 64)
    val Bar40 get() = t(40, FontWeight.Bold, lh = 52)
    val Box40 get() = t(40, FontWeight.Bold, lh = 54)
    val Item36 get() = t(36, FontWeight.SemiBold, lh = 48)
    val Item36Semi get() = t(36, FontWeight.SemiBold, lh = 48)
    val Body32 get() = t(32, FontWeight.Normal, lh = 42)
    val Button36 get() = t(36, FontWeight.SemiBold)
    val Small30 get() = t(30, FontWeight.SemiBold, lh = 41)
    val Caption28 get() = t(28, FontWeight.Normal, lh = 38)
    val Brand44 get() = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Beff.TextPrimary, lineHeight = 58.sp, letterSpacing = (-0.03f).em)
}
