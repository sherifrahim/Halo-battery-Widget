package com.halo.btbattery.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halo.btbattery.R
import com.halo.btbattery.ui.theme.Fluid
import com.halo.btbattery.ui.theme.LocalAccent
import com.halo.btbattery.ui.theme.LocalTones
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Continuous-curvature ("G2") rounded rectangle: the curve starts further along each edge and
 * eases in, so corners melt into the sides instead of meeting them at a visible seam.
 */
class SmoothShape(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(with(density) { radius.toPx() }, min(size.width, size.height) / 2f)
        val s = min(r * 1.28f, min(size.width, size.height) / 2f)
        val c = s * 0.16f
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(s, 0f)
            lineTo(w - s, 0f)
            cubicTo(w - c, 0f, w, c, w, s)
            lineTo(w, h - s)
            cubicTo(w, h - c, w - c, h, w - s, h)
            lineTo(s, h)
            cubicTo(c, h, 0f, h - c, 0f, h - s)
            lineTo(0f, s)
            cubicTo(0f, c, c, 0f, s, 0f)
            close()
        }
        return Outline.Generic(p)
    }
}

/** Compress on press, spring back with a touch of overshoot. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bouncy(
    onLongClick: (() -> Unit)? = null,
    pressedScale: Float = 0.94f,
    onClick: (() -> Unit)?,
): Modifier = composed {
    if (onClick == null && onLongClick == null) return@composed this
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = { onClick?.invoke() })
}

/**
 * Translucent card lit from above. Pressing it squishes it slightly and blooms a soft light
 * exactly where the finger lands.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTones.current
    val shape = SmoothShape(radius)
    val source = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val glow = remember { Animatable(0f) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "card",
    )
    LaunchedEffect(source) {
        source.interactions.collect { i ->
            when (i) {
                is PressInteraction.Press -> { touch = i.pressPosition; scope.launch { glow.animateTo(1f, tween(120)) } }
                is PressInteraction.Release, is PressInteraction.Cancel -> scope.launch { glow.animateTo(0f, tween(450)) }
            }
        }
    }
    val clickable = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(interactionSource = source, indication = null, onLongClick = onLongClick, onClick = { onClick?.invoke() })
    } else Modifier

    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(if (!t.dark) Modifier.shadow(10.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x1A000000)) else Modifier)
            .clip(shape)
            .background(t.card)
            .drawWithContent {
                drawContent()
                if (glow.value > 0f) {
                    val r = size.maxDimension * 0.55f
                    drawCircle(
                        Brush.radialGradient(listOf(t.touchGlow.copy(alpha = t.touchGlow.alpha * glow.value), Color.Transparent), touch, r),
                        r, touch,
                    )
                }
            }
            .border(1.dp, Brush.verticalGradient(0f to t.edgeTop, 0.3f to Color.Transparent, 0.75f to Color.Transparent, 1f to t.edgeBottom), shape)
            .then(clickable)
            .padding(contentPadding),
        content = content,
    )
}

/** Neutral canvas with a faint ambient light from the top, the way ColorOS tints with the wallpaper. */
@Composable
fun AmbientBackdrop(modifier: Modifier = Modifier) {
    val t = LocalTones.current
    val accent = LocalAccent.current
    val breathe = rememberInfiniteTransition(label = "breathe")
    val k by breathe.animateFloat(0f, 1f, infiniteRepeatable(tween(9_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "k")
    Canvas(modifier.fillMaxSize().background(t.base)) {
        val w = size.width
        val strength = if (t.dark) 0.16f else 0.12f
        fun bloom(color: Color, center: Offset, radius: Float) = drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = strength), Color.Transparent), center, radius), radius, center,
        )
        bloom(accent, Offset(w * (0.15f + 0.08f * k), -w * 0.1f), w * 0.95f)
        bloom(Fluid.Violet, Offset(w * (0.95f - 0.08f * k), w * 0.05f), w * 0.8f)
    }
}

/** Battery ring. Thick track, solid level colour; a slow breathing halo only while charging. */
@Composable
fun Ring(
    level: Int?,
    colors: Pair<Color, Color>,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 7.dp,
    dimmed: Boolean = false,
    charging: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    val progress by animateFloatAsState(
        (level ?: 0) / 100f,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessVeryLow),
        label = "ring",
    )
    val pulse = rememberInfiniteTransition(label = "pulse")
    val halo by pulse.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(1_400), RepeatMode.Reverse), label = "halo")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2f + sw * 0.5f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(colors.second.copy(alpha = 0.16f), 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
            if (progress > 0f) {
                val a = if (dimmed) 0.42f else 1f
                if (charging && !dimmed) drawArc(
                    colors.second.copy(alpha = 0.28f * halo), -90f, 360f * progress, false, topLeft, arcSize,
                    style = Stroke(sw * 2f, cap = StrokeCap.Round),
                )
                val capDeg = Math.toDegrees((sw / 2f / (arcSize.width / 2f)).toDouble()).toFloat()
                val end = ((360f * progress + capDeg) / 360f).coerceIn(0.02f, 1f)
                rotate(-90f - capDeg) {
                    drawArc(
                        Brush.sweepGradient(0f to colors.first.copy(alpha = a), end to colors.second.copy(alpha = a), 1f to colors.second.copy(alpha = a)),
                        capDeg, 360f * progress, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
            }
        }
        content()
    }
}

/** Bold capsule meter with a glossy sheen. */
@Composable
fun Capsule(level: Int?, colors: Pair<Color, Color>, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val progress by animateFloatAsState(
        (level ?: 0) / 100f,
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessVeryLow),
        label = "capsule",
    )
    Canvas(modifier) {
        val h = size.height
        val r = CornerRadius(h / 2, h / 2)
        drawRoundRect(colors.second.copy(alpha = 0.14f), cornerRadius = r)
        if (progress > 0f) {
            val a = if (dimmed) 0.42f else 1f
            val fw = (size.width * progress).coerceAtLeast(h)
            drawRoundRect(
                Brush.verticalGradient(listOf(colors.first.copy(alpha = a), colors.second.copy(alpha = a))),
                size = Size(fw, h), cornerRadius = r,
            )
            if (!dimmed) drawRoundRect(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), startY = 0f, endY = h * 0.5f),
                topLeft = Offset(h * 0.3f, h * 0.12f), size = Size((fw - h * 0.6f).coerceAtLeast(0f), h * 0.36f), cornerRadius = r,
            )
        }
    }
}

/** "78%" with a smaller percent sign on the same baseline, and a bolt while charging. */
@Composable
fun BigPercent(level: Int?, size: TextUnit, color: Color, modifier: Modifier = Modifier, charging: Boolean = false, weight: FontWeight = FontWeight.Medium) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (charging) {
            Icon(painterResource(R.drawable.ic_bolt), "Charging", Modifier.size((size.value * 0.62f).dp), tint = Fluid.Green)
        }
        Text(
            buildAnnotatedString {
                append(level?.toString() ?: "—")
                if (level != null) withStyle(SpanStyle(fontSize = size * (if (size.value >= 30f) 0.46f else 0.62f), fontWeight = FontWeight.Medium)) { append("%") }
            },
            fontSize = size, fontWeight = weight, color = color, letterSpacing = (-0.02).sp * size.value,
        )
    }
}

/** Circular well holding an icon: ColorOS's fully circular controls. */
@Composable
fun IconWell(@DrawableRes icon: Int, modifier: Modifier = Modifier, size: Dp = 42.dp, tint: Color = LocalTones.current.ink, fill: Color = LocalTones.current.well) {
    Box(modifier.size(size).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, Modifier.size(size * 0.5f), tint = tint)
    }
}

/** Round glass button. */
@Composable
fun CircleButton(@DrawableRes icon: Int, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, iconModifier: Modifier = Modifier) {
    val t = LocalTones.current
    Box(
        modifier
            .size(44.dp)
            .bouncy(onClick = onClick)
            .clip(CircleShape)
            .background(t.card)
            .border(1.dp, Brush.verticalGradient(listOf(t.edgeTop, t.edgeBottom)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription, iconModifier.size(22.dp), tint = t.ink)
    }
}

/** Accent capsule with a glossy top sheen. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Box(
        modifier
            .height(48.dp)
            .bouncy(onClick = onClick, pressedScale = 0.96f)
            .clip(CircleShape)
            .background(accent)
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), endY = size.height * 0.55f))
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

/** ColorOS-style switch: capsule track, white thumb that springs across. */
@Composable
fun FluidSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTones.current
    val accent = LocalAccent.current
    val track by animateColorAsState(if (checked) accent else t.inkFaint.copy(alpha = if (t.dark) 0.35f else 0.22f), label = "track")
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "thumb")
    Box(
        modifier
            .size(width = 50.dp, height = 30.dp)
            .bouncy(onClick = { onCheckedChange(!checked) }, pressedScale = 0.92f)
            .clip(CircleShape)
            .background(track),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(26.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Hairline divider indented to line up with row text. */
@Composable
fun RowDivider(start: Dp = 72.dp) {
    Box(Modifier.padding(start = start, end = 16.dp).fillMaxWidth().height(0.6.dp).background(LocalTones.current.divider))
}
