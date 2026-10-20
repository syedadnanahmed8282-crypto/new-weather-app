package com.example.weather

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.util.Locale

// ------------------------------------------------------------------ models the screens use

data class WeatherAlert(val title: String, val severity: String, val desc: String, val expires: String)

data class WeatherResponse(
    val current: Current,
    val hourly: Hourly,
    val daily: Daily,
    val placeName: String = "",
    val updated: String = "",                       // when the weather service last updated this reading (local time)
    val alerts: List<WeatherAlert> = emptyList(),
    val fetchedAt: Long = System.currentTimeMillis(),
)

data class CurrentOnly(val current: Current)

data class Current(
    val time: String,                               // local time at the place, ISO: 2026-10-20T13:05
    val temp: Double,
    val feelsLike: Double = 0.0,
    val humidity: Int = 0,
    val wind: Double = 0.0,                         // km/h
    val code: Int,                                  // WeatherAPI condition code
    val uv: Double = 0.0,
)

data class Hourly(val time: List<String>, val temp: List<Double>, val code: List<Int>)
data class Daily(val time: List<String>, val code: List<Int>, val max: List<Double>, val min: List<Double>)

data class GeoResponse(val results: List<GeoResult>? = null)
data class GeoResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
)

// ------------------------------------------------------------------ raw WeatherAPI.com answers

@Serializable data class RawLocation(val name: String = "", val localtime: String = "")
@Serializable data class RawCondition(val code: Int = 1000)

@Serializable
data class RawCurrent(
    @SerialName("last_updated") val lastUpdated: String = "",
    @SerialName("temp_c") val tempC: Double = 0.0,
    @SerialName("feelslike_c") val feelsC: Double = 0.0,
    val humidity: Int = 0,
    @SerialName("wind_kph") val windKph: Double = 0.0,
    val uv: Double = 0.0,
    val condition: RawCondition = RawCondition(),
)

@Serializable data class RawHour(val time: String, @SerialName("temp_c") val tempC: Double = 0.0, val condition: RawCondition = RawCondition())

@Serializable
data class RawDay(
    @SerialName("maxtemp_c") val max: Double = 0.0,
    @SerialName("mintemp_c") val min: Double = 0.0,
    val condition: RawCondition = RawCondition(),
)

@Serializable data class RawForecastDay(val date: String, val day: RawDay = RawDay(), val hour: List<RawHour> = emptyList())
@Serializable data class RawForecastBlock(@SerialName("forecastday") val days: List<RawForecastDay> = emptyList())
@Serializable data class RawAlert(val headline: String = "", val event: String = "", val severity: String = "", val desc: String = "", val expires: String = "")
@Serializable data class RawAlerts(val alert: List<RawAlert> = emptyList())

@Serializable
data class RawResponse(
    val location: RawLocation = RawLocation(),
    val current: RawCurrent = RawCurrent(),
    val forecast: RawForecastBlock = RawForecastBlock(),
    val alerts: RawAlerts = RawAlerts(),
)

@Serializable
data class RawPlace(val name: String = "", val region: String = "", val country: String = "", val lat: Double = 0.0, val lon: Double = 0.0)

interface WeatherApiRaw {
    @GET("forecast.json")
    suspend fun forecast(
        @Query("key") key: String,
        @Query("q") q: String,
        @Query("days") days: Int = 3,
        @Query("aqi") aqi: String = "no",
        @Query("alerts") alerts: String = "yes",
    ): RawResponse

    @GET("current.json")
    suspend fun current(@Query("key") key: String, @Query("q") q: String, @Query("aqi") aqi: String = "no"): RawResponse

    @GET("search.json")
    suspend fun search(@Query("key") key: String, @Query("q") q: String): List<RawPlace>
}

// ------------------------------------------------------------------ helpers

private fun apiKey(): String {
    val k = BuildConfig.WEATHER_API_KEY
    if (k.isBlank()) throw IOException("Add WEATHER_API_KEY=your_key to local.properties and rebuild the app")
    return k
}

private suspend fun <T> apiCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: HttpException) {
        throw IOException(
            when (e.code()) {
                400 -> "Place not found"
                401, 403 -> "WeatherAPI key problem: invalid, disabled, or the monthly limit is used up"
                else -> "Weather service error (${e.code()})"
            }
        )
    }

// WeatherAPI times look like "2026-10-20 1:05"; the screens want "2026-10-20T01:05"
private fun iso(s: String): String {
    val parts = s.trim().split(" ")
    if (parts.size < 2) return s
    val hm = parts[1].split(":")
    return String.format(Locale.US, "%sT%02d:%02d", parts[0], hm[0].toInt(), hm.getOrElse(1) { "0" }.toInt())
}

private fun RawResponse.toModel(): WeatherResponse {
    val hours = forecast.days.flatMap { it.hour }
    return WeatherResponse(
        current = Current(
            time = iso(location.localtime),
            temp = current.tempC, feelsLike = current.feelsC, humidity = current.humidity,
            wind = current.windKph, code = current.condition.code, uv = current.uv,
        ),
        hourly = Hourly(hours.map { iso(it.time) }, hours.map { it.tempC }, hours.map { it.condition.code }),
        daily = Daily(
            forecast.days.map { it.date },
            forecast.days.map { it.day.condition.code },
            forecast.days.map { it.day.max },
            forecast.days.map { it.day.min },
        ),
        placeName = location.name,
        updated = if (current.lastUpdated.isBlank()) "" else iso(current.lastUpdated),
        alerts = alerts.alert.map { WeatherAlert(it.headline.ifBlank { it.event }, it.severity, it.desc, it.expires) },
    )
}

// ------------------------------------------------------------------ services (same names the app already uses)

class WeatherService(private val raw: WeatherApiRaw) {
    suspend fun forecast(lat: Double, lon: Double): WeatherResponse =
        apiCall { raw.forecast(apiKey(), "$lat,$lon").toModel() }

    suspend fun currentOnly(lat: Double, lon: Double): CurrentOnly =
        apiCall { CurrentOnly(raw.current(apiKey(), "$lat,$lon").toModel().current) }
}

class GeoService(private val raw: WeatherApiRaw) {
    suspend fun search(name: String): GeoResponse = apiCall {
        GeoResponse(raw.search(apiKey(), name).map {
            GeoResult(it.name, it.lat, it.lon, it.country.ifBlank { null }, it.region.ifBlank { null })
        })
    }
}

object Network {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val raw: WeatherApiRaw by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.weatherapi.com/v1/")
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WeatherApiRaw::class.java)
    }

    val api: WeatherService by lazy { WeatherService(raw) }
    val geo: GeoService by lazy { GeoService(raw) }
}
