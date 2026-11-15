package com.beff.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.beff.app.ui.BeffAsset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beff.app.AppViewModel
import com.beff.app.Tab
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffButton
import com.beff.app.ui.BeffGnb
import com.beff.app.ui.BeffType
import com.beff.app.ui.ButtonKind
import com.beff.app.ui.LocalWide
import com.beff.app.ui.ScrollFade
import androidx.compose.foundation.layout.offset
import com.beff.app.ui.beffBackground

/**
 * 탭 첫 화면 공통 틀. GNB는 탭 첫 화면에만 있다(정책 53).
 * 3분할(840)은 위 48 + 본문 + 아래 GNB + 토스트. 2/3 전체 화면(LocalWide)은 각 탭 화면이 WideTab으로
 * GNB·토스트까지 직접 그린다(홈·검색·여정·설정 — 설정은 SettingsScreen.kt).
 */
@Composable
fun MainScreen(vm: AppViewModel, tab: Tab) {
    if (LocalWide.current) {
        // 4-18: 여정 탭(F)은 바탕 #F1F6F6.
        Box(Modifier.fillMaxSize().beffBackground(base = if (tab == Tab.Journey) Beff.BgCool else Beff.Bg)) {
            when (tab) {
                Tab.Home -> HomeScreen(vm)
                Tab.Search -> SearchScreen(vm)
                Tab.Journey -> JourneyScreen(vm)
                Tab.Settings -> SettingsTab(vm)
            }
        }
        vm.pendingStop?.let { StopConfirmPopup(it, vm) }
        if (vm.askEndJourney) EndJourneyPopup(vm)
        return
    }
    Column(Modifier.fillMaxSize().beffBackground(base = if (tab == Tab.Journey) Beff.BgCool else Beff.Bg).padding(top = 48.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.Home -> HomeScreen(vm)
                Tab.Search -> SearchScreen(vm)
                Tab.Journey -> JourneyScreen(vm)
                Tab.Settings -> SettingsTab(vm)
            }
            ScrollFade(Modifier.align(Alignment.BottomCenter))
            ToastLayer(vm, tab, Modifier.align(Alignment.BottomCenter), wide = false)
        }
        Box(Modifier.padding(bottom = 40.dp)) { BeffGnb(tab, vm::selectTab) }
    }
    vm.pendingStop?.let { StopConfirmPopup(it, vm) }
    if (vm.needDestination) NeedDestinationPopup(vm)
    if (vm.askEndJourney) EndJourneyPopup(vm)
}

/** 여정 취소 확인 창. 확인하면 여정을 끝내고 내비 안내도 함께 끈다. */
@Composable
private fun EndJourneyPopup(vm: AppViewModel) {
    val name = vm.journey?.dest?.name ?: return
    SystemPopup {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "${com.beff.app.ui.withEul(name)}\n가는 여정을 취소할까요?",
                style = BeffType.Box40.copy(color = Beff.TextStrong, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 52.sp),
            )
            Text(
                "내비 안내도 함께 끝나요. 경유지도 지워져요.",
                style = BeffType.Caption28.copy(color = Beff.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BeffButton("여정 취소", { vm.askEndJourney = false; vm.endJourney() })
            BeffButton("계속 가기", { vm.askEndJourney = false }, kind = ButtonKind.Secondary, height = 80.dp)
        }
    }
}

/**
 * 03-3 체크인 토스트(답하거나 X를 누를 때까지 유지, 배경 블러·딤 없음, 정책 58)와
 * 경유지 추가·삭제 확인. 3분할은 GNB 위 24, 2/3는 왼쪽 열 GNB 위 24(홈·검색이 열 안에서 직접 호출).
 */
@Composable
fun ToastLayer(vm: AppViewModel, tab: Tab, modifier: Modifier, wide: Boolean) {
    if (tab == Tab.Home) vm.checkIn?.let { CheckInToast(it, vm, modifier, wide) }
    vm.notice?.let { WaypointToast(it, vm, modifier, wide) }
}

/** 2/3 전체 화면: 왼쪽 열(GNB 위 32 간격 안) 맨 아래에 붙여 GNB 위 24가 되게 8만큼 내린다. */
@Composable
fun WideToasts(vm: AppViewModel, tab: Tab, modifier: Modifier) {
    ToastLayer(vm, tab, modifier.offset(y = 8.dp), wide = true)
}

@Composable
private fun CheckInToast(c: com.beff.app.CheckIn, vm: AppViewModel, modifier: Modifier, wide: Boolean) {
    androidx.compose.runtime.LaunchedEffect(c) { com.beff.app.device.Sfx.toast() } // 1113 효과음
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(36.dp)
    Box(
        modifier.padding(horizontal = if (wide) 0.dp else 56.dp).padding(bottom = if (wide) 0.dp else 24.dp).fillMaxWidth()
            // B-5(4-10b): 토스트 공통 — 짙은 카드(다크 모드는 흰 카드) + 틸 글로우, 얼굴 원 + 36 Bold, [달라요](회색) [맞아요](틸).
            .shadow(28.dp, shape, ambientColor = Color(0x8000B3AE), spotColor = Color(0x8000B3AE))
            .clip(shape)
            .background(Beff.Toast),
    ) {
        Column(Modifier.padding(36.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Row(Modifier.padding(end = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(Modifier.size(96.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Beff.ToastFaceBg), contentAlignment = Alignment.Center) {
                    BeffAsset("face/face_smile.svg", 68.dp, tint = Beff.ToastFaceInk)
                }
                Text(
                    "${c.dateText} ${c.placeName},\n정보가 맞았나요?",
                    style = BeffType.Item36.copy(color = Beff.ToastText, fontWeight = FontWeight.Bold, lineHeight = 48.sp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.weight(1f)) {
                    BeffButton("달라요", { vm.answerCheckIn(false) }, kind = ButtonKind.Secondary, height = 80.dp, containerColor = Beff.ToastSecondaryBtn, contentColor = Beff.ToastSecondaryText)
                }
                Box(Modifier.weight(1f)) { BeffButton("맞아요", { vm.answerCheckIn(true) }, height = 80.dp) }
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp).size(76.dp)
                .clickable { vm.answerCheckIn(true) },
            contentAlignment = Alignment.Center,
        ) { BeffAsset("pict/close.svg", 48.dp, tint = Beff.ToastText) }
    }
}

private fun Modifier.androidShadow(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.shadow(16.dp, shape, ambientColor = Color(0x47004040), spotColor = Color(0x47004040))

/** 경유지 추가·삭제 확인 표시. 제목은 앱 안 결과, 아래 줄은 내비에 실제로 반영됐는지(또는 왜 못 보냈는지). */
@Composable
private fun WaypointToast(n: AppViewModel.Notice, vm: AppViewModel, modifier: Modifier, wide: Boolean) {
    val shape = RoundedCornerShape(36.dp)
    val sync = vm.nav.sync
    val ok = sync?.state == com.beff.app.nav.SyncState.Confirmed || sync?.state == com.beff.app.nav.SyncState.Sent
    val waiting = sync?.state == com.beff.app.nav.SyncState.Pending || sync?.state == com.beff.app.nav.SyncState.Late
    // 1113 효과음: 뜰 때 토스트음, 내비 반영에 실패하면 경고음.
    androidx.compose.runtime.LaunchedEffect(n) { com.beff.app.device.Sfx.toast() }
    androidx.compose.runtime.LaunchedEffect(n, sync?.state) {
        if (sync?.state == com.beff.app.nav.SyncState.NotSent || sync?.state == com.beff.app.nav.SyncState.Failed) {
            kotlinx.coroutines.delay(350)
            com.beff.app.device.Sfx.warning()
        }
    }
    Box(
        modifier.padding(horizontal = if (wide) 0.dp else 56.dp).padding(bottom = if (wide) 0.dp else 24.dp).fillMaxWidth()
            .androidShadow(shape).clip(shape)
            .background(if (ok || waiting) Beff.TealTint else Beff.WarningTint)
            .border(2.dp, if (ok || waiting) Color(0x59008E8C) else Color(0x59C62828), shape)
            .clickable { vm.dismissNotice() },
    ) {
        Column(Modifier.padding(horizontal = 36.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(n.title, style = BeffType.Item36.copy(color = Beff.TextStrong), modifier = Modifier.padding(end = 56.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BeffAsset(
                    if (ok) "pict/verified.svg" else if (waiting) "pict/route.svg" else "pict/warning.svg",
                    40.dp, tint = if (ok) Beff.Teal else if (waiting) Beff.TextSecondary else Beff.Warning,
                )
                Text(
                    sync?.message ?: "내비에 전달하지 않았어요",
                    style = BeffType.Body32.copy(fontSize = 30.sp, color = Beff.TextSecondary),
                )
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp).size(76.dp).clickable { vm.dismissNotice() },
            contentAlignment = Alignment.Center,
        ) { BeffAsset("pict/close.svg", 48.dp) }
    }
}

/** 목적지가 없을 때 경유지를 바꾸려 하면: 목적지부터 정하라고 알린다. */
@Composable
private fun NeedDestinationPopup(vm: AppViewModel) {
    androidx.compose.runtime.LaunchedEffect(Unit) { com.beff.app.device.Sfx.warning() } // 1113 효과음: 할 수 없는 동작을 막을 때
    SystemPopup {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "목적지를 먼저 정해 주세요",
                style = BeffType.Box40.copy(color = Beff.TextStrong, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 52.sp),
            )
            Text(
                "목적지가 있어야 가는 길에 경유지를 넣을 수 있어요.",
                style = BeffType.Caption28.copy(color = Beff.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BeffButton("목적지 검색", vm::goPickDestination)
            BeffButton("닫기", { vm.needDestination = false }, kind = ButtonKind.Secondary, height = 80.dp)
        }
    }
}

/** 경유지 추가·삭제 확인 창. 내비가 앞으로 나온다는 것과 돌아오는 방법을 미리 알린다. */
@Composable
private fun StopConfirmPopup(action: AppViewModel.StopAction, vm: AppViewModel) {
    val add = action is AppViewModel.StopAction.Add
    SystemPopup {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                if (add) "${com.beff.app.ui.withEul(action.place.name)}\n경유지로 추가할까요?" else "${com.beff.app.ui.withEul(action.place.name)}\n경유지에서 뺄까요?",
                style = BeffType.Box40.copy(color = Beff.TextStrong, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 52.sp),
            )
            Text(
                if (vm.nav.routeActive) "내비가 경로를 다시 계산해요.\n베프로 돌아오려면 하단 앱 목록에서 열어 주세요."
                else "경유지를 정한 뒤 출발 브리핑을 보여 드려요.\n안내 시작을 누르면 내비로 넘어가요.",
                style = BeffType.Caption28.copy(color = Beff.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BeffButton("확인", vm::confirmPendingStop)
            BeffButton("취소", vm::cancelPendingStop, kind = ButtonKind.Secondary, height = 80.dp)
        }
    }
}
