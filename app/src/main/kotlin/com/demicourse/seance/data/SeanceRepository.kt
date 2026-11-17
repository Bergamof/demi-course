package com.demicourse.seance.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.demicourse.domain.AppSettings
import com.demicourse.domain.SeanceData
import com.demicourse.domain.SeanceJson
import com.demicourse.domain.StepSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "seance")

/**
 * Local-only persistence for the single current session, saved templates, and app settings.
 * There is no history and no backend: everything lives in one DataStore file so the app can
 * reopen on the session the user left it on, per the spec.
 */
class SeanceRepository(
    private val context: Context,
) {
    private object Keys {
        val STEPS = stringPreferencesKey("steps_json")
        val TEMPLATES = stringPreferencesKey("templates_json")
        val SETTINGS = stringPreferencesKey("settings_json")
    }

    val data: Flow<SeanceData> =
        context.dataStore.data.map { prefs ->
            SeanceData(
                steps = SeanceJson.decodeSteps(prefs[Keys.STEPS]),
                templates = SeanceJson.decodeSteps(prefs[Keys.TEMPLATES]),
                settings = SeanceJson.decodeSettings(prefs[Keys.SETTINGS]),
            )
        }

    suspend fun saveSteps(steps: List<StepSpec>) {
        context.dataStore.edit { it[Keys.STEPS] = SeanceJson.encodeSteps(steps) }
    }

    suspend fun saveTemplates(templates: List<StepSpec>) {
        context.dataStore.edit { it[Keys.TEMPLATES] = SeanceJson.encodeSteps(templates) }
    }

    suspend fun saveSettings(settings: AppSettings) {
        context.dataStore.edit { it[Keys.SETTINGS] = SeanceJson.encodeSettings(settings) }
    }
}
