package com.beff.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale as drawScale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.SvgDecoder

val LocalSvgLoader = compositionLocalOf<ImageLoader?> { null }

@Composable
fun ProvideSvgLoader(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val loader = remember { ImageLoader.Builder(ctx).components { add(SvgDecoder.Factory()) }.build() }
    CompositionLocalProvider(LocalSvgLoader provides loader, content = content)
}

/** assets/beff 하위 SVG를 그대로 표시한다(다시 그리지 않음). path 예: "pict/edit.svg" */
@Composable
fun BeffAsset(path: String, size: Dp, modifier: Modifier = Modifier, tint: Color? = null) {
    val loader = LocalSvgLoader.current ?: return
    val ctx = LocalContext.current
    val dark = Beff.dark
    // 1113: 다크에서 컬러 일러스트의 검은 선 · 면(#252B31)은 바탕에 묻힌다(경사로 난간). Figma 다크와 같이 #C9D1D5로 바꿔 읽는다.
    val model: Any = remember(path, dark) {
        if (dark && path.startsWith("illust/")) runCatching {
            val svg = ctx.assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
            java.nio.ByteBuffer.wrap(svg.replace("#252B31", "#C9D1D5", ignoreCase = true).toByteArray())
        }.getOrDefault("file:///android_asset/$path") else "file:///android_asset/$path"
    }
    AsyncImage(
        model = model,
        imageLoader = loader,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        // 다크 모드: 선 픽토(pict/ · face/)는 검은 선이라 밝은 글자색으로 칠한다. 컬러 일러스트(illust/)는 그대로.
        colorFilter = (tint ?: if (Beff.dark && (path.startsWith("pict/") || path.startsWith("face/"))) Beff.TextPrimary else null)?.let { ColorFilter.tint(it) },
        modifier = modifier.size(size),
    )
}

/**
 * 흰 바탕 + 틸 번짐 3곳(18% · 10.5% · 7%). 개발 정책 46.
 * 번짐은 프레임 비율 기준이다(Figma 그라디언트 변환 역산): 왼쪽 아래 (0.12, 1.0) 반지름 0.8,
 * 오른쪽 (1.0, 0.82) 반지름 0.6, 왼쪽 위 (0.05, 0) 반지름 0.5. 840 · 1700 폭에서 같은 식으로 맞는다.
 */
fun Modifier.beffBackground(tint: Color = Color(0xFF008F8C), base: Color = Beff.Bg): Modifier = this
    .background(base)
    .drawBehind {
        val w = size.width
        val h = size.height
        fun blob(cx: Float, cy: Float, r: Float, a: Float) {
            val c = Offset(cx * w, cy * h)
            val rx = r * w
            val ry = r * h
            drawScale(1f, ry / rx, pivot = c) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(tint.copy(alpha = a), tint.copy(alpha = 0f)),
                        center = c, radius = rx,
                    ),
                    radius = rx, center = c,
                )
            }
        }
        blob(0.05f, 0f, 0.5f, 0.07f)
        blob(1f, 0.82f, 0.6f, 0.105f)
        blob(0.12f, 1f, 0.8f, 0.182f)
    }

/** 흰색 90% + 흰 테두리 + 옅은 그림자 박스. 개발 정책 46. */
@Composable
fun BeffBox(
    modifier: Modifier = Modifier,
    radius: Dp = 36.dp,
    onClick: (() -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    /** 1113: 흰 패널 위에 놓일 때 경계가 보이게 회색 선(Beff.Stroke)을 준다. 없으면 흰 테두리(정책 46). */
    stroke: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .shadow(8.dp, shape, ambientColor = Beff.BoxShadow, spotColor = Beff.BoxShadow)
            .clip(shape)
            .background(Beff.BoxFill)
            .border(2.dp, stroke ?: Beff.BoxFill, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

/** 알약 버튼(프로필 편집·모두 선택 등). 작은 버튼은 왼쪽 기준(정책 42). */
@Composable
fun BeffPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null) {
    val shape = RoundedCornerShape(100.dp)
    Row(
        modifier = modifier
            .shadow(8.dp, shape, ambientColor = Beff.BoxShadow, spotColor = Beff.BoxShadow)
            .clip(shape)
            .background(Beff.BoxFill)
            .border(2.dp, Beff.BoxFill, shape)
            .clickable(onClick = onClick)
            .height(64.dp)
            .padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) BeffAsset(icon, 36.dp)
        Text(text, style = BeffType.Small30, maxLines = 1)
    }
}

enum class ButtonKind { Primary, Secondary, Ghost, Muted }

/** Beff / Button — Large 100/r32/36 SemiBold. 화면당 하나, 하단 고정(정책 41). */
@Composable
fun BeffButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    height: Dp = 100.dp,
    containerColor: Color? = null,   // 경고 화면(07-1) 주 버튼처럼 색만 바꿀 때
    contentColor: Color? = null,     // 토스트 안 보조 버튼처럼 글자색만 바꿀 때
) {
    val shape = RoundedCornerShape(32.dp)
    val bg = containerColor ?: when (kind) {
        ButtonKind.Primary -> Beff.Teal
        ButtonKind.Secondary -> Beff.SecondaryBtn // 5-3: 보조 회색 #EDEEF2
        ButtonKind.Ghost -> Beff.BoxFill
        ButtonKind.Muted -> Beff.Divider
    }
    val fg = contentColor ?: when (kind) {
        ButtonKind.Primary -> Color.White
        ButtonKind.Secondary -> Beff.SecondaryBtnText
        else -> Beff.TextSecondary
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (enabled) 1f else 0.4f)
            .then(
                if (kind == ButtonKind.Ghost)
                    Modifier.shadow(6.dp, shape, ambientColor = Beff.BoxShadow, spotColor = Beff.BoxShadow)
                else Modifier
            )
            .clip(shape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = BeffType.Button36.copy(color = fg), maxLines = 1)
    }
}

/** Beff / Chip — Filter 80dp. 선택 = 옅은 틸 + 틸 테두리. */
@Composable
fun BeffChip(text: String, selected: Boolean, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(100.dp)
    Box(
        modifier = modifier
            .height(80.dp)
            .clip(shape)
            .background(if (selected) Beff.TealSoft else Beff.Surface)
            .then(if (selected) Modifier.border(1.dp, Beff.TealMain, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = BeffType.Body32.copy(color = if (selected) Beff.TealText else Beff.TextPrimary),
            maxLines = 1,
        )
    }
}

/** 이동 보조수단 알약(프로필 카드 안). */
@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Beff.Surface)
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) { Text(text, style = BeffType.Caption28.copy(color = Beff.TextSecondary), maxLines = 1) }
}

/** ivi_stepper — 420×100, − 값 +. */
@Composable
fun BeffStepper(
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    plusEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .width(420.dp)
            .height(100.dp)
            .clip(shape)
            .background(Beff.Surface)
            .border(2.dp, Beff.Divider, shape)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        StepperButton("−", true, onMinus)
        Box(Modifier.width(2.dp).fillMaxSize().background(Beff.Divider))
        Text(
            "${value}명",
            style = BeffType.Item36.copy(color = Beff.TextStrong, textAlign = TextAlign.Center),
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(2.dp).fillMaxSize().background(Beff.Divider))
        StepperButton("+", plusEnabled, onPlus)
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .alpha(if (enabled) 1f else 0.3f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = BeffType.Title48.copy(color = Beff.TextStrong, lineHeight = 48.sp)) }
}

/** 제목 바 80dp — 뒤로가기 + 제목. 단계 표시는 바로 아래 진행 막대(정책 44). */
@Composable
fun TitleBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(start = 26.dp, end = 24.dp), // 뒤로 누름 76(리뷰 1) — 화살표 위치는 그대로
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (onBack != null) {
            Box(Modifier.size(76.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                BeffAsset("pict/chevron_right.svg", 48.dp, Modifier.scale(-1f, 1f))
            }
        }
        Text(title, style = BeffType.Bar40.copy(color = Beff.TextStrong), maxLines = 1, modifier = Modifier.weight(1f))
    }
}

/** 로고 + 베프 (01-1~3, 02-6~7은 제목 바 대신). 정책 48. */
@Composable
fun BrandBar(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(80.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
        // 1113: 새 앱 아이콘(Figma logo — 바탕 · 모서리 포함) 그대로 64.
        BeffAsset("app_icon/beff_logo.png", 64.dp)
        Text("베프", style = BeffType.Brand44.copy(color = Beff.TextStrong))
    }
}

/** 진행 막대 12dp. */
@Composable
fun StepProgress(step: Int, total: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(horizontal = 56.dp, vertical = 8.dp)) {
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Beff.TealTrack)) {
            Box(
                Modifier
                    .fillMaxWidth(step / total.toFloat())
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Beff.Teal)
            )
        }
    }
}

/** 하단 고정 주 버튼 영역(좌우 56, 위 24, 아래 40). */
@Composable
fun BottomBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 40.dp),
        content = content,
    )
}

/** 점선 테두리(프로필 추가 칸). */
fun Modifier.dashedBorder(color: Color, radius: Dp = 36.dp, width: Dp = 3.dp): Modifier = this.drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))),
    )
}

/** 화면 공통 틀: 배경 + 상단 영역 + 본문(가변) + 하단 영역. */
@Composable
fun ScreenFrame(
    modifier: Modifier = Modifier,
    top: @Composable () -> Unit = {},
    bottom: @Composable () -> Unit = {},
    tint: Color = Color(0xFF008F8C),
    body: @Composable (Modifier) -> Unit,
) {
    Column(modifier.fillMaxSize().beffBackground(tint)) {
        top()
        body(Modifier.weight(1f))
        bottom()
    }
}
