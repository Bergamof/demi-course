package com.demicourse.seance.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.demicourse.domain.AppSettings
import com.demicourse.domain.SeanceData
import com.demicourse.domain.StepSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "seance")

/**
 * Local-only persistence for the single current session, saved templates, and app settings.
 * There is no history and no backend: everything lives in one DataStore file so the app can
 * reopen on the session the user left it on, per the spec.
 */
class SeanceRepository(private val context: Context) {

    private object Keys {
        val STEPS = stringPreferencesKey("steps_json")
        val TEMPLATES = stringPreferencesKey("templates_json")
        val SETTINGS = stringPreferencesKey("settings_json")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val data: Flow<SeanceData> = context.dataStore.data.map { prefs ->
        SeanceData(
            steps = prefs[Keys.STEPS].decodeListOrEmpty(),
            templates = prefs[Keys.TEMPLATES].decodeListOrEmpty(),
            settings = prefs[Keys.SETTINGS]?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() }
                ?: AppSettings(),
        )
    }

    private fun String?.decodeListOrEmpty(): List<StepSpec> =
        this?.let { runCatching { json.decodeFromString<List<StepSpec>>(it) }.getOrNull() } ?: emptyList()

    suspend fun saveSteps(steps: List<StepSpec>) {
        context.dataStore.edit { it[Keys.STEPS] = json.encodeToString(steps) }
    }

    suspend fun saveTemplates(templates: List<StepSpec>) {
        context.dataStore.edit { it[Keys.TEMPLATES] = json.encodeToString(templates) }
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { it[Keys.SETTINGS] = json.encodeToString(settings) }
    }
}
