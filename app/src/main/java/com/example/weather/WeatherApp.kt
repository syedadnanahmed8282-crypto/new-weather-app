package com.example.weather

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val success = ui.weather as? WeatherState.Success
    val theme = if (success != null) {
        themeFor(timeOfDay(LocalDateTime.parse(success.data.current.time).hour), skyOf(success.data.current.code))
    } else {
        themeFor(TimeOfDay.Noon, Sky.Sunny)
    }

    Box(Modifier.fillMaxSize()) {
        SkyBackground(theme)
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
                else -> AlertsScreen(theme)
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

@Composable
private fun AlertsScreen(theme: SkyTheme) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Alerts", color = theme.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Column(
            Modifier.fillMaxWidth().glass(theme, 28.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BellIcon(56.dp)
            Text("No active alerts", color = theme.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Severe weather alerts will appear here.", color = theme.muted, fontSize = 13.sp)
        }
    }
}
