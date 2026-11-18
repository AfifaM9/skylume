package com.example.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.LocationItem
import com.example.data.model.TempUnit
import com.example.data.model.WeatherCondition
import com.example.data.model.WeatherSnapshot
import com.example.data.model.formatTemp
import com.example.ui.components.DioramaBackdropStage
import com.example.ui.components.LocationSearchSheet
import com.example.ui.components.WeatherMetricsSection
import com.example.ui.components.iconTint
import com.example.ui.components.iconVector
import com.example.ui.theme.GlassBorderLight
import com.example.ui.theme.GlassSurfaceDark
import com.example.ui.theme.SkyCerulean
import com.example.ui.theme.SkyDeepNavy
import com.example.ui.theme.SunGold

@Composable
fun WeatherVisualizerScreen(viewModel: WeatherViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSearchSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.loadCurrentCoarseLocation()
        } else {
            viewModel.onLocationPermissionDenied()
        }
    }

    LaunchedEffect(uiState.transientMessage) {
        val msg = uiState.transientMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissTransientMessage()
        }
    }

    if (uiState.isImmersiveMode) {
        BackHandler {
            viewModel.toggleImmersiveMode()
        }
    }

    val snapshot = (uiState.weatherState as? WeatherDataState.Success)?.snapshot
    val activeCondition = snapshot?.condition ?: WeatherCondition.PARTLY_CLOUDY
    val isDay = snapshot?.isDay ?: true
    val windSpeed = snapshot?.windSpeedKmh ?: 8.0
    val cloudCover = snapshot?.cloudCoverPercent ?: 25

    Scaffold(
        containerColor = SkyDeepNavy,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isExpandedLayout = maxWidth >= 840.dp

            // Full-bleed Gemini 3D miniature landmark cluster + live weather particle canvas
            DioramaBackdropStage(
                dioramaState = uiState.dioramaState,
                condition = activeCondition,
                isDay = isDay,
                windSpeedKmh = windSpeed,
                cloudCoverPercent = cloudCover,
                onRetryDiorama = { viewModel.retryDioramaOnly() },
                modifier = Modifier.fillMaxSize()
            )

            if (isExpandedLayout) {
                ExpandedStudioLayout(
                    uiState = uiState,
                    snapshot = snapshot,
                    onOpenSearch = { showSearchSheet = true },
                    onRequestGps = {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    onRegenerateDiorama = { viewModel.refreshWeatherAndDiorama(forceRegenerateDiorama = true) },
                    onToggleUnit = { viewModel.toggleTempUnit() },
                    onToggleImmersive = { viewModel.toggleImmersiveMode() },
                    onToggleSave = { loc -> viewModel.toggleSavedLocation(loc) },
                    onRetryWeather = { viewModel.refreshWeatherAndDiorama(forceRegenerateDiorama = false) }
                )
            } else {
                CompactVisualizerLayout(
                    uiState = uiState,
                    snapshot = snapshot,
                    onOpenSearch = { showSearchSheet = true },
                    onRequestGps = {
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    onRegenerateDiorama = { viewModel.refreshWeatherAndDiorama(forceRegenerateDiorama = true) },
                    onToggleUnit = { viewModel.toggleTempUnit() },
                    onToggleImmersive = { viewModel.toggleImmersiveMode() },
                    onToggleSave = { loc -> viewModel.toggleSavedLocation(loc) },
                    onRetryWeather = { viewModel.refreshWeatherAndDiorama(forceRegenerateDiorama = false) }
                )
            }
        }
    }

    if (showSearchSheet) {
        LocationSearchSheet(
            searchState = uiState.searchState,
            savedLocations = uiState.savedLocations,
            activeLocation = uiState.activeLocation,
            isLocatingGps = uiState.isLocatingGps,
            onQueryChange = { viewModel.onSearchQueryChanged(it) },
            onClearSearch = { viewModel.clearSearch() },
            onSelectLocation = { location ->
                viewModel.selectLocation(location, persistAsActive = true, saveToHistory = true)
            },
            onToggleSaveLocation = { viewModel.toggleSavedLocation(it) },
            onRemoveSavedLocation = { viewModel.removeSavedLocation(it) },
            onRequestCurrentLocation = {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onDismiss = { showSearchSheet = false }
        )
    }
}

@Composable
private fun CompactVisualizerLayout(
    uiState: WeatherVisualizerUiState,
    snapshot: WeatherSnapshot?,
    onOpenSearch: () -> Unit,
    onRequestGps: () -> Unit,
    onRegenerateDiorama: () -> Unit,
    onToggleUnit: () -> Unit,
    onToggleImmersive: () -> Unit,
    onToggleSave: (LocationItem) -> Unit,
    onRetryWeather: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TopAtmosphereBar(
            activeLocation = uiState.activeLocation,
            savedLocations = uiState.savedLocations,
            tempUnit = uiState.tempUnit,
            isImmersiveMode = uiState.isImmersiveMode,
            isLocatingGps = uiState.isLocatingGps,
            isDioramaLoading = uiState.dioramaState is DioramaUiState.Loading,
            onOpenSearch = onOpenSearch,
            onRequestGps = onRequestGps,
            onRegenerateDiorama = onRegenerateDiorama,
            onToggleUnit = onToggleUnit,
            onToggleImmersive = onToggleImmersive,
            onToggleSave = onToggleSave
        )

        if (uiState.isImmersiveMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(20.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                snapshot?.let {
                    CompactImmersivePill(
                        snapshot = it,
                        tempUnit = uiState.tempUnit,
                        onExitImmersive = onToggleImmersive
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Leave breathing room in the upper hero so the 3D miniature horizontal landmark cluster shines
                Spacer(modifier = Modifier.height(210.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HeroWeatherHeader(
                        weatherState = uiState.weatherState,
                        tempUnit = uiState.tempUnit,
                        onRetryWeather = onRetryWeather
                    )

                    AnimatedVisibility(
                        visible = snapshot != null,
                        enter = fadeIn() + slideInVertically { it / 8 },
                        exit = fadeOut() + slideOutVertically { it / 8 }
                    ) {
                        snapshot?.let { data ->
                            WeatherMetricsSection(
                                snapshot = data,
                                tempUnit = uiState.tempUnit
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ExpandedStudioLayout(
    uiState: WeatherVisualizerUiState,
    snapshot: WeatherSnapshot?,
    onOpenSearch: () -> Unit,
    onRequestGps: () -> Unit,
    onRegenerateDiorama: () -> Unit,
    onToggleUnit: () -> Unit,
    onToggleImmersive: () -> Unit,
    onToggleSave: (LocationItem) -> Unit,
    onRetryWeather: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        TopAtmosphereBar(
            activeLocation = uiState.activeLocation,
            savedLocations = uiState.savedLocations,
            tempUnit = uiState.tempUnit,
            isImmersiveMode = uiState.isImmersiveMode,
            isLocatingGps = uiState.isLocatingGps,
            isDioramaLoading = uiState.dioramaState is DioramaUiState.Loading,
            onOpenSearch = onOpenSearch,
            onRequestGps = onRequestGps,
            onRegenerateDiorama = onRegenerateDiorama,
            onToggleUnit = onToggleUnit,
            onToggleImmersive = onToggleImmersive,
            onToggleSave = onToggleSave
        )

        if (uiState.isImmersiveMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(24.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                snapshot?.let {
                    CompactImmersivePill(
                        snapshot = it,
                        tempUnit = uiState.tempUnit,
                        onExitImmersive = onToggleImmersive
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Left Pane: Hero diorama viewport & live weather summary
                Box(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomStart
                ) {
                    HeroWeatherHeader(
                        weatherState = uiState.weatherState,
                        tempUnit = uiState.tempUnit,
                        onRetryWeather = onRetryWeather,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Right Pane: Scrollable forecast & atmospheric telemetry
                Box(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(bottom = 20.dp)
                    ) {
                        snapshot?.let { data ->
                            WeatherMetricsSection(
                                snapshot = data,
                                tempUnit = uiState.tempUnit
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopAtmosphereBar(
    activeLocation: LocationItem?,
    savedLocations: List<LocationItem>,
    tempUnit: TempUnit,
    isImmersiveMode: Boolean,
    isLocatingGps: Boolean,
    isDioramaLoading: Boolean,
    onOpenSearch: () -> Unit,
    onRequestGps: () -> Unit,
    onRegenerateDiorama: () -> Unit,
    onToggleUnit: () -> Unit,
    onToggleImmersive: () -> Unit,
    onToggleSave: (LocationItem) -> Unit
) {
    val isSaved = activeLocation != null && savedLocations.any {
        it.name.equals(activeLocation.name, ignoreCase = true) &&
            it.country.equals(activeLocation.country, ignoreCase = true)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // City Selector Pill
        Surface(
            color = GlassSurfaceDark,
            shape = CircleShape,
            border = BorderStroke(1.dp, GlassBorderLight),
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(CircleShape)
                .clickable(onClick = onOpenSearch)
                .minimumInteractiveComponentSize()
                .testTag("location_selector_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = stringResource(R.string.cd_search_location),
                    tint = SkyCerulean,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = activeLocation?.name ?: "Select City",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Save / Bookmark Current City
            if (activeLocation != null) {
                CircleGlassIconButton(
                    onClick = { onToggleSave(activeLocation) },
                    testTag = "bookmark_location_button"
                ) {
                    Icon(
                        imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (isSaved) "Remove saved city" else "Save city",
                        tint = if (isSaved) SunGold else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // GPS Coarse Location Button
            CircleGlassIconButton(
                onClick = onRequestGps,
                enabled = !isLocatingGps,
                testTag = "gps_location_button"
            ) {
                if (isLocatingGps) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = SkyCerulean,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = stringResource(R.string.cd_use_current_location),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Regenerate 3D Miniature Skyline via Gemini
            CircleGlassIconButton(
                onClick = onRegenerateDiorama,
                enabled = !isDioramaLoading,
                testTag = "regenerate_diorama_button"
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = stringResource(R.string.cd_regenerate_diorama),
                    tint = if (isDioramaLoading) SkyCerulean.copy(alpha = 0.5f) else SkyCerulean,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Temperature Unit Toggle (°C / °F)
            Surface(
                color = GlassSurfaceDark,
                shape = CircleShape,
                border = BorderStroke(1.dp, GlassBorderLight),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onToggleUnit)
                    .minimumInteractiveComponentSize()
                    .testTag("unit_toggle_button")
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tempUnit.symbol,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Immersive Diorama View Toggle
            CircleGlassIconButton(
                onClick = onToggleImmersive,
                testTag = "immersive_toggle_button"
            ) {
                Icon(
                    imageVector = if (isImmersiveMode) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = stringResource(R.string.cd_toggle_immersive),
                    tint = if (isImmersiveMode) SunGold else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CircleGlassIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    testTag: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = GlassSurfaceDark,
        shape = CircleShape,
        border = BorderStroke(1.dp, GlassBorderLight),
        modifier = Modifier
            .size(44.dp)
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled
        ) {
            content()
        }
    }
}

@Composable
private fun HeroWeatherHeader(
    weatherState: WeatherDataState,
    tempUnit: TempUnit,
    onRetryWeather: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (weatherState) {
        is WeatherDataState.Loading -> {
            Surface(
                color = GlassSurfaceDark,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, GlassBorderLight),
                modifier = modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = SkyCerulean,
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = "Fetching live meteorological telemetry…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        is WeatherDataState.Error -> {
            Surface(
                color = GlassSurfaceDark,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Weather Unavailable",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = weatherState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onRetryWeather,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SkyCerulean,
                            contentColor = SkyDeepNavy
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("retry_weather_button")
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

        is WeatherDataState.Success -> {
            val snapshot = weatherState.snapshot
            Surface(
                color = Color.Transparent,
                modifier = modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    SkyDeepNavy.copy(alpha = 0.45f),
                                    SkyDeepNavy.copy(alpha = 0.78f)
                                )
                            )
                        )
                        .border(1.dp, GlassBorderLight, RoundedCornerShape(28.dp))
                        .padding(horizontal = 22.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = snapshot.condition.iconVector(snapshot.isDay),
                                    contentDescription = snapshot.condition.label,
                                    tint = snapshot.condition.iconTint(snapshot.isDay),
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = snapshot.condition.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = snapshot.currentTempC.formatTemp(tempUnit),
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.testTag("hero_temperature_text")
                            )
                            if (snapshot.location.displaySubtitle.isNotEmpty()) {
                                Text(
                                    text = snapshot.location.displaySubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = Color.White.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "H ${snapshot.highTempC.formatTemp(tempUnit)}  •  L ${snapshot.lowTempC.formatTemp(tempUnit)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            Text(
                                text = "Feels ${snapshot.apparentTempC.formatTemp(tempUnit, includeUnit = true)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Swipe skyline to pan",
                                style = MaterialTheme.typography.labelSmall,
                                color = SkyCerulean.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactImmersivePill(
    snapshot: WeatherSnapshot,
    tempUnit: TempUnit,
    onExitImmersive: () -> Unit
) {
    Surface(
        color = GlassSurfaceDark,
        shape = CircleShape,
        border = BorderStroke(1.dp, GlassBorderLight),
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onExitImmersive)
            .minimumInteractiveComponentSize()
            .testTag("exit_immersive_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = snapshot.condition.iconVector(snapshot.isDay),
                contentDescription = snapshot.condition.label,
                tint = snapshot.condition.iconTint(snapshot.isDay),
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "${snapshot.location.name} • ${snapshot.currentTempC.formatTemp(tempUnit, includeUnit = true)} • ${snapshot.condition.label}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
