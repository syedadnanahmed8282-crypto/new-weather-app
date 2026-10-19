package com.example.weather

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class TimeOfDay { Morning, Noon, Evening, Night }
enum class Sky { Sunny, Partly, Cloudy, Rain, Storm, Windy, Snow }

fun timeOfDay(hour: Int) = when (hour) {
    in 5..10 -> TimeOfDay.Morning
    in 11..15 -> TimeOfDay.Noon
    in 16..18 -> TimeOfDay.Evening
    else -> TimeOfDay.Night
}

// Open-Meteo WMO weather codes. `wind` (km/h) turns a clear or cloudy sky into a windy one.
fun skyOf(code: Int, wind: Double = 0.0): Sky = when {
    code in 95..99 -> Sky.Storm
    code in 71..77 || code == 85 || code == 86 -> Sky.Snow
    code in 51..67 || code in 80..82 -> Sky.Rain
    wind >= 30.0 && code <= 3 -> Sky.Windy
    code == 0 || code == 1 -> Sky.Sunny
    code == 2 -> Sky.Partly
    else -> Sky.Cloudy
}

fun Sky.label(night: Boolean) = when (this) {
    Sky.Sunny -> if (night) "Clear" else "Sunny"
    Sky.Partly -> "Mostly sunny"
    Sky.Cloudy -> "Cloudy"
    Sky.Rain -> "Rain"
    Sky.Storm -> "Thunderstorm"
    Sky.Windy -> "Windy"
    Sky.Snow -> "Snow"
}

data class Orb(val x: Dp, val y: Dp, val w: Dp, val h: Dp, val color: Color, val blur: Dp)

data class SkyTheme(
    val top: Color, val mid: Color, val bottom: Color,
    val ink: Color, val muted: Color,
    val glassAlpha: Float, val borderAlpha: Float,
    val accent: Color,
    val sun: Orb, val cloud1: Orb, val cloud2: Orb,
    val starAlpha: Float,
)

private fun orb(x: Int, y: Int, w: Int, h: Int, argb: Long, alpha: Float, blur: Int) =
    Orb(x.dp, y.dp, w.dp, h.dp, Color(argb).copy(alpha = alpha), blur.dp)

private fun Color.scaleAlpha(f: Float) = copy(alpha = (alpha * f).coerceIn(0f, 1f))
private fun Orb.scaleAlpha(f: Float) = copy(color = color.scaleAlpha(f))

fun themeFor(time: TimeOfDay, sky: Sky): SkyTheme {
    val base = when (time) {
        TimeOfDay.Morning -> SkyTheme(
            Color(0xFFFFE0B0), Color(0xFFFFB7C5), Color(0xFF9CCFFF),
            Color(0xFF2A2346), Color(0xFF5E5578), 0.38f, 0.85f, Color(0xFFD9480F),
            orb(230, 60, 150, 150, 0xFFFFD27A, 0.95f, 12),
            orb(-40, 150, 230, 115, 0xFFFFFFFF, 0.40f, 34),
            orb(170, 250, 190, 95, 0xFFFFFFFF, 0.30f, 34), 0f)
        TimeOfDay.Noon -> SkyTheme(
            Color(0xFF4FB0FF), Color(0xFF8FD0FF), Color(0xFFEAF6FF),
            Color(0xFF0B2545), Color(0xFF35557C), 0.38f, 0.90f, Color(0xFF1E6FE0),
            orb(220, 20, 140, 140, 0xFFFFF3A0, 0.95f, 12),
            orb(30, 130, 230, 115, 0xFFFFFFFF, 0.55f, 34),
            orb(200, 230, 190, 95, 0xFFFFFFFF, 0.45f, 34), 0f)
        TimeOfDay.Evening -> SkyTheme(
            Color(0xFFFF9A62), Color(0xFFD96BA0), Color(0xFF5A5BC4),
            Color.White, Color(0xFFF6E9FF), 0.20f, 0.55f, Color(0xFFFFD166),
            orb(110, 190, 170, 170, 0xFFFF8A4C, 0.90f, 12),
            orb(200, 120, 230, 115, 0xFFF6B8D8, 0.45f, 34),
            orb(-30, 260, 200, 100, 0xFFFFB199, 0.40f, 34), 0f)
        TimeOfDay.Night -> SkyTheme(
            Color(0xFF0A1030), Color(0xFF1A2A5C), Color(0xFF3B4C94),
            Color.White, Color(0xFFC9D3F5), 0.14f, 0.42f, Color(0xFFFFD166),
            orb(260, 50, 90, 90, 0xFFF4F1DE, 0.95f, 12),
            orb(10, 110, 230, 115, 0xFF5C6BA8, 0.55f, 34),
            orb(170, 200, 200, 100, 0xFF6B7BC0, 0.50f, 34), 0.9f)
    }
    val night = time == TimeOfDay.Night

    // How strongly the weather pulls the sky toward its own color, and which color
    val (amount, tint) = when (sky) {
        Sky.Rain -> 0.40f to (if (night) Color(0xFF1B2440) else Color(0xFF8E9BB0))
        Sky.Cloudy -> 0.25f to (if (night) Color(0xFF1B2440) else Color(0xFF8E9BB0))
        Sky.Storm -> 0.60f to (if (night) Color(0xFF101530) else Color(0xFF4A5278))
        Sky.Snow -> 0.45f to (if (night) Color(0xFF2B3C6E) else Color(0xFFD3E6FA))
        Sky.Windy -> 0.20f to (if (night) Color(0xFF1B3A5C) else Color(0xFF9ADFE9))
        else -> 0f to Color.Transparent
    }
    val sunFactor = when (sky) {
        Sky.Rain -> 0.30f; Sky.Cloudy -> 0.55f; Sky.Storm -> 0.15f; Sky.Snow -> 0.40f; else -> 1f
    }
    val cloudColor: Color? = when (sky) {
        Sky.Storm -> Color(0xFF5B6390)
        Sky.Rain -> Color(0xFFA9B6CF)
        else -> null
    }
    val cloudBoost = when (sky) {
        Sky.Rain, Sky.Cloudy, Sky.Storm -> 1.3f; Sky.Snow -> 1.2f; else -> 1f
    }
    fun Orb.cloud() = scaleAlpha(cloudBoost).let { if (cloudColor != null) it.copy(color = cloudColor.copy(alpha = it.color.alpha)) else it }

    var t = base.copy(
        top = lerp(base.top, tint, amount),
        mid = lerp(base.mid, tint, amount),
        bottom = lerp(base.bottom, tint, amount),
        sun = base.sun.scaleAlpha(sunFactor),
        cloud1 = base.cloud1.cloud(),
        cloud2 = base.cloud2.cloud(),
        starAlpha = when {
            !night -> 0f
            sky == Sky.Sunny || sky == Sky.Partly || sky == Sky.Windy -> 0.9f
            else -> 0.25f
        },
    )
    // A dark daytime thunderstorm needs light text
    if (sky == Sky.Storm && !night) {
        t = t.copy(ink = Color.White, muted = Color(0xFFD5DAF5), glassAlpha = 0.18f, borderAlpha = 0.5f, accent = Color(0xFFFFD166))
    }
    return t
}
