package com.example.weather

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(ui: UiState, theme: SkyTheme, onSearchClick: () -> Unit, onRetry: () -> Unit) {
    when (val w = ui.weather) {
        is WeatherState.Loading ->
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = theme.ink) }

        is WeatherState.Error ->
            Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                Column(
                    Modifier.glass(theme).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Couldn't load the weather", color = theme.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(w.message, color = theme.muted, fontSize = 13.sp)
                    Box(
                        Modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xFF2F80ED))
                            .clickable(onClick = onRetry).padding(horizontal = 24.dp, vertical = 10.dp)
                    ) { Text("Retry", color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }

        is WeatherState.Success -> WeatherContent(w.data, ui.selected.name, ui.following && ui.selected.current, theme, onSearchClick, onRetry)
    }
}

/** Fades and slides a block up when it first appears. `index` staggers the start. */
@Composable
private fun Appear(index: Int, content: @Composable () -> Unit) {
    var shown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 90L); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(450, easing = FastOutSlowInEasing), label = "appear")
    Box(Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 40.dp.toPx() }) { content() }
}

/** The temperature counts up to its value. */
@Composable
private fun AnimatedTemp(value: Int, color: Color) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) { anim.animateTo(value.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
    Text("${anim.value.roundToInt()}°", color = color, fontSize = 88.sp, fontWeight = FontWeight.Light)
}

@Composable
private fun WeatherContent(
    data: WeatherResponse, city: String, live: Boolean, theme: SkyTheme,
    onSearchClick: () -> Unit, onRefresh: () -> Unit,
) {
    val now = LocalDateTime.parse(data.current.time)
    val tod = timeOfDay(now.hour)
    val sky = skyOf(data.current.code, data.current.wind)
    val night = tod == TimeOfDay.Night
    val ink = theme.ink
    val muted = theme.muted
    val greeting = when (tod) {
        TimeOfDay.Morning -> "Good morning"
        TimeOfDay.Noon -> "Good afternoon"
        TimeOfDay.Evening -> "Good evening"
        TimeOfDay.Night -> "Good night"
    }

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Appear(0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (live) "$greeting · live location" else greeting, color = muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(city, color = ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMM · h:mm a")), color = muted, fontSize = 13.sp)
                    }
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).glass(theme, 22.dp).clickable(onClick = onSearchClick),
                        contentAlignment = Alignment.Center,
                    ) { SearchIcon(22.dp) }
                }
            }
        }
        item {
            Appear(1) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Tap the character for a surprise
                    WeatherCharacter(sky, night, 220.dp)
                    AnimatedTemp(data.current.temp.roundToInt(), ink)
                    Text(sky.label(night), color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Feels like ${data.current.feelsLike.roundToInt()}° · H ${data.daily.max[0].roundToInt()}° L ${data.daily.min[0].roundToInt()}°",
                        color = muted, fontSize = 12.sp,
                    )
                    UpdatedLabel(data, muted, onRefresh)
                }
            }
        }
        item {
            Appear(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Humidity", "${data.current.humidity}%", Color(0xFFDCEBFF), theme, Modifier.weight(1f)) { DropIcon(16.dp) }
                    StatCard("Wind", "${data.current.wind.roundToInt()} km/h", Color(0xFFD5F5F0), theme, Modifier.weight(1f)) { WindIcon(16.dp) }
                    StatCard("UV index", "${data.current.uv.roundToInt()}", Color(0xFFFFE9D6), theme, Modifier.weight(1f)) { WeatherIcon(Sky.Sunny, 22.dp) }
                }
            }
        }
        item {
            Appear(3) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Next hours", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    val start = data.hourly.time.indexOfFirst { it.startsWith(data.current.time.substring(0, 13)) }.coerceAtLeast(0)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in start until minOf(start + 5, data.hourly.time.size)) {
                            val label = if (i == start) "Now"
                            else LocalDateTime.parse(data.hourly.time[i]).format(DateTimeFormatter.ofPattern("h a"))
                            Column(
                                Modifier.weight(1f)
                                    .glass(theme, 20.dp, if (i == start) theme.glassAlpha + 0.12f else theme.glassAlpha)
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(label, color = muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                WeatherIcon(skyOf(data.hourly.code[i]), 30.dp)
                                Text("${data.hourly.temp[i].roundToInt()}°", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        item {
            Appear(4) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Next days", color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Column(Modifier.fillMaxWidth().glass(theme, 24.dp).padding(horizontal = 16.dp, vertical = 6.dp)) {
                        for (i in 0 until minOf(4, data.daily.time.size)) {
                            val day = if (i == 0) "Today"
                            else LocalDate.parse(data.daily.time[i]).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                            val dSky = skyOf(data.daily.code[i])
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    day, Modifier.width(56.dp),
                                    color = if (i == 0) theme.accent else ink, fontSize = 15.sp,
                                    fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Medium,
                                )
                                WeatherIcon(dSky, 26.dp)
                                Text(dSky.label(false), Modifier.weight(1f).padding(start = 10.dp), color = muted, fontSize = 14.sp)
                                Text(
                                    "${data.daily.max[i].roundToInt()}° / ${data.daily.min[i].roundToInt()}°",
                                    color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "Updated 3 min ago": age of the reading at the weather service plus the time since it was fetched. */
@Composable
private fun UpdatedLabel(data: WeatherResponse, color: Color, onClick: () -> Unit) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }
    val sourceAge = runCatching {
        ChronoUnit.MINUTES.between(LocalDateTime.parse(data.updated), LocalDateTime.parse(data.current.time))
    }.getOrDefault(0L).coerceAtLeast(0L)
    val minutes = sourceAge + (now - data.fetchedAt) / 60_000L
    val text = if (minutes < 1) "Updated just now" else "Updated $minutes min ago"
    Text(
        "$text · tap to refresh", color = color, fontSize = 12.sp,
        modifier = Modifier.clickable(onClick = onClick).padding(top = 6.dp),
    )
}

@Composable
private fun StatCard(
    label: String, value: String, tint: Color, theme: SkyTheme, modifier: Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier.glass(theme, 20.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) { icon() }
        Text(value, color = theme.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(label, color = theme.muted, fontSize = 11.sp)
    }
}
