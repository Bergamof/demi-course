package com.demicourse.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The JSON the app stores on the phone. Reading is deliberately forgiving: an unknown field
 * (written by a newer version) is ignored, a missing one takes its default, and a corrupted blob
 * falls back to an empty list or default settings instead of crashing at launch. The enum
 * constant names are the stored values: renaming one breaks data already on users' phones.
 */
object SeanceJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeSteps(steps: List<StepSpec>): String = json.encodeToString(steps)

    fun encodeSettings(settings: AppSettings): String = json.encodeToString(settings)

    fun decodeSteps(raw: String?): List<StepSpec> =
        raw?.let { runCatching { json.decodeFromString<List<StepSpec>>(it) }.getOrNull() } ?: emptyList()

    fun decodeSettings(raw: String?): AppSettings =
        raw?.let { runCatching { json.decodeFromString<AppSettings>(it) }.getOrNull() } ?: AppSettings()
}
