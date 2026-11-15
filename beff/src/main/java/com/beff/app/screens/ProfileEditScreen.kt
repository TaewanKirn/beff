package com.beff.app.screens

import com.beff.app.ui.imeField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beff.app.AppViewModel
import com.beff.app.data.FilterCatalog
import com.beff.app.data.Profile
import com.beff.app.data.SeatKind
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffButton
import com.beff.app.ui.LocalWide
import com.beff.app.ui.TitleBar
import com.beff.app.ui.WIDE_BOTTOM
import com.beff.app.ui.beffBackground

/**
 * 1110 수정 H-2 프로필 편집. 설정 > 프로필 행에서 연다.
 * 이름은 이 화면에서 고치고, 「이 프로필로 갈 때」 항목은 프로필 만들기 단계 화면(A-1 수단 · A-2 앉은 자리 · A-3 필터)을
 * 그대로 다시 써서 고친다(단계 화면의 「다음」을 누르면 값만 받아 이 화면으로 돌아온다). [저장]을 눌러야 저장된다.
 * 와이드(1700): 왼쪽 = 제목 바 + 이름 카드 + [저장], 오른쪽 흰 패널 = 항목. 3분할(840): 두 카드 스크롤 + 하단 [저장].
 */
@Composable
fun ProfileEditScreen(vm: AppViewModel) {
    val p = vm.editProfile
    // 지우기 확인 창(02-7)에서 지웠으면 편집을 끝낸다.
    val exists = p != null && vm.profiles.any { it.id == p.id }
    LaunchedEffect(exists) { if (!exists) vm.onEditedProfileDeleted() }
    if (p == null) return
    val canSave = p.name.isNotBlank()

    Box(Modifier.fillMaxSize().beffBackground()) {
        if (LocalWide.current) {
            SettingsPanelLayout(
                left = {
                    // 제목 바의 뒤로 버튼은 화면 왼쪽 40(열 시작 56보다 16 앞).
                    TitleBar("프로필 편집", onBack = { vm.back() }, modifier = Modifier.padding(top = 40.dp).offset(x = (-16).dp))
                    Spacer(Modifier.height(24.dp))
                    NameCard(vm, p)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.padding(top = 24.dp, bottom = WIDE_BOTTOM + 1.dp)) {
                        BeffButton("저장", vm::saveProfileEdit, enabled = canSave)
                    }
                },
                right = {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(start = 58.dp, end = 58.dp, top = 74.dp, bottom = 40.dp),
                    ) { EditOptions(vm, p) }
                },
            )
            return@Box
        }
        Column(Modifier.fillMaxSize().padding(top = 40.dp)) {
            TitleBar("프로필 편집", onBack = { vm.back() })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = 56.dp).padding(top = 32.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    NameCard(vm, p)
                    Column(Modifier.fillMaxWidth().settingsCard().padding(start = 34.dp, end = 34.dp, top = 34.dp, bottom = 22.dp)) {
                        EditOptions(vm, p, gap = 16)
                    }
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(48.dp)
                        .background(Brush.verticalGradient(listOf(Color(0x00E5F4F4), Beff.TealTint))),
                )
            }
            Box(Modifier.padding(start = 56.dp, end = 56.dp, top = 24.dp, bottom = WIDE_BOTTOM + 1.dp)) {
                BeffButton("저장", vm::saveProfileEdit, enabled = canSave)
            }
        }
    }
}

/** 이름 카드: 프로필 이미지 160 · 「이름」 28 + 입력칸 101(r24, 테두리 #CFDADA) · 「프로필 지우기」 30 빨강. */
@Composable
private fun NameCard(vm: AppViewModel, p: Profile) {
    Column(
        Modifier.fillMaxWidth().settingsCard().padding(start = 42.dp, end = 42.dp, top = 42.dp, bottom = 42.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(160.dp).clip(CircleShape).background(Beff.TealTint), contentAlignment = Alignment.Center) {
            ProfileAvatar(p, 160.dp)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "이름",
            style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Beff.TextSecondary, lineHeight = 38.sp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        val shape = RoundedCornerShape(24.dp)
        Box(
            Modifier.fillMaxWidth().height(101.dp).clip(shape).background(Beff.Card)
                .border(2.dp, Color(0xFFCFDADA), shape).padding(horizontal = 30.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = p.name,
                onValueChange = vm::renameEditProfile,
                singleLine = true,
                textStyle = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Normal, color = Beff.TextStrong, lineHeight = 49.sp),
                cursorBrush = SolidColor(Beff.TealMain),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().imeField(),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "프로필 지우기",
            style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Beff.Warning, lineHeight = 41.sp),
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = vm::askDeleteEditProfile).padding(horizontal = 12.dp),
        )
    }
}

/** 「이 프로필로 갈 때」: 이동 보조수단 · 먼저 찾을 시설(행 94, 제목 34 · 값 30 · >). 앉은 자리는 1115에 뺐다(좌석 SDK 없음). */
@Composable
private fun EditOptions(vm: AppViewModel, p: Profile, gap: Int = 48) {
    Text("이 프로필로 갈 때", style = SetType.Section)
    Spacer(Modifier.height(gap.dp))
    EditRow("이동 보조수단", p.summary) { vm.openEditStep(AppViewModel.EditStep.Mobility) }
    Box(Modifier.fillMaxWidth().height(2.dp).background(SetDivider))
    EditRow("먼저 찾을 시설", filterText(p)) { vm.openEditStep(AppViewModel.EditStep.Filter) }
}

@Composable
private fun EditRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(94.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = SetType.Item, maxLines = 1)
        Spacer(Modifier.width(24.dp))
        Text(
            value, style = SetType.Value.copy(textAlign = androidx.compose.ui.text.style.TextAlign.End),
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(24.dp))
        BeffAsset("pict/chevron_right.svg", 36.dp)
    }
}

/** 「화장실 · 경사로 외 2」: 필터 칩 순서대로 앞 두 개 + 나머지 개수. */
private fun filterText(p: Profile): String {
    val order = (FilterCatalog.stroller.chips + FilterCatalog.wheelchair.chips + FilterCatalog.walker.chips + FilterCatalog.route.chips)
    val sorted = p.filters.sortedBy { order.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    if (sorted.isEmpty()) return "고르지 않음"
    val head = sorted.take(2).joinToString(" · ")
    return if (sorted.size > 2) "$head 외 ${sorted.size - 2}" else head
}
