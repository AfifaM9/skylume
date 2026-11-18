package com.example.ui

import android.graphics.Bitmap
import com.example.data.model.LocationItem
import com.example.data.model.TempUnit
import com.example.data.model.WeatherSnapshot

sealed interface WeatherDataState {
    data object Loading : WeatherDataState
    data class Success(val snapshot: WeatherSnapshot) : WeatherDataState
    data class Error(val message: String) : WeatherDataState
}

sealed interface DioramaUiState {
    data object Idle : DioramaUiState
    data class Loading(val cityName: String) : DioramaUiState
    data class Success(
        val bitmap: Bitmap,
        val cityName: String,
        val fromCache: Boolean
    ) : DioramaUiState
    data class Error(val message: String) : DioramaUiState
}

data class LocationSearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<LocationItem> = emptyList(),
    val errorMessage: String? = null
)

data class WeatherVisualizerUiState(
    val activeLocation: LocationItem? = null,
    val weatherState: WeatherDataState = WeatherDataState.Loading,
    val dioramaState: DioramaUiState = DioramaUiState.Idle,
    val savedLocations: List<LocationItem> = emptyList(),
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val searchState: LocationSearchState = LocationSearchState(),
    val isImmersiveMode: Boolean = false,
    val isLocatingGps: Boolean = false,
    val transientMessage: String? = null
)
