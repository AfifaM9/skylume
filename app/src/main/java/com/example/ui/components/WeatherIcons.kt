package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.model.WeatherCondition
import com.example.ui.theme.CloudMist
import com.example.ui.theme.SkyCerulean
import com.example.ui.theme.SunGold

@Composable
fun WeatherCondition.iconVector(isDay: Boolean): ImageVector = when (this) {
    WeatherCondition.CLEAR -> if (isDay) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    WeatherCondition.PARTLY_CLOUDY -> Icons.Rounded.WbCloudy
    WeatherCondition.OVERCAST -> Icons.Rounded.Cloud
    WeatherCondition.FOG -> Icons.Rounded.Cloud
    WeatherCondition.DRIZZLE -> Icons.Rounded.Grain
    WeatherCondition.RAIN -> Icons.Rounded.WaterDrop
    WeatherCondition.SNOW -> Icons.Rounded.AcUnit
    WeatherCondition.THUNDERSTORM -> Icons.Rounded.Thunderstorm
}

@Composable
fun WeatherCondition.iconTint(isDay: Boolean): Color = when (this) {
    WeatherCondition.CLEAR -> if (isDay) SunGold else Color(0xFFBAE6FD)
    WeatherCondition.PARTLY_CLOUDY -> if (isDay) Color(0xFFFDE68A) else CloudMist
    WeatherCondition.OVERCAST, WeatherCondition.FOG -> CloudMist
    WeatherCondition.DRIZZLE, WeatherCondition.RAIN -> SkyCerulean
    WeatherCondition.SNOW -> Color(0xFFE0F2FE)
    WeatherCondition.THUNDERSTORM -> Color(0xFFFBBF24)
}
