package com.example.weather

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

@Serializable
data class City(
    val name: String,
    val lat: Double,
    val lon: Double,
    val subtitle: String = "",
) {
    val key: String get() = "$lat,$lon"
}

data class CityWeather(val temp: Double, val code: Int)

sealed interface WeatherState {
    data object Loading : WeatherState
    data class Success(val data: WeatherResponse) : WeatherState
    data class Error(val message: String) : WeatherState
}

data class UiState(
    val selected: City = DEFAULT_CITIES.first(),
    val weather: WeatherState = WeatherState.Loading,
    val cities: List<City> = emptyList(),
    val cityWeather: Map<String, CityWeather> = emptyMap(),
    val results: List<City> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
)

val DEFAULT_CITIES = listOf(
    City("Dhaka", 23.8103, 90.4125, "Bangladesh"),
    City("Chattogram", 22.3569, 91.7832, "Bangladesh"),
    City("Sylhet", 24.8949, 91.8687, "Bangladesh"),
    City("Rajshahi", 24.3745, 88.6042, "Bangladesh"),
)

class WeatherViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    private var searchJob: Job? = null

    init {
        val cities = readCities()
        val selected = readSelected() ?: cities.first()
        _ui.update { it.copy(cities = cities, selected = selected) }
        loadWeather(selected)
        loadCityTemps(cities)
    }

    fun refresh() {
        loadWeather(_ui.value.selected)
        loadCityTemps(_ui.value.cities)
    }

    fun select(city: City) {
        prefs.edit().putString("selected", json.encodeToString(City.serializer(), city)).apply()
        _ui.update { it.copy(selected = city) }
        loadWeather(city)
    }

    fun addAndSelect(city: City) {
        val list = if (_ui.value.cities.any { it.key == city.key }) _ui.value.cities else _ui.value.cities + city
        writeCities(list)
        _ui.update { it.copy(cities = list, results = emptyList()) }
        loadCityTemps(listOf(city))
        select(city)
    }

    fun remove(city: City) {
        val list = _ui.value.cities.filterNot { it.key == city.key }
        if (list.isEmpty()) return
        writeCities(list)
        _ui.update { it.copy(cities = list) }
        if (_ui.value.selected.key == city.key) select(list.first())
    }

    fun useLocation(lat: Double, lon: Double) {
        val me = City("Current location", lat, lon, "GPS")
        val list = listOf(me) + _ui.value.cities.filterNot { it.name == me.name }
        writeCities(list)
        _ui.update { it.copy(cities = list) }
        loadCityTemps(listOf(me))
        select(me)
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _ui.update { it.copy(results = emptyList(), searching = false, searchError = null) }
            return
        }
        searchJob = viewModelScope.launch {
            _ui.update { it.copy(searching = true, searchError = null) }
            runCatching { Network.geo.search(query.trim()) }
                .onSuccess { r ->
                    val found = r.results.orEmpty().map { g ->
                        City(g.name, g.latitude, g.longitude, listOfNotNull(g.admin1, g.country).joinToString(", "))
                    }
                    _ui.update { it.copy(searching = false, results = found) }
                }
                .onFailure { e ->
                    if (e !is CancellationException) {
                        _ui.update { it.copy(searching = false, searchError = "Search failed. Check your connection.") }
                    }
                }
        }
    }

    private fun loadWeather(city: City) {
        viewModelScope.launch {
            if (_ui.value.weather !is WeatherState.Success) _ui.update { it.copy(weather = WeatherState.Loading) }
            val result = runCatching { Network.api.forecast(city.lat, city.lon) }
            // Ignore the answer if the user already picked another city
            if (_ui.value.selected.key != city.key) return@launch
            _ui.update {
                it.copy(weather = result.fold(
                    { data -> WeatherState.Success(data) },
                    { e -> WeatherState.Error(e.message ?: "Something went wrong") },
                ))
            }
        }
    }

    private fun loadCityTemps(cities: List<City>) {
        viewModelScope.launch {
            cities.forEach { c ->
                launch {
                    runCatching { Network.api.currentOnly(c.lat, c.lon) }.onSuccess { r ->
                        _ui.update { it.copy(cityWeather = it.cityWeather + (c.key to CityWeather(r.current.temp, r.current.code))) }
                    }
                }
            }
        }
    }

    private fun readCities(): List<City> =
        prefs.getString("cities", null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(City.serializer()), it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_CITIES

    private fun writeCities(list: List<City>) {
        prefs.edit().putString("cities", json.encodeToString(ListSerializer(City.serializer()), list)).apply()
    }

    private fun readSelected(): City? =
        prefs.getString("selected", null)
            ?.let { runCatching { json.decodeFromString(City.serializer(), it) }.getOrNull() }
}
