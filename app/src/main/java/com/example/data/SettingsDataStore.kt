package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_settings")

class SettingsDataStore(context: Context) {
    private val dataStore = context.dataStore

    fun getCategoryEnabled(category: NotificationCategory): Flow<Boolean> {
        val key = booleanPreferencesKey(category.name)
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[key] ?: true // Default to true
            }
    }

    suspend fun setCategoryEnabled(category: NotificationCategory, isEnabled: Boolean) {
        val key = booleanPreferencesKey(category.name)
        dataStore.edit { preferences ->
            preferences[key] = isEnabled
        }
    }

    fun getCategoryRecordingEnabled(category: NotificationCategory): Flow<Boolean> {
        val key = booleanPreferencesKey("${category.name}_RECORDING")
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[key] ?: true // Default to true
            }
    }

    suspend fun setCategoryRecordingEnabled(category: NotificationCategory, isEnabled: Boolean) {
        val key = booleanPreferencesKey("${category.name}_RECORDING")
        dataStore.edit { preferences ->
            preferences[key] = isEnabled
        }
    }

}