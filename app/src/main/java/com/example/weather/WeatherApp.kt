package com.example.weather

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDateTime

@Composable
fun WeatherApp(vm: WeatherViewModel, onRequestLocation: () -> Unit) {
    val ui by vm.ui.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // Jump back to Today whenever the selected city changes (new city, GPS, tap in the list)
    var lastKey by rememberSaveable { mutableStateOf(ui.selected.key) }
    LaunchedEffect(ui.selected.key) {
        if (ui.selected.key != lastKey) { lastKey = ui.selected.key; tab = 0 }
    }

    // Auto refresh: while the app is on screen, check every minute and fetch again once the data is 10+ minutes old
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                vm.refreshIfStale()
                delay(60_000)
            }
        }
    }

    // The sky follows the real weather and the local time at the selected place
    val success = ui.weather as? WeatherState.Success
    val sky = success?.let { skyOf(it.data.current.code, it.data.current.wind) } ?: Sky.Sunny
    val tod = success?.let { timeOfDay(LocalDateTime.parse(it.data.current.time).hour) } ?: TimeOfDay.Noon
    val theme = themeFor(tod, sky)

    // Real glass blur needs Android 12+ (API 31). Older phones get the frosted look instead.
    val useBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val density = LocalDensity.current
    val blurLayer = rememberGraphicsLayer()
    SideEffect {
        if (useBlur) {
            val r = with(density) { 18.dp.toPx() }
            blurLayer.renderEffect = BlurEffect(r, r, TileMode.Clamp)
        }
    }
    val backdrop = if (useBlur) blurLayer else null

    CompositionLocalProvider(LocalBlurBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize()) {
            SkyBackground(theme, sky, backdrop)
            Crossfade(tab, label = "screen") { t ->
                when (t) {
                    0 -> HomeScreen(ui, theme, onSearchClick = { tab = 1 }, onRetry = vm::refresh)
                    1 -> CitiesScreen(
                        ui, theme,
                        onQuery = vm::search,
                        onSelect = vm::select,
                        onAdd = vm::addAndSelect,
                        onRemove = vm::remove,
                        onUseLocation = onRequestLocation,
                    )
                    else -> AlertsScreen(theme, success?.data?.alerts.orEmpty())
                }
            }
            GlassTabBar(
                selected = tab,
                onSelect = { tab = it },
                theme = theme,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun AlertsScreen(theme: SkyTheme, alerts: List<WeatherAlert>) {
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Alerts", color = theme.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        if (alerts.isEmpty()) {
            item {
                Column(
                    Modifier.fillMaxWidth().glass(theme, 28.dp).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BellIcon(56.dp)
                    Text("No active alerts", color = theme.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Severe weather alerts for this place will appear here.", color = theme.muted, fontSize = 13.sp)
                }
            }
        } else {
            items(alerts) { a ->
                Column(
                    Modifier.fillMaxWidth().glass(theme, 24.dp).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BellIcon(26.dp)
                        Text(a.title, color = theme.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    if (a.severity.isNotBlank()) Text("Severity: ${a.severity}", color = theme.muted, fontSize = 12.sp)
                    if (a.desc.isNotBlank()) Text(a.desc.take(400), color = theme.ink, fontSize = 13.sp)
                    if (a.expires.isNotBlank()) Text("Until ${a.expires}", color = theme.muted, fontSize = 12.sp)
                }
            }
        }
    }
}
