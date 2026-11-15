package com.beff.app.screens

import com.beff.app.ui.fadeInOnce
import com.beff.app.ui.popIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beff.app.AppViewModel
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffType
import com.beff.app.ui.LocalWide
import com.beff.app.ui.TtsToast
import com.beff.app.ui.beffBackground

/**
 * E-1 주행 중 화면(1113, 4-8 · 4-13 · 정책 #47 변경). 가림 화면 대신 간결 정보: 제목 「○○과 함께 가는 중이에요」 + 세로 타임라인
 * (지금 가는 곳만 강조) + 큰 버튼 [경유지 변경하기] 하나. GNB · 프로필 · 스크롤 목록 · 애니메이션 없음.
 * 근거: Pleos 기획 가이드 §3 · §4 — DO 앱 · Mobility Services는 주행 중 현재 위치 기반 정보 · 경유지 설정 허용.
 * [경유지 변경하기] → E-V 음성 전용(터치 버튼 없음). 음성 토스트(E-2~E-5)는 화면 위쪽 가운데.
 * 여정이 없으면(내비만 안내 중 등) 예전 안내 문구를 둔다.
 */
@Composable
fun DrivingLockScreen(guiding: Boolean, caption: String? = null, vm: AppViewModel? = null) {
    val wide = LocalWide.current
    val j = vm?.journey
    Box(Modifier.fillMaxSize().beffBackground(base = Beff.BgCool)) {
        if (vm != null && j != null) {
            val left: @Composable () -> Unit = {
                // 4-13: 위 · 아래 40, 제목 ↔ 카드 ↔ 버튼 48.
                Column(Modifier.fillMaxHeight().padding(top = 40.dp, bottom = 40.dp)) {
                    Text(goingTitle(vm), style = BeffType.Title48.copy(color = Beff.TextStrong), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // 1113: 주행 중 조작 제한 안내(Pleos 주의 분산 가이드). 경유지는 음성으로만 바꾼다.
                    Spacer(Modifier.height(12.dp))
                    Text(DRIVING_NOTICE, style = BeffType.Body32.copy(fontSize = 30.sp, lineHeight = 41.sp, color = Beff.TextSecondary), maxLines = 1)
                    Spacer(Modifier.height(36.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) { JourneyTimeline(vm, j) }
                    Spacer(Modifier.height(48.dp))
                    Box(
                        Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(32.dp)).background(TealButton)
                            .clickable { vm.startVoiceChange() },
                        contentAlignment = Alignment.Center,
                    ) { Text("경유지 변경하기", style = BeffType.Button36.copy(color = Color.White)) }
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(866f).fillMaxHeight().padding(start = 56.dp, end = 32.dp)) { left() }
                    val shape = RoundedCornerShape(topStart = 36.dp, bottomStart = 36.dp)
                    Column(
                        Modifier.weight(834f).fillMaxHeight()
                            .shadow(20.dp, shape, ambientColor = Beff.BoxShadow, spotColor = Beff.BoxShadow)
                            .clip(shape).background(Beff.Card).padding(start = 58.dp, end = 58.dp, top = 74.dp),
                    ) { DestSummary(vm, j.dest) }
                }
            } else {
                Box(Modifier.fillMaxSize().padding(horizontal = 56.dp)) { left() }
            }
        } else {
            Column(Modifier.align(Alignment.Center).padding(horizontal = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BeffAsset("illust/car.svg", 200.dp)
                Spacer(Modifier.height(32.dp))
                Text(
                    if (guiding) "길 안내 중이에요" else "주행 중에는 화면을 쓸 수 없어요",
                    style = BeffType.Title48.copy(color = Beff.TextStrong, textAlign = TextAlign.Center),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (guiding) "안내가 끝나면 다시 보여 드려요\n안내는 음성으로 계속돼요" else "차를 세우면 다시 보여 드려요\n안내는 음성으로 계속돼요",
                    style = BeffType.Body32.copy(color = Beff.TextSecondary, textAlign = TextAlign.Center, lineHeight = 43.sp),
                )
            }
        }
        // 4-10 · 6-15: 음성 토스트는 화면 위쪽(위 40) 가운데. 와이드는 1700 가운데.
        if (caption != null) TtsToast(caption, Modifier.align(Alignment.TopCenter).padding(top = 40.dp, start = 56.dp, end = 56.dp))
        if (vm?.voiceChange == true) VoiceChangePopup(vm)
    }
}

/** E 와이드 오른쪽 목적지 카드(리뷰 3): 사진 없이 이름 · 시간 · 거리 · 시설만. */
@Composable
private fun DestSummary(vm: AppViewModel, dest: com.beff.app.data.Place) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("목적지", style = BeffType.Caption28.copy(fontWeight = FontWeight.SemiBold, color = Beff.TealText))
        Text(dest.name, style = BeffType.Box40.copy(color = Beff.TextStrong), maxLines = 1, overflow = TextOverflow.Ellipsis)
        remainText(vm, dest, dest = true)?.let { Text(it, style = BeffType.Item36Semi.copy(color = Beff.TealText)) }
        FacilityGrid(vm.places.facilitiesFor(dest, vm.active).ifEmpty { dest.facilities }.take(4), tile = Beff.Surface)
    }
}

/**
 * E-V 경유지 변경 · 음성 전용(4-9 · 6-14). 터치 버튼 없음: 듣는 중 표정 120(틸 원) · 「어떻게 바꿀까요?」 40 Bold ·
 * 「이렇게 말해 보세요」 30 회색 · 말 예시 2개(회색 알약 32 SemiBold, “ ”) · 「취소하려면 ‘취소’라고 말해 주세요」 28 회색.
 */
@Composable
private fun VoiceChangePopup(vm: AppViewModel) {
    Box(
        Modifier.fillMaxSize().fadeInOnce().background(Color(0x8C000000)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(40.dp)
        Box(Modifier.popIn().width(720.dp).clip(shape).background(Beff.Popup).border(1.5.dp, Beff.PopupBorder, shape)) {
        // 1113: 닫기 ✕(76dp). 말이 없으면 저절로 닫힌다(AppViewModel.startVoiceChange).
        Box(
            Modifier.align(Alignment.TopEnd).padding(12.dp).size(76.dp).clip(CircleShape).clickable { vm.cancelVoiceChange() },
            contentAlignment = Alignment.Center,
        ) { BeffAsset("pict/close.svg", 40.dp, tint = Beff.TextSecondary) }
        Column(
            Modifier.fillMaxWidth().padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Box(Modifier.size(120.dp).clip(CircleShape).background(Beff.Teal), contentAlignment = Alignment.Center) {
                BeffAsset("face/face_tilt.svg", 80.dp, tint = Color.White)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("어떻게 바꿀까요?", style = BeffType.Box40.copy(color = Beff.TextStrong))
                Text("이렇게 말해 보세요", style = BeffType.Body32.copy(fontSize = 30.sp, color = Beff.TextSecondary))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("“다른 쉼터로 바꿔 줘”", "“쉬지 않고 갈래”").forEach {
                    Box(Modifier.clip(RoundedCornerShape(24.dp)).background(Beff.Surface).padding(horizontal = 32.dp, vertical = 16.dp)) {
                        Text(it, style = BeffType.Body32.copy(fontWeight = FontWeight.SemiBold, color = Beff.TextStrong))
                    }
                }
            }
            Text("취소하려면 ‘취소’라고 말해 주세요", style = BeffType.Caption28.copy(color = Beff.TextSecondary))
        }
        }
    }
}

/** 주행 중 화면 제목 아래 안내. */
private const val DRIVING_NOTICE = "주행 중에는 이 화면 말고 다른 조작은 할 수 없어요"
