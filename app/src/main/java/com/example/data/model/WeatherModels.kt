package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.squareup.moshi.JsonClass
import kotlin.math.roundToInt

@JsonClass(generateAdapter = true)
data class LocationItem(
    val id: String,
    val name: String,
    val country: String,
    val admin1: String? = null,
    val latitude: Double,
    val longitude: Double,
    val timezone: String = "auto"
) {
    val displaySubtitle: String
        get() = listOfNotNull(admin1?.takeIf { it.isNotBlank() && it != name }, country.takeIf { it.isNotBlank() })
            .joinToString(", ")

    val cacheSlug: String
        get() = "${name}_${country}".lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
}

enum class TempUnit(val symbol: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F")
}

fun Double.formatTemp(unit: TempUnit, includeUnit: Boolean = false): String {
    val converted = when (unit) {
        TempUnit.CELSIUS -> this
        TempUnit.FAHRENHEIT -> (this * 9.0 / 5.0) + 32.0
    }
    return if (includeUnit) "${converted.roundToInt()}${unit.symbol}" else "${converted.roundToInt()}°"
}

enum class WeatherCondition(
    val label: String,
    val promptAtmosphere: String,
    val promptSkyTone: String
) {
    CLEAR(
        label = "Clear Sky",
        promptAtmosphere = "crisp golden sunlight, delicate warm rim light, serene sky",
        promptSkyTone = "deep azure blue sky fading to soft horizon glow"
    ),
    PARTLY_CLOUDY(
        label = "Partly Cloudy",
        promptAtmosphere = "dappled sunlight filtering through sculpted cumulus clouds",
        promptSkyTone = "cerulean sky with soft luminous cream cloud wisps"
    ),
    OVERCAST(
        label = "Overcast",
        promptAtmosphere = "soft diffused silver daylight under a calm cloud canopy",
        promptSkyTone = "cool slate-blue and pearl gray overcast sky"
    ),
    FOG(
        label = "Misty Fog",
        promptAtmosphere = "ethereal low-lying mist weaving gently between the miniature buildings",
        promptSkyTone = "silvery pearl-blue atmospheric haze"
    ),
    DRIZZLE(
        label = "Light Drizzle",
        promptAtmosphere = "soft misty rain sheen on rooftops and subtle wet reflections",
        promptSkyTone = "cool misty blue-gray sky"
    ),
    RAIN(
        label = "Rainy",
        promptAtmosphere = "glistening wet architectural surfaces, subtle falling rain atmosphere",
        promptSkyTone = "moody slate-indigo rain sky"
    ),
    SNOW(
        label = "Snowfall",
        promptAtmosphere = "delicate fresh snow dusting rooftops and spires, crisp winter glow",
        promptSkyTone = "frosted pale ice-blue and twilight lavender sky"
    ),
    THUNDERSTORM(
        label = "Thunderstorm",
        promptAtmosphere = "dramatic storm clouds above illuminated landmarks with electric rim highlights",
        promptSkyTone = "deep dramatic charcoal-indigo storm sky"
    );

    fun skyGradientColors(isDay: Boolean): List<Color> = when {
        !isDay -> when (this) {
            CLEAR, PARTLY_CLOUDY -> listOf(Color(0xFF070F1E), Color(0xFF102242), Color(0xFF1B365D))
            SNOW -> listOf(Color(0xFF0D172B), Color(0xFF1E3254), Color(0xFF2E4970))
            THUNDERSTORM -> listOf(Color(0xFF070B14), Color(0xFF161E31), Color(0xFF263047))
            else -> listOf(Color(0xFF0A1220), Color(0xFF15243A), Color(0xFF223552))
        }
        else -> when (this) {
            CLEAR -> listOf(Color(0xFF0E3460), Color(0xFF1E5B94), Color(0xFF3E8BC2))
            PARTLY_CLOUDY -> listOf(Color(0xFF143D6B), Color(0xFF276296), Color(0xFF4D88B8))
            OVERCAST, FOG -> listOf(Color(0xFF1E2D40), Color(0xFF334961), Color(0xFF516B85))
            DRIZZLE, RAIN -> listOf(Color(0xFF142136), Color(0xFF243959), Color(0xFF3A5478))
            SNOW -> listOf(Color(0xFF1E3354), Color(0xFF385780), Color(0xFF6082AB))
            THUNDERSTORM -> listOf(Color(0xFF0D1424), Color(0xFF1D2942), Color(0xFF33415E))
        }
    }

    companion object {
        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1, 2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65, 66, 67, 80, 81, 82 -> RAIN
            71, 73, 75, 77, 85, 86 -> SNOW
            95, 96, 99 -> THUNDERSTORM
            else -> PARTLY_CLOUDY
        }
    }
}

data class HourlyForecastPoint(
    val timeIso: String,
    val hourLabel: String,
    val tempC: Double,
    val condition: WeatherCondition,
    val precipProbPercent: Int,
    val isDay: Boolean
)

data class DailyForecastPoint(
    val dateIso: String,
    val dayLabel: String,
    val condition: WeatherCondition,
    val tempMaxC: Double,
    val tempMinC: Double,
    val precipProbMaxPercent: Int,
    val uvIndexMax: Double
)

data class WeatherSnapshot(
    val location: LocationItem,
    val currentTempC: Double,
    val apparentTempC: Double,
    val humidityPercent: Int,
    val windSpeedKmh: Double,
    val windDirectionDeg: Int,
    val pressureHpa: Double,
    val cloudCoverPercent: Int,
    val isDay: Boolean,
    val condition: WeatherCondition,
    val highTempC: Double,
    val lowTempC: Double,
    val uvIndexMax: Double,
    val sunriseLabel: String,
    val sunsetLabel: String,
    val hourlyForecast: List<HourlyForecastPoint>,
    val dailyForecast: List<DailyForecastPoint>,
    val fetchedAtEpochMs: Long
)

object FeaturedLocations {
    val curated = listOf(
        LocationItem(
            id = "tokyo_jp",
            name = "Tokyo",
            country = "Japan",
            admin1 = "Tokyo",
            latitude = 35.6895,
            longitude = 139.6917,
            timezone = "Asia/Tokyo"
        ),
        LocationItem(
            id = "paris_fr",
            name = "Paris",
            country = "France",
            admin1 = "Île-de-France",
            latitude = 48.8566,
            longitude = 2.3522,
            timezone = "Europe/Paris"
        ),
        LocationItem(
            id = "new_york_us",
            name = "New York",
            country = "United States",
            admin1 = "New York",
            latitude = 40.7128,
            longitude = -74.0060,
            timezone = "America/New_York"
        ),
        LocationItem(
            id = "london_gb",
            name = "London",
            country = "United Kingdom",
            admin1 = "England",
            latitude = 51.5072,
            longitude = -0.1276,
            timezone = "Europe/London"
        ),
        LocationItem(
            id = "reykjavik_is",
            name = "Reykjavik",
            country = "Iceland",
            admin1 = "Capital Region",
            latitude = 64.1466,
            longitude = -21.9426,
            timezone = "Atlantic/Reykjavik"
        ),
        LocationItem(
            id = "cairo_eg",
            name = "Cairo",
            country = "Egypt",
            admin1 = "Cairo",
            latitude = 30.0444,
            longitude = 31.2357,
            timezone = "Africa/Cairo"
        ),
        LocationItem(
            id = "sydney_au",
            name = "Sydney",
            country = "Australia",
            admin1 = "New South Wales",
            latitude = -33.8688,
            longitude = 151.2093,
            timezone = "Australia/Sydney"
        )
    )
}
