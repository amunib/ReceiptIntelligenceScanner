package com.receiptintel.scanner.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {

    private object Keys {
        val DARK_MODE_OVERRIDE = stringPreferencesKey("dark_mode_override") // "on" | "off" | "system"
        val LANGUAGE = stringPreferencesKey("language") // "en" | "am" | "ar"
        val EXPORT_FOLDER_URI = stringPreferencesKey("export_folder_uri")
    }

    val darkModeOverride: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.DARK_MODE_OVERRIDE]) {
            "on" -> true
            "off" -> false
            else -> null // follow system
        }
    }

    val language: Flow<String> = context.dataStore.data.map { it[Keys.LANGUAGE] ?: "en" }

    val exportFolderUri: Flow<String?> = context.dataStore.data.map { it[Keys.EXPORT_FOLDER_URI] }

    suspend fun setDarkModeOverride(value: Boolean?) {
        context.dataStore.edit {
            it[Keys.DARK_MODE_OVERRIDE] = when (value) {
                true -> "on"
                false -> "off"
                null -> "system"
            }
        }
    }

    suspend fun setLanguage(langCode: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = langCode }
    }

    suspend fun setExportFolderUri(uri: String) {
        context.dataStore.edit { it[Keys.EXPORT_FOLDER_URI] = uri }
    }
}
