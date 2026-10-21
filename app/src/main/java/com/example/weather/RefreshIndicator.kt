package com.example.weather

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * A weather-dial refresh indicator.
 *
 * Pulling down lights up a ring of 24 tick marks like a barometer dial, while the current weather icon
 * grows inside a glass badge. At the end of the pull the ring lights fully, the badge pops with a small
 * vibration, and the label changes to "Release to update". While updating, a comet of light circles the
 * dial and the icon breathes (the sun and snowflake turn).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherRefreshIndicator(
    state: PullToRefreshState,
    refreshing: Boolean,
    sky: Sky,
    theme: SkyTheme,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val armed by remember { derivedStateOf { state.distanceFraction >= 1f } }
    LaunchedEffect(armed) { if (armed && !refreshing) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    val inf = rememberInfiniteTransition(label = "refresh")
    val spin by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin")
    val pulse by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val pop by animateFloatAsState(
        if (armed || refreshing) 1.1f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pop",
    )
    val label = when {
        refreshing -> "Updating…"
        armed -> "Release to update"
        else -> "Pull to refresh"
    }
    val tickOff = theme.ink.copy(alpha = 0.25f)
    val tickOn = theme.accent

    Column(
        modifier.graphicsLayer {
            // slides down from above the screen as the finger pulls
            val f = state.distanceFraction.coerceIn(0f, 1.2f)
            translationY = (f - 1f) * 92.dp.toPx()
            alpha = f.coerceIn(0f, 1f)
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(64.dp)
                .graphicsLayer { scaleX = pop; scaleY = pop }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.55f))
                .border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) {
                val n = 24
                val c = center
                val outer = size.minDimension / 2f - 5.dp.toPx()
                val f = state.distanceFraction.coerceIn(0f, 1f)

                // soft glow ring when ready or updating
                if (armed || refreshing) {
                    drawCircle(tickOn.copy(alpha = 0.18f + 0.2f * pulse), outer + 3.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                }
                for (i in 0 until n) {
                    val lit = if (refreshing) {
                        // a comet of light circling the dial
                        val dist = (((spin * n - i) % n) + n) % n
                        (1f - dist / 9f).coerceIn(0f, 1f)
                    } else if (i / n.toFloat() < f) 1f else 0f
                    val len = (3f + 4f * lit).dp.toPx()
                    val a = Math.toRadians((i * 360.0 / n) - 90.0)
                    val dx = cos(a).toFloat()
                    val dy = sin(a).toFloat()
                    drawLine(
                        color = lerp(tickOff, tickOn, lit),
                        start = Offset(c.x + dx * (outer - len), c.y + dy * (outer - len)),
                        end = Offset(c.x + dx * outer, c.y + dy * outer),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
            WeatherIcon(
                sky, 32.dp,
                Modifier.graphicsLayer {
                    val f = state.distanceFraction.coerceIn(0f, 1f)
                    val breathe = if (refreshing) 0.92f + 0.12f * pulse else 1f
                    val s = (0.6f + 0.4f * f) * breathe
                    scaleX = s; scaleY = s
                    rotationZ = when (sky) {
                        Sky.Sunny, Sky.Snow -> if (refreshing) spin * 360f else f * 120f
                        else -> 0f
                    }
                },
            )
        }
        Crossfade(label, label = "refreshLabel") { text ->
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(text, color = Color(0xFF0B2545), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

