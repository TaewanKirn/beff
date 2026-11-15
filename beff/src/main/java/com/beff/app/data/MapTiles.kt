package com.beff.app.data

/**
 * 카카오맵 바탕 타일을 베프 지도 시안(스타일프레임 「G-2 지도 POI 시안」) 색 체계에 가깝게 누르는 필터.
 * 카카오맵 JS API는 바탕 지도 스타일을 바꿀 수 없어서, 타일 이미지(img)에만 SVG 필터를 건다.
 * 마커 · 경로선은 DOM/SVG 오버레이라 필터를 받지 않는다(원래 색 유지).
 *
 * 시안 색: 바탕 ≈ #EDF3EB · 녹지 #D2E7CE · 물 #CFE3EE · 건물 #DCE1DF · 도로 흰색 · 글자 #4A545C.
 * 1) 채도를 0.45로 낮추고 2) 밝기 범위를 0.18~0.97로 눌러(대비 ↓) 3) G 채널을 조금 더 남겨 연두빛을 입힌다.
 * 예: 카카오 바탕(베이지) → #EDF5EB, 공원 초록 → #D4E3C9, 물 → #C4D9DE, 검은 글자 → 회색(#575A57).
 * 스카이뷰(위성)에서는 끈다: setType('sky')가 #map에 sky 클래스를 붙인다.
 */
object MapTiles {
    const val CSS = "#map:not(.sky) img{filter:url(#beffmap)}"

    const val SVG = "<svg width=\"0\" height=\"0\" style=\"position:absolute\" aria-hidden=\"true\">" +
        "<filter id=\"beffmap\" color-interpolation-filters=\"sRGB\">" +
        "<feColorMatrix type=\"saturate\" values=\"0.45\"/>" +
        "<feComponentTransfer>" +
        "<feFuncR type=\"linear\" slope=\"0.78\" intercept=\"0.18\"/>" +
        "<feFuncG type=\"linear\" slope=\"0.80\" intercept=\"0.19\"/>" +
        "<feFuncB type=\"linear\" slope=\"0.76\" intercept=\"0.19\"/>" +
        "</feComponentTransfer></filter></svg>"

    /** setType에 붙여 스카이뷰일 때 필터를 끈다. */
    const val SKY_TOGGLE = "document.getElementById('map').classList.toggle('sky',t==='sky');"
}
