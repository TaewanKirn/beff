package com.beff.app.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beff.app.AppViewModel
import com.beff.app.Route
import com.beff.app.Tab
import com.beff.app.data.MAX_PROFILES
import com.beff.app.data.Profile
import com.beff.app.data.SeatKind
import com.beff.app.data.SpeechRate
import com.beff.app.data.TextSize
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffButton
import com.beff.app.ui.BeffGnb
import com.beff.app.ui.BeffType
import com.beff.app.ui.ButtonKind
import com.beff.app.ui.LocalWide
import com.beff.app.ui.TitleBar
import com.beff.app.ui.WIDE_BOTTOM
import com.beff.app.ui.WIDE_GAP
import com.beff.app.ui.WIDE_SIDE
import com.beff.app.ui.WIDE_TOP
import com.beff.app.ui.beffBackground

// ───────── 1110 수정 H-1 설정 홈 ─────────
// 와이드(1700): 왼쪽 열(56 · 778) = 「설정」 + 프로필 카드 + GNB, 오른쪽 = 화면 오른쪽 끝까지 붙은 흰 패널(866~1700, 안 여백 58).
// 3분할(840): 한 열 스크롤 — 「설정」 + 프로필 카드 + 설정 카드(음성 안내 · 화면 · 정보 출처).
// 임시 설정 목록(연동 상태 · 저작권 · 출처 · 앱 초기화)은 정보 출처 아래 행과 맨 아래 흐린 버튼으로 옮겼다.

internal val SetDivider: Color get() = Beff.Stroke
private val SetSegBg: Color get() = Beff.Surface
private val SetSegOn: Color get() = Beff.ChipText
private val AvatarBg: Color get() = Beff.TealTint

/** 설정 화면 글자(Figma H-1 · H-2). */
internal object SetType {
    // 6-5: 카드 제목 40 Bold · 줄 제목 36 SemiBold · 보조/값 32 Regular · 링크 30 SemiBold. 다크 모드를 따르도록 getter.
    val Section get() = BeffType.Box40.copy(color = Beff.TextStrong)
    val Label get() = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold, color = Beff.TextStrong, lineHeight = 48.sp)
    val Item get() = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.SemiBold, color = Beff.TextStrong, lineHeight = 48.sp)
    val Sub get() = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal, color = Beff.TextSecondary, lineHeight = 42.sp)
    val Value get() = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal, color = Beff.TextSecondary, lineHeight = 42.sp)
    val Link get() = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Beff.TealText, lineHeight = 42.sp)
}

/** 흰 카드: 흰 채움 · 흰 90% 테두리 2 · r36 · 그림자 20(7%). Figma 「프로필」 · 「오른쪽 열」 공통. */
internal fun Modifier.settingsCard(shape: Shape = RoundedCornerShape(36.dp)): Modifier = this
    .shadow(20.dp, shape, ambientColor = Color(0x12004D4D), spotColor = Color(0x12004D4D))
    .clip(shape)
    .background(Beff.Card)
    .border(2.dp, Beff.BoxBorder, shape)

/**
 * 와이드 깊이 화면 · 탭 공통 2열 틀(H-1 · H-2): 왼쪽 열(시작 여백 56, 폭 778) + 간격 32 + 오른쪽 흰 패널(폭 834, 위아래 끝까지).
 * [left]의 위아래 여백은 호출하는 쪽이 정한다(탭은 위 48 · GNB, 깊이 화면은 제목 바).
 */
@Composable
internal fun SettingsPanelLayout(
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(834f).fillMaxHeight().padding(start = WIDE_SIDE), content = left)
        Spacer(Modifier.weight(WIDE_GAP.value))
        Column(
            Modifier.weight(834f).fillMaxHeight().settingsCard(RoundedCornerShape(topStart = 36.dp, bottomStart = 36.dp)),
            content = right,
        )
    }
}

/** 설정 탭 첫 화면. MainScreen의 Tab.Settings 분기에서 부른다(와이드는 GNB까지 직접 그린다). */
@Composable
fun SettingsTab(vm: AppViewModel) {
    if (LocalWide.current) {
        // 1113 Figma H-1 와이드: 오른쪽 패널 없이 왼쪽 열 하나(제목 · 프로필 카드 · 정보 출처/앱 초기화 카드 · GNB).
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(834f).fillMaxHeight().padding(start = WIDE_SIDE)) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = WIDE_TOP + 24.dp)) {
                    SettingsTitle()
                    Spacer(Modifier.height(36.dp))
                    ProfileListCard(vm)
                    Spacer(Modifier.height(36.dp))
                    InfoResetCard(vm)
                }
                Spacer(Modifier.height(WIDE_GAP))
                Box(Modifier.padding(bottom = WIDE_BOTTOM)) { BeffGnb(Tab.Settings, vm::selectTab, sidePadding = 0.dp) }
            }
            Spacer(Modifier.weight(WIDE_GAP.value + 834f))
        }
        return
    }
    // 3분할: MainScreen이 위 48 · GNB · 스크롤 페이드를 그린다.
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 56.dp).padding(top = 24.dp, bottom = 72.dp),
    ) {
        SettingsTitle()
        Spacer(Modifier.height(36.dp))
        ProfileListCard(vm)
        Spacer(Modifier.height(36.dp))
        InfoResetCard(vm)
    }
}

@Composable
private fun SettingsTitle() {
    Text("설정", style = BeffType.Title48.copy(color = Beff.TextStrong))
}

/** 플로우 점검용: 저장한 프로필 · 여정을 모두 지우고 처음부터 다시 시작한다. 되돌릴 수 없어 흐린 버튼으로 맨 아래에 둔다. */
@Composable
private fun ResetButton(vm: AppViewModel, modifier: Modifier = Modifier) {
    // 6-4: 「앱 초기화」 빨강 한 줄. 리뷰 N5: 되돌릴 수 없으니 확인 창을 먼저 띄운다(1113 추가 — 이전엔 바로 초기화).
    var ask by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(
        modifier.fillMaxWidth().height(96.dp).clickable { ask = true },
        contentAlignment = Alignment.CenterStart,
    ) { Text("앱 초기화", style = SetType.Label.copy(color = Beff.Warning)) }
    if (ask) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { ask = false }) {
            val shape = RoundedCornerShape(40.dp)
            Column(
                Modifier.width(720.dp).clip(shape).background(Beff.Popup).border(1.5.dp, Beff.PopupBorder, shape).padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("앱을 초기화할까요?", style = BeffType.Box40.copy(color = Beff.TextStrong))
                Text("프로필 · 여정 · 최근 검색이 모두 지워지고 되돌릴 수 없어요", style = BeffType.Body32.copy(fontSize = 30.sp, color = Beff.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.weight(1f)) { BeffButton("초기화", { ask = false; vm.resetApp() }, containerColor = Beff.Warning) }
                    Box(Modifier.weight(1f)) { BeffButton("취소", { ask = false }, kind = ButtonKind.Secondary, containerColor = Beff.PopupSecondary) }
                }
            }
        }
    }
}

// ── 프로필 카드: 헤더(프로필 · + 추가) + 행(이미지 72 · 이름 34 · 보조수단 · 앉은 자리 28 · >) ──

@Composable
private fun ProfileListCard(vm: AppViewModel) {
    Column(Modifier.fillMaxWidth().settingsCard().padding(start = 34.dp, end = 34.dp, top = 30.dp, bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("프로필", style = SetType.Section)
            if (vm.profiles.size < MAX_PROFILES) {
                Text(
                    "+ 추가", style = SetType.Link,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = vm::startCreate),
                )
            }
        }
        if (vm.profiles.isEmpty()) {
            Text("아직 만든 프로필이 없어요", style = SetType.Sub, modifier = Modifier.padding(vertical = 40.dp))
        }
        vm.profiles.forEachIndexed { i, p ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(SetDivider))
            ProfileRow(p, onClick = { vm.openProfileEdit(p) }, onSelect = { vm.selectProfile(p) })
        }
    }
}

@Composable
private fun ProfileRow(p: Profile, onClick: () -> Unit, onSelect: () -> Unit = {}) {
    // 6-4: 프로필 아바타(BEFF Avatar) · 이름 36 SemiBold · 보조 32 · [편집][선택](리뷰 1: Beff Button Medium 높이 80).
    Row(
        Modifier.fillMaxWidth().height(136.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(80.dp).clip(CircleShape), contentAlignment = Alignment.Center) { ProfileAvatar(p, 80.dp) }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(p.name, style = SetType.Item, maxLines = 1)
            Text(profileLine(p), style = SetType.Sub, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(120.dp)) { BeffButton("편집", onClick, kind = ButtonKind.Secondary, height = 80.dp) }
            Box(Modifier.width(120.dp)) { BeffButton("선택", onSelect, height = 80.dp) }
        }
    }
}

/** 「휠체어 · 유모차」: 고른 보조수단 이름(앉은 자리 표시는 1115에 뺐다). */
internal fun profileLine(p: Profile): String =
    p.counts.filterValues { it > 0 }.keys.sortedBy { it.ordinal }.joinToString(" · ") { it.label }

// ── 정보 카드(1113 Figma H-1): 「정보 출처 ›」 한 줄 + 구분선 + 「앱 초기화」(빨강). ──
// 음성 안내(말하는 속도 · 소리 크기) · 화면(글자 크기) · 연동 상태는 Figma에 없어 뺐다(설정값 저장 코드는 그대로).

@Composable
private fun InfoResetCard(vm: AppViewModel) {
    Column(Modifier.fillMaxWidth().settingsCard().padding(horizontal = 34.dp, vertical = 8.dp)) {
        // 1113: 효과음 켜기/끄기(사용자 요청). 줄 전체를 눌러도 바뀐다.
        val sfxOn = vm.settings.soundEffects
        Row(
            Modifier.fillMaxWidth().height(96.dp).clickable { vm.settings.updateSoundEffects(!sfxOn) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("효과음", style = SetType.Label, maxLines = 1, modifier = Modifier.weight(1f))
            Text(if (sfxOn) "켜짐" else "꺼짐", style = SetType.Value, maxLines = 1)
            OnOffSwitch(sfxOn)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(SetDivider))
        // 1115: 위치정보 · 개인정보 동의를 다시 보거나 철회한다
        Row(
            Modifier.fillMaxWidth().height(96.dp).clickable { vm.push(Route.Consent(review = true)) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("개인정보 · 위치정보", style = SetType.Label, maxLines = 1, modifier = Modifier.weight(1f))
            Text(com.beff.app.data.Consent.label(), style = SetType.Value, maxLines = 1)
            BeffAsset("pict/chevron_right.svg", 36.dp, tint = Beff.TextSecondary)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(SetDivider))
        Row(
            Modifier.fillMaxWidth().height(96.dp).clickable { vm.push(Route.Credits) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("정보 출처", style = SetType.Label, maxLines = 1, modifier = Modifier.weight(1f))
            Text("열린관광 · 주차장 데이터", style = SetType.Value, maxLines = 1)
            BeffAsset("pict/chevron_right.svg", 36.dp, tint = Beff.TextSecondary)
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(SetDivider))
        ResetButton(vm)
    }
}

/** 켜기/끄기 스위치 88×52: 켜짐 = 틸 바탕 + 오른쪽 흰 손잡이 40, 꺼짐 = 회색 바탕 + 왼쪽 손잡이. 누르는 동작은 줄이 받는다. */
@Composable
private fun OnOffSwitch(on: Boolean) {
    val x by androidx.compose.animation.core.animateDpAsState(if (on) 42.dp else 6.dp, label = "sfxSwitch")
    Box(
        Modifier.width(88.dp).height(52.dp).clip(RoundedCornerShape(26.dp)).background(if (on) Beff.Teal else Beff.SurfaceHigh),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = x).size(40.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun LinkRow(label: String, value: String? = null, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(2.dp).background(SetDivider))
    Row(
        Modifier.fillMaxWidth().height(96.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(label, style = SetType.Label, maxLines = 1, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = SetType.Value, maxLines = 1)
        BeffAsset("pict/chevron_right.svg", 36.dp, tint = Beff.TextSecondary)
    }
}

/** 알약 분할 선택: 바탕 #EEF3F3 높이 78(안 여백 6), 고른 칸 = 흰 66 + 그림자, 글자 28 Bold 짙은 틸 / 28 SemiBold 회색. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val pill = RoundedCornerShape(100.dp)
    Row(Modifier.height(78.dp).clip(pill).background(SetSegBg).padding(6.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.fillMaxHeight()
                    .then(if (on) Modifier.shadow(6.dp, pill, ambientColor = Color(0x1F004D4D), spotColor = Color(0x1F004D4D)) else Modifier)
                    .clip(pill)
                    .background(if (on) Beff.Card else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = TextStyle(
                        fontSize = 28.sp, lineHeight = 38.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (on) SetSegOn else Beff.TextSecondary,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

/** 소리 크기 막대 300: 트랙 10(#DCE5E5) · 채움 틸 · 손잡이 32(흰 + 틸 테두리 4). 누르거나 끌어서 바꾼다. 누르기 쉽게 높이 64. */
@Composable
private fun VolumeSlider(value: Float, onChange: (Float) -> Unit) {
    BoxWithConstraints(
        Modifier.width(300.dp).height(64.dp)
            .pointerInput(Unit) { detectTapGestures { onChange(it.x / size.width) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> onChange(change.position.x / size.width) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        val v = value.coerceIn(0f, 1f)
        val track = RoundedCornerShape(5.dp)
        Box(Modifier.fillMaxWidth().height(10.dp).clip(track).background(Beff.SurfaceHigh))
        Box(Modifier.fillMaxWidth(v).height(10.dp).clip(track).background(Beff.Teal))
        val x = (maxWidth * v - 16.dp).coerceIn(0.dp, maxWidth - 32.dp)
        Box(
            Modifier.offset(x = x).size(32.dp).clip(CircleShape).background(Beff.Card).border(4.dp, Beff.Teal, CircleShape),
        )
    }
}

// ── 설정 > 연동 상태 ──

/** 설정 > 연동 상태. */
@Composable
fun DeviceStatusScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().beffBackground().padding(top = 40.dp)) {
        TitleBar("연동 상태", onBack = { vm.back() })
        Column(Modifier.padding(horizontal = 56.dp).padding(top = 24.dp)) { DeviceStatus(vm) }
    }
}

/** 기기 연동 상태. 실제 연결 결과를 그대로 보여 준다(연동 점검용). */
@Composable
private fun DeviceStatus(vm: AppViewModel) {
    val nav = when (vm.nav.link) {
        com.beff.app.nav.NaviLink.Connected -> "연결됨"
        com.beff.app.nav.NaviLink.Connecting -> "연결 중"
        com.beff.app.nav.NaviLink.Failed -> "연결 실패"
        else -> "대기"
    }
    val loc = when (vm.location.link) {
        com.beff.app.device.LocationLink.Connected -> vm.location.location?.let { "연결됨 · %.4f, %.4f".format(it.latitude, it.longitude) } ?: "연결됨 · 위치 대기"
        com.beff.app.device.LocationLink.Waiting -> "권한 대기"
        com.beff.app.device.LocationLink.Unsupported -> "이 환경은 지원하지 않아요"
        com.beff.app.device.LocationLink.Failed -> "연결 실패"
        else -> "대기"
    }
    val gear = when (vm.vehicle.gear) {
        com.beff.app.device.GearState.Park -> "P 주차"
        com.beff.app.device.GearState.Reverse -> "R 후진"
        com.beff.app.device.GearState.Neutral -> "N 중립"
        com.beff.app.device.GearState.Drive -> "D 주행"
        else -> if (vm.vehicle.gearAvailable == false) "읽을 수 없어요" else "확인 중"
    }
    val voice = when (vm.voice.link) {
        com.beff.app.device.VoiceLink.Ready -> "준비됨"
        com.beff.app.device.VoiceLink.Connecting -> "준비 중"
        com.beff.app.device.VoiceLink.Failed -> "사용할 수 없어요"
        else -> "대기"
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("내비" to nav, "차량 위치" to loc, "차량 기어" to gear, "음성 안내" to voice).forEach { (k, v) ->
            Text("$k · $v", style = BeffType.Body32.copy(color = Beff.TextSecondary))
        }
    }
}
