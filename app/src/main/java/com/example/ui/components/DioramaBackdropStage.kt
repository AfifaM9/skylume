package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.WeatherCondition
import com.example.ui.DioramaUiState
import com.example.ui.theme.GlassBorderLight
import com.example.ui.theme.GlassSurfaceDark
import com.example.ui.theme.SkyCerulean
import com.example.ui.theme.SkyDeepNavy

@Composable
fun DioramaBackdropStage(
    dioramaState: DioramaUiState,
    condition: WeatherCondition,
    isDay: Boolean,
    windSpeedKmh: Double,
    cloudCoverPercent: Int,
    onRetryDiorama: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skyColors = remember(condition, isDay) { condition.skyGradientColors(isDay) }
    var panOffsetPx by remember(dioramaState) { mutableFloatStateOf(0f) }

    val breathingTransition = rememberInfiniteTransition(label = "diorama_motion")
    val gentleFloatY by breathingTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "diorama_float_y"
    )
    val shimmerProgress by breathingTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "diorama_shimmer"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = skyColors))
            .pointerInput(dioramaState) {
                detectHorizontalDragGestures { _, dragAmount ->
                    panOffsetPx = (panOffsetPx + dragAmount * 0.55f).coerceIn(-120f, 120f)
                }
            }
    ) {
        // Gemini-generated 3D miniature horizontal cluster of landmarks & buildings
        AnimatedContent(
            targetState = dioramaState,
            transitionSpec = { fadeIn(tween(650)) togetherWith fadeOut(tween(400)) },
            label = "diorama_crossfade",
            modifier = Modifier.fillMaxSize()
        ) { state ->
            when (state) {
                is DioramaUiState.Success -> {
                    val imageBitmap = remember(state.bitmap) { state.bitmap.asImageBitmap() }
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "3D miniature landmarks skyline for ${state.cityName}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = 1.09f
                                    scaleY = 1.09f
                                    translationX = panOffsetPx
                                    translationY = gentleFloatY
                                }
                        )
                        // Soft atmospheric top/bottom sky blend scrims for crisp foreground legibility
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to skyColors.first().copy(alpha = 0.72f),
                                        0.24f to Color.Transparent,
                                        0.62f to Color.Transparent,
                                        0.86f to skyColors.last().copy(alpha = 0.65f),
                                        1.0f to SkyDeepNavy.copy(alpha = 0.94f)
                                    )
                                )
                        )
                    }
                }

                is DioramaUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        skyColors.first().copy(alpha = 0.45f),
                                        SkyCerulean.copy(alpha = 0.14f + shimmerProgress * 0.12f),
                                        skyColors.last().copy(alpha = 0.55f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = GlassSurfaceDark,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .border(1.dp, GlassBorderLight, RoundedCornerShape(24.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(26.dp),
                                    color = SkyCerulean,
                                    strokeWidth = 2.6.dp
                                )
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = SkyCerulean,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = stringResource(
                                                R.string.diorama_generating,
                                                state.cityName
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.diorama_generating_sub),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                is DioramaUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = GlassSurfaceDark,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "3D Skyline Unavailable",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = onRetryDiorama,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SkyCerulean,
                                        contentColor = SkyDeepNavy
                                    ),
                                    modifier = Modifier.testTag("retry_diorama_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = stringResource(R.string.retry))
                                }
                            }
                        }
                    }
                }

                DioramaUiState.Idle -> Unit
            }
        }

        // Live atmospheric weather particle canvas layered above the 3D miniature landmarks
        WeatherParticlesCanvas(
            condition = condition,
            isDay = isDay,
            windSpeedKmh = windSpeedKmh,
            cloudCoverPercent = cloudCoverPercent,
            modifier = Modifier.fillMaxSize()
        )
    }
}
