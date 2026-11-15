package com.beff.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beff.app.AppViewModel
import com.beff.app.data.Consent
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffButton
import com.beff.app.ui.BeffType
import com.beff.app.ui.ButtonKind
import com.beff.app.ui.LocalWide

/*
 * 위치정보 · 개인정보 동의 (2026-11-15, 페타 C00-P와 같은 구성).
 * 첫 실행 때 스플래시 다음에 연다. 기어가 P가 아니면 앱 전체가 주행 잠금 화면이라 자연히 주차 중에만 보인다.
 * 차 화면엔 세 줄 요약만, 전문은 QR로 폰에서 읽는다. 동의하지 않아도 장소 추천 · 검색 · 하차 브리핑은 쓸 수 있다.
 * 설정 › 「개인정보 · 위치정보」에서 다시 열어 철회할 수 있다([review]).
 */

private val ITEMS = listOf("[필수] 위치정보 이용 동의", "[필수] 개인정보 수집 · 이용 동의")
private val SUMMARY = listOf(
    "모으는 정보" to "차의 현재 위치",
    "보내는 곳" to "카카오 · OpenStreetMap 길찾기\n(장소 검색 · 도착 후 걷는 길)",
    "보관" to "위치는 저장하지 않아요\n프로필은 이 기기 안에만",
)
private const val NOTE = "동의하지 않아도 장소 추천과 검색은 쓸 수 있어요\n설정에서 언제든 다시 보거나 철회할 수 있어요"

@Composable
fun ConsentScreen(vm: AppViewModel, review: Boolean) {
    val ctx = LocalContext.current
    val wide = LocalWide.current
    var sheet by remember { mutableStateOf<String?>(null) }
    val reviewing = review && Consent.state == Consent.State.Agreed
    val agree = { Consent.set(ctx, Consent.State.Agreed); vm.onConsentChanged(review) }
    val decline = { Consent.set(ctx, Consent.State.Declined); vm.onConsentChanged(review) }
    val close = { vm.onConsentChanged(review) }

    Box(Modifier.fillMaxSize().background(Beff.BgCool)) {
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 96.dp), horizontalArrangement = Arrangement.spacedBy(80.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    BeffAsset("face/face_smile.svg", 240.dp, tint = Beff.TealMain)
                    Spacer(Modifier.height(48.dp))
                    Text("시작하기 전에\n확인해 주세요", style = BeffType.Title48.copy(color = Beff.TextStrong, fontSize = 56.sp, lineHeight = 76.sp))
                    Spacer(Modifier.height(28.dp))
                    Text("이동하기 편한 장소와 가는 길에 쉬어갈 곳을 찾을 때\n차의 현재 위치를 써요", style = BeffType.Body32.copy(color = Beff.TextSecondary, fontSize = 30.sp, lineHeight = 46.sp))
                    Spacer(Modifier.weight(1f))
                    Text(NOTE, style = BeffType.Caption28.copy(color = Beff.TextSecondary, fontSize = 24.sp, lineHeight = 36.sp))
                }
                Column(Modifier.width(724.dp).fillMaxHeight()) {
                    SummaryCard()
                    Spacer(Modifier.height(40.dp))
                    ItemRows(withLink = false) {}
                    Spacer(Modifier.height(24.dp))
                    QrCard()
                    Spacer(Modifier.weight(1f))
                    Buttons(reviewing, agree, decline, close)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 48.dp).padding(top = 56.dp, bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BeffAsset("face/face_smile.svg", 150.dp, tint = Beff.TealMain)
                Spacer(Modifier.height(24.dp))
                Text("시작하기 전에 확인해 주세요", style = BeffType.Title48.copy(color = Beff.TextStrong, fontSize = 44.sp, lineHeight = 60.sp), maxLines = 1)
                Spacer(Modifier.height(12.dp))
                Text("이동하기 편한 장소를 찾을 때\n차의 현재 위치를 써요", style = BeffType.Body32.copy(color = Beff.TextSecondary, fontSize = 30.sp, lineHeight = 44.sp, textAlign = TextAlign.Center))
                Spacer(Modifier.height(36.dp))
                SummaryCard()
                Spacer(Modifier.height(28.dp))
                ItemRows(withLink = true) { sheet = it }
                Spacer(Modifier.weight(1f))
                Buttons(reviewing, agree, decline, close)
                Spacer(Modifier.height(20.dp))
                Text(NOTE, style = BeffType.Caption28.copy(color = Beff.TextSecondary, fontSize = 24.sp, lineHeight = 36.sp, textAlign = TextAlign.Center))
            }
            sheet?.let { QrSheet(it) { sheet = null } }
        }
    }
}

@Composable
private fun SummaryCard() {
    val shape = RoundedCornerShape(32.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Beff.Card).border(2.dp, Beff.Stroke, shape).padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        SUMMARY.forEachIndexed { i, (k, v) ->
            Row(Modifier.fillMaxWidth()) {
                Text(k, style = BeffType.Small30.copy(color = Beff.TealText, fontSize = 26.sp), modifier = Modifier.width(188.dp).padding(top = 4.dp))
                Text(v, style = BeffType.Body32.copy(color = Beff.TextStrong, fontSize = 30.sp, lineHeight = 44.sp))
            }
            if (i < SUMMARY.lastIndex) Box(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(2.dp).background(Beff.Divider))
        }
    }
}

@Composable
private fun ItemRows(withLink: Boolean, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ITEMS.forEach { label ->
            Row(
                Modifier.fillMaxWidth().height(72.dp)
                    .then(if (withLink) Modifier.clickable { onOpen(label.removePrefix("[필수] ")) } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Beff.TealSoft).border(3.dp, Beff.TealMain, CircleShape), contentAlignment = Alignment.Center) {
                    Text("✓", style = BeffType.Small30.copy(color = Beff.TealText, fontSize = 24.sp))
                }
                Spacer(Modifier.width(20.dp))
                Text(label, style = BeffType.Item36.copy(color = Beff.TextStrong, fontSize = 30.sp, lineHeight = 42.sp), modifier = Modifier.weight(1f), maxLines = 1)
                if (withLink) Text("전문 보기 ›", style = BeffType.Caption28.copy(color = Beff.TextSecondary, fontSize = 26.sp))
            }
        }
    }
}

@Composable
private fun QrCard() {
    val shape = RoundedCornerShape(32.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Beff.Card).border(2.dp, Beff.Stroke, shape).padding(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QrImage(200)
        Spacer(Modifier.width(32.dp))
        Column {
            Text("약관 전문은 폰으로 확인하세요", style = BeffType.Item36.copy(color = Beff.TextStrong, fontSize = 30.sp, lineHeight = 42.sp))
            Spacer(Modifier.height(10.dp))
            Text("카메라로 QR을 비추면\n위치정보 이용약관과\n개인정보 처리방침이 열려요", style = BeffType.Caption28.copy(color = Beff.TextSecondary, fontSize = 26.sp, lineHeight = 40.sp))
        }
    }
}

/** QR은 다크에서도 흰 바탕이어야 읽힌다 */
@Composable
private fun QrImage(size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)) {
        BeffAsset("graphic/qr_privacy.png", size.dp, Modifier.fillMaxSize())
    }
}

@Composable
private fun ColumnScope.Buttons(reviewing: Boolean, agree: () -> Unit, decline: () -> Unit, close: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (reviewing) {
            BeffButton("동의 철회", decline, Modifier.weight(0.42f), kind = ButtonKind.Secondary)
            BeffButton("닫기", close, Modifier.weight(0.58f))
        } else {
            BeffButton("동의 안 함", decline, Modifier.weight(0.42f), kind = ButtonKind.Secondary)
            BeffButton("동의하고 시작", agree, Modifier.weight(0.58f))
        }
    }
}

/** 1/3 「전문 보기」: 약관 QR 시트 */
@Composable
private fun QrSheet(title: String, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().height(720.dp).clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)).background(Beff.Card)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.width(96.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Beff.Divider))
            Spacer(Modifier.height(36.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = BeffType.Box40.copy(color = Beff.TextStrong), modifier = Modifier.weight(1f))
                Box(Modifier.size(88.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    BeffAsset("pict/close.svg", 44.dp, tint = Beff.TextStrong)
                }
            }
            Text("전문은 폰에서 읽을 수 있어요. 카메라로 QR을 비추세요.", style = BeffType.Caption28.copy(color = Beff.TextSecondary), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(40.dp))
            QrImage(260)
            Spacer(Modifier.height(36.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Beff.Surface).padding(horizontal = 28.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    "· 차의 현재 위치를 장소 검색 · 길 안내에만 써요",
                    "· 카카오 · OSM 길찾기에 좌표만 보내고, 저장하지 않아요",
                    "· 설정에서 언제든 동의를 철회할 수 있어요",
                ).forEach { Text(it, style = BeffType.Caption28.copy(color = Beff.TextStrong, fontSize = 26.sp)) }
            }
        }
    }
}
