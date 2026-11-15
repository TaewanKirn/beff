package com.beff.app.data

/** 이동 보조수단 3종 (개발 정책 24). 합계 3명까지(27). */
enum class Mobility(val key: String, val label: String, val desc: String, val illust: String) {
    // 6-12 · 1113: 메인 타겟 휠체어 우선 — 카드 · 인원 · 필터 그룹 순서가 이 순서를 따른다(저장은 key라 순서와 무관).
    Wheelchair("wheelchair", "휠체어", "휠체어로 이동", "illust/wheelchair.svg"),
    Stroller("stroller", "유모차", "영유아와 함께", "illust/stroller.svg"),
    Walker("walker", "보행 도움", "지팡이 · 천천히", "illust/cane.svg");

    companion object {
        fun of(key: String) = entries.first { it.key == key }
    }
}

/** 프로필 이미지용 인물 6종 (정책 33). 휠체어 탄 인물은 따로 그리지 않는다(10). */
enum class Person(val key: String, val label: String, val asset: String, val isChild: Boolean) {
    Boy("boy", "남자아이", "people/boy.svg", true),
    Girl("girl", "여자아이", "people/girl.svg", true),
    AdultMan("adult_man", "남자 성인", "people/adult_man.svg", false),
    AdultWoman("adult_woman", "여자 성인", "people/adult_woman.svg", false),
    Grandpa("grandpa", "남자 어르신", "people/grandpa.svg", false),
    Grandma("grandma", "여자 어르신", "people/grandma.svg", false);

    companion object {
        fun of(key: String) = entries.first { it.key == key }
    }
}

/** 추천 장소 필터 칩 그룹. 선택 수단에 따라 자동 선택(정책 29, 31). */
data class FilterGroup(val id: String, val title: String, val icon: String, val chips: List<String>, val autoOn: Set<String>)

object FilterCatalog {
    val stroller = FilterGroup(
        "stroller", "유모차", "illust/stroller.svg",
        listOf("수유실", "기저귀 교환대", "유모차 대여", "가족 화장실", "유아 의자"),
        setOf("수유실", "기저귀 교환대", "가족 화장실"),
    )
    val wheelchair = FilterGroup(
        "wheelchair", "휠체어", "illust/wheelchair.svg",
        listOf("장애인 주차", "엘리베이터", "경사로", "측면 승하차 공간", "장애인 화장실", "휠체어 대여", "턱 없는 입구"),
        setOf("장애인 주차", "엘리베이터", "경사로", "측면 승하차 공간"),
    )
    // 보행 도움의 자동 선택 칩이 비어도 진행 가능한지는 정책 32 [미결] — 일단 자동 선택 없음.
    val walker = FilterGroup(
        "walker", "보행 도움", "illust/cane.svg",
        listOf("계단 없는 길", "쉼터", "의자 있는 곳", "가까운 화장실", "손잡이 있는 길"),
        emptySet(),
    )
    val route = FilterGroup(
        "route", "가는 길에 들르는 곳", "pict/rest.svg",
        listOf("휴게소", "주차장", "편의점", "카페", "식당", "약국"),
        emptySet(),
    )

    fun forMobility(m: Mobility) = when (m) {
        Mobility.Stroller -> stroller
        Mobility.Wheelchair -> wheelchair
        Mobility.Walker -> walker
    }
}

/** 함께 타는 사람 묶음. 시스템(운전자) 프로필과 별개 개념이다. */
data class Profile(
    val id: String,
    val name: String,
    val counts: Map<Mobility, Int>,
    val filters: Set<String>,
    val people: List<Person>,
    /** 탑승자 순번 → 앉는 자리(SeatKind.key). 1110 「옮겨 타기 시트」: 도착하면 이 자리 시트를 뒤로 밀지 묻는다. */
    val seats: Map<Int, String> = emptyMap(),
    /** 1110 A-6 프로필 이미지(얼굴 아바타 9종 중 순번 0..8). -1 = 고르지 않음(예전 저장분 · 자동 등록) → id로 정한다. */
    val avatar: Int = -1,
) {
    val total get() = counts.values.sum()

    /** 탑승자 이름표: 한 명이면 "휠체어", 여럿이면 "유모차 1" · "유모차 2" … (프로필 만들 때 순서와 같다) */
    val riderLabels: List<String>
        get() = Mobility.entries.flatMap { m -> (1..(counts[m] ?: 0)).map { n -> if (total > 1) "${m.label} $n" else m.label } }

    /** 도착 때 시트 도움을 물을 탑승자들: (이름표, 앉은 자리). 운전석은 묻지 않는다. 탑승자 순서대로. */
    val seatAssists: List<Pair<String, SeatKind>>
        get() = seats.entries.sortedBy { it.key }.mapNotNull { (i, key) ->
            SeatKind.of(key)?.takeIf { it.assist }?.let { (riderLabels.getOrNull(i) ?: "함께 타는 분") to it }
        }

    /** "휠체어 1명" · "유모차 2 · 휠체어 1" 형식 */
    val summary: String
        get() {
            val parts = Mobility.entries.mapNotNull { m -> counts[m]?.takeIf { it > 0 }?.let { m to it } }
            return if (parts.size == 1) "${parts[0].first.label} ${parts[0].second}명"
            else parts.joinToString(" · ") { "${it.first.label} ${it.second}" }
        }
}

/**
 * 앉는 자리. key는 Vehicle SDK 좌석 영역(SeatValue)과 같은 이름이다. [assist] = 도착 때 시트 도움을 묻는 자리.
 * 조수석은 그 시트를 뒤로, 뒷좌석은 같은 쪽 앞줄 시트를 앞으로 당겨 문 앞 공간을 넓힌다(화면 시안, 1110 「시트 동작 기준」).
 */
enum class SeatKind(val key: String, val label: String, val assist: Boolean) {
    Driver("ROW_1_LEFT", "운전석", false),
    Passenger("ROW_1_RIGHT", "조수석", true),
    RearLeft("ROW_2_LEFT", "뒷좌석 왼쪽", true),
    RearRight("ROW_2_RIGHT", "뒷좌석 오른쪽", true);

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

const val MAX_PROFILES = 4
const val MAX_RIDERS = 3
const val MAX_NAME = 10
