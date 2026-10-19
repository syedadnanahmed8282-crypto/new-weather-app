package com.example.weather

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun CitiesScreen(
    ui: UiState,
    theme: SkyTheme,
    onQuery: (String) -> Unit,
    onSelect: (City) -> Unit,
    onAdd: (City) -> Unit,
    onRemove: (City) -> Unit,
    onUseLocation: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(query) {
        if (query.isBlank()) onQuery("") else { delay(400); onQuery(query) }
    }

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Cities", color = theme.ink, fontSize = 30.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(
                Modifier.fillMaxWidth().glass(theme, 26.dp).padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SearchIcon(20.dp)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search for a city", color = theme.muted, fontSize = 15.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = theme.ink, fontSize = 15.sp),
                        cursorBrush = SolidColor(theme.ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF2F80ED).copy(alpha = 0.92f))
                    .clickable(onClick = onUseLocation).padding(horizontal = 18.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PinIcon(20.dp, Color.White)
                Text("Use current location", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (query.isNotBlank()) {
            item { Text("Results", color = theme.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            if (ui.searching) item { Text("Searching…", color = theme.muted, fontSize = 14.sp) }
            ui.searchError?.let { err -> item { Text(err, color = theme.muted, fontSize = 14.sp) } }
            if (!ui.searching && ui.searchError == null && ui.results.isEmpty()) {
                item { Text("No cities found", color = theme.muted, fontSize = 14.sp) }
            }
            items(ui.results, key = { "r" + it.key }) { c ->
                CityRow(c.name, c.subtitle, null, theme, onClick = { query = ""; onAdd(c) })
            }
        } else {
            item { Text("Saved cities", color = theme.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            items(ui.cities, key = { it.key }) { c ->
                CityRow(c.name, c.subtitle, ui.cityWeather[c.key], theme, onClick = { onSelect(c) }, onLongClick = { onRemove(c) })
            }
            item { Text("Tap a city to open it. Long-press to remove.", color = theme.muted, fontSize = 12.sp) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CityRow(
    name: String,
    subtitle: String,
    cw: CityWeather?,
    theme: SkyTheme,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().glass(theme, 24.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 20.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (cw != null) WeatherIcon(skyOf(cw.code), 48.dp) else PinIcon(36.dp, Color(0xFFFF5C7A))
            Column {
                Text(name, color = theme.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (cw != null) skyOf(cw.code).label(false) else subtitle,
                    color = theme.muted, fontSize = 13.sp,
                )
            }
        }
        if (cw != null) Text("${cw.temp.roundToInt()}°", color = theme.ink, fontSize = 36.sp, fontWeight = FontWeight.Light)
    }
}
