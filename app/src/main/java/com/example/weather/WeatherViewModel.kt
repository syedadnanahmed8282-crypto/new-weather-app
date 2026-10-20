package com.example.weather

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
    val current: Boolean = false,          // true for the entry that follows the phone's location
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
    val following: Boolean = false,        // true while the app follows the live location
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
    private var weatherJob: Job? = null

    init {
        val cities = readCities()
        val saved = readSelected()
        val selected = saved?.let { s -> cities.firstOrNull { it.key == s.key } ?: s } ?: cities.first()
        _ui.update { it.copy(cities = cities, selected = selected, following = prefs.getBoolean("following", false)) }
        loadWeather(selected)
        loadCityTemps(cities)
    }

    // ---------------------------------------------------------------- refreshing

    fun refresh() {
        loadWeather(_ui.value.selected)
        loadCityTemps(_ui.value.cities)
    }

    /** Called every minute while the app is on screen. Fetches again once the data is 10+ minutes old. */
    fun refreshIfStale(maxAgeMs: Long = 10 * 60 * 1000L) {
        val w = _ui.value.weather
        if (w is WeatherState.Loading) return
        val stale = w !is WeatherState.Success || System.currentTimeMillis() - w.data.fetchedAt > maxAgeMs
        if (stale) refresh()
    }

    // ---------------------------------------------------------------- live location

    fun setFollowing(on: Boolean) {
        prefs.edit().putBoolean("following", on).apply()
        _ui.update { it.copy(following = on) }
    }

    /**
     * The phone reported a position. The "current location" entry is updated when the phone moved
     * 1 km or more. With [force] (the person asked for it) or while following, it becomes the selected place.
     */
    fun onLocation(lat: Double, lon: Double, force: Boolean = false) {
        val state = _ui.value
        val prev = state.cities.firstOrNull { it.current }
        if (prev != null && !force && distanceKm(prev.lat, prev.lon, lat, lon) < 1.0) return

        val me = City(prev?.name ?: "Current location", lat, lon, "Current location", current = true)
        val list = listOf(me) + state.cities.filterNot { it.current }
        writeCities(list)
        _ui.update { it.copy(cities = list) }
        loadCityTemps(listOf(me))
        if (force || state.following) {
            setFollowing(true)
            selectInternal(me)
        }
    }

    private fun distanceKm(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = FloatArray(1)
        Location.distanceBetween(aLat, aLon, bLat, bLon, r)
        return r[0] / 1000.0
    }

    // ---------------------------------------------------------------- places

    fun select(city: City) {
        setFollowing(city.current)
        selectInternal(city)
    }

    private fun selectInternal(city: City) {
        writeSelected(city)
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
        if (city.current) setFollowing(false)
        writeCities(list)
        _ui.update { it.copy(cities = list) }
        if (_ui.value.selected.key == city.key) select(list.first())
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
                        _ui.update { it.copy(searching = false, searchError = e.message ?: "Search failed") }
                    }
                }
        }
    }

    // ---------------------------------------------------------------- loading

    private fun loadWeather(city: City) {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            if (_ui.value.weather !is WeatherState.Success) _ui.update { it.copy(weather = WeatherState.Loading) }
            val result = runCatching { Network.api.forecast(city.lat, city.lon) }
            ensureActive()
            // Ignore the answer if the person already picked another place
            if (_ui.value.selected.key != city.key) return@launch
            result.onSuccess { data ->
                _ui.update { it.copy(weather = WeatherState.Success(data)) }
                // The live location entry takes the real place name from the weather service
                if (city.current && data.placeName.isNotBlank() && data.placeName != city.name) renameCurrent(city.key, data.placeName)
            }.onFailure { e ->
                // Keep showing the last good data if a refresh fails
                _ui.update { s ->
                    if (s.weather is WeatherState.Success) s
                    else s.copy(weather = WeatherState.Error(e.message ?: "Something went wrong"))
                }
            }
        }
    }

    private fun renameCurrent(key: String, name: String) {
        val list = _ui.value.cities.map { if (it.key == key) it.copy(name = name) else it }
        writeCities(list)
        _ui.update { s ->
            val sel = if (s.selected.key == key) s.selected.copy(name = name) else s.selected
            s.copy(cities = list, selected = sel)
        }
        writeSelected(_ui.value.selected)
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

    // ---------------------------------------------------------------- saved data

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

    private fun writeSelected(city: City) {
        prefs.edit().putString("selected", json.encodeToString(City.serializer(), city)).apply()
    }
}
