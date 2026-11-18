package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.FeaturedLocations
import com.example.data.model.LocationItem
import com.example.data.model.TempUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.skylumeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "skylume_preferences"
)

class PreferencesRepository(private val context: Context) {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val locationAdapter = moshi.adapter(LocationItem::class.java)
    private val locationListAdapter = moshi.adapter<List<LocationItem>>(
        Types.newParameterizedType(List::class.java, LocationItem::class.java)
    )

    private object Keys {
        val ACTIVE_LOCATION = stringPreferencesKey("active_location")
        val SAVED_LOCATIONS = stringPreferencesKey("saved_locations")
        val TEMP_UNIT = stringPreferencesKey("temp_unit")
    }

    val activeLocationFlow: Flow<LocationItem> = context.skylumeDataStore.data.map { prefs ->
        prefs[Keys.ACTIVE_LOCATION]?.let { json ->
            runCatching { locationAdapter.fromJson(json) }.getOrNull()
        } ?: FeaturedLocations.curated.first()
    }

    val savedLocationsFlow: Flow<List<LocationItem>> = context.skylumeDataStore.data.map { prefs ->
        prefs[Keys.SAVED_LOCATIONS]?.let { json ->
            runCatching { locationListAdapter.fromJson(json) }.getOrNull()
        } ?: FeaturedLocations.curated.take(3)
    }

    val tempUnitFlow: Flow<TempUnit> = context.skylumeDataStore.data.map { prefs ->
        when (prefs[Keys.TEMP_UNIT]) {
            TempUnit.FAHRENHEIT.name -> TempUnit.FAHRENHEIT
            else -> TempUnit.CELSIUS
        }
    }

    suspend fun setActiveLocation(location: LocationItem) {
        context.skylumeDataStore.edit { prefs ->
            prefs[Keys.ACTIVE_LOCATION] = locationAdapter.toJson(location)
        }
    }

    suspend fun saveLocation(location: LocationItem) {
        context.skylumeDataStore.edit { prefs ->
            val current = prefs[Keys.SAVED_LOCATIONS]?.let { json ->
                runCatching { locationListAdapter.fromJson(json) }.getOrNull()
            } ?: FeaturedLocations.curated.take(3)

            val updated = buildList {
                add(location)
                addAll(current.filterNot {
                    it.name.equals(location.name, ignoreCase = true) &&
                        it.country.equals(location.country, ignoreCase = true)
                })
            }.take(12)

            prefs[Keys.SAVED_LOCATIONS] = locationListAdapter.toJson(updated)
        }
    }

    suspend fun removeSavedLocation(location: LocationItem) {
        context.skylumeDataStore.edit { prefs ->
            val current = prefs[Keys.SAVED_LOCATIONS]?.let { json ->
                runCatching { locationListAdapter.fromJson(json) }.getOrNull()
            } ?: emptyList()

            val updated = current.filterNot { it.id == location.id }
            prefs[Keys.SAVED_LOCATIONS] = locationListAdapter.toJson(updated)
        }
    }

    suspend fun setTempUnit(unit: TempUnit) {
        context.skylumeDataStore.edit { prefs ->
            prefs[Keys.TEMP_UNIT] = unit.name
        }
    }
}
