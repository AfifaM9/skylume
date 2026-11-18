package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DailyForecastPoint
import com.example.data.model.HourlyForecastPoint
import com.example.data.model.TempUnit
import com.example.data.model.WeatherSnapshot
import com.example.data.model.formatTemp
import com.example.ui.theme.AuroraTeal
import com.example.ui.theme.GlassBorderLight
import com.example.ui.theme.GlassSurfaceDark
import com.example.ui.theme.SkyCerulean
import com.example.ui.theme.SunGold
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun WeatherMetricsSection(
    snapshot: WeatherSnapshot,
    tempUnit: TempUnit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HourlyHorizonCard(
            hourly = snapshot.hourlyForecast,
            tempUnit = tempUnit
        )

        AtmosphereGridCard(snapshot = snapshot)

        DailyOutlookCard(
            daily = snapshot.dailyForecast,
            tempUnit = tempUnit
        )
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        color = GlassSurfaceDark,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, GlassBorderLight, RoundedCornerShape(24.dp))
    ) {
        content()
    }
}

@Composable
private fun HourlyHorizonCard(
    hourly: List<HourlyForecastPoint>,
    tempUnit: TempUnit
) {
    GlassPanel {
        Column(modifier = Modifier.padding(vertical = 18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.hourly_forecast_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${hourly.size}H",
                    style = MaterialTheme.typography.labelMedium,
                    color = SkyCerulean
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (hourly.size >= 2) {
                HourlyTemperatureSpline(
                    hourly = hourly.take(12),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(hourly, key = { it.timeIso }) { point ->
                    Surface(
                        color = if (point.hourLabel == "Now") {
                            SkyCerulean.copy(alpha = 0.2f)
                        } else {
                            Color.White.copy(alpha = 0.05f)
                        },
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.border(
                            width = 1.dp,
                            color = if (point.hourLabel == "Now") {
                                SkyCerulean.copy(alpha = 0.6f)
                            } else {
                                Color.White.copy(alpha = 0.08f)
                            },
                            shape = RoundedCornerShape(18.dp)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = point.hourLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (point.hourLabel == "Now") {
                                    SkyCerulean
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Icon(
                                imageVector = point.condition.iconVector(point.isDay),
                                contentDescription = point.condition.label,
                                tint = point.condition.iconTint(point.isDay),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = point.tempC.formatTemp(tempUnit),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${point.precipProbPercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (point.precipProbPercent >= 30) {
                                    SkyCerulean
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyTemperatureSpline(
    hourly: List<HourlyForecastPoint>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (hourly.size < 2) return@Canvas
        val minT = hourly.minOf { it.tempC }
        val maxT = hourly.maxOf { it.tempC }
        val range = (maxT - minT).coerceAtLeast(1.0)

        val points = hourly.mapIndexed { idx, point ->
            val x = (idx.toFloat() / (hourly.size - 1)) * size.width
            val normY = ((point.tempC - minT) / range).toFloat()
            val y = size.height * (0.82f - normY * 0.64f)
            Offset(x, y)
        }

        val strokePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val cx = (p0.x + p1.x) * 0.5f
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    SkyCerulean.copy(alpha = 0.28f),
                    Color.Transparent
                )
            )
        )

        drawPath(
            path = strokePath,
            brush = Brush.horizontalGradient(
                colors = listOf(SkyCerulean, SunGold)
            ),
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )

        points.forEachIndexed { index, pt ->
            drawCircle(
                color = if (index == 0) SunGold else SkyCerulean,
                radius = if (index == 0) 6f else 3.5f,
                center = pt
            )
        }
    }
}

@Composable
private fun AtmosphereGridCard(snapshot: WeatherSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.atmosphere_metrics_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            WindCompassMetricCard(
                windSpeedKmh = snapshot.windSpeedKmh,
                windDirectionDeg = snapshot.windDirectionDeg,
                modifier = Modifier.weight(1f)
            )
            MetricTileCard(
                title = "Humidity",
                value = "${snapshot.humidityPercent}%",
                subtitle = "Cloud cover ${snapshot.cloudCoverPercent}%",
                icon = Icons.Rounded.Opacity,
                accent = SkyCerulean,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTileCard(
                title = "Pressure",
                value = "${snapshot.pressureHpa.roundToInt()} hPa",
                subtitle = if (snapshot.pressureHpa >= 1013.0) "High pressure" else "Low pressure",
                icon = Icons.Rounded.Compress,
                accent = AuroraTeal,
                modifier = Modifier.weight(1f)
            )
            MetricTileCard(
                title = "UV Peak",
                value = String.format("%.1f", snapshot.uvIndexMax),
                subtitle = uvRatingLabel(snapshot.uvIndexMax),
                icon = Icons.Rounded.WbSunny,
                accent = SunGold,
                modifier = Modifier.weight(1f)
            )
        }

        DaylightSolarArcCard(
            sunrise = snapshot.sunriseLabel,
            sunset = snapshot.sunsetLabel,
            cloudCover = snapshot.cloudCoverPercent,
            isDay = snapshot.isDay
        )
    }
}

@Composable
private fun WindCompassMetricCard(
    windSpeedKmh: Double,
    windDirectionDeg: Int,
    modifier: Modifier = Modifier
) {
    GlassPanel(modifier = modifier) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Air,
                        contentDescription = null,
                        tint = SkyCerulean,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Wind",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${windSpeedKmh.roundToInt()} km/h",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${compassLabel(windDirectionDeg)} ($windDirectionDeg°)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Canvas(modifier = Modifier.size(46.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 4f
                drawCircle(
                    color = Color.White.copy(alpha = 0.14f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2.5f)
                )
                val rad = ((windDirectionDeg - 90) * PI / 180.0).toFloat()
                val tip = Offset(
                    center.x + cos(rad) * (radius - 3f),
                    center.y + sin(rad) * (radius - 3f)
                )
                val tail = Offset(
                    center.x - cos(rad) * (radius * 0.45f),
                    center.y - sin(rad) * (radius * 0.45f)
                )
                drawLine(
                    color = SkyCerulean,
                    start = tail,
                    end = tip,
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = SunGold,
                    radius = 4f,
                    center = tip
                )
            }
        }
    }
}

@Composable
private fun MetricTileCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    GlassPanel(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DaylightSolarArcCard(
    sunrise: String,
    sunset: String,
    cloudCover: Int,
    isDay: Boolean
) {
    GlassPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WbTwilight,
                    contentDescription = null,
                    tint = SunGold,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Sunrise $sunrise",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Sunset $sunset",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudQueue,
                    contentDescription = null,
                    tint = SkyCerulean,
                    modifier = Modifier.size(18.dp)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isDay) "Daylight" else "Night",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isDay) SunGold else SkyCerulean
                    )
                    Text(
                        text = "$cloudCover% clouds",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyOutlookCard(
    daily: List<DailyForecastPoint>,
    tempUnit: TempUnit
) {
    if (daily.isEmpty()) return
    val overallMin = daily.minOf { it.tempMinC }
    val overallMax = daily.maxOf { it.tempMaxC }
    val totalSpan = (overallMax - overallMin).coerceAtLeast(1.0)

    GlassPanel {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.daily_forecast_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            daily.forEach { day ->
                val startFraction = ((day.tempMinC - overallMin) / totalSpan).toFloat().coerceIn(0f, 0.9f)
                val endFraction = ((day.tempMaxC - overallMin) / totalSpan).toFloat().coerceIn(startFraction + 0.08f, 1f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = day.dayLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (day.dayLabel == "Today") FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.width(58.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.width(62.dp)
                    ) {
                        Icon(
                            imageVector = day.condition.iconVector(isDay = true),
                            contentDescription = day.condition.label,
                            tint = day.condition.iconTint(isDay = true),
                            modifier = Modifier.size(20.dp)
                        )
                        if (day.precipProbMaxPercent >= 20) {
                            Text(
                                text = "${day.precipProbMaxPercent}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = SkyCerulean
                            )
                        }
                    }

                    Text(
                        text = day.tempMinC.formatTemp(tempUnit),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(34.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val startX = startFraction * size.width
                            val endX = endFraction * size.width
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(SkyCerulean, SunGold),
                                    startX = startX,
                                    endX = endX
                                ),
                                start = Offset(startX, size.height / 2f),
                                end = Offset(endX, size.height / 2f),
                                strokeWidth = size.height,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    Text(
                        text = day.tempMaxC.formatTemp(tempUnit),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.width(34.dp)
                    )
                }
            }
        }
    }
}

private fun uvRatingLabel(uv: Double): String = when {
    uv < 3.0 -> "Low"
    uv < 6.0 -> "Moderate"
    uv < 8.0 -> "High"
    uv < 11.0 -> "Very High"
    else -> "Extreme"
}

private fun compassLabel(deg: Int): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = (((deg % 360) + 360) % 360 + 22) / 45 % 8
    return dirs[index]
}
