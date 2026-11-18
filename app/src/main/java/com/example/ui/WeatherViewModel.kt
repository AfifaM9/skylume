package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.DioramaGenerator
import com.example.data.local.PreferencesRepository
import com.example.data.location.CoarseLocationClient
import com.example.data.model.LocationItem
import com.example.data.model.TempUnit
import com.example.data.model.WeatherCondition
import com.example.data.remote.WeatherRepository
import com.google.firebase.ai.type.QuotaExceededException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "WeatherViewModel"

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val weatherRepository = WeatherRepository(application)
    private val preferencesRepository = PreferencesRepository(application)
    private val dioramaGenerator = DioramaGenerator(application)
    private val locationClient = CoarseLocationClient(application)

    private val _uiState = MutableStateFlow(WeatherVisualizerUiState())
    val uiState: StateFlow<WeatherVisualizerUiState> = _uiState.asStateFlow()

    private var loadLocationJob: Job? = null
    private var dioramaJob: Job? = null
    private var searchJob: Job? = null

    init {
        observePreferences()
        viewModelScope.launch {
            val initialLocation = preferencesRepository.activeLocationFlow.first()
            selectLocation(initialLocation, persistAsActive = false)
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesRepository.savedLocationsFlow.collect { saved ->
                _uiState.update { it.copy(savedLocations = saved) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.tempUnitFlow.collect { unit ->
                _uiState.update { it.copy(tempUnit = unit) }
            }
        }
    }

    fun selectLocation(
        location: LocationItem,
        persistAsActive: Boolean = true,
        saveToHistory: Boolean = false
    ) {
        loadLocationJob?.cancel()
        dioramaJob?.cancel()

        loadLocationJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeLocation = location,
                    weatherState = WeatherDataState.Loading,
                    dioramaState = DioramaUiState.Loading(location.name),
                    transientMessage = null
                )
            }

            if (persistAsActive) {
                preferencesRepository.setActiveLocation(location)
            }
            if (saveToHistory) {
                preferencesRepository.saveLocation(location)
            }

            try {
                val snapshot = weatherRepository.fetchWeather(location)
                _uiState.update { it.copy(weatherState = WeatherDataState.Success(snapshot)) }
                generateDiorama(
                    location = location,
                    condition = snapshot.condition,
                    isDay = snapshot.isDay,
                    forceRegenerate = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch live weather for ${location.name}", e)
                _uiState.update {
                    it.copy(
                        weatherState = WeatherDataState.Error(
                            e.localizedMessage ?: "Unable to fetch live weather data."
                        )
                    )
                }
                generateDiorama(
                    location = location,
                    condition = WeatherCondition.PARTLY_CLOUDY,
                    isDay = true,
                    forceRegenerate = false
                )
            }
        }
    }

    fun refreshWeatherAndDiorama(forceRegenerateDiorama: Boolean = false) {
        val currentLocation = _uiState.value.activeLocation ?: return
        if (forceRegenerateDiorama) {
            val currentWeather = (_uiState.value.weatherState as? WeatherDataState.Success)?.snapshot
            if (currentWeather != null) {
                generateDiorama(
                    location = currentLocation,
                    condition = currentWeather.condition,
                    isDay = currentWeather.isDay,
                    forceRegenerate = true
                )
            } else {
                selectLocation(currentLocation, persistAsActive = false)
            }
        } else {
            selectLocation(currentLocation, persistAsActive = false)
        }
    }

    fun retryDioramaOnly() {
        val currentLocation = _uiState.value.activeLocation ?: return
        val currentWeather = (_uiState.value.weatherState as? WeatherDataState.Success)?.snapshot
        generateDiorama(
            location = currentLocation,
            condition = currentWeather?.condition ?: WeatherCondition.PARTLY_CLOUDY,
            isDay = currentWeather?.isDay ?: true,
            forceRegenerate = true
        )
    }

    private fun generateDiorama(
        location: LocationItem,
        condition: WeatherCondition,
        isDay: Boolean,
        forceRegenerate: Boolean
    ) {
        dioramaJob?.cancel()
        dioramaJob = viewModelScope.launch {
            _uiState.update { it.copy(dioramaState = DioramaUiState.Loading(location.name)) }
            try {
                val result = dioramaGenerator.generateLandmarkCluster(
                    location = location,
                    condition = condition,
                    isDay = isDay,
                    forceRegenerate = forceRegenerate
                )
                _uiState.update {
                    it.copy(
                        dioramaState = DioramaUiState.Success(
                            bitmap = result.bitmap,
                            cityName = location.name,
                            fromCache = result.fromCache
                        )
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini diorama generation timed out", e)
                _uiState.update {
                    it.copy(
                        dioramaState = DioramaUiState.Error(
                            "3D diorama generation timed out after 60 seconds. Tap Retry."
                        )
                    )
                }
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Gemini quota exceeded", e)
                _uiState.update {
                    it.copy(
                        dioramaState = DioramaUiState.Error(
                            "Gemini image quota exceeded or Blaze billing plan required."
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini diorama generation failed", e)
                _uiState.update {
                    it.copy(
                        dioramaState = DioramaUiState.Error(
                            e.localizedMessage ?: "Failed to generate 3D miniature landmarks."
                        )
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchState = it.searchState.copy(
                    query = query,
                    errorMessage = null
                )
            )
        }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update {
                it.copy(
                    searchState = it.searchState.copy(
                        isSearching = false,
                        results = emptyList(),
                        errorMessage = null
                    )
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(320L)
            _uiState.update {
                it.copy(searchState = it.searchState.copy(isSearching = true, errorMessage = null))
            }
            try {
                val results = weatherRepository.searchCities(query)
                _uiState.update {
                    it.copy(
                        searchState = it.searchState.copy(
                            isSearching = false,
                            results = results,
                            errorMessage = if (results.isEmpty()) "No matching cities found." else null
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "City search failed", e)
                _uiState.update {
                    it.copy(
                        searchState = it.searchState.copy(
                            isSearching = false,
                            errorMessage = e.localizedMessage ?: "City search failed."
                        )
                    )
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchState = LocationSearchState()) }
    }

    fun removeSavedLocation(location: LocationItem) {
        viewModelScope.launch {
            preferencesRepository.removeSavedLocation(location)
        }
    }

    fun toggleSavedLocation(location: LocationItem) {
        viewModelScope.launch {
            val isSaved = _uiState.value.savedLocations.any {
                it.name.equals(location.name, ignoreCase = true) &&
                    it.country.equals(location.country, ignoreCase = true)
            }
            if (isSaved) {
                preferencesRepository.removeSavedLocation(location)
            } else {
                preferencesRepository.saveLocation(location)
            }
        }
    }

    fun toggleTempUnit() {
        viewModelScope.launch {
            val next = if (_uiState.value.tempUnit == TempUnit.CELSIUS) {
                TempUnit.FAHRENHEIT
            } else {
                TempUnit.CELSIUS
            }
            preferencesRepository.setTempUnit(next)
        }
    }

    fun toggleImmersiveMode() {
        _uiState.update { it.copy(isImmersiveMode = !it.isImmersiveMode) }
    }

    fun loadCurrentCoarseLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocatingGps = true, transientMessage = null) }
            val result = locationClient.getCurrentLocation()
            _uiState.update { it.copy(isLocatingGps = false) }
            result
                .onSuccess { location ->
                    selectLocation(location, persistAsActive = true, saveToHistory = true)
                }
                .onFailure { error ->
                    Log.e(TAG, "Location lookup failed", error)
                    _uiState.update {
                        it.copy(
                            transientMessage = error.localizedMessage
                                ?: "Could not obtain device location."
                        )
                    }
                }
        }
    }

    fun onLocationPermissionDenied() {
        _uiState.update {
            it.copy(
                transientMessage = "Location permission denied. Search any city manually instead."
            )
        }
    }

    fun dismissTransientMessage() {
        _uiState.update { it.copy(transientMessage = null) }
    }
}
