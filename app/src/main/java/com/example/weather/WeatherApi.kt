package com.example.weather

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

private const val CURRENT_FIELDS =
    "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,uv_index"

@Serializable
data class WeatherResponse(val current: Current, val hourly: Hourly, val daily: Daily)

@Serializable
data class CurrentOnly(val current: Current)

@Serializable
data class Current(
    val time: String,
    @SerialName("temperature_2m") val temp: Double,
    @SerialName("apparent_temperature") val feelsLike: Double = 0.0,
    @SerialName("relative_humidity_2m") val humidity: Int = 0,
    @SerialName("wind_speed_10m") val wind: Double = 0.0,
    @SerialName("weather_code") val code: Int,
    @SerialName("uv_index") val uv: Double = 0.0,
)

@Serializable
data class Hourly(
    val time: List<String>,
    @SerialName("temperature_2m") val temp: List<Double>,
    @SerialName("weather_code") val code: List<Int>,
)

@Serializable
data class Daily(
    val time: List<String>,
    @SerialName("weather_code") val code: List<Int>,
    @SerialName("temperature_2m_max") val max: List<Double>,
    @SerialName("temperature_2m_min") val min: List<Double>,
)

@Serializable
data class GeoResponse(val results: List<GeoResult>? = null)

@Serializable
data class GeoResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
)

interface WeatherApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = CURRENT_FIELDS,
        @Query("hourly") hourly: String = "temperature_2m,weather_code",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") days: Int = 7,
    ): WeatherResponse

    @GET("v1/forecast")
    suspend fun currentOnly(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = CURRENT_FIELDS,
        @Query("timezone") timezone: String = "auto",
    ): CurrentOnly
}

interface GeoApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 6,
        @Query("language") language: String = "en",
    ): GeoResponse
}

object Network {
    private val json = Json { ignoreUnknownKeys = true }
    private val factory = json.asConverterFactory("application/json".toMediaType())

    val api: WeatherApi by lazy {
        Retrofit.Builder().baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(factory).build().create(WeatherApi::class.java)
    }

    val geo: GeoApi by lazy {
        Retrofit.Builder().baseUrl("https://geocoding-api.open-meteo.com/")
            .addConverterFactory(factory).build().create(GeoApi::class.java)
    }
}
