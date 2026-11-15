package com.beff.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beff.app.Tab
import com.beff.app.data.Profile
import com.beff.app.screens.ProfileAvatar

/**
 * 하단 GNB 3탭: 홈 · 여정 · 설정(1110 회의: 검색 탭 없음, 검색은 홈 검색창에서 들어간다).
 * Figma 「Beff / GNB」 Version=new: 778×132 흰 90% 알약 r36 + 흰 테두리 2, 탭 254×116 r28, 선택 탭은 틸 12%.
 * 아이콘 48 · 라벨 28(선택 Bold 틸 / 나머지 SemiBold #4A545C). 검색 화면은 홈 탭이 선택된 상태로 보인다.
 */
@Composable
fun BeffGnb(active: Tab, onTab: (Tab) -> Unit, modifier: Modifier = Modifier, sidePadding: androidx.compose.ui.unit.Dp = 56.dp) {
    val shape = RoundedCornerShape(36.dp)
    val shown = if (active == Tab.Search) Tab.Home else active
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = sidePadding)
            .height(132.dp)
            .shadow(20.dp, shape, ambientColor = Color(0x12004D4D), spotColor = Color(0x12004D4D))
            .clip(shape)
            .background(Beff.BoxFill)
            .border(2.dp, Beff.BoxBorder, shape)
            .padding(8.dp),
    ) {
        listOf(
            Triple(Tab.Home, "홈", "pict/home.svg"),
            Triple(Tab.Journey, "여정", "pict/route.svg"),
            Triple(Tab.Settings, "설정", "pict/settings.svg"),
        ).forEach { (tab, label, icon) ->
            val on = tab == shown
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (on) Color(0x1F008E8C) else Color.Transparent)
                    .clickable { onTab(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BeffAsset(icon, 48.dp, tint = if (on) Beff.TealText else Beff.TextPrimary)
                Text(
                    label,
                    style = BeffType.Caption28.copy(
                        color = if (on) Beff.TealText else Beff.TextSecondary,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}

private fun Modifier.fillMaxHeight() = this.then(Modifier.height(116.dp))

/** 스크롤 화면은 GNB(하단 고정 버튼) 위 72dp에서 내용이 흐려진다(정책 47). */
@Composable
fun ScrollFade(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Brush.verticalGradient(listOf(Color(0x00E5F4F4), Beff.TealTint))),
    )
}

/** 한국어 조사: 받침 있으면 "과", 없으면 "와". */
fun withWa(name: String): String {
    val last = name.lastOrNull() ?: return name
    val code = last.code - 0xAC00
    // 숫자는 읽는 소리 기준: 일·삼·육·칠·팔·영은 받침 있음, 이·사·오·구는 없음
    val hasBatchim = when {
        last in '0'..'9' -> last in "013678"
        else -> code in 0..11171 && code % 28 != 0
    }
    return name + if (hasBatchim) "과" else "와"
}

/** 한국어 조사: 받침 있으면 "을", 없으면 "를" (숫자는 읽는 소리 기준). */
fun withEul(name: String): String {
    val last = name.lastOrNull() ?: return name
    val code = last.code - 0xAC00
    val hasBatchim = when {
        last in '0'..'9' -> last in "013678"
        else -> code in 0..11171 && code % 28 != 0
    }
    return name + if (hasBatchim) "을" else "를"
}

/** 화면 상단 "OO과 함께" — 누르면 프로필 선택(정책 54). */
@Composable
fun WhoHeader(profile: Profile?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = profile?.let { if (it.id.isEmpty()) it.summary else it.name } ?: "혼자"
    Row(
        modifier
            .clip(RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (profile != null) {
            Box(
                Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .border(3.6.dp, Beff.Teal, RoundedCornerShape(40.dp)),
            ) { ProfileAvatar(profile, 80.dp) }
        }
        Text(
            buildAnnotatedString {
                val w = withWa(label)
                withStyle(SpanStyle(color = Beff.TealText, textDecoration = TextDecoration.Underline)) { append(label) }
                withStyle(SpanStyle(color = Beff.TextStrong)) { append(w.removePrefix(label) + " 함께") }
            },
            style = BeffType.Title56.copy(lineHeight = 73.sp),
            maxLines = 1,
        )
    }
}

/** 박스 제목 오른쪽의 작은 회색 알약: 편집 · 수정 · 더보기(정책 75). */
@Composable
fun SmallTagButton(text: String, onClick: () -> Unit) {
    // 2/3 전체 화면은 터치 76(정책 2/3 확장), 3분할은 72
    Box(
        Modifier
            .height(if (LocalWide.current) 76.dp else 72.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(Beff.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BeffType.Small30.copy(fontSize = 32.sp, color = Beff.TextPrimary)) }
}

/** 시설 타일: 일러스트 72 + 이름 (도착하면 쓸 수 있어요 / 하차 미리보기). */
@Composable
fun FacilityTile(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(Beff.Surface)
            .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BeffAsset(facilityIllust(label), 72.dp)
        Text(label, style = BeffType.Body32.copy(color = Beff.TextStrong, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

fun facilityIllust(label: String): String = when (label) {
    "수유실" -> "illust/nursing.svg"
    "기저귀 교환대" -> "illust/diaper.svg"
    "가족 화장실" -> "illust/family_toilet.svg"
    "장애인 화장실" -> "illust/toilet.svg"
    "장애인 주차" -> "illust/parking.svg"
    "엘리베이터" -> "illust/elevator.svg"
    "경사로" -> "illust/ramp.svg"
    "측면 승하차 공간" -> "illust/side_space.svg"
    "쉼터" -> "illust/bench.svg"
    "계단 없는 길" -> "illust/deck_path.svg"
    "유모차 대여" -> "illust/stroller.svg"
    "유모차 주차" -> "illust/stroller_parking.svg"
    "휠체어 대여" -> "illust/wheelchair.svg"
    "턱 없는 입구" -> "illust/door.svg"
    else -> "illust/badge.svg"
}

/** 이전/다음 둥근 버튼 72dp (캐러셀). */
@Composable
fun RoundArrow(forward: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onWhite: Boolean = false) {
    val d = if (LocalWide.current) 76.dp else 72.dp
    Box(
        modifier
            .size(d)
            .clip(RoundedCornerShape(d / 2))
            .background(if (onWhite) Beff.Card else Beff.BoxFill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BeffAsset(
            "pict/chevron_right.svg", 48.dp,
            if (forward) Modifier else Modifier.graphicsLayer(scaleX = -1f),
        )
    }
}

/** 쪽 표시 점: 선택은 36×12 알약, 나머지는 12 원. */
@Composable
fun PageDots(count: Int, index: Int, onDark: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val on = i == index
            val color = when {
                onDark && on -> Color.White
                onDark -> Color(0x99FFFFFF)
                on -> Beff.Teal
                else -> Color(0xFF8BCBCA)
            }
            Box(
                Modifier
                    .height(12.dp)
                    .size(if (on) 36.dp else 12.dp, 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color),
            )
        }
    }
}
