package com.example.data.remote

import com.example.data.model.DailyForecastPoint
import com.example.data.model.HourlyForecastPoint
import com.example.data.model.LocationItem
import com.example.data.model.WeatherCondition
import com.example.data.model.WeatherSnapshot
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

@JsonClass(generateAdapter = true)
data class MetNorwayResponseDto(
    val properties: MetNorwayPropertiesDto
)

@JsonClass(generateAdapter = true)
data class MetNorwayPropertiesDto(
    val timeseries: List<MetTimeseriesDto>
)

@JsonClass(generateAdapter = true)
data class MetTimeseriesDto(
    val time: String,
    val data: MetTimeDataDto
)

@JsonClass(generateAdapter = true)
data class MetTimeDataDto(
    val instant: MetInstantDto,
    @Json(name = "next_1_hours") val next1Hours: MetPeriodDto? = null,
    @Json(name = "next_6_hours") val next6Hours: MetPeriodDto? = null
)

@JsonClass(generateAdapter = true)
data class MetInstantDto(
    val details: MetInstantDetailsDto
)

@JsonClass(generateAdapter = true)
data class MetInstantDetailsDto(
    @Json(name = "air_pressure_at_sea_level") val airPressureAtSeaLevel: Double? = null,
    @Json(name = "air_temperature") val airTemperature: Double,
    @Json(name = "cloud_area_fraction") val cloudAreaFraction: Double? = null,
    @Json(name = "relative_humidity") val relativeHumidity: Double? = null,
    @Json(name = "wind_from_direction") val windFromDirection: Double? = null,
    @Json(name = "wind_speed") val windSpeed: Double? = null
)

@JsonClass(generateAdapter = true)
data class MetPeriodDto(
    val summary: MetSummaryDto? = null,
    val details: MetPeriodDetailsDto? = null
)

@JsonClass(generateAdapter = true)
data class MetSummaryDto(
    @Json(name = "symbol_code") val symbolCode: String? = null
)

@JsonClass(generateAdapter = true)
data class MetPeriodDetailsDto(
    @Json(name = "precipitation_amount") val precipitationAmount: Double? = null
)

@JsonClass(generateAdapter = true)
data class NominatimResultDto(
    @Json(name = "place_id") val placeId: Long,
    val lat: String,
    val lon: String,
    val name: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    val address: NominatimAddressDto? = null
)

@JsonClass(generateAdapter = true)
data class NominatimAddressDto(
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val state: String? = null,
    val country: String? = null
)

interface MetNorwayService {
    @GET("weatherapi/locationforecast/2.0/compact")
    suspend fun getForecast(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Header("User-Agent") userAgent: String = "SkylumeWeatherAndroid/1.0"
    ): MetNorwayResponseDto
}

interface NominatimGeocodingService {
    @GET("search")
    suspend fun searchLocations(
        @Query("q") query: String,
        @Query("format") format: String = "jsonv2",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit") limit: Int = 10,
        @Header("User-Agent") userAgent: String = "SkylumeWeatherAndroid/1.0"
    ): List<NominatimResultDto>
}

class SecondaryWeatherClient(
    okHttpClient: OkHttpClient,
    moshi: Moshi
) {
    private val metService: MetNorwayService = Retrofit.Builder()
        .baseUrl("https://api.met.no/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(MetNorwayService::class.java)

    private val nominatimService: NominatimGeocodingService = Retrofit.Builder()
        .baseUrl("https://nominatim.openstreetmap.org/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(NominatimGeocodingService::class.java)

    suspend fun searchCities(query: String): List<LocationItem> {
        return nominatimService.searchLocations(query = query).mapNotNull { dto ->
            val lat = dto.lat.toDoubleOrNull() ?: return@mapNotNull null
            val lon = dto.lon.toDoubleOrNull() ?: return@mapNotNull null
            val cityName = dto.address?.city
                ?: dto.address?.town
                ?: dto.address?.village
                ?: dto.name
                ?: dto.displayName?.substringBefore(",")
                ?: return@mapNotNull null
            LocationItem(
                id = "nom_${dto.placeId}_${lat}_${lon}",
                name = cityName,
                country = dto.address?.country.orEmpty(),
                admin1 = dto.address?.state,
                latitude = lat,
                longitude = lon,
                timezone = "auto"
            )
        }
    }

    suspend fun fetchWeather(location: LocationItem): WeatherSnapshot {
        val response = metService.getForecast(
            latitude = String.format(Locale.US, "%.4f", location.latitude).toDouble(),
            longitude = String.format(Locale.US, "%.4f", location.longitude).toDouble()
        )
        val series = response.properties.timeseries
        val current = series.firstOrNull()
            ?: throw IllegalStateException("Fallback weather service returned no timeseries.")

        val currentDetails = current.data.instant.details
        val currentSymbol = current.data.next1Hours?.summary?.symbolCode
            ?: current.data.next6Hours?.summary?.symbolCode
        val currentCondition = mapMetSymbolToCondition(currentSymbol)
        val isDayNow = isSymbolDaytime(currentSymbol, location.longitude)

        val hourlyList = series.take(24).mapIndexed { index, entry ->
            val symbol = entry.data.next1Hours?.summary?.symbolCode
                ?: entry.data.next6Hours?.summary?.symbolCode
            val precipMm = entry.data.next1Hours?.details?.precipitationAmount
                ?: entry.data.next6Hours?.details?.precipitationAmount
                ?: 0.0
            val precipProb = when {
                precipMm >= 1.5 -> 85
                precipMm >= 0.4 -> 60
                precipMm > 0.05 -> 35
                else -> 5
            }
            HourlyForecastPoint(
                timeIso = entry.time,
                hourLabel = if (index == 0) "Now" else formatUtcToLocalHour(entry.time),
                tempC = entry.data.instant.details.airTemperature,
                condition = mapMetSymbolToCondition(symbol),
                precipProbPercent = precipProb,
                isDay = isSymbolDaytime(symbol, location.longitude)
            )
        }

        val groupedByDate = series.groupBy { it.time.substringBefore("T") }
        val dailyList = groupedByDate.entries.take(7).mapIndexed { idx, (dateIso, entries) ->
            val maxTemp = entries.maxOf { it.data.instant.details.airTemperature }
            val minTemp = entries.minOf { it.data.instant.details.airTemperature }
            val midEntry = entries[entries.size / 2]
            val daySymbol = midEntry.data.next6Hours?.summary?.symbolCode
                ?: midEntry.data.next1Hours?.summary?.symbolCode
            val maxPrecip = entries.maxOf {
                it.data.next1Hours?.details?.precipitationAmount
                    ?: it.data.next6Hours?.details?.precipitationAmount
                    ?: 0.0
            }
            DailyForecastPoint(
                dateIso = dateIso,
                dayLabel = if (idx == 0) "Today" else formatDayName(dateIso),
                condition = mapMetSymbolToCondition(daySymbol),
                tempMaxC = maxTemp,
                tempMinC = minTemp,
                precipProbMaxPercent = if (maxPrecip > 0.2) 70 else 10,
                uvIndexMax = if (mapMetSymbolToCondition(daySymbol) == WeatherCondition.CLEAR) 5.0 else 2.0
            )
        }

        val windKmh = (currentDetails.windSpeed ?: 0.0) * 3.6
        val todayHigh = dailyList.firstOrNull()?.tempMaxC ?: currentDetails.airTemperature
        val todayLow = dailyList.firstOrNull()?.tempMinC ?: currentDetails.airTemperature

        return WeatherSnapshot(
            location = location,
            currentTempC = currentDetails.airTemperature,
            apparentTempC = currentDetails.airTemperature,
            humidityPercent = (currentDetails.relativeHumidity ?: 50.0).roundToInt(),
            windSpeedKmh = windKmh,
            windDirectionDeg = (currentDetails.windFromDirection ?: 0.0).roundToInt(),
            pressureHpa = currentDetails.airPressureAtSeaLevel ?: 1013.0,
            cloudCoverPercent = (currentDetails.cloudAreaFraction ?: 0.0).roundToInt(),
            isDay = isDayNow,
            condition = currentCondition,
            highTempC = todayHigh,
            lowTempC = todayLow,
            uvIndexMax = dailyList.firstOrNull()?.uvIndexMax ?: 2.0,
            sunriseLabel = "06:00",
            sunsetLabel = "18:00",
            hourlyForecast = hourlyList,
            dailyForecast = dailyList,
            fetchedAtEpochMs = System.currentTimeMillis()
        )
    }

    private fun formatUtcToLocalHour(isoUtc: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val formatter = SimpleDateFormat("HH:00", Locale.US)
            val date = parser.parse(isoUtc)
            if (date != null) formatter.format(date) else isoUtc.substringAfter("T", "00:00").take(5)
        } catch (_: Exception) {
            isoUtc.substringAfter("T", "00:00").take(5)
        }
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

    private fun isSymbolDaytime(symbolCode: String?, longitude: Double): Boolean {
        if (symbolCode != null) {
            if (symbolCode.endsWith("_day")) return true
            if (symbolCode.endsWith("_night")) return false
        }
        val utcHour = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.HOUR_OF_DAY)
        val approxLocalHour = ((utcHour + (longitude / 15.0).roundToInt()) % 24 + 24) % 24
        return approxLocalHour in 6..18
    }

    companion object {
        internal fun mapMetSymbolToCondition(symbolCode: String?): WeatherCondition {
            val base = symbolCode?.substringBefore("_")?.lowercase(Locale.US) ?: return WeatherCondition.PARTLY_CLOUDY
            return when {
                base.contains("thunder") -> WeatherCondition.THUNDERSTORM
                base.contains("snow") || base.contains("sleet") -> WeatherCondition.SNOW
                base.contains("heavyrain") || base.contains("rain") -> WeatherCondition.RAIN
                base.contains("drizzle") -> WeatherCondition.DRIZZLE
                base.contains("fog") -> WeatherCondition.FOG
                base == "cloudy" -> WeatherCondition.OVERCAST
                base == "partlycloudy" || base == "fair" -> WeatherCondition.PARTLY_CLOUDY
                base == "clearsky" -> WeatherCondition.CLEAR
                else -> WeatherCondition.PARTLY_CLOUDY
            }
        }
    }
}
