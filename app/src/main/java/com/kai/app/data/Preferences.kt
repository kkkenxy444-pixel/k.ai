package com.kai.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import java.io.IOException

private val Context.settingsStore by preferencesDataStore("preferences")
data class UserPreferences(
    val onboarded: Boolean = false,
    val theme: String = "dark",
    val normalModel: String = "",
    val roleplayModel: String = "",
    val ttsModel: String = "",
    val stream: Boolean = true,
    val autoSummarize: Boolean = true,
)

class Preferences(context: Context) {
    private val store = context.settingsStore
    val flow: Flow<UserPreferences> = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map {
        UserPreferences(it[booleanPreferencesKey("onboarded")] ?: false, it[stringPreferencesKey("theme")] ?: "dark",
            it[stringPreferencesKey("normalModel")].orEmpty(), it[stringPreferencesKey("roleplayModel")].orEmpty(),
            it[stringPreferencesKey("ttsModel")].orEmpty(), it[booleanPreferencesKey("stream")] ?: true,
            it[booleanPreferencesKey("autoSummarize")] ?: true)
    }
    suspend fun text(key: String, value: String) { store.edit { it[stringPreferencesKey(key)] = value } }
    suspend fun flag(key: String, value: Boolean) { store.edit { it[booleanPreferencesKey(key)] = value } }
}
