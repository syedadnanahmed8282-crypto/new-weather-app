package com.example.weather

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

private enum class CharKind { Sun, Moon, Cloud, Rain, Storm, Wind, Snow }

private fun kindFor(sky: Sky, night: Boolean) = when (sky) {
    Sky.Snow -> CharKind.Snow
    Sky.Storm -> CharKind.Storm
    Sky.Rain -> CharKind.Rain
    Sky.Windy -> CharKind.Wind
    Sky.Cloudy -> CharKind.Cloud
    Sky.Sunny, Sky.Partly -> if (night) CharKind.Moon else CharKind.Sun
}

private val Ink = Color(0xFF2B2140)
private val MouthC = Color(0xFF7A2338)
private val Tongue = Color(0xFFFF7B95)
private val Blush = Color(0xFFFF7F9C)
private val Brown = Color(0xFF8B5A2B)

private class Anim(
    val spin: Float, val pulse: Float, val talk: Float, val t: Float, val t3: Float,
    val bolt: Float, val sway: Float, val blink: Float, val ooh: Boolean,
)

private fun stroke(w: Float) = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)

/**
 * A cute animated weather character. Everything moves on its own (bobbing, blinking, talking,
 * spinning rays, falling drops, flickering lightning...). Tap it and it jumps and says "ooh".
 * The drawing uses a 200 x 200 design grid that is scaled to [size].
 */
@Composable
fun WeatherCharacter(sky: Sky, night: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val kind = kindFor(sky, night)
    val inf = rememberInfiniteTransition(label = "character")
    val bob by inf.animateFloat(-5f, 5f, infiniteRepeatable(tween(2100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val blink by inf.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 4200; 1f at 0; 1f at 3860; 0.1f at 3990; 1f at 4120 }),
        label = "blink",
    )
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "spin")
    val pulse by inf.animateFloat(1f, 1.04f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val talk by inf.animateFloat(0.88f, 1.12f, infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "talk")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "t")
    val t3 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "t3")
    val bolt by inf.animateFloat(
        0f, 0f,
        infiniteRepeatable(keyframes { durationMillis = 4500; 0f at 0; 0f at 3000; 1f at 3050; 0.2f at 3150; 1f at 3250; 0f at 3400 }),
        label = "bolt",
    )
    val sway by inf.animateFloat(-4f, 4f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")

    val bounce = remember { Animatable(1f) }
    var ooh by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val label = sky.label(night)

    Canvas(
        modifier
            .size(size)
            .semantics { contentDescription = label }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                scope.launch {
                    ooh = true
                    bounce.animateTo(1.2f, tween(110))
                    bounce.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 300f))
                    delay(700)
                    ooh = false
                }
            },
    ) {
        val k = this.size.minDimension / 200f
        val a = Anim(spin, pulse, talk, t, t3, bolt, sway, blink, ooh)
        val b = bounce.value
        withTransform({
            scale(2f - b, b, Offset(this@Canvas.size.width / 2f, this@Canvas.size.height * 0.95f))
            translate(0f, bob * k)
            scale(k, k, Offset.Zero)
        }) {
            when (kind) {
                CharKind.Sun -> drawSun(a)
                CharKind.Moon -> drawMoon(a)
                CharKind.Cloud -> drawCloudy(a)
                CharKind.Rain -> drawRainy(a)
                CharKind.Storm -> drawStormy(a)
                CharKind.Wind -> drawWindy(a)
                CharKind.Snow -> drawSnowman(a)
            }
        }
    }
}

// ---------------------------------------------------------------- shared pieces

private fun DrawScope.shadow(cx: Float, cy: Float, rx: Float) =
    drawOval(Color.Black.copy(alpha = 0.1f), Offset(cx - rx, cy - 6f), Size(rx * 2, 12f))

private fun DrawScope.gloss(cx: Float, cy: Float, rx: Float, ry: Float, deg: Float, a: Float) =
    rotate(deg, Offset(cx, cy)) { drawOval(Color.White.copy(alpha = a), Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2)) }

// Gradients are built once and reused. Creating them on every frame was the main cost of the animation.
private val sphereBrushes = HashMap<String, Brush>()
private val cloudBrushes = HashMap<String, Brush>()
private val RayBrush = Brush.verticalGradient(listOf(Color(0xFFFFE066), Color(0xFFFF9F1C)), 7f, 36f)
private val BoltBrush = Brush.verticalGradient(listOf(Color(0xFFFFF27A), Color(0xFFFFAE00)), 140f, 200f)
private val DropBrush = Brush.verticalGradient(listOf(Color(0xFFA9D9FF), Color(0xFF2F8CF0)), -9f, 8f)
private val ScarfBrush = Brush.verticalGradient(listOf(Color(0xFF3FE0CF), Color(0xFF0F9F93)), 108f, 148f)
private val ScarfTailBrush = Brush.verticalGradient(listOf(Color(0xFF3FE0CF), Color(0xFF0F9F93)), 108f, 148f)
private val NoseBrush = Brush.horizontalGradient(listOf(Color(0xFFFFC15A), Color(0xFFFF7A00)), 100f, 128f)
private val HatBrush = Brush.verticalGradient(listOf(Color(0xFFFF8FB7), Color(0xFFE03D7C)), 40f, 66f)
private val CapBrush = Brush.verticalGradient(listOf(Color(0xFF8D7DFF), Color(0xFF4A3FD4)), 24f, 66f)

private fun DrawScope.sphere(c: Offset, r: Float, light: Color, dark: Color) {
    val brush = sphereBrushes.getOrPut("${c.x},${c.y},$r,${light.value},${dark.value}") {
        Brush.radialGradient(listOf(light, dark), Offset(c.x - r * 0.3f, c.y - r * 0.35f), r * 1.5f)
    }
    drawCircle(brush, r, c)
}

private fun cloudBrush(i: Int, top: Color, bottom: Color): Brush =
    cloudBrushes.getOrPut("$i,${top.value},${bottom.value}") { Brush.verticalGradient(listOf(top, bottom), 56f, 152f) }

private fun DrawScope.line(ax: Float, ay: Float, bx: Float, by: Float, w: Float, c: Color = Ink) =
    drawLine(c, Offset(ax, ay), Offset(bx, by), w, StrokeCap.Round)

private fun DrawScope.sparkle(x: Float, y: Float, s: Float, c: Color = Color.White, a: Float = 1f) {
    val p = Path().apply {
        moveTo(x, y - 7f * s); lineTo(x + 2f * s, y - 2f * s); lineTo(x + 7f * s, y); lineTo(x + 2f * s, y + 2f * s)
        lineTo(x, y + 7f * s); lineTo(x - 2f * s, y + 2f * s); lineTo(x - 7f * s, y); lineTo(x - 2f * s, y - 2f * s); close()
    }
    drawPath(p, c, alpha = a)
}

private fun twinkle(t: Float, offset: Float) = 0.55f + 0.45f * sin((t + offset) * 6.2832f)

private fun DrawScope.zShape(x: Float, y: Float, s: Float, alpha: Float) {
    val p = Path().apply { moveTo(x, y); lineTo(x + 12f * s, y); lineTo(x, y + 12f * s); lineTo(x + 12f * s, y + 12f * s) }
    drawPath(p, Color.White.copy(alpha = alpha), style = stroke(3f))
}

private fun DrawScope.zzz(x: Float, y: Float, t: Float) {
    for (i in 0..1) {
        val ph = (t + i * 0.5f) % 1f
        zShape(x + ph * 14f, y - ph * 26f, 1f - i * 0.3f, sin(ph * 3.1416f))
    }
}

// ---------------------------------------------------------------- faces

private fun DrawScope.eye(x: Float, y: Float, s: Float, blink: Float) {
    scale(1f, blink, Offset(x, y)) {
        drawOval(Ink, Offset(x - 7.5f * s, y - 10f * s), Size(15f * s, 20f * s))
        drawCircle(Color.White, 3.5f * s, Offset(x - 2.4f * s, y - 3.8f * s))
        drawCircle(Color.White, 1.7f * s, Offset(x + 2.8f * s, y + 3.4f * s))
    }
}

private fun DrawScope.blush(cx: Float, y: Float, g: Float, s: Float = 1f, a: Float = 0.7f) {
    val c = Blush.copy(alpha = a)
    drawOval(c, Offset(cx - g - 13f - 9f * s, y + 13f - 5.5f * s), Size(18f * s, 11f * s))
    drawOval(c, Offset(cx + g + 13f - 9f * s, y + 13f - 5.5f * s), Size(18f * s, 11f * s))
}

private fun DrawScope.closedEyes(cx: Float, y: Float, g: Float) {
    val p = Path().apply {
        moveTo(cx - g - 8f, y); quadraticBezierTo(cx - g, y + 7f, cx - g + 8f, y)
        moveTo(cx + g - 8f, y); quadraticBezierTo(cx + g, y + 7f, cx + g + 8f, y)
    }
    drawPath(p, Ink, style = stroke(4f))
}

private fun DrawScope.faceWow(cx: Float, y: Float, g: Float) {
    eye(cx - g, y, 1.3f, 1f); eye(cx + g, y, 1.3f, 1f); blush(cx, y, g)
    drawOval(MouthC, Offset(cx - 6f, y + 14f), Size(12f, 16f))
    drawOval(Ink, Offset(cx - 6f, y + 14f), Size(12f, 16f), style = stroke(3f))
}

private fun DrawScope.face(kind: CharKind, cx: Float, y: Float, g: Float, a: Anim) {
    if (a.ooh) { faceWow(cx, y, g); return }
    when (kind) {
        CharKind.Sun -> {
            eye(cx - g, y, 1f, a.blink); eye(cx + g, y, 1f, a.blink); blush(cx, y, g)
            val h = 20f * a.talk
            val m = Path().apply { moveTo(cx - 13f, y + 12f); quadraticBezierTo(cx, y + 12f + h, cx + 13f, y + 12f); close() }
            drawPath(m, MouthC); drawPath(m, Ink, style = stroke(3f))
            drawOval(Tongue, Offset(cx - 6.5f, y + 12f + h * 0.38f - 3.4f), Size(13f, 6.8f))
        }
        CharKind.Cloud -> {                       // yawning
            closedEyes(cx, y, g); blush(cx, y, g)
            val h = 17f * a.talk
            drawOval(MouthC, Offset(cx - 6.5f, y + 21f - h / 2), Size(13f, h))
            drawOval(Ink, Offset(cx - 6.5f, y + 21f - h / 2), Size(13f, h), style = stroke(3f))
            drawOval(Tongue, Offset(cx - 3.8f, y + 21f + h / 2 - 6f), Size(7.6f, 5f))
        }
        CharKind.Rain -> {                        // sad, teary
            eye(cx - g, y, 1.1f, a.blink); eye(cx + g, y, 1.1f, a.blink)
            line(cx - g - 10f, y - 13f, cx - g + 3f, y - 19f, 3.5f); line(cx + g + 10f, y - 13f, cx + g - 3f, y - 19f, 3.5f)
            blush(cx, y, g, 0.9f, 0.5f)
            val fr = Path().apply { moveTo(cx - 8f, y + 24f); quadraticBezierTo(cx, y + 15f, cx + 8f, y + 24f) }
            drawPath(fr, Ink, style = stroke(4f))
            val tear = Path().apply {
                moveTo(cx - g - 2f, y + 12f); quadraticBezierTo(cx - g + 3f, y + 20f, cx - g - 2f, y + 23f)
                quadraticBezierTo(cx - g - 7f, y + 20f, cx - g - 2f, y + 12f)
            }
            drawPath(tear, Color(0xFF8CCAFF)); drawPath(tear, Color(0xFF4AA3F0), style = Stroke(1.5f))
        }
        CharKind.Storm -> {                       // angry, shouting
            eye(cx - g, y + 1f, 0.85f, a.blink); eye(cx + g, y + 1f, 0.85f, a.blink)
            line(cx - g - 10f, y - 14f, cx - g + 5f, y - 6f, 5f); line(cx + g + 10f, y - 14f, cx + g - 5f, y - 6f, 5f)
            blush(cx, y, g, 0.9f, 0.5f)
            val m = Path().apply { moveTo(cx - 12f, y + 14f); lineTo(cx + 12f, y + 14f); lineTo(cx + 8f, y + 29f); lineTo(cx - 8f, y + 29f); close() }
            drawPath(m, MouthC); drawPath(m, Ink, style = stroke(3f))
            drawRoundRect(Color.White, Offset(cx - 9f, y + 15f), Size(18f, 5f), CornerRadius(1.5f))
        }
        CharKind.Wind -> {                        // puffed cheeks, blowing
            val p = Path().apply {
                moveTo(cx - g - 7f, y - 7f); lineTo(cx - g + 4f, y); lineTo(cx - g - 7f, y + 7f)
                moveTo(cx + g + 7f, y - 7f); lineTo(cx + g - 4f, y); lineTo(cx + g + 7f, y + 7f)
            }
            drawPath(p, Ink, style = stroke(4f))
            val r = 11f * a.talk
            drawCircle(Color(0xFFFF9FB4).copy(alpha = 0.75f), r, Offset(cx - g - 14f, y + 12f))
            drawCircle(Color(0xFFFF9FB4).copy(alpha = 0.75f), r, Offset(cx + g + 14f, y + 12f))
            drawCircle(MouthC, 5.5f, Offset(cx + 4f, y + 17f)); drawCircle(Ink, 5.5f, Offset(cx + 4f, y + 17f), style = stroke(3f))
        }
        CharKind.Moon -> {                        // sleeping with a snore bubble
            closedEyes(cx, y, g); blush(cx, y, g)
            val sm = Path().apply { moveTo(cx - 5f, y + 15f); quadraticBezierTo(cx, y + 19f, cx + 5f, y + 15f) }
            drawPath(sm, Ink, style = stroke(4f))
            drawCircle(Color(0xFFBFE4FF).copy(alpha = 0.85f), 6f * a.talk, Offset(cx + 14f, y + 18f))
            drawCircle(Color(0xFF8CC8FF), 6f * a.talk, Offset(cx + 14f, y + 18f), style = Stroke(2f))
        }
        CharKind.Snow -> Unit
    }
}

// ---------------------------------------------------------------- characters

private fun DrawScope.drawSun(a: Anim) {
    shadow(100f, 190f, 48f)
    val c = Offset(100f, 100f)
    rotate(a.spin, c) {
        for (i in 0 until 12) rotate(i * 30f, c) {
            drawRoundRect(RayBrush, Offset(94f, 7f), Size(12f, 29f), CornerRadius(6f))
        }
    }
    scale(a.pulse, c) {
        sphere(c, 57f, Color(0xFFFFF8B8), Color(0xFFFF9A1A))
        drawCircle(Color.White.copy(alpha = 0.35f), 54f, c, style = Stroke(3f))
        gloss(76f, 68f, 17f, 9f, -35f, 0.6f)
        gloss(128f, 140f, 22f, 9f, -30f, 0f)
    }
    face(CharKind.Sun, 100f, 98f, 20f, a)
    sparkle(160f, 40f, 0.9f, a = twinkle(a.t3, 0f))
    sparkle(36f, 150f, 0.7f, a = twinkle(a.t3, 0.4f))
}

private fun DrawScope.cloudBody(top: Color, bottom: Color, shade: Color) {
    shadow(103f, 180f, 46f)
    drawCircle(cloudBrush(0, top, bottom), 32f, Offset(64f, 118f))
    drawCircle(cloudBrush(1, top, bottom), 42f, Offset(102f, 96f))
    drawCircle(cloudBrush(2, top, bottom), 28f, Offset(142f, 120f))
    drawRoundRect(cloudBrush(3, top, bottom), Offset(64f, 118f), Size(78f, 32f), CornerRadius(14f))
    drawRoundRect(shade.copy(alpha = 0.22f), Offset(64f, 136f), Size(78f, 14f), CornerRadius(7f))
    gloss(88f, 70f, 17f, 9f, -28f, 0.6f); gloss(46f, 106f, 8f, 4.5f, -30f, 0.5f); gloss(146f, 104f, 6f, 3.5f, -30f, 0.4f)
}

private fun DrawScope.drawCloudy(a: Anim) {
    cloudBody(Color.White, Color(0xFFC9DBF3), Color(0xFF6F90C4))
    face(CharKind.Cloud, 103f, 108f, 17f, a)
    zzz(150f, 60f, a.t3)
}

private fun DrawScope.drop(x: Float, y: Float, alpha: Float) {
    translate(x, y) {
        val p = Path().apply {
            moveTo(0f, -9f); quadraticBezierTo(8f, 2f, 0f, 8f); quadraticBezierTo(-8f, 2f, 0f, -9f); close()
        }
        drawPath(p, DropBrush, alpha = alpha)
        drawOval(Color.White.copy(alpha = 0.7f * alpha), Offset(-3.8f, -4f), Size(3.6f, 6f))
    }
}

private fun DrawScope.drawRainy(a: Anim) {
    cloudBody(Color(0xFFDDE8F9), Color(0xFF9BB4DA), Color(0xFF5F7FB5))
    face(CharKind.Rain, 103f, 106f, 17f, a)
    val xs = floatArrayOf(70f, 104f, 138f)
    for (i in 0..2) {
        val ph = (a.t + i * 0.33f) % 1f
        drop(xs[i], 160f + ph * 34f, 1f - ph)
    }
}

private fun DrawScope.drawStormy(a: Anim) {
    val jitter = if (a.bolt > 0.5f) 2f else 0f
    translate(jitter, 0f) {
        cloudBody(Color(0xFF9096BE), Color(0xFF4C527D), Color(0xFF2A2F55))
        face(CharKind.Storm, 103f, 112f, 17f, a)
    }
    val bolt = Path().apply {
        moveTo(108f, 140f); lineTo(86f, 172f); lineTo(102f, 172f); lineTo(92f, 200f)
        lineTo(128f, 160f); lineTo(111f, 160f); lineTo(122f, 140f); close()
    }
    val al = 0.55f + 0.45f * a.bolt
    drawPath(bolt, BoltBrush, alpha = al)
    drawPath(bolt, Color(0xFFFF9800), alpha = al, style = stroke(3f))
    line(104f, 150f, 94f, 166f, 3f, Color.White.copy(alpha = 0.7f * al))
    sparkle(150f, 150f, 0.7f, Color(0xFFFFE680), a.bolt)
    sparkle(52f, 158f, 0.6f, Color(0xFFFFE680), a.bolt)
}

private fun DrawScope.drawWindy(a: Anim) {
    rotate(a.sway, Offset(100f, 150f)) {
        cloudBody(Color.White, Color(0xFFC6E6EE), Color(0xFF6AA9BD))
        face(CharKind.Wind, 103f, 110f, 17f, a)
    }
    val dash = PathEffect.dashPathEffect(floatArrayOf(60f, 40f), -a.t3 * 100f)
    val s = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = dash)
    val p1 = Path().apply { moveTo(152f, 106f); lineTo(182f, 106f); quadraticBezierTo(194f, 106f, 194f, 96f); quadraticBezierTo(194f, 87f, 185f, 86f) }
    val p2 = Path().apply { moveTo(148f, 128f); lineTo(188f, 128f); quadraticBezierTo(200f, 128f, 200f, 138f); quadraticBezierTo(200f, 147f, 190f, 148f) }
    val p3 = Path().apply { moveTo(160f, 84f); lineTo(184f, 84f); quadraticBezierTo(193f, 84f, 193f, 76f) }
    drawPath(p1, Color.White, style = s); drawPath(p2, Color.White, style = s); drawPath(p3, Color.White, style = s)
}

private fun DrawScope.drawSnowman(a: Anim) {
    shadow(100f, 192f, 54f)
    rotate(a.sway * 0.7f, Offset(100f, 190f)) {
        val wave = a.sway * 2f
        rotate(-wave, Offset(60f, 142f)) {
            line(60f, 142f, 30f, 124f, 5f, Brown); line(30f, 124f, 21f, 115f, 5f, Brown); line(30f, 124f, 24f, 135f, 5f, Brown)
        }
        rotate(wave, Offset(140f, 142f)) {
            line(140f, 142f, 170f, 124f, 5f, Brown); line(170f, 124f, 179f, 115f, 5f, Brown); line(170f, 124f, 176f, 135f, 5f, Brown)
        }
        val ice = Color(0xFFB5D0EE)
        sphere(Offset(100f, 148f), 46f, Color.White, ice)
        gloss(80f, 128f, 14f, 8f, -30f, 0.65f)
        drawCircle(Color(0xFF3A3358), 4f, Offset(100f, 146f)); drawCircle(Color(0xFF3A3358), 4f, Offset(100f, 162f))
        sphere(Offset(100f, 86f), 36f, Color.White, ice)
        gloss(84f, 68f, 11f, 6f, -30f, 0.7f)
        drawRoundRect(ScarfBrush, Offset(64f, 108f), Size(72f, 15f), CornerRadius(7.5f))
        drawRoundRect(ScarfTailBrush, Offset(112f, 116f), Size(15f, 32f), CornerRadius(7f))
        line(118f, 126f, 127f, 126f, 3f, Color.White.copy(alpha = 0.55f)); line(118f, 136f, 127f, 136f, 3f, Color.White.copy(alpha = 0.55f))
        val s = if (a.ooh) 1.2f else 0.9f
        eye(87f, 84f, s, a.blink); eye(113f, 84f, s, a.blink)
        val nose = Path().apply { moveTo(100f, 92f); lineTo(128f, 99f); lineTo(100f, 104f); close() }
        drawPath(nose, NoseBrush)
        if (a.ooh) {
            drawOval(MouthC, Offset(95f, 105f), Size(10f, 12f))
        } else {
            val sm = Path().apply { moveTo(92f, 106f); quadraticBezierTo(101f, 114f, 110f, 106f) }
            drawPath(sm, Ink, style = stroke(3f))
        }
        blush(100f, 86f, 13f, 0.8f, 0.75f)
        val hat = Path().apply { moveTo(66f, 66f); quadraticBezierTo(100f, 16f, 134f, 66f); close() }
        drawPath(hat, HatBrush)
        drawRoundRect(Color.White, Offset(62f, 61f), Size(76f, 13f), CornerRadius(6.5f))
        drawCircle(Color.White, 9f, Offset(100f + a.sway, 36f + (a.talk - 1f) * 10f))
    }
    sparkle(150f, 60f, 0.8f, a = twinkle(a.t3, 0f))
    sparkle(46f, 70f, 0.7f, Color(0xFFFFD1E6), twinkle(a.t3, 0.3f))
    sparkle(160f, 120f, 0.6f, a = twinkle(a.t3, 0.6f))
}

private fun DrawScope.drawMoon(a: Anim) {
    shadow(100f, 190f, 46f)
    val c = Offset(100f, 104f)
    sphere(c, 56f, Color(0xFFFFFDE8), Color(0xFFF2C455))
    gloss(78f, 78f, 16f, 8f, -35f, 0.6f)
    drawCircle(Color(0xFFE9B843).copy(alpha = 0.55f), 9f, Offset(138f, 130f))
    drawCircle(Color(0xFFE9B843).copy(alpha = 0.5f), 6f, Offset(70f, 136f))
    face(CharKind.Moon, 100f, 104f, 18f, a)
    val cap = Path().apply {
        moveTo(52f, 66f); quadraticBezierTo(64f, 18f, 116f, 24f); quadraticBezierTo(150f, 30f, 160f, 66f)
        quadraticBezierTo(112f, 44f, 52f, 66f); close()
    }
    drawPath(cap, CapBrush)
    rotate(-16f, Offset(82f, 66f)) { drawRoundRect(Color.White, Offset(46f, 60f), Size(72f, 12f), CornerRadius(6f)) }
    drawCircle(Color.White, 9f, Offset(160f + a.sway, 66f + a.sway.coerceAtLeast(0f)))
    zzz(156f, 100f, a.t3)
    sparkle(36f, 44f, 0.8f, a = twinkle(a.t3, 0f))
    sparkle(168f, 150f, 0.7f, a = twinkle(a.t3, 0.5f))
}
