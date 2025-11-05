package com.android.chatalystai.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Create the DataStore instance
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object ThemeHelper {
    private val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")

    /**
     * Returns a Flow that emits the current dark mode preference.
     */
    fun isDarkTheme(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[IS_DARK_MODE] ?: false // Default to false (light mode)
        }
    }

    /**
     * Saves the user's dark mode preference.
     */
    suspend fun setDarkTheme(context: Context, isDark: Boolean) {
        context.dataStore.edit { settings ->
            settings[IS_DARK_MODE] = isDark
        }
    }
}