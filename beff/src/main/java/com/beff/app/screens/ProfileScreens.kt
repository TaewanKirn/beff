@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.beff.app.screens

import com.beff.app.ui.fadeInOnce
import com.beff.app.ui.popIn
import com.beff.app.ui.imeField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beff.app.AppViewModel
import com.beff.app.Flow
import com.beff.app.data.FilterCatalog
import com.beff.app.data.FilterGroup
import com.beff.app.data.MAX_NAME
import com.beff.app.data.MAX_PROFILES
import com.beff.app.data.Mobility
import com.beff.app.data.Profile
import com.beff.app.ui.AddProfileCell
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffType
import com.beff.app.ui.DeleteBadge
import com.beff.app.ui.FACE_AVATARS
import com.beff.app.ui.FaceAvatarTile
import com.beff.app.ui.LocalWide
import com.beff.app.ui.PKind
import com.beff.app.ui.ProfileBottom
import com.beff.app.ui.ProfileBrandBar
import com.beff.app.ui.ProfileButton
import com.beff.app.ui.ProfilePill
import com.beff.app.ui.ProfileProgress
import com.beff.app.ui.ProfileTitleBar
import com.beff.app.ui.ScrollFade
import com.beff.app.ui.SummaryPill
import com.beff.app.ui.beffBackground
import kotlinx.coroutines.delay

// 1110 수정 「A. 시작 · 프로필」(A-2 ~ A-7). Figma px = dp 1:1. 와이드 1700 · 3분할 840.

// ───────────────────────── 공통: 프로필 이미지 ─────────────────────────

/** 고른 아바타가 없으면(예전 저장분 · 자동 등록 「이동 N」) id로 정해 늘 같은 얼굴이 나오게 한다. */
fun avatarIndexOf(profile: Profile): Int =
    if (profile.avatar >= 0) profile.avatar else profile.id.hashCode().mod(FACE_AVATARS.size)

/** 프로필 이미지 = 얼굴 아바타(1110 A-6 · A-7). 홈 · 여정 화면도 쓴다. */
@Composable
fun ProfileAvatar(profile: Profile, size: Dp = 124.dp) {
    FaceAvatarTile(avatarIndexOf(profile), size)
}

/** 흰 90% 상자 + 흰 테두리 2 + 그림자(Figma drop shadow 20/6, rgba(0,77,77,.07)). */
private fun Modifier.whiteBox(radius: Dp = 36.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(8.dp, shape, ambientColor = Beff.BoxShadow, spotColor = Beff.BoxShadow)
        .clip(shape).background(Beff.BoxFill).border(2.dp, Beff.BoxBorder, shape)
}

/** 프로필 카드. 와이드 379×561(이미지 200, A-7) / 3분할 352×272(이미지 124). 누르는 동안 옅은 틸. */
@Composable
private fun ProfileCard(profile: Profile, wide: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, deletable: Boolean = false) {
    val shape = RoundedCornerShape(36.dp)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        modifier
            .shadow(8.dp, shape, ambientColor = Beff.BoxShadow, spotColor = Beff.BoxShadow)
            .clip(shape)
            .background(if (pressed) Beff.TealSoft else Beff.BoxFill)
            .border(if (pressed) 4.dp else 2.dp, if (pressed) Beff.Teal else Beff.BoxBorder, shape)
            .clickable(interactionSource = src, indication = null) { com.beff.app.device.Sfx.tap(); onClick() }, // 눌림 표시를 직접 그려 탭 소리도 직접(1113)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (wide) 20.dp else 10.dp, Alignment.CenterVertically),
        ) {
            ProfileAvatar(profile, if (wide) 200.dp else 124.dp)
            Text(profile.name, style = BeffType.Item36.copy(color = Beff.TextStrong, lineHeight = 48.sp), maxLines = 1)
            SummaryPill(profile.summary, if (pressed) Beff.Card else Beff.Surface)
        }
        if (deletable) DeleteBadge(if (wide) 76.dp else 64.dp, Modifier.padding(12.dp))
    }
}

/**
 * 카드 그리드: 와이드 4열 한 줄(카드 561), 3분할 2열(카드 272). 빈 칸은 자리만 비워 폭을 맞춘다.
 * Figma A-7 와이드는 4번째 카드가 다음 줄로 넘어가 하단 버튼과 겹치는 버그가 있어 따르지 않고 4장을 한 줄에 둔다.
 */
@Composable
private fun CardGrid(wide: Boolean, cells: List<@Composable (Modifier) -> Unit>) {
    val perRow = if (wide) 4 else 2
    val cellH = if (wide) 561.dp else 272.dp
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        cells.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { it(Modifier.weight(1f).height(cellH)) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ───────────────────────── A-2 오늘 누구와 함께 가나요? ─────────────────────────

@Composable
fun ProfileSelectScreen(vm: AppViewModel) {
    val wide = LocalWide.current
    val profiles = vm.profiles
    Box(Modifier.fillMaxSize().beffBackground()) {
    Column(Modifier.fillMaxSize()) {
        ProfileBrandBar(Modifier.padding(top = 40.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 56.dp).padding(top = if (wide) 40.dp else 76.dp),
        ) {
            Text(
                "오늘은 누구와 함께 가시나요?",
                style = BeffType.Title48.copy(color = Beff.TextPrimary, fontWeight = FontWeight.Bold, lineHeight = 63.sp),
            )
            if (profiles.isEmpty()) {
                // 프로필이 없을 때: 프로필 영역 전체 크기의 큰 추가 버튼(와이드 1588×640, y355)
                Spacer(Modifier.height(if (wide) 132.dp else 140.dp))
                AddProfileCell(Modifier.fillMaxWidth().height(if (wide) 640.dp else 568.dp), big = true, onClick = vm::startCreate)
            } else {
                // 「자리 맞춤」 68 칸(와이드 y255 · 3분할 y295)에 프로필 편집 알약, 카드는 y355 · y399부터(Figma 빈 상태와 같은 기준선)
                Spacer(Modifier.height(if (wide) 32.dp else 36.dp))
                Row(Modifier.height(68.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 프로필 편집 = 지우기 모드(A-7처럼 카드에 X). 이름 · 구성 수정 화면은 디자인에 없다 [미결].
                    ProfilePill(
                        if (vm.editingProfiles) "완료" else "프로필 편집", onClick = vm::toggleProfileEdit,
                        height = 68.dp, icon = "pict/edit.svg",
                    )
                    if (vm.editingProfiles) {
                        Text("지울 프로필을 눌러 주세요", style = BeffType.Caption28.copy(color = Beff.TextMuted))
                    } else if (profiles.size >= MAX_PROFILES) {
                        Text("4개까지 등록할 수 있어요", style = BeffType.Caption28.copy(color = Beff.TextMuted))
                    }
                }
                Spacer(Modifier.height(if (wide) 32.dp else 36.dp))
                val cells = buildList<@Composable (Modifier) -> Unit> {
                    profiles.forEach { p ->
                        if (vm.editingProfiles) add { m -> ProfileCard(p, wide, { vm.deleteTarget = p }, m, deletable = true) }
                        else add { m -> ProfileCard(p, wide, { vm.selectProfile(p) }, m) }
                    }
                    // [+ 추가]는 항상 오른쪽 끝
                    if (profiles.size < MAX_PROFILES && !vm.editingProfiles) add { m -> AddProfileCell(m, big = false, onClick = vm::startCreate) }
                }
                CardGrid(wide, cells)
            }
        }
        ProfileBottom(wide) { ProfileButton("프로필 설정 없이 출발하기", vm::startOneTime, kind = PKind.Ghost) }
    }
    // 설정 · 홈에서 들어왔을 때(돌아갈 화면이 있을 때)만 뒤로 가기.
    if (vm.active != null) {
        Box(
            Modifier.align(Alignment.TopStart).padding(start = 32.dp, top = 40.dp).size(72.dp)
                .clickable { if (vm.editingProfiles) vm.toggleProfileEdit(); vm.back() },
            contentAlignment = Alignment.Center,
        ) { BeffAsset("pict/chevron_right.svg", 48.dp, Modifier.scale(-1f, 1f)) }
    }
    }
}

// ───────────────────────── 단계 화면 틀 (A-3 ~ A-6) ─────────────────────────

/**
 * 제목 바(y40) + 진행 막대(y136) + 본문 + 하단 버튼(y1043, 전체 폭).
 * [graphic]은 쓰지 않는다(1113에 장식 뺌).
 */
@Composable
internal fun StepFrame(
    title: String,
    progress: Float,
    onBack: () -> Unit,
    button: @Composable () -> Unit,
    graphic: Boolean = false,
    body: @Composable (wide: Boolean) -> Unit,
) {
    val wide = LocalWide.current
    Box(Modifier.fillMaxSize().beffBackground()) {
        // 1113: 와이드 오른쪽 「G05 합류 · 페이드」 장식은 뺌(거슬린다는 피드백). [graphic]은 호환용으로만 남김.
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(40.dp))
            ProfileTitleBar(title, onBack, wide)
            Spacer(Modifier.height(16.dp))
            ProfileProgress(progress)
            Box(Modifier.weight(1f).fillMaxWidth()) { body(wide) }
            ProfileBottom(wide, button)
        }
    }
}

internal fun stepTitle(flow: Flow) = if (flow == Flow.Create) "프로필 만들기" else "오늘의 도움"

/** 단계 화면 본문 열: 왼쪽 여백 56, 폭 728(3분할은 화면 전체, 와이드는 왼쪽 절반). 위 48(진행 막대 아래). */
@Composable
internal fun StepColumn(scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxHeight()
            .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(start = 56.dp, top = 48.dp, bottom = 16.dp).width(728.dp),
        content = content,
    )
}

// ───────────────────────── A-3 이동 보조수단 + 인원 ─────────────────────────

/** Figma A-3 설명 문구. 보행 도움은 데이터(지팡이 · 천천히) 대신 「보행 도움 필요」. */
private fun mobilityDesc(m: Mobility) = if (m == Mobility.Walker) "보행 도움 필요" else m.desc

@Composable
fun MobilityStepScreen(vm: AppViewModel, flow: Flow) {
    val d = vm.draft
    val picked = d.total > 0
    // 진행 막대: 만들기 1/4, 오늘의 도움 1/3
    StepFrame(
        title = stepTitle(flow),
        progress = if (flow == Flow.Create) 1f / 3f else 1f / 2f,
        onBack = { vm.back() },
        // 1115: 다음은 바로 시설(앉은 자리 A-4는 뺐다)
        button = { ProfileButton("다음", { vm.goFilterStep(flow) }, enabled = picked) },
        graphic = true,
    ) {
        StepColumn {
            Text("어떤 이동 보조수단을 쓰시나요?", style = BeffType.Title48.copy(color = Beff.TextPrimary, lineHeight = 64.sp), maxLines = 1)
            Spacer(Modifier.height(36.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Mobility.entries.forEach { m ->
                    MobilityCard(m, (d.counts[m] ?: 0) > 0, { vm.toggleMobility(m) }, Modifier.weight(1f).height(256.dp))
                }
            }
            if (picked) {
                // 구분선(y604) 아래 사용 인원: 수단 이름 왼쪽, − 숫자 + 스테퍼 오른쪽(합계 3명까지, 정책 27)
                Spacer(Modifier.height(52.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).background(Beff.Divider))
                Spacer(Modifier.height(48.dp))
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Mobility.entries.filter { (d.counts[it] ?: 0) > 0 }.forEach { m ->
                        Row(Modifier.fillMaxWidth().height(100.dp), verticalAlignment = Alignment.CenterVertically) {
                            BeffAsset(m.illust, 72.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(m.label, style = BeffType.Item36.copy(color = Beff.TextStrong, lineHeight = 48.sp), maxLines = 1, modifier = Modifier.weight(1f))
                            CountStepper(
                                value = d.counts[m] ?: 0,
                                onMinus = { vm.changeCount(m, -1) },
                                onPlus = { vm.changeCount(m, +1) },
                                plusEnabled = d.total < 3,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 수단 카드 232×256: 그림 96(위 22) · 이름 40 ExtraBold · 설명 28. 고르면 틸 12% + 틸 테두리 2. */
@Composable
private fun MobilityCard(m: Mobility, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(36.dp)
    val base = if (selected) modifier.clip(shape).background(Beff.TealSoft).border(2.dp, Beff.TealMain, shape)
    else modifier.whiteBox()
    Column(base.clickable(onClick = onClick).padding(top = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BeffAsset(m.illust, 96.dp)
        Spacer(Modifier.height(16.dp))
        Text(m.label, style = BeffType.Title48.copy(fontSize = 40.sp, lineHeight = 52.sp, color = Beff.TextStrong, letterSpacing = 0.sp), maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(mobilityDesc(m), style = BeffType.Caption28.copy(color = Beff.TextSecondary, lineHeight = 36.sp), maxLines = 1)
    }
}

/** 인원 스테퍼 420×100(Figma ivi_stepper): 흰 바탕 r24 · 테두리 10% · − | N명 | +. */
@Composable
private fun CountStepper(value: Int, onMinus: () -> Unit, onPlus: () -> Unit, plusEnabled: Boolean) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.width(420.dp).height(100.dp).clip(shape).background(Beff.Card).border(2.dp, Beff.Divider, shape)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        StepperIcon(plus = false, enabled = true, onClick = onMinus)
        Box(Modifier.width(2.dp).height(64.dp).background(Color(0x33131417)))
        Text(
            "${value}명",
            style = BeffType.Item36.copy(color = Beff.TextStrong, lineHeight = 44.sp, textAlign = TextAlign.Center),
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(2.dp).height(64.dp).background(Color(0x33131417)))
        StepperIcon(plus = true, enabled = plusEnabled, onClick = onPlus)
    }
}

@Composable
private fun StepperIcon(plus: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = if (enabled) Beff.TextStrong else Color(0x4D131417)
    Box(
        Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(31.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(c))
        if (plus) Box(Modifier.size(3.dp, 31.dp).clip(RoundedCornerShape(2.dp)).background(c))
    }
}

// ───────────────────────── A-5 추천 장소 필터 ─────────────────────────

@Composable
fun FilterStepScreen(vm: AppViewModel, flow: Flow) {
    val d = vm.draft
    val groups: List<FilterGroup> = d.counts.keys.sortedBy { it.ordinal }.map { FilterCatalog.forMobility(it) } + FilterCatalog.route
    StepFrame(
        title = stepTitle(flow),
        progress = if (flow == Flow.Create) 2f / 3f else 1f,
        onBack = { vm.back() },
        button = {
            ProfileButton(
                if (flow == Flow.Create) "다음" else "선택 완료",
                { if (flow == Flow.Create) vm.goPeopleStep() else vm.finishOneTimeFilters() },
            )
        },
    ) { wide ->
        // 1110 A-5: 그룹 상자 없이 머리(그림 64 + 40 Bold) 아래 칩 80이 바로 놓인다. 「모두 선택 · 해제」 알약은 빠졌다.
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 56.dp).padding(top = 32.dp, bottom = 48.dp),
            ) {
                Text(
                    if (wide) "어떤 곳을 먼저 찾아둘까요?" else "어떤 곳을\n먼저 찾아둘까요?",
                    style = BeffType.Title48.copy(color = Beff.TextStrong, lineHeight = 63.sp),
                )
                Spacer(Modifier.height(48.dp))
                Column(verticalArrangement = Arrangement.spacedBy(48.dp)) {
                    groups.forEach { g -> FilterGroupBlock(vm, g) }
                }
            }
            // 스크롤 화면은 하단 고정 버튼 위 72에서 내용이 흐려짐(정책 47)
            ScrollFade(Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun FilterGroupBlock(vm: AppViewModel, g: FilterGroup) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(
            Modifier.padding(start = 4.dp).height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BeffAsset(g.icon, 64.dp)
            Text(g.title, style = BeffType.Box40.copy(color = Beff.TextPrimary, lineHeight = 53.sp, letterSpacing = 0.sp), maxLines = 1)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            g.chips.forEach { c -> FilterChip(c, c in vm.draft.filters) { vm.toggleFilter(c) } }
        }
    }
}

/** 필터 칩 80(Figma Beff / Chip): 고름 = 틸 12% + 틸 테두리 2 + 32 SemiBold 틸, 안 고름 = 흰 + #F5F7F8 테두리 + 32 Regular. */
@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(100.dp)
    Box(
        Modifier.height(80.dp).clip(shape)
            .background(if (selected) Beff.TealSoft else Beff.Card) // 1113: 다크에서 흰 칩이던 것 → 카드색
            .border(2.dp, if (selected) Beff.TealMain else if (Beff.dark) Beff.Stroke else Beff.Surface, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = BeffType.Body32.copy(
                color = if (selected) Beff.TealText else Beff.TextPrimary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                lineHeight = 42.sp, letterSpacing = 0.sp,
            ),
            maxLines = 1,
        )
    }
}

// ───────────────────────── A-6 프로필 이미지 · 이름 → 저장 ─────────────────────────

@Composable
fun PeopleStepScreen(vm: AppViewModel) {
    val d = vm.draft
    StepFrame(
        title = "프로필 만들기",
        progress = 1f,
        onBack = { vm.back() },
        button = { ProfileButton("프로필 저장", vm::saveCreatedProfile, enabled = d.name.isNotBlank()) },
    ) { wide ->
        val title = @Composable {
            Text("프로필을 저장해 주세요", style = BeffType.Title48.copy(color = Beff.TextStrong, lineHeight = 63.sp), maxLines = 1)
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp)) {
                Column(Modifier.width(778.dp).padding(top = 40.dp)) {
                    title()
                    Spacer(Modifier.height(32.dp))
                    NameBlock(vm)
                }
                Spacer(Modifier.width(26.dp))
                AvatarPanel(vm, perRow = 3, cell = 172.dp, gap = 48.dp, modifier = Modifier.weight(1f).fillMaxHeight().padding(top = 16.dp))
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 56.dp).padding(top = 40.dp, bottom = 48.dp),
                ) {
                    title()
                    Spacer(Modifier.height(32.dp))
                    NameBlock(vm)
                    Spacer(Modifier.height(32.dp))
                    AvatarPanel(vm, perRow = 4, cell = 148.dp, gap = 24.dp, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                }
                ScrollFade(Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** 고른 이미지 170(r51) + 「프로필 이름」 28 + 흰 입력칸 86(r28, 40 Bold, 글자 수 28). */
@Composable
private fun NameBlock(vm: AppViewModel) {
    val d = vm.draft
    Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        FaceAvatarTile(d.avatar, 170.dp)
        Column(Modifier.weight(1f).padding(top = 13.dp)) {
            Text("프로필 이름", style = BeffType.Caption28.copy(color = Beff.TextMuted, lineHeight = 37.sp), modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(28.dp)).background(Beff.Card).padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = d.name,
                    onValueChange = vm::setName,
                    singleLine = true,
                    textStyle = BeffType.Box40.copy(color = Beff.TextPrimary, lineHeight = 53.sp, letterSpacing = 0.sp),
                    cursorBrush = SolidColor(Beff.TealMain),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f).imeField(),
                )
                Text("${d.name.length}/$MAX_NAME", style = BeffType.Caption28.copy(color = Beff.TextMuted, lineHeight = 37.sp))
            }
        }
    }
}

/** 「프로필 이미지를 골라 주세요」 흰 상자 + 아바타 격자. 고른 칸은 틸 테두리 4(바깥 172, r54) 안에 160(r48). */
@Composable
private fun AvatarPanel(vm: AppViewModel, perRow: Int, cell: Dp, gap: Dp, modifier: Modifier) {
    val d = vm.draft
    Column(
        modifier.whiteBox().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(if (perRow == 3) 80.dp else 32.dp))
        Text("프로필 이미지를 골라 주세요", style = BeffType.Body32.copy(fontWeight = FontWeight.SemiBold, color = Beff.TextPrimary, lineHeight = 42.sp, letterSpacing = 0.sp))
        Spacer(Modifier.height(if (perRow == 3) 41.dp else 28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            FACE_AVATARS.indices.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { i ->
                        val sel = d.avatar == i
                        val ring = RoundedCornerShape(cell * (54f / 172f))
                        Box(
                            Modifier.size(cell).clip(ring)
                                .then(if (sel) Modifier.border(4.dp, Beff.Teal, ring) else Modifier)
                                .clickable { vm.pickAvatar(i) },
                            contentAlignment = Alignment.Center,
                        ) { FaceAvatarTile(i, cell * (160f / 172f)) }
                    }
                    repeat(perRow - row.size) { Spacer(Modifier.size(cell)) }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ───────────────────────── A-7 프로필이 가득 찼을 때 ─────────────────────────

@Composable
fun ReplaceProfileScreen(vm: AppViewModel) {
    val wide = LocalWide.current
    Column(Modifier.fillMaxSize().beffBackground()) {
        ProfileBrandBar(Modifier.padding(top = 40.dp))
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 56.dp).padding(top = if (wide) 40.dp else 76.dp),
        ) {
            Text("어떤 프로필을 지울까요?", style = BeffType.Title48.copy(color = Beff.TextPrimary, fontWeight = FontWeight.Bold, lineHeight = 63.sp))
            Spacer(Modifier.height(8.dp))
            Text(
                "4개가 가득 찼어요. 하나를 지우면 새 프로필을 저장해요",
                style = BeffType.Small30.copy(fontWeight = FontWeight.Normal, color = Beff.TextSecondary, lineHeight = 40.sp),
            )
            // 카드 위 y387(제목 묶음 아래 「자리 맞춤」 48 + 간격 32 · 36)
            Spacer(Modifier.height(if (wide) 116.dp else 120.dp))
            val cells = buildList<@Composable (Modifier) -> Unit> {
                vm.profiles.forEach { p -> add { m -> ProfileCard(p, wide, { vm.deleteTarget = p }, m, deletable = true) } }
            }
            CardGrid(wide, cells)
        }
        ProfileBottom(wide) { ProfileButton("등록하지 않고 출발하기", vm::skipFromReplace, kind = PKind.Ghost) }
    }
}

// ───────────────────────── 팝업 (02-4 · 02-5 · 02-7), 720×613 ─────────────────────────

/** 다른 화면(MainScreen)이 쓰는 시스템 팝업 틀: 720×613 흰 카드 + 어두운 막. */
@Composable
internal fun SystemPopup(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x66131417)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(720.dp).height(613.dp).clip(RoundedCornerShape(40.dp)).background(Beff.Popup).border(1.5.dp, Beff.PopupBorder, RoundedCornerShape(40.dp)).padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) { content() }
    }
}

@Composable
private fun PopupHost(
    avatar: @Composable () -> Unit,
    title: String,
    desc: String,
    buttons: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().fadeInOnce().background(Color(0x8C000000)).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(40.dp)
        Column(
            Modifier.popIn().width(720.dp).height(613.dp).clip(shape).background(Beff.Popup).border(1.5.dp, Beff.PopupBorder, shape),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            avatar()
            // 아바타 아래 ~ 버튼 위 사이에서 가운데 정렬(Figma: 2줄 제목 y273, 1줄 제목 y300)
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text(
                        title,
                        style = BeffType.Title48.copy(fontSize = 40.sp, lineHeight = 52.8.sp, color = Beff.TextStrong, textAlign = TextAlign.Center),
                    )
                    Text(
                        desc,
                        style = BeffType.Small30.copy(fontWeight = FontWeight.Normal, lineHeight = 41.sp, color = Beff.TextSecondary, textAlign = TextAlign.Center),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 40.dp)) { buttons() }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PopupButtons(primary: String, onPrimary: () -> Unit, secondary: String? = null, onSecondary: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PopupButton(primary, true, onPrimary, Modifier.weight(1f))
        if (secondary != null) PopupButton(secondary, false, onSecondary, Modifier.weight(1f))
    }
}

@Composable
private fun PopupButton(text: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        // 5-3: 팝업 버튼 통일 — 주 버튼 틸 · 보조 회색, 높이 100 · r32 · SemiBold 30.
        modifier.fillMaxHeight().clip(RoundedCornerShape(32.dp))
            .background(if (primary) Beff.Teal else Beff.PopupSecondary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = BeffType.Small30.copy(color = if (primary) Color.White else Beff.SecondaryBtnText),
            maxLines = 1,
        )
    }
}

@Composable
fun RegisterPopups(vm: AppViewModel) {
    if (vm.askRegister) {
        PopupHost(
            avatar = { ProfileAvatar(Profile("", "", vm.draft.counts, emptySet(), emptyList()), 200.dp) },
            title = "지금 등록한 이동을\n프로필로 등록할까요?",
            desc = "다음에도 같은 설정을 불러올 수 있어요",
            buttons = { PopupButtons("프로필로 등록", vm::registerOneTime, "괜찮아요", vm::skipRegister) },
        )
    }
    vm.deleteTarget?.let { t ->
        PopupHost(
            avatar = { ProfileAvatar(t, 200.dp) },
            title = "「${t.name}」 프로필을 지울까요?",
            desc = "지운 프로필은 되돌릴 수 없어요",
            buttons = { PopupButtons("지우기", vm::confirmDelete, "취소", { vm.deleteTarget = null }) },
        )
    }
    if (vm.registeredDone) {
        var left by remember { mutableIntStateOf(3) }
        LaunchedEffect(Unit) {
            while (left > 0) { delay(1000); left -= 1 }
            vm.finishRegisteredDone()
        }
        PopupHost(
            avatar = { ProfileAvatar(vm.active ?: Profile("", "", vm.draft.counts, emptySet(), emptyList()), 200.dp) },
            title = "프로필이 등록되었습니다",
            desc = "「${vm.active?.name ?: "이동 1"}」로 저장했어요",
            buttons = { PopupButtons("확인 ($left)", vm::finishRegisteredDone) },
        )
    }
}
