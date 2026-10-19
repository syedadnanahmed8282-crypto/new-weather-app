package com.example.weather

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
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

private val STARS = listOf(40f to 60f, 110f to 28f, 200f to 92f, 330f to 40f, 300f to 132f, 70f to 140f)

/** Animated sky: gradient + sun/moon + clouds + stars. Everything cross-fades when the theme changes. */
@Composable
fun SkyBackground(theme: SkyTheme) {
    val top by animateColorAsState(theme.top, tween(1200), label = "top")
    val mid by animateColorAsState(theme.mid, tween(1200), label = "mid")
    val bottom by animateColorAsState(theme.bottom, tween(1200), label = "bottom")
    val starAlpha by animateFloatAsState(theme.starAlpha, tween(1200), label = "stars")

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, mid, bottom)))) {
        AnimatedOrb(theme.cloud1)
        AnimatedOrb(theme.cloud2)
        AnimatedOrb(theme.sun)
        Canvas(Modifier.fillMaxSize()) {
            STARS.forEach { (x, y) ->
                drawCircle(Color.White.copy(alpha = starAlpha), 2.5.dp.toPx(), Offset(x.dp.toPx(), y.dp.toPx()))
            }
        }
    }
}

@Composable
private fun AnimatedOrb(o: Orb) {
    val x by animateDpAsState(o.x, tween(1200), label = "x")
    val y by animateDpAsState(o.y, tween(1200), label = "y")
    val w by animateDpAsState(o.w, tween(1200), label = "w")
    val h by animateDpAsState(o.h, tween(1200), label = "h")
    val color by animateColorAsState(o.color, tween(1200), label = "c")
    // blur() needs Android 12+ (API 31); on older versions the orbs are simply sharp.
    Box(
        Modifier.offset(x, y).size(w, h)
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
