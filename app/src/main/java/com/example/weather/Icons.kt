package com.example.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp

private fun DrawScope.iconGloss(cx: Float, cy: Float, rx: Float, ry: Float, deg: Float, a: Float) =
    rotate(deg, Offset(cx, cy)) { drawOval(Color.White.copy(alpha = a), Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2)) }

private fun DrawScope.iconSphere(cx: Float, cy: Float, r: Float, light: Color, dark: Color) {
    drawCircle(
        Brush.radialGradient(listOf(light, dark), Offset(cx - r * 0.3f, cy - r * 0.35f), r * 1.5f),
        r, Offset(cx, cy),
    )
    iconGloss(cx - r * 0.3f, cy - r * 0.35f, r * 0.38f, r * 0.22f, -35f, 0.6f)
}

private fun DrawScope.iconSun(cx: Float, cy: Float, r: Float, rays: Boolean) {
    if (rays) {
        for (i in 0 until 8) rotate(i * 45f, Offset(cx, cy)) {
            drawLine(Color(0xFFFFA726), Offset(cx, cy - 20f), Offset(cx, cy - 14f), 3.5f, StrokeCap.Round)
        }
    }
    iconSphere(cx, cy, r, Color(0xFFFFF6AA), Color(0xFFFF9A1A))
}

private fun DrawScope.iconCloud(dy: Float, a: Color, b: Color) {
    val br = Brush.verticalGradient(listOf(a, b), 15f + dy, 38f + dy)
    drawCircle(br, 8f, Offset(18f, 30f + dy))
    drawCircle(br, 10f, Offset(28f, 25f + dy))
    drawCircle(br, 7f, Offset(36f, 31f + dy))
    drawRoundRect(br, Offset(18f, 30f + dy), Size(18f, 8f), CornerRadius(1f))
    drawRoundRect(Color(0xFF6F90C4).copy(alpha = 0.22f), Offset(18f, 34f + dy), Size(18f, 4f), CornerRadius(2f))
    iconGloss(26f, 19f + dy, 5f, 2.6f, -20f, 0.7f)
}

private fun DrawScope.iconDrop(x: Float, y: Float) {
    val p = Path().apply {
        moveTo(x, y - 4f); quadraticBezierTo(x + 3.5f, y + 1f, x, y + 4f)
        quadraticBezierTo(x - 3.5f, y + 1f, x, y - 4f); close()
    }
    drawPath(p, Brush.verticalGradient(listOf(Color(0xFFA9D9FF), Color(0xFF2F8CF0)), y - 4f, y + 4f))
}

private fun DrawScope.iconBolt(a: Float = 1f) {
    val p = Path().apply {
        moveTo(26f, 30f); lineTo(18f, 41f); lineTo(24f, 41f); lineTo(21f, 48f)
        lineTo(33f, 36f); lineTo(27f, 36f); lineTo(31f, 30f); close()
    }
    drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFF27A), Color(0xFFFFAE00)), 30f, 48f), alpha = a)
}

@Composable
fun WeatherIcon(sky: Sky, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 48f
        withTransform({ scale(k, k, Offset.Zero) }) {
            val white = Color.White
            val blueGrey = Color(0xFFBCD3F5)
            when (sky) {
                Sky.Sunny -> iconSun(24f, 24f, 10f, true)
                Sky.Partly -> { iconSun(17f, 17f, 9f, false); iconCloud(0f, white, blueGrey) }
                Sky.Cloudy -> iconCloud(0f, white, blueGrey)
                Sky.Rain -> {
                    iconCloud(-6f, Color(0xFFE4EEFC), Color(0xFF9DB6DC))
                    iconDrop(17f, 40f); iconDrop(26f, 42f); iconDrop(35f, 40f)
                }
                Sky.Storm -> {
                    iconCloud(-6f, Color(0xFFA9AED0), Color(0xFF5A6090))
                    iconBolt()
                }
                Sky.Snow -> {
                    for (i in 0 until 3) rotate(i * 60f, Offset(24f, 24f)) {
                        drawLine(Color(0xFF7BBCF7), Offset(24f, 5f), Offset(24f, 43f), 4f, StrokeCap.Round)
                    }
                    drawCircle(
                        Brush.radialGradient(listOf(Color.White, Color(0xFF9FD0FB)), Offset(22f, 22f), 12f),
                        8f, Offset(24f, 24f),
                    )
                    drawCircle(Color(0xFFBFE0FF), 8f, Offset(24f, 24f), style = Stroke(1.5f))
                }
                Sky.Windy -> {
                    val br = Brush.verticalGradient(listOf(Color(0xFF7FF0DE), Color(0xFF0D9488)), 12f, 38f)
                    drawLine(br, Offset(8f, 16f), Offset(30f, 16f), 4.5f, StrokeCap.Round)
                    drawLine(br, Offset(8f, 26f), Offset(38f, 26f), 4.5f, StrokeCap.Round)
                    drawLine(br, Offset(8f, 36f), Offset(22f, 36f), 4.5f, StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(10f, 15f), Offset(26f, 15f), 1.5f, StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(10f, 25f), Offset(32f, 25f), 1.5f, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
fun DropIcon(size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val tip = Path().apply {
            moveTo(s * 0.5f, s * 0.06f); lineTo(s * 0.22f, s * 0.52f); lineTo(s * 0.78f, s * 0.52f); close()
        }
        val br = Brush.verticalGradient(listOf(Color(0xFF7DB8FF), Color(0xFF2F80ED)), 0f, s)
        drawPath(tip, br)
        drawCircle(br, s * 0.3f, Offset(s * 0.5f, s * 0.64f))
        drawOval(Color.White.copy(alpha = 0.55f), Offset(s * 0.36f, s * 0.5f), Size(s * 0.1f, s * 0.16f))
    }
}

@Composable
fun WindIcon(size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val br = Brush.verticalGradient(listOf(Color(0xFF5EEAD4), Color(0xFF0D9488)), 0f, s)
        val w = s * 0.1f
        drawLine(br, Offset(s * 0.1f, s * 0.3f), Offset(s * 0.75f, s * 0.3f), w, StrokeCap.Round)
        drawLine(br, Offset(s * 0.1f, s * 0.5f), Offset(s * 0.9f, s * 0.5f), w, StrokeCap.Round)
        drawLine(br, Offset(s * 0.1f, s * 0.7f), Offset(s * 0.6f, s * 0.7f), w, StrokeCap.Round)
    }
}

@Composable
fun SearchIcon(size: Dp, color: Color = Color(0xFF2F80ED)) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawCircle(color, s * 0.3f, Offset(s * 0.45f, s * 0.45f), style = Stroke(width = s * 0.1f))
        drawLine(color, Offset(s * 0.68f, s * 0.68f), Offset(s * 0.9f, s * 0.9f), s * 0.1f, StrokeCap.Round)
    }
}

@Composable
fun PinIcon(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val light = lerp(color, Color.White, 0.4f)
        val br = Brush.radialGradient(listOf(light, color), Offset(s * 0.4f, s * 0.25f), s * 0.8f)
        val tip = Path().apply {
            moveTo(s * 0.26f, s * 0.5f); lineTo(s * 0.5f, s * 0.96f); lineTo(s * 0.74f, s * 0.5f); close()
        }
        drawPath(tip, br)
        drawCircle(br, s * 0.3f, Offset(s * 0.5f, s * 0.38f))
        drawOval(Color.White.copy(alpha = 0.55f), Offset(s * 0.3f, s * 0.2f), Size(s * 0.2f, s * 0.1f))
        drawCircle(Color.White, s * 0.11f, Offset(s * 0.5f, s * 0.38f))
    }
}

@Composable
fun BellIcon(size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val br = Brush.radialGradient(listOf(Color(0xFFFFE08A), Color(0xFFFF9A1A)), Offset(s * 0.4f, s * 0.25f), s * 0.8f)
        drawCircle(br, s * 0.26f, Offset(s * 0.5f, s * 0.4f))
        drawRect(br, Offset(s * 0.24f, s * 0.4f), Size(s * 0.52f, s * 0.26f))
        drawRoundRect(br, Offset(s * 0.14f, s * 0.62f), Size(s * 0.72f, s * 0.1f), CornerRadius(s * 0.05f))
        drawOval(Color.White.copy(alpha = 0.6f), Offset(s * 0.34f, s * 0.22f), Size(s * 0.12f, s * 0.07f))
        drawCircle(Color(0xFFFF7A1A), s * 0.09f, Offset(s * 0.5f, s * 0.84f))
    }
}
