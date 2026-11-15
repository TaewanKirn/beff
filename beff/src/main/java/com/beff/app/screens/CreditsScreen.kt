package com.beff.app.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beff.app.AppViewModel
import com.beff.app.ui.Beff
import com.beff.app.ui.BeffType
import com.beff.app.ui.TitleBar
import com.beff.app.ui.beffBackground

/** 설정 > 저작권 · 출처. 화면 곳곳에 두지 않고 여기 모아 보여 준다(데이터 출처 + 사진 저작자·라이선스). */
@Composable
fun CreditsScreen(vm: AppViewModel) {
    val photos = remember { vm.places.photoCredits() }
    Column(Modifier.fillMaxSize().beffBackground().padding(top = 40.dp)) {
        TitleBar("저작권 · 출처", onBack = { vm.back() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 56.dp).padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(36.dp),
        ) {
            Section("데이터 출처", listOf(
                "장소 · 시설 · 사진: 한국관광공사 열린관광 정보(KorWithService2)",
                "장애인 주차장: 전국주차장정보표준데이터(공공데이터포털)",
                "장소 검색 · 경유지 후보: 카카오 로컬",
                "지도: 카카오맵(베프 색으로 보정) · 시설 약도: OpenFreeMap © OpenMapTiles · 스카이뷰: Esri World Imagery · 도식 지도 배경: 브이월드 · 시설 위치: 카카오 장소 검색 · 보행로 · 계단 · 경사 · 도보 길찾기(FOSSGIS OSRM): © OpenStreetMap contributors(ODbL)",
                "거리 · 소요 시간: 차량 내비게이션(Pleos NaviHelper)",
            ))
            // 1113: 관광공사 대표 사진(공공누리)은 한 줄로 묶고, 위키미디어 공용 사진만 장소별 저작자 · 라이선스를 적는다.
            val (tour, commons) = photos.partition { it.second.contains("한국관광공사") }
            Section(
                "사진 저작자 (${photos.size}장)",
                listOfNotNull(if (tour.isNotEmpty()) "한국관광공사 열린관광 대표 사진 ${tour.size}장" else null) +
                    commons.map { (name, credit) -> "$name · $credit" },
            )
        }
    }
}

@Composable
private fun Section(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = BeffType.Body32.copy(color = Beff.TextPrimary))
        lines.forEach { Text(it, style = BeffType.Caption28.copy(color = Beff.TextSecondary)) }
    }
}
