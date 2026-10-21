package com.example.weather

import android.os.Build
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toIntSize

/**
 * The blurred copy of the sky. Set on Android 12+ (API 31+), where cards show a real blur of
 * what is behind them. It is null on older phones, which use the milky frosted look instead.
 */
val LocalBlurBackdrop = staticCompositionLocalOf<GraphicsLayer?> { null }

// Frosted glass.
// Android 12+: each card draws a real blurred copy of the sky (with its rain, clouds and stars)
//   exactly where the card sits, plus a light tint.
// Older Android: a milky tint taken from the sky behind the card, so the scene looks diffused.
// `alpha` above the theme default makes a card thicker still (used for highlighted cards).
// All brushes are built once per card size (drawWithCache), not on every frame.
fun Modifier.glass(t: SkyTheme, radius: Dp = 28.dp, alpha: Float = t.glassAlpha): Modifier = composed {
    val backdrop = LocalBlurBackdrop.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    val boost = alpha - t.glassAlpha
    val dark = t.ink.luminance() > 0.5f
    val real = backdrop != null
    val frost = if (dark) lerp(t.mid, Color.White, 0.22f) else lerp(t.mid, Color.White, 0.8f)
    val a1 = ((if (real) (if (dark) 0.30f else 0.34f) else (if (dark) 0.62f else 0.66f)) + boost).coerceIn(0f, 0.9f)
    val a2 = ((if (real) (if (dark) 0.20f else 0.24f) else (if (dark) 0.46f else 0.50f)) + boost).coerceIn(0f, 0.9f)
    val glowAlpha = if (dark) 0.18f else 0.40f
    val edgeA = (t.borderAlpha + 0.1f).coerceAtMost(1f)
    val edgeB = t.borderAlpha * 0.25f

    // Only track where the card is when there is a blurred backdrop to line up with
    val track = if (backdrop != null) Modifier.onGloballyPositioned { pos = it.positionInRoot() } else Modifier

    track
        .clip(RoundedCornerShape(radius))
        .drawWithCache {
            val fill = Brush.linearGradient(listOf(lerp(frost, Color.White, 0.3f).copy(alpha = a1), frost.copy(alpha = a2)))
            val glow = Brush.radialGradient(listOf(Color.White.copy(alpha = glowAlpha), Color.Transparent), Offset.Zero, size.width * 0.9f)
            val edge = Brush.linearGradient(listOf(Color.White.copy(alpha = edgeA), Color.White.copy(alpha = edgeB)))
            val sw = 2.dp.toPx()
            val corner = CornerRadius((radius.toPx() - sw / 2f).coerceAtLeast(0f))
            onDrawWithContent {
                if (backdrop != null) translate(-pos.x, -pos.y) { drawLayer(backdrop) }
                drawRect(fill)
                drawRect(glow)
                drawContent()
                drawRoundRect(edge, Offset(sw / 2f, sw / 2f), Size(size.width - sw, size.height - sw), corner, style = Stroke(sw))
            }
        }
}

/**
 * The living sky: gradient, sun or moon, slowly drifting clouds, then the weather itself
 * (rain, snow, wind, dust, stars) and lightning. Everything cross-fades when the theme changes.
 */
@Composable
fun SkyBackground(theme: SkyTheme, sky: Sky, blurLayer: GraphicsLayer?) {
    val top by animateColorAsState(theme.top, tween(1200), label = "top")
    val mid by animateColorAsState(theme.mid, tween(1200), label = "mid")
    val bottom by animateColorAsState(theme.bottom, tween(1200), label = "bottom")

    // These two are passed down as State and only read while drawing, so the screen is not rebuilt every frame
    val inf = rememberInfiniteTransition(label = "drift")
    val d1 = inf.animateFloat(-16f, 16f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d1")
    val d2 = inf.animateFloat(14f, -14f, infiniteRepeatable(tween(12000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d2")

    // On Android 12+ the sky is also recorded into a layer that the glass cards blur and draw
    val skyLayer = rememberGraphicsLayer()
    val capture = if (blurLayer != null) {
        Modifier.drawWithContent {
            val sz = size.toIntSize()
            skyLayer.record(this, layoutDirection, sz) { this@drawWithContent.drawContent() }
            drawLayer(skyLayer)
            blurLayer.record(this, layoutDirection, sz) { drawLayer(skyLayer) }
        }
    } else Modifier

    Box(Modifier.fillMaxSize().then(capture).background(Brush.verticalGradient(listOf(top, mid, bottom)))) {
        AnimatedOrb(theme.cloud1, d1, soft = true)
        AnimatedOrb(theme.cloud2, d2, soft = true)
        AnimatedOrb(theme.sun, null, soft = false)
        WeatherParticles(sky, theme.starAlpha)
        LightningFlash(active = sky == Sky.Storm)
    }
}

// Soft glowing sun, moon and clouds.
// Android 12+ (API 31): real blur. Older phones: a fading radial gradient that looks blurred too.
@Composable
private fun AnimatedOrb(o: Orb, drift: State<Float>?, soft: Boolean) {
    val x by animateDpAsState(o.x, tween(1200), label = "x")
    val y by animateDpAsState(o.y, tween(1200), label = "y")
    val w by animateDpAsState(o.w, tween(1200), label = "w")
    val h by animateDpAsState(o.h, tween(1200), label = "h")
    val color by animateColorAsState(o.color, tween(1200), label = "c")
    val place = Modifier
        .offset(x, y)
        .size(w, h)
        .graphicsLayer { translationX = (drift?.value ?: 0f) * density }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Box(place.blur(o.blur, BlurredEdgeTreatment.Unbounded).background(color, CircleShape))
    } else {
        Box(
            place.drawWithCache {
                val center = Offset(size.width / 2f, size.height / 2f)
                val r = size.width / 2f * 1.35f
                val stops = if (soft) {
                    arrayOf(0f to color, 0.45f to color.copy(alpha = color.alpha * 0.7f), 1f to Color.Transparent)
                } else {
                    arrayOf(0f to color, 0.6f to color, 1f to Color.Transparent)
                }
                val brush = Brush.radialGradient(colorStops = stops, center = center, radius = r)
                onDrawBehind {
                    scale(1f, size.height / size.width, center) { drawCircle(brush, r, center) }
                }
            }
        )
    }
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
