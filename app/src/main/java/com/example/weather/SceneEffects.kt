package com.example.weather

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** True when the person turned system animations off. Motion is then reduced to a still scene. */
@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** How many particles to draw: fewer on old or low-memory phones so the app stays smooth. */
@Composable
private fun rememberParticleScale(reduce: Boolean): Float {
    val ctx = LocalContext.current
    return remember(reduce) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        when {
            reduce -> 0.25f
            am?.isLowRamDevice == true -> 0.5f
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> 0.65f
            else -> 1f
        }
    }
}

private class Particle(
    var x: Float, var y: Float, var v: Float, var s: Float, var ph: Float,
    var len: Float = 0f, var kind: Int = 0,
)

private class Ripple(var x: Float, var y: Float, var r: Float, var a: Float)

private fun rnd(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

private fun makePrecip(sky: Sky, f: Float): List<Particle> = when (sky) {
    Sky.Rain, Sky.Storm -> {
        val storm = sky == Sky.Storm
        List(((if (storm) 160 else 100) * f).toInt()) {
            Particle(rnd(-0.1f, 1.1f), rnd(0f, 1f), rnd(0.014f, 0.022f) * (if (storm) 1.25f else 1f), rnd(0.55f, 0.9f), 0f, rnd(10f, 22f))
        }
    }
    Sky.Snow -> List((60 * f).toInt()) { Particle(rnd(0f, 1f), rnd(0f, 1f), rnd(0.0015f, 0.004f), rnd(1.5f, 4.5f), rnd(0f, 6.3f)) }
    Sky.Windy -> List((24 * f).toInt()) {
        val leaf = Random.nextFloat() < 0.2f
        Particle(rnd(0f, 1f), rnd(0f, 0.8f), rnd(0.005f, 0.011f), 0f, rnd(0f, 6.3f), rnd(30f, 90f), if (leaf) 1 + Random.nextInt(3) else 0)
    }
    // Floating sparkles of light in sun, drifting motes in cloud: every sky has something alive in it
    Sky.Sunny, Sky.Partly -> List((28 * f).toInt()) { Particle(rnd(0f, 1f), rnd(0f, 1f), rnd(0.0008f, 0.0025f), rnd(1.5f, 3.5f), rnd(0f, 6.3f)) }
    Sky.Cloudy -> List((34 * f).toInt()) { Particle(rnd(0f, 1f), rnd(0f, 1f), rnd(0.0006f, 0.002f), rnd(1.5f, 3.5f), rnd(0f, 6.3f)) }
}

private val LeafColors = listOf(Color(0xFF7BC96F), Color(0xFFFFB84D), Color(0xFFFF8A65))

/**
 * Full-screen weather: falling rain with ripples, snow, wind streaks and leaves, floating sun sparkles,
 * drifting cloud motes and mist, twinkling stars and shooting stars at night.
 * Drawn at about 30 frames per second, in a few batched drawing calls. Pauses when the app is in the background.
 */
@Composable
fun WeatherParticles(sky: Sky, starAlpha: Float, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val scale = rememberParticleScale(reduce)
    val precip = remember(sky, scale) { makePrecip(sky, scale) }
    val stars = remember(starAlpha > 0f, scale) {
        List(if (starAlpha > 0f) (60 * scale).toInt().coerceAtLeast(12) else 0) {
            Particle(rnd(0f, 1f), rnd(0f, 0.7f), rnd(0.8f, 2.2f), rnd(0.6f, 1.8f), rnd(0f, 6.3f))
        }
    }
    val ripples = remember(sky) { mutableListOf<Ripple>() }
    val shoot = remember { floatArrayOf(0f, 0f, 0f) }     // x, y, alpha
    val wisp = remember { floatArrayOf(0.1f, 0.55f, 0.85f) }
    val linePath = remember { Path() }
    val mist = remember { Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)) }
    val last = remember { LongArray(1) }
    var tick by remember { mutableLongStateOf(0L) }
    val owner = LocalLifecycleOwner.current

    LaunchedEffect(owner, reduce) {
        if (!reduce) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var stamp = 0L
            while (true) withFrameNanos { now ->
                if (now - stamp >= 32_000_000L) { stamp = now; tick = now }      // ~30 fps
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        val frame = tick
        val dt = if (last[0] == 0L || frame == 0L) 1f else ((frame - last[0]) / 16_666_667f).coerceIn(0f, 4f)
        last[0] = frame
        val w = size.width
        val h = size.height
        val d = density

        when (sky) {
            Sky.Rain, Sky.Storm -> {
                val slant = if (sky == Sky.Storm) 0.3f else 0.12f
                linePath.rewind()
                for (p in precip) {
                    val x0 = p.x * w
                    val y0 = p.y * h
                    val len = p.len * d
                    linePath.moveTo(x0, y0)
                    linePath.lineTo(x0 + len * slant, y0 + len)
                    p.y += p.v * dt
                    p.x += p.v * slant * (h / w) * dt
                    if (p.y > p.s) {
                        if (ripples.size < 24) ripples.add(Ripple(p.x, p.s, 1f, 0.6f))
                        p.y = -0.05f; p.x = rnd(-0.1f, 1.1f); p.s = rnd(0.55f, 0.9f)
                    }
                }
                drawPath(linePath, Color(0xFFD7E8FF).copy(alpha = 0.75f), style = Stroke(1.6f * d, cap = StrokeCap.Round))
                val iter = ripples.iterator()
                while (iter.hasNext()) {
                    val r = iter.next()
                    r.r += 0.5f * dt; r.a -= 0.018f * dt
                    if (r.a <= 0f) { iter.remove(); continue }
                    val rw = r.r * 2f * d
                    drawOval(
                        Color(0xFFE1F0FF).copy(alpha = r.a), Offset(r.x * w - rw, r.y * h - rw * 0.35f),
                        Size(rw * 2f, rw * 0.7f), style = Stroke(1.2f * d),
                    )
                }
            }
            Sky.Snow -> for (p in precip) {
                p.y += p.v * dt; p.ph += 0.02f * dt; p.x += sin(p.ph) * 0.0006f * dt
                if (p.y > 1.02f) { p.y = -0.02f; p.x = rnd(0f, 1f) }
                drawCircle(Color.White.copy(alpha = 0.92f), p.s * d, Offset(p.x * w, p.y * h))
            }
            Sky.Windy -> {
                linePath.rewind()
                for (p in precip) {
                    p.x += p.v * dt; p.ph += 0.05f * dt
                    if (p.x > 1.1f) { p.x = -0.1f; p.y = rnd(0f, 0.8f) }
                    val xx = p.x * w
                    val yy = p.y * h + sin(p.ph) * 14f * d
                    if (p.kind > 0) {
                        rotate(p.ph * 57.3f, Offset(xx, yy)) {
                            drawOval(LeafColors[p.kind - 1], Offset(xx - 7f * d, yy - 3.5f * d), Size(14f * d, 7f * d))
                        }
                    } else {
                        linePath.moveTo(xx - p.len * d, yy)
                        linePath.lineTo(xx, yy)
                    }
                }
                drawPath(linePath, Color.White.copy(alpha = 0.75f), style = Stroke(2f * d, cap = StrokeCap.Round))
            }
            Sky.Sunny, Sky.Partly -> for (p in precip) {
                p.y -= p.v * dt; p.ph += 0.02f * dt; p.x += sin(p.ph) * 0.0004f * dt
                if (p.y < -0.02f) { p.y = 1.02f; p.x = rnd(0f, 1f) }
                drawCircle(Color(0xFFFFF4B4).copy(alpha = 0.5f + 0.3f * sin(p.ph * 2f)), p.s * d, Offset(p.x * w, p.y * h))
            }
            Sky.Cloudy -> {
                // slow drifting mist
                for (i in wisp.indices) {
                    wisp[i] += 0.0004f * (i + 1) * dt
                    if (wisp[i] > 1.2f) wisp[i] = -0.5f
                    drawOval(mist, Offset(wisp[i] * w, h * (0.15f + 0.3f * i)), Size(w * 0.9f, h * 0.2f))
                }
                // floating motes
                for (p in precip) {
                    p.y -= p.v * dt; p.ph += 0.015f * dt; p.x += sin(p.ph) * 0.0004f * dt
                    if (p.y < -0.02f) { p.y = 1.02f; p.x = rnd(0f, 1f) }
                    drawCircle(Color.White.copy(alpha = 0.35f + 0.25f * sin(p.ph * 2f)), p.s * d, Offset(p.x * w, p.y * h))
                }
            }
        }

        if (starAlpha > 0f) {
            for (s in stars) {
                s.ph += 0.03f * s.v * dt
                val a = (0.3f + 0.6f * abs(sin(s.ph))) * (starAlpha / 0.9f)
                drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 1f)), s.s * d, Offset(s.x * w, s.y * h))
            }
            if (starAlpha >= 0.5f && !reduce) {
                if (shoot[2] <= 0f && Random.nextFloat() < 0.004f * dt) { shoot[0] = rnd(0.3f, 1f); shoot[1] = rnd(0f, 0.25f); shoot[2] = 1f }
                if (shoot[2] > 0f) {
                    val sx = shoot[0] * w
                    val sy = shoot[1] * h
                    drawLine(Color.White.copy(alpha = shoot[2]), Offset(sx, sy), Offset(sx + 70f * d, sy - 40f * d), 2f * d, StrokeCap.Round)
                    shoot[0] -= 9f * dt / w; shoot[1] += 5f * dt / h; shoot[2] -= 0.02f * dt
                }
            }
        }
    }
}

/** White flash across the whole screen at random moments during a thunderstorm. */
@Composable
fun LightningFlash(active: Boolean, modifier: Modifier = Modifier) {
    val reduce = rememberReduceMotion()
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(active, reduce) {
        if (!active || reduce) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2500, 6000))
            alpha.animateTo(0.8f, tween(60)); alpha.animateTo(0f, tween(120))
            alpha.animateTo(0.55f, tween(50)); alpha.animateTo(0f, tween(250))
        }
    }
    Canvas(modifier.fillMaxSize()) {
        if (alpha.value > 0f) drawRect(Color.White.copy(alpha = alpha.value))
    }
}
