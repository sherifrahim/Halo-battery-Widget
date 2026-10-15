package com.halo.btbattery.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.halo.btbattery.ui.theme.Fluid
import com.halo.btbattery.ui.theme.LocalTones
import kotlin.math.min

/**
 * Continuous-curvature ("G2") rounded rectangle. The curve starts further along each edge and
 * eases in, so corners melt into the sides instead of meeting them at a visible seam.
 */
class SmoothShape(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = min(with(density) { radius.toPx() }, min(size.width, size.height) / 2f)
        val s = min(r * 1.28f, min(size.width, size.height) / 2f) // where the curve begins
        val c = s * 0.16f                                          // control handle near the corner
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

/** Squash on press, spring back with a little overshoot on release. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bouncy(
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)?,
): Modifier = composed {
    if (onClick == null && onLongClick == null) return@composed this
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = source,
            indication = null,
            onLongClick = onLongClick,
            onClick = { onClick?.invoke() },
        )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    strong: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTones.current
    val shape = SmoothShape(radius)
    Column(
        modifier
            .bouncy(onLongClick, onClick)
            .clip(shape)
            .background(if (strong) t.glassStrong else t.glass)
            .border(1.dp, Brush.verticalGradient(listOf(t.edgeTop, t.edgeBottom)), shape)
            .padding(18.dp),
        content = content,
    )
}

/** Slow-drifting colour blooms behind everything: the "living" backdrop. */
@Composable
fun FluidBackdrop(modifier: Modifier = Modifier) {
    val t = LocalTones.current
    val drift = rememberInfiniteTransition(label = "drift")
    val a by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(19_000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    Canvas(modifier.fillMaxSize().background(t.base)) {
        val w = size.width
        val h = size.height
        fun orb(color: Color, center: Offset, radius: Float) = drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = t.orbStrength), Color.Transparent), center, radius),
            radius, center,
        )
        orb(Fluid.Mint, Offset(w * (0.1f + 0.25f * a), h * (0.08f + 0.06f * b)), w * 0.85f)
        orb(Fluid.Aqua, Offset(w * (0.95f - 0.2f * b), h * (0.32f + 0.1f * a)), w * 0.75f)
        orb(Fluid.Violet, Offset(w * (0.2f + 0.3f * b), h * (0.85f - 0.08f * a)), w * 0.8f)
    }
}

/** Battery ring with a glowing gradient arc that springs to its value. */
@Composable
fun Ring(
    level: Int?,
    colors: Pair<Color, Color>,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 7.dp,
    dimmed: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    val target = (level ?: 0) / 100f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow),
        label = "ring",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val inset = sw / 2f + sw * 0.6f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(colors.first.copy(alpha = 0.18f), 0f, 360f, false, topLeft, arcSize, style = Stroke(sw))
            if (progress > 0f) {
                val alpha = if (dimmed) 0.45f else 1f
                // Glow: a wider, faint arc under the main one.
                drawArc(
                    colors.second.copy(alpha = 0.22f * alpha), -90f, 360f * progress, false, topLeft, arcSize,
                    style = Stroke(sw * 2.1f, cap = StrokeCap.Round),
                )
                // Start the gradient a cap-width before the arc so the round cap keeps the start colour.
                val radius = arcSize.width / 2f
                val capDeg = Math.toDegrees((sw / 2f / radius).toDouble()).toFloat()
                rotate(-90f - capDeg) {
                    val c1 = colors.first.copy(alpha = alpha)
                    val c2 = colors.second.copy(alpha = alpha)
                    val end = ((360f * progress + capDeg) / 360f).coerceIn(0.01f, 1f)
                    drawArc(
                        Brush.sweepGradient(0f to c1, end to c2, 1f to c2),
                        capDeg, 360f * progress, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
            }
        }
        content()
    }
}

/** Horizontal capsule meter. */
@Composable
fun Capsule(level: Int?, colors: Pair<Color, Color>, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val progress by animateFloatAsState(
        targetValue = (level ?: 0) / 100f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow),
        label = "capsule",
    )
    Canvas(modifier) {
        val h = size.height
        val r = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2)
        drawRoundRect(colors.first.copy(alpha = 0.16f), cornerRadius = r)
        if (progress > 0f) {
            val a = if (dimmed) 0.45f else 1f
            val fw = (size.width * progress).coerceAtLeast(h)
            drawRoundRect(
                Brush.horizontalGradient(listOf(colors.first.copy(alpha = a), colors.second.copy(alpha = a)), endX = fw),
                size = Size(fw, h), cornerRadius = r,
            )
        }
    }
}
