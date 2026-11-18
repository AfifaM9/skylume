package com.example.data.remote

import android.util.Log
import com.example.data.model.DailyForecastPoint
import com.example.data.model.HourlyForecastPoint
import com.example.data.model.LocationItem
import com.example.data.model.WeatherCondition
import com.example.data.model.WeatherSnapshot
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "WeatherRepository"

@JsonClass(generateAdapter = true)
data class GeocodingResponseDto(
    val results: List<GeocodingResultDto>? = null
)

@JsonClass(generateAdapter = true)
data class GeocodingResultDto(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
    val timezone: String? = null
)

@JsonClass(generateAdapter = true)
data class ForecastResponseDto(
    val current: CurrentWeatherDto,
    val hourly: HourlyWeatherDto,
    val daily: DailyWeatherDto
)

@JsonClass(generateAdapter = true)
data class CurrentWeatherDto(
    val time: String,
    @Json(name = "temperature_2m") val temperature2m: Double,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: Int,
    @Json(name = "apparent_temperature") val apparentTemperature: Double,
    @Json(name = "is_day") val isDay: Int,
    val precipitation: Double,
    @Json(name = "weather_code") val weatherCode: Int,
    @Json(name = "cloud_cover") val cloudCover: Int,
    @Json(name = "pressure_msl") val pressureMsl: Double,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double,
    @Json(name = "wind_direction_10m") val windDirection10m: Int
)

@JsonClass(generateAdapter = true)
data class HourlyWeatherDto(
    val time: List<String>,
    @Json(name = "temperature_2m") val temperature2m: List<Double>,
    @Json(name = "weather_code") val weatherCode: List<Int>,
    @Json(name = "precipitation_probability") val precipitationProbability: List<Int?>,
    @Json(name = "is_day") val isDay: List<Int>
)

@JsonClass(generateAdapter = true)
data class DailyWeatherDto(
    val time: List<String>,
    @Json(name = "weather_code") val weatherCode: List<Int>,
    @Json(name = "temperature_2m_max") val temperature2mMax: List<Double>,
    @Json(name = "temperature_2m_min") val temperature2mMin: List<Double>,
    val sunrise: List<String>,
    val sunset: List<String>,
    @Json(name = "uv_index_max") val uvIndexMax: List<Double?>,
    @Json(name = "precipitation_probability_max") val precipitationProbabilityMax: List<Int?>
)

interface OpenMeteoGeocodingService {
    @GET("v1/search")
    suspend fun searchLocations(
        @Query("name") query: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeocodingResponseDto
}

interface OpenMeteoForecastService {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String =
            "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m",
        @Query("hourly") hourly: String =
            "temperature_2m,weather_code,precipitation_probability,is_day",
        @Query("daily") daily: String =
            "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 7
    ): ForecastResponseDto
}

class WeatherRepository(appContext: android.content.Context? = null) {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(ResilientDns(appContext))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    private val geocodingService: OpenMeteoGeocodingService = Retrofit.Builder()
        .baseUrl("https://geocoding-api.open-meteo.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenMeteoGeocodingService::class.java)

    private val forecastService: OpenMeteoForecastService = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OpenMeteoForecastService::class.java)

    private val secondaryClient = SecondaryWeatherClient(okHttpClient, moshi)

    suspend fun searchCities(query: String): List<LocationItem> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()
        return try {
            val response = geocodingService.searchLocations(query = trimmed)
            response.results.orEmpty().map { dto ->
                LocationItem(
                    id = "${dto.id}_${dto.latitude}_${dto.longitude}",
                    name = dto.name,
                    country = dto.country.orEmpty(),
                    admin1 = dto.admin1,
                    latitude = dto.latitude,
                    longitude = dto.longitude,
                    timezone = dto.timezone ?: "auto"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Open-Meteo geocoding failed, trying Nominatim fallback", e)
            secondaryClient.searchCities(trimmed)
        }
    }

    suspend fun fetchWeather(location: LocationItem): WeatherSnapshot {
        val dto = try {
            forecastService.getForecast(
                latitude = location.latitude,
                longitude = location.longitude
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Open-Meteo forecast failed, falling back to MET Norway", e)
            return secondaryClient.fetchWeather(location)
        }

        val currentHourPrefix = dto.current.time.take(13)
        val startHourIndex = dto.hourly.time.indexOfFirst { it.startsWith(currentHourPrefix) }
            .takeIf { it >= 0 } ?: 0

        val hourlyList = (startHourIndex until minOf(startHourIndex + 24, dto.hourly.time.size))
            .mapIndexed { offset, idx ->
                val rawTime = dto.hourly.time[idx]
                val hourPart = rawTime.substringAfter("T", "00:00").take(5)
                HourlyForecastPoint(
                    timeIso = rawTime,
                    hourLabel = if (offset == 0) "Now" else hourPart,
                    tempC = dto.hourly.temperature2m.getOrElse(idx) { dto.current.temperature2m },
                    condition = WeatherCondition.fromWmoCode(dto.hourly.weatherCode.getOrElse(idx) { 0 }),
                    precipProbPercent = dto.hourly.precipitationProbability.getOrNull(idx) ?: 0,
                    isDay = dto.hourly.isDay.getOrElse(idx) { dto.current.isDay } == 1
                )
            }

        val dailyList = dto.daily.time.indices.map { idx ->
            val dateIso = dto.daily.time[idx]
            DailyForecastPoint(
                dateIso = dateIso,
                dayLabel = if (idx == 0) "Today" else formatDayName(dateIso),
                condition = WeatherCondition.fromWmoCode(dto.daily.weatherCode.getOrElse(idx) { 0 }),
                tempMaxC = dto.daily.temperature2mMax.getOrElse(idx) { dto.current.temperature2m },
                tempMinC = dto.daily.temperature2mMin.getOrElse(idx) { dto.current.temperature2m },
                precipProbMaxPercent = dto.daily.precipitationProbabilityMax.getOrNull(idx) ?: 0,
                uvIndexMax = dto.daily.uvIndexMax.getOrNull(idx) ?: 0.0
            )
        }

        val todayHigh = dailyList.firstOrNull()?.tempMaxC ?: dto.current.temperature2m
        val todayLow = dailyList.firstOrNull()?.tempMinC ?: dto.current.temperature2m
        val todayUv = dailyList.firstOrNull()?.uvIndexMax ?: 0.0
        val sunrise = dto.daily.sunrise.firstOrNull()?.substringAfter("T", "06:00")?.take(5) ?: "06:00"
        val sunset = dto.daily.sunset.firstOrNull()?.substringAfter("T", "18:00")?.take(5) ?: "18:00"

        return WeatherSnapshot(
            location = location,
            currentTempC = dto.current.temperature2m,
            apparentTempC = dto.current.apparentTemperature,
            humidityPercent = dto.current.relativeHumidity2m,
            windSpeedKmh = dto.current.windSpeed10m,
            windDirectionDeg = dto.current.windDirection10m,
            pressureHpa = dto.current.pressureMsl,
            cloudCoverPercent = dto.current.cloudCover,
            isDay = dto.current.isDay == 1,
            condition = WeatherCondition.fromWmoCode(dto.current.weatherCode),
            highTempC = todayHigh,
            lowTempC = todayLow,
            uvIndexMax = todayUv,
            sunriseLabel = sunrise,
            sunsetLabel = sunset,
            hourlyForecast = hourlyList,
            dailyForecast = dailyList,
            fetchedAtEpochMs = System.currentTimeMillis()
        )
    }

    private fun formatDayName(dateIso: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val formatter = SimpleDateFormat("EEE", Locale.US)
            val date = parser.parse(dateIso)
            if (date != null) formatter.format(date) else dateIso
        } catch (_: Exception) {
            dateIso
        }
    }
}
