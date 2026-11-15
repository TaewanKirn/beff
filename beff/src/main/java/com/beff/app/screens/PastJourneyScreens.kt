package com.beff.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beff.app.AppViewModel
import com.beff.app.Tab
import com.beff.app.data.PastJourney
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffAsset
import com.beff.app.ui.BeffBox
import com.beff.app.ui.BeffButton
import com.beff.app.ui.BeffGnb
import com.beff.app.ui.BeffType
import com.beff.app.ui.JourneyPanelFrame
import com.beff.app.ui.LocalWide
import com.beff.app.ui.withWa
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

// ── F-4 지난 여정 목록 · F-5 지난 여정 상세. 기록은 AppViewModel.history(기기 안 저장)에서 읽는다. ──

/** "11월 8일" (요일을 붙이면 "11월 8일 (토)"). */
private fun dateText(at: Long, weekday: Boolean = false): String {
    val d = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
    val base = "${d.monthValue}월 ${d.dayOfMonth}일"
    return if (weekday) "$base (${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})" else base
}

/** "11월 8일 · 동생과 함께" — 프로필 없이 다녀왔으면 날짜만. */
private fun subLine(p: PastJourney, weekday: Boolean = false): String =
    listOfNotNull(dateText(p.at, weekday), p.profileLabel?.let { "${withWa(it)} 함께" }).joinToString(" · ")

/**
 * F-4 오른쪽(3분할은 아래) 「지난 여정」: 제목 40 Bold · 목록 r28(줄 125: 핀 원 72 #EEF4F4 · 이름 36 SemiBold · 날짜 · 프로필 28 #66727B).
 * 기록이 없으면 언제 남는지 알린다.
 */
@Composable
fun ColumnScope.PastJourneyList(vm: AppViewModel, onOpen: (PastJourney) -> Unit) {
    Text("지난 여정", style = BeffType.Box40.copy(color = Beff.TextPrimary, letterSpacing = 0.sp))
    Spacer(Modifier.height(24.dp))
    val items = vm.history.items
    if (items.isEmpty()) {
        Text(
            "아직 다녀온 여정이 없어요\n목적지에 도착하면 여기에 남겨 둘게요",
            style = BeffType.Body32.copy(color = Beff.TextSecondary, lineHeight = 44.sp),
        )
        return
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Beff.Card)) {
        items.forEachIndexed { i, p ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(2.dp).background(Beff.Stroke))
            Row(
                Modifier.fillMaxWidth().height(125.dp).clickable { onOpen(p) }.padding(start = 32.dp, end = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(Beff.Surface), contentAlignment = Alignment.Center) {
                    BeffAsset("pict/pin.svg", 48.dp)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = BeffType.Item36Semi.copy(color = Beff.TextPrimary, lineHeight = 48.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subLine(p), style = BeffType.Caption28.copy(color = Beff.TextMuted), maxLines = 1)
                }
            }
        }
    }
}

/**
 * F-5 지난 여정 상세. 왼쪽: 「< 지난 여정」 · 카드(이름 48 ExtraBold · 날짜(요일) · 프로필 · 기록 줄 · [다시 가기]) + GNB.
 * 오른쪽: 그 장소의 주차한 곳 → 필요한 시설 지도(FacilityMapCard). 3분할은 한 열.
 * [다시 가기]는 그 장소의 출발 브리핑을 연다(경로는 안내 시작을 눌러야 내비로 넘어간다).
 */
@Composable
fun PastJourneyDetail(vm: AppViewModel, p: PastJourney, onBack: () -> Unit) {
    val place = vm.places.placeOrNull(p.placeId) ?: p.toPlace()
    val again = {
        if (vm.places.placeOrNull(place.id) == null) vm.places.register(place)
        vm.openBriefing(place.id)
    }
    if (LocalWide.current) {
        JourneyPanelFrame(
            gnb = { Box(Modifier.padding(end = 32.dp)) { BeffGnb(Tab.Journey, vm::selectTab, sidePadding = 0.dp) } },
            left = {
                PastBackRow(onBack, Modifier.offset(x = (-16).dp, y = 4.dp))
                Spacer(Modifier.height(36.dp))
                PastCard(p, again)
            },
            right = {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 40.dp, end = 40.dp, top = 56.dp, bottom = 40.dp)) {
                    FacilityMapCard(vm, place)
                }
            },
        )
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 56.dp).padding(top = 4.dp, bottom = 72.dp)) {
            PastBackRow(onBack, Modifier.offset(x = (-16).dp))
            Spacer(Modifier.height(36.dp))
            PastCard(p, again)
            Spacer(Modifier.height(40.dp))
            FacilityMapCard(vm, place)
        }
    }
}

/** 제목 줄: 뒤로 꺾쇠 56 + 「지난 여정」 40 Bold. */
@Composable
private fun PastBackRow(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(76.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            BeffAsset("pict/chevron_right.svg", 56.dp, Modifier.graphicsLayer(scaleX = -1f), tint = Beff.TextStrong)
        }
        Spacer(Modifier.width(12.dp))
        Text("지난 여정", style = BeffType.Box40.copy(color = Beff.TextStrong, letterSpacing = 0.sp))
    }
}

/** 지난 여정 카드: 흰 r36 · 안쪽 42 · 기록 줄 81(라벨 30 #4A545C / 값 30 SemiBold 오른쪽). */
@Composable
private fun PastCard(p: PastJourney, onAgain: () -> Unit) {
    BeffBox(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Column(Modifier.padding(42.dp)) {
            Text(p.name, style = BeffType.Title48.copy(color = Beff.TextStrong), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(subLine(p, weekday = true), style = BeffType.Body32.copy(fontSize = 30.sp, color = Beff.TextSecondary))
            Spacer(Modifier.height(28.dp))
            PastRow("가는 데 걸린 시간", p.minutes?.let { fmtMinutes(it) } ?: "기록이 없어요")
            PastRow("쉬어 간 곳", p.stopNames.joinToString(" · ").ifBlank { "없어요" })
            PastRow("쓸 수 있던 시설", p.facilities.joinToString(" · ").ifBlank { "정보 없음" })
            Spacer(Modifier.height(28.dp))
            BeffButton("다시 가기", onAgain)
        }
    }
}

@Composable
private fun PastRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().height(81.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = BeffType.Body32.copy(fontSize = 30.sp, color = Beff.TextSecondary), maxLines = 1)
        Spacer(Modifier.width(24.dp))
        Text(
            value,
            style = BeffType.Body32.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Beff.TextStrong, textAlign = TextAlign.End),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
