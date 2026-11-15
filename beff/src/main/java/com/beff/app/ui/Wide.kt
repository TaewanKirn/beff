package com.beff.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 창 폭 기준. 3분할 가운데 창은 840, 2/3 전체 화면은 1700(스타일프레임 `2/3 확장` 섹션).
 * 1200dp 이상이면 true = 2/3 전체 화면 2열 레이아웃을 쓴다. 화면 코드는 이 값만 본다.
 */
val LocalWide = compositionLocalOf { false }

const val WIDE_MIN_DP = 1200

@Composable
fun ProvideWindowWidth(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWide provides (maxWidth >= WIDE_MIN_DP.dp)) { content() }
    }
}

// ── 2/3 전체 화면 틀 (스타일프레임 `2/3 확장`, 1700×1184) ──
// 좌우 여백 56 · 열 폭 778 · 열 간격 32 · 위 48 · 아래 40. 왼쪽 열 = 운전석 쪽(주 버튼 · GNB · 선택지), 오른쪽 열 = 보조 내용.
val WIDE_SIDE = 56.dp
val WIDE_TOP = 48.dp
val WIDE_BOTTOM = 40.dp
val WIDE_GAP = 32.dp

/** GNB 없는 2열 틀(01 · 02 · 06 · 07 같은 깊이 화면). 제목은 호출하는 쪽이 열 위에 따로 둔다. */
@Composable
fun WideColumns(
    modifier: Modifier = Modifier,
    left: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    right: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier.fillMaxSize(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(WIDE_GAP),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f).fillMaxSize(), content = left)
        androidx.compose.foundation.layout.Column(Modifier.weight(1f).fillMaxSize(), content = right)
    }
}

/**
 * 1110 수정 홈 · 검색(B-1 · C-1 · C-3) 2열 틀: 왼쪽 열(여백 56, 폭 778) + GNB, 오른쪽은 위아래 끝까지 붙은 흰 패널(폭 818).
 * 왼쪽 열 끝(834)과 패널(882) 사이 48. [right]는 패널 안 여백(좌우 40)까지 호출하는 쪽이 정한다.
 */
@Composable
fun WidePanelTab(
    tab: com.beff.app.Tab,
    onTab: (com.beff.app.Tab) -> Unit,
    left: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    right: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Column(
            Modifier.weight(834f).fillMaxSize().padding(start = WIDE_SIDE, top = WIDE_TOP, bottom = WIDE_BOTTOM),
        ) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f).fillMaxWidth(), content = left)
            androidx.compose.foundation.layout.Spacer(Modifier.height(WIDE_GAP))
            BeffGnb(tab, onTab, sidePadding = 0.dp)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.weight(48f))
        androidx.compose.foundation.layout.Column(
            Modifier.weight(818f).fillMaxSize().background(Beff.Card),
            content = right,
        )
    }
}

/**
 * 탭 첫 화면(홈 · 검색 · 여정 · 설정)의 2열 틀. GNB는 왼쪽 열 맨 아래(위 간격 32, 아래 40)에 붙는다.
 * left는 GNB 위 남은 높이를, right는 열 전체 높이를 쓴다.
 */
@Composable
fun WideTab(
    tab: com.beff.app.Tab,
    onTab: (com.beff.app.Tab) -> Unit,
    modifier: Modifier = Modifier,
    left: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    right: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier
            .fillMaxSize()
            .padding(start = WIDE_SIDE, end = WIDE_SIDE, top = WIDE_TOP, bottom = WIDE_BOTTOM),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(WIDE_GAP),
    ) {
        androidx.compose.foundation.layout.Column(Modifier.weight(1f).fillMaxSize()) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f).fillMaxWidth(), content = left)
            androidx.compose.foundation.layout.Spacer(Modifier.height(WIDE_GAP))
            BeffGnb(tab, onTab, sidePadding = 0.dp)
        }
        androidx.compose.foundation.layout.Column(Modifier.weight(1f).fillMaxSize(), content = right)
    }
}
