package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.example.data.model.WeatherCondition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class AtmosphericParticle(
    val xFraction: Float,
    val yFraction: Float,
    val speed: Float,
    val size: Float,
    val alpha: Float,
    val phase: Float
)

@Composable
fun WeatherParticlesCanvas(
    condition: WeatherCondition,
    isDay: Boolean,
    windSpeedKmh: Double,
    cloudCoverPercent: Int,
    modifier: Modifier = Modifier
) {
    var frameNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                frameNanos = now - startNanos
            }
        }
    }

    val particles = remember(condition) {
        val count = when (condition) {
            WeatherCondition.RAIN, WeatherCondition.THUNDERSTORM -> 95
            WeatherCondition.DRIZZLE -> 55
            WeatherCondition.SNOW -> 70
            WeatherCondition.CLEAR -> 38
            WeatherCondition.PARTLY_CLOUDY, WeatherCondition.OVERCAST -> 22
            WeatherCondition.FOG -> 16
        }
        val rng = Random(condition.ordinal * 997 + 42)
        List(count) {
            AtmosphericParticle(
                xFraction = rng.nextFloat(),
                yFraction = rng.nextFloat(),
                speed = 0.25f + rng.nextFloat() * 0.75f,
                size = 0.5f + rng.nextFloat(),
                alpha = 0.25f + rng.nextFloat() * 0.65f,
                phase = rng.nextFloat() * (2f * PI.toFloat())
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = frameNanos / 1_000_000_000f
        val w = size.width
        val h = size.height
        val windFactor = (windSpeedKmh.toFloat() / 40f).coerceIn(0.05f, 1.8f)

        // Nighttime twinkling starfield for clear or partly cloudy nights
        if (!isDay && (condition == WeatherCondition.CLEAR || condition == WeatherCondition.PARTLY_CLOUDY)) {
            particles.forEach { p ->
                val twinkle = 0.35f + 0.65f * ((sin(t * (1.5f + p.speed) + p.phase) + 1f) * 0.5f)
                val starY = p.yFraction * h * 0.58f
                val starX = p.xFraction * w
                drawCircle(
                    color = Color(0xFFF8FAFC).copy(alpha = p.alpha * twinkle * 0.85f),
                    radius = p.size * 1.9f,
                    center = Offset(starX, starY)
                )
            }
        }

        // Daytime sunbeams & warm floating dust motes
        if (isDay && (condition == WeatherCondition.CLEAR || condition == WeatherCondition.PARTLY_CLOUDY)) {
            val sunCenter = Offset(w * 0.84f, h * 0.14f)
            val pulse = 0.92f + 0.08f * sin(t * 0.7f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFDE68A).copy(alpha = 0.34f * pulse),
                        Color(0xFFFBBF24).copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = sunCenter,
                    radius = w * 0.45f
                ),
                radius = w * 0.45f,
                center = sunCenter
            )
            if (condition == WeatherCondition.CLEAR) {
                particles.take(24).forEach { p ->
                    val driftX = ((p.xFraction + t * 0.018f * windFactor) % 1.0f) * w
                    val driftY = (p.yFraction * h * 0.65f) + sin(t * 0.9f + p.phase) * 14f
                    drawCircle(
                        color = Color(0xFFFEF3C7).copy(alpha = p.alpha * 0.32f),
                        radius = p.size * 2.5f,
                        center = Offset(driftX, driftY)
                    )
                }
            }
        }

        // Drifting cloud wisps / fog bands
        if (condition == WeatherCondition.PARTLY_CLOUDY ||
            condition == WeatherCondition.OVERCAST ||
            condition == WeatherCondition.FOG ||
            cloudCoverPercent >= 45
        ) {
            val isFog = condition == WeatherCondition.FOG
            val bandColor = if (isDay) Color(0xFFF1F5F9) else Color(0xFF94A3B8)
            val maxBandAlpha = if (isFog) 0.18f else 0.09f
            particles.take(12).forEachIndexed { index, p ->
                val bandWidth = w * (0.35f + p.size * 0.25f)
                val bandHeight = if (isFog) h * 0.12f else h * 0.08f
                val x = ((p.xFraction * 1.4f + t * 0.025f * (0.4f + windFactor) * p.speed) % 1.4f - 0.2f) * w
                val yBase = if (isFog) 0.28f + (index % 5) * 0.11f else 0.08f + (index % 4) * 0.09f
                val y = h * yBase + cos(t * 0.4f + p.phase) * 8f

                drawOval(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            bandColor.copy(alpha = maxBandAlpha * p.alpha),
                            Color.Transparent
                        ),
                        center = Offset(x, y),
                        radius = bandWidth * 0.55f
                    ),
                    topLeft = Offset(x - bandWidth * 0.5f, y - bandHeight * 0.5f),
                    size = androidx.compose.ui.geometry.Size(bandWidth, bandHeight)
                )
            }
        }

        // Rain, Drizzle, and Thunderstorm streaks
        if (condition == WeatherCondition.RAIN ||
            condition == WeatherCondition.DRIZZLE ||
            condition == WeatherCondition.THUNDERSTORM
        ) {
            val isDrizzle = condition == WeatherCondition.DRIZZLE
            val streakLength = if (isDrizzle) 18f else 34f
            val slantX = windFactor * (if (isDrizzle) 5f else 12f)
            val fallRate = if (isDrizzle) 0.42f else 0.85f
            val rainColor = if (isDay) Color(0xFFE0F2FE) else Color(0xFFBAE6FD)

            particles.forEach { p ->
                val progress = (p.yFraction + t * fallRate * (0.7f + p.speed * 0.6f)) % 1.0f
                val y = progress * h
                val x = ((p.xFraction + progress * 0.08f * windFactor) % 1.0f) * w
                drawLine(
                    color = rainColor.copy(alpha = p.alpha * (if (isDrizzle) 0.42f else 0.62f)),
                    start = Offset(x, y),
                    end = Offset(x - slantX, y + streakLength * p.size),
                    strokeWidth = if (isDrizzle) 1.4f else 2.1f,
                    cap = StrokeCap.Round
                )
            }

            // Periodic atmospheric lightning pulse during thunderstorms
            if (condition == WeatherCondition.THUNDERSTORM) {
                val cycle = t % 6.5f
                val flashAlpha = when {
                    cycle in 1.20f..1.29f -> 0.28f
                    cycle in 1.34f..1.42f -> 0.18f
                    else -> 0f
                }
                if (flashAlpha > 0f) {
                    drawRect(
                        color = Color(0xFFE0F2FE).copy(alpha = flashAlpha)
                    )
                }
            }
        }

        // Snowfall crystals with sinusoidal sway
        if (condition == WeatherCondition.SNOW) {
            particles.forEach { p ->
                val progress = (p.yFraction + t * 0.14f * (0.5f + p.speed * 0.7f)) % 1.0f
                val y = progress * h
                val sway = sin(t * 1.6f + p.phase) * (16f + windFactor * 12f)
                val x = (((p.xFraction * w) + sway + t * 18f * windFactor) % w + w) % w
                drawCircle(
                    color = Color.White.copy(alpha = p.alpha * 0.85f),
                    radius = 2.2f + p.size * 2.8f,
                    center = Offset(x, y)
                )
            }
        }
    }
}
