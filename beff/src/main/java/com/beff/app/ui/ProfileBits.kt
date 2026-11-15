package com.beff.app.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.beff.app.data.Person
import java.io.File

// 정차·설정 진입 그룹(00 · 01 · 02) 전용 조각. 스타일프레임 840 · 1700 폭 수치 그대로.

// ───────────────────────── 프로필 이미지 합성 ─────────────────────────

/**
 * 프로필 이미지 합성(정책 36): 1명 가운데 / 2명 좌우 / 3명 어른 뒤 · 아이 앞, 앞사람 그림자.
 * 인물 SVG(240 원) 안쪽 그림만 꺼내 슬롯 위치로 옮겨 새 SVG 하나를 만든다. 같은 인물이 겹치면 옷 색만 바꾼다.
 * 슬롯 좌표는 Figma `auto_sample_3` · `kids` · `elders` 내보내기에서 얼굴 타원을 역산한 값이다.
 */
internal object PersonSvg {
    private class Face(val cx: Float, val cy: Float, val rx: Float)
    private class Slot(val cx: Float, val cy: Float, val rx: Float, val front: Boolean)

    private val faces = mapOf(
        Person.AdultMan to Face(120f, 110f, 54f), Person.AdultWoman to Face(120f, 110f, 52f),
        Person.Boy to Face(120f, 110f, 54f), Person.Girl to Face(120f, 110f, 52f),
        Person.Grandpa to Face(120f, 110f, 54f), Person.Grandma to Face(120f, 120f, 50f),
    )
    private val bodyHex = mapOf(
        Person.AdultMan to "#2F4A66", Person.AdultWoman to "#F08A6C", Person.Boy to "#00A39F",
        Person.Girl to "#FFC94D", Person.Grandpa to "#2F4A66", Person.Grandma to "#F08A6C",
    )
    private val altHex = listOf("#E9748B", "#5B8FD9", "#8A6CC9")
    private val innerRe = Regex("""<rect width="240" height="240" rx="120" fill="#[0-9A-Fa-f]+"/>(.*)</g>\s*<rect x=""", RegexOption.DOT_MATCHES_ALL)
    private val raw = HashMap<Person, String>()
    private val written = HashSet<String>()

    private fun inner(ctx: Context, p: Person): String = raw.getOrPut(p) {
        val text = ctx.assets.open(p.asset).bufferedReader().use { it.readText() }
        innerRe.find(text)?.groupValues?.get(1) ?: ""
    }

    private fun slots(n: Int): List<Slot> = when (n) {
        2 -> listOf(Slot(78f, 124f, 40f, true), Slot(166f, 122f, 38f, true))
        else -> listOf(Slot(120f, 86f, 38f, false), Slot(62f, 152f, 30f, true), Slot(178f, 152f, 29f, true))
    }

    fun build(ctx: Context, people: List<Person>): String {
        val list = people.take(3).let { if (it.size == 3) it.sortedBy { p -> p.isChild } else it }
        val sb = StringBuilder()
        sb.append("""<svg width="240" height="240" viewBox="0 0 240 240" fill="none" xmlns="http://www.w3.org/2000/svg">""")
        sb.append("""<defs><clipPath id="c"><rect width="240" height="240" rx="120"/></clipPath>""")
        sb.append("""<filter id="s" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur in="SourceAlpha" stdDeviation="3.3"/><feOffset dy="1" result="b"/><feFlood flood-color="#051A1F" flood-opacity="0.28"/><feComposite in2="b" operator="in"/><feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>""")
        sb.append("""<g clip-path="url(#c)"><rect width="240" height="240" fill="#DFF1EF"/>""")
        if (list.size == 1) {
            sb.append(inner(ctx, list[0]))
        } else if (list.size >= 2) {
            val seen = HashMap<Person, Int>()
            val sl = slots(list.size)
            list.forEachIndexed { i, p ->
                val f = faces.getValue(p)
                val s = sl[i].rx / f.rx
                val tx = sl[i].cx - s * f.cx
                val ty = sl[i].cy - s * f.cy
                val k = seen.merge(p, 1, Int::plus)!! - 1
                var body = inner(ctx, p)
                if (k > 0) body = body.replace("fill=\"${bodyHex.getValue(p)}\"", "fill=\"${altHex[(k - 1) % altHex.size]}\"")
                val filter = if (sl[i].front) """ filter="url(#s)"""" else ""
                sb.append("""<g transform="translate($tx $ty) scale($s)"$filter>$body</g>""")
            }
        }
        sb.append("""</g><rect x="2" y="2" width="236" height="236" rx="118" stroke="#C9D0D6" stroke-width="4"/></svg>""")
        return sb.toString()
    }

    /** 만든 SVG를 앱 캐시 파일로 두고 경로를 돌려준다(Coil이 파일로 읽음). */
    fun file(ctx: Context, people: List<Person>): File {
        val name = "people_v1_" + people.take(3).joinToString("_") { it.key } + ".svg"
        val f = File(ctx.cacheDir, name)
        synchronized(written) {
            if (written.add(name) || !f.exists()) f.writeText(build(ctx, people))
        }
        return f
    }
}

/** 2~3명 합성 프로필 이미지. 1명이면 인물 SVG 그대로, 0명이면 빈 원. */
@Composable
fun PersonComposite(people: List<Person>, size: Dp, modifier: Modifier = Modifier) {
    val loader = LocalSvgLoader.current ?: return
    val ctx = LocalContext.current
    when {
        people.isEmpty() -> Box(
            modifier.size(size).clip(CircleShape).background(Beff.TealTint).border(size * 0.0167f, Color(0xFFC9D0D6), CircleShape),
        )
        people.size == 1 -> BeffAsset(people[0].asset, size, modifier)
        else -> {
            val file = remember(people.take(3).map { it.key }) { PersonSvg.file(ctx, people) }
            AsyncImage(
                model = file, imageLoader = loader, contentDescription = null,
                contentScale = ContentScale.Fit, modifier = modifier.size(size),
            )
        }
    }
}

// ───────────────────────── 틀 · 막대 · 버튼 ─────────────────────────

/** 로고 + 베프 (01-1~3 · 02-6~7). 앱 아이콘 64 r19, 글자 44 Medium, 간격 14. */
@Composable
fun ProfileBrandBar(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(80.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
        // 1113: 새 앱 아이콘(Figma logo — 바탕 · 모서리 포함) 그대로 64.
        BeffAsset("app_icon/beff_logo.png", 64.dp)
        Text("베프", style = BeffType.Brand44.copy(color = Beff.TextStrong, fontWeight = FontWeight.Medium))
    }
}

/** 제목 바 80: 뒤로 + 제목 40 Bold. 3분할(840)에서는 오른쪽에 점 셋(창 메뉴 자리, 동작 없음 [미확인]). */
@Composable
fun ProfileTitleBar(title: String, onBack: (() -> Unit)?, wide: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(80.dp).padding(start = 40.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (onBack != null) {
            Box(Modifier.size(56.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                BeffAsset("pict/chevron_right.svg", 48.dp, Modifier.scale(-1f, 1f))
            }
        }
        Text(title, style = BeffType.Bar40.copy(color = Beff.TextStrong), maxLines = 1, modifier = Modifier.weight(1f))
        if (!wide) {
            Canvas(Modifier.size(80.dp)) {
                val c = Color(0xFF252525)
                listOf(-14f, 0f, 14f).forEach { dx -> drawCircle(c, 3.dp.toPx(), Offset(size.width / 2 + dx.dp.toPx(), size.height / 2)) }
            }
        }
    }
}

/** 진행 막대 12 (좌우 56). */
@Composable
fun ProfileProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 56.dp)) {
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Beff.TealTrack)) {
            Box(Modifier.fillMaxWidth(fraction).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Beff.Teal))
        }
    }
}

/** 하단 고정 버튼 영역. 1110 A: 3분할 · 와이드 모두 좌우 56 전체 폭(와이드 1588). [wide]는 호출 호환용. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun ProfileBottom(wide: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 40.dp)) { content() }
}

enum class PKind { Primary, Ghost }

/** 큰 버튼 100 r32. 비활성은 흰 85% + 회색 글자(Figma 02-1). */
@Composable
fun ProfileButton(text: String, onClick: () -> Unit, kind: PKind = PKind.Primary, enabled: Boolean = true) {
    val shape = RoundedCornerShape(32.dp)
    val primary = kind == PKind.Primary && enabled
    val bg = when {
        primary -> Beff.Teal
        kind == PKind.Primary -> Color(0xD9FFFFFF)
        else -> Beff.BoxFill
    }
    val fg = when {
        primary -> Color.White
        kind == PKind.Primary -> Color(0xFFB6BFC5)
        else -> Beff.TextSecondary
    }
    Box(
        Modifier
            .fillMaxWidth().height(100.dp)
            .then(if (!primary) Modifier.shadow(6.dp, shape, ambientColor = Beff.BoxShadow, spotColor = Beff.BoxShadow) else Modifier)
            .clip(shape).background(bg)
            .then(if (!primary) Modifier.border(2.dp, Beff.BoxBorder, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = BeffType.Button36.copy(color = fg), maxLines = 1) }
}

/** 알약 버튼(프로필 편집 · 모두 선택 · 모두 해제). 높이 72(3분할) / 76(2/3), 글자 30 SemiBold. */
@Composable
fun ProfilePill(text: String, onClick: () -> Unit, height: Dp, modifier: Modifier = Modifier, icon: String? = null) {
    val shape = RoundedCornerShape(100.dp)
    Row(
        modifier
            .shadow(8.dp, shape, ambientColor = Beff.BoxShadow, spotColor = Beff.BoxShadow)
            .clip(shape).background(Beff.BoxFill).border(2.dp, Beff.BoxBorder, shape)
            .clickable(onClick = onClick).height(height).padding(horizontal = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) BeffAsset(icon, 36.dp)
        Text(text, style = BeffType.Small30, maxLines = 1)
    }
}

/** 프로필 추가 칸: 점선 12/12 · 굵기 3 · r36. big = A-2의 큰 버튼(더하기 60, 글자 44). */
@Composable
fun AddProfileCell(modifier: Modifier, big: Boolean, onClick: () -> Unit) {
    // 1110 A-2: 큰 추가 버튼의 더하기도 60 · 굵기 4
    val plus = if (big) 60.dp else 56.dp
    val bar = 4.dp
    androidx.compose.foundation.layout.Column(
        modifier
            .drawBehind {
                val w = 3.dp.toPx()
                drawRoundRect(
                    color = Beff.DashBorder,
                    topLeft = Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(36.dp.toPx()),
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(), 12.dp.toPx()))),
                )
            }
            .clip(RoundedCornerShape(36.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (big) 24.dp else 14.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(plus)) {
            Box(Modifier.align(Alignment.Center).size(bar, plus).clip(RoundedCornerShape(bar / 2)).background(Beff.TextSecondary))
            Box(Modifier.align(Alignment.Center).size(plus, bar).clip(RoundedCornerShape(bar / 2)).background(Beff.TextSecondary))
        }
        Text(
            "프로필 추가",
            style = (if (big) BeffType.Item36Semi.copy(fontSize = 44.sp, lineHeight = 58.sp) else BeffType.Item36Semi.copy(lineHeight = 48.sp))
                .copy(color = Beff.TextSecondary),
        )
    }
}

/** 프로필 카드 왼쪽 위 지우기 표식(02-6 · 02-7): 어두운 원 + 흰 X. 3분할 64 / 2/3 76. */
@Composable
fun DeleteBadge(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        drawCircle(Beff.TextPrimary)
        val h = size.toPx() * 0.17f
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val st = 4.dp.toPx()
        drawLine(Color.White, Offset(c.x - h, c.y - h), Offset(c.x + h, c.y + h), st, StrokeCap.Round)
        drawLine(Color.White, Offset(c.x + h, c.y - h), Offset(c.x - h, c.y + h), st, StrokeCap.Round)
    }
}

/** 완료 표시 체크 32. */
@Composable
fun CheckMark(modifier: Modifier = Modifier, color: Color = Beff.TextPrimary) {
    Canvas(modifier.size(32.dp)) {
        val p = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.53f)
            lineTo(size.width * 0.42f, size.height * 0.72f)
            lineTo(size.width * 0.78f, size.height * 0.30f)
        }
        drawPath(p, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 박스 안 요약 알약(수단 · 인원). */
@Composable
fun SummaryPill(text: String, bg: Color = Beff.Surface) {
    Box(Modifier.clip(RoundedCornerShape(100.dp)).background(bg).padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(text, style = BeffType.Caption28.copy(color = Beff.TextSecondary), maxLines = 1)
    }
}

// ───────────────────────── 프로필 이미지: 얼굴 아바타 (1110 A-6) ─────────────────────────

/** 얼굴 아바타 9종(Figma `BEFF Avatar/01_teal` … `09_lightgray`): 바탕색 + 흰 표정. */
class FaceAvatar(val color: Color, val face: String)

val FACE_AVATARS = listOf(
    FaceAvatar(Color(0xFF00837F), "face/face_smile.svg"),    // 01 teal
    FaceAvatar(Color(0xFF2DB3AF), "face/face_eung.svg"),     // 02 mint
    FaceAvatar(Color(0xFFF08A6B), "face/face_spread.svg"),   // 03 coral
    FaceAvatar(Color(0xFFF2B84B), "face/face_smile.svg"),    // 04 yellow
    FaceAvatar(Color(0xFF4A7BD0), "face/face_tilt.svg"),     // 05 blue
    FaceAvatar(Color(0xFF8C7AD6), "face/face_eung.svg"),     // 06 purple
    FaceAvatar(Color(0xFF5DAE6E), "face/face_neutral.svg"),  // 07 green
    FaceAvatar(Color(0xFF5F6B73), "face/face_spread.svg"),   // 08 gray
    FaceAvatar(Color(0xFFBBCEC5), "face/face_route.svg"),    // 09 lightgray (길 모양)
)

/** 아바타 한 칸. 모서리는 한 변의 0.3(160 → r48, 200 → r60), 표정은 0.85(160 → 136). */
@Composable
fun FaceAvatarTile(index: Int, size: Dp, modifier: Modifier = Modifier) {
    val a = FACE_AVATARS[index.mod(FACE_AVATARS.size)]
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(a.color),
        contentAlignment = Alignment.Center,
    ) { BeffAsset(a.face, size * 0.85f) }
}
