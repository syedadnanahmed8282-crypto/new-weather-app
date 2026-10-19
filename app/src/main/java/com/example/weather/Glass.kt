package com.example.weather

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Translucent "glass" surface: soft white fill + light edge highlight.
fun Modifier.glass(t: SkyTheme, radius: Dp = 28.dp, alpha: Float = t.glassAlpha): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(Color.White.copy(alpha = alpha))
        .border(
            1.5.dp,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = t.borderAlpha), Color.White.copy(alpha = t.borderAlpha * 0.3f))
            ),
            shape,
        )
}

/**
 * The living sky: gradient, sun or moon, slowly drifting clouds, then the weather itself
 * (rain, snow, wind, dust, stars) and lightning. Everything cross-fades when the theme changes.
 */
@Composable
fun SkyBackground(theme: SkyTheme, sky: Sky) {
    val top by animateColorAsState(theme.top, tween(1200), label = "top")
    val mid by animateColorAsState(theme.mid, tween(1200), label = "mid")
    val bottom by animateColorAsState(theme.bottom, tween(1200), label = "bottom")

    val inf = rememberInfiniteTransition(label = "drift")
    val d1 by inf.animateFloat(-16f, 16f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d1")
    val d2 by inf.animateFloat(14f, -14f, infiniteRepeatable(tween(12000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d2")

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, mid, bottom)))) {
        AnimatedOrb(theme.cloud1, d1)
        AnimatedOrb(theme.cloud2, d2)
        AnimatedOrb(theme.sun, 0f)
        WeatherParticles(sky, theme.starAlpha)
        LightningFlash(active = sky == Sky.Storm)
    }
}

@Composable
private fun AnimatedOrb(o: Orb, drift: Float) {
    val x by animateDpAsState(o.x, tween(1200), label = "x")
    val y by animateDpAsState(o.y, tween(1200), label = "y")
    val w by animateDpAsState(o.w, tween(1200), label = "w")
    val h by animateDpAsState(o.h, tween(1200), label = "h")
    val color by animateColorAsState(o.color, tween(1200), label = "c")
    // blur() needs Android 12+ (API 31); on older versions the orbs are simply sharp.
    Box(
        Modifier.offset(x + drift.dp, y).size(w, h)
            .blur(o.blur, BlurredEdgeTreatment.Unbounded)
            .background(color, CircleShape)
    )
}

@Composable
fun GlassTabBar(selected: Int, onSelect: (Int) -> Unit, theme: SkyTheme, modifier: Modifier = Modifier) {
    val labels = listOf("Today", "Cities", "Alerts")
    Row(
        modifier.fillMaxWidth().glass(theme, 34.dp, theme.glassAlpha + 0.08f).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            val weight by animateFloatAsState(if (on) 2f else 1f, label = "tabWeight")
            Row(
                Modifier.weight(weight).height(48.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (on) Color.White.copy(alpha = 0.5f) else Color.Transparent)
                    .clickable { onSelect(i) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                when (i) {
                    0 -> WeatherIcon(Sky.Partly, 28.dp)
                    1 -> PinIcon(24.dp, Color(0xFFFF5C7A))
                    else -> BellIcon(24.dp)
                }
                if (on) {
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Color(0xFF0B2545), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
