package com.example.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

private val SunCore = Color(0xFFFFC21A)
private val SunRay = Color(0xFFFF9F1C)
private val CloudColor = Color(0xFFE4F0FF)
private val DropColor = Color(0xFF3B9CFF)

@Composable
fun WeatherIcon(sky: Sky, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension

        fun cloud(dy: Float) {
            drawCircle(CloudColor, s * 0.17f, Offset(s * 0.38f, s * (0.62f + dy)))
            drawCircle(CloudColor, s * 0.21f, Offset(s * 0.58f, s * (0.52f + dy)))
            drawCircle(CloudColor, s * 0.15f, Offset(s * 0.75f, s * (0.64f + dy)))
            drawRect(CloudColor, Offset(s * 0.38f, s * (0.62f + dy)), Size(s * 0.37f, s * 0.17f))
        }

        when (sky) {
            Sky.Sunny -> {
                val c = Offset(s / 2, s / 2)
                val r = s * 0.19f
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val d = Offset(cos(a).toFloat(), sin(a).toFloat())
                    drawLine(SunRay, c + d * (r * 1.5f), c + d * (r * 2.1f), s * 0.06f, StrokeCap.Round)
                }
                drawCircle(SunCore, r, c)
            }
            Sky.Partly -> {
                drawCircle(SunCore, s * 0.17f, Offset(s * 0.34f, s * 0.34f))
                cloud(0f)
            }
            Sky.Cloudy -> cloud(0f)
            Sky.Rain -> {
                cloud(-0.12f)
                for (x in listOf(0.40f, 0.56f, 0.72f)) {
                    drawLine(DropColor, Offset(s * x, s * 0.76f), Offset(s * (x - 0.04f), s * 0.88f), s * 0.06f, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
fun DropIcon(size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val c = Color(0xFF2F80ED)
        val tip = Path().apply {
            moveTo(s * 0.5f, s * 0.06f)
            lineTo(s * 0.22f, s * 0.52f)
            lineTo(s * 0.78f, s * 0.52f)
            close()
        }
        drawPath(tip, c)
        drawCircle(c, s * 0.3f, Offset(s * 0.5f, s * 0.64f))
    }
}

@Composable
fun WindIcon(size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val c = Color(0xFF14B8A6)
        val w = s * 0.1f
        drawLine(c, Offset(s * 0.1f, s * 0.3f), Offset(s * 0.75f, s * 0.3f), w, StrokeCap.Round)
        drawLine(c, Offset(s * 0.1f, s * 0.5f), Offset(s * 0.9f, s * 0.5f), w, StrokeCap.Round)
        drawLine(c, Offset(s * 0.1f, s * 0.7f), Offset(s * 0.6f, s * 0.7f), w, StrokeCap.Round)
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
        val tip = Path().apply {
            moveTo(s * 0.26f, s * 0.5f)
            lineTo(s * 0.5f, s * 0.96f)
            lineTo(s * 0.74f, s * 0.5f)
            close()
        }
        drawPath(tip, color)
        drawCircle(color, s * 0.3f, Offset(s * 0.5f, s * 0.38f))
        drawCircle(Color.White, s * 0.11f, Offset(s * 0.5f, s * 0.38f))
    }
}

@Composable
fun BellIcon(size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val c = Color(0xFFFFB020)
        drawCircle(c, s * 0.26f, Offset(s * 0.5f, s * 0.4f))
        drawRect(c, Offset(s * 0.24f, s * 0.4f), Size(s * 0.52f, s * 0.26f))
        drawRoundRect(c, Offset(s * 0.14f, s * 0.62f), Size(s * 0.72f, s * 0.1f), CornerRadius(s * 0.05f))
        drawCircle(Color(0xFFFF7A1A), s * 0.09f, Offset(s * 0.5f, s * 0.84f))
    }
}
