package com.demicourse.domain

import kotlinx.serialization.Serializable

@Serializable
enum class PaceMode { SINGLE, RANGE }

@Serializable
enum class Measure { DISTANCE, DURATION }

@Serializable
enum class HalfBy { DISTANCE, DURATION }

@Serializable
enum class PaceUnit { MIN_PER_KM, MIN_PER_MILE }

@Serializable
enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/**
 * Shape shared by a session step and a saved template — mirrors the prototype,
 * where templates and steps are the same record shape plus a name.
 *
 * Numeric fields are kept as raw user-entered strings (mm.ss / km) so an
 * incomplete or invalid draft can still round-trip through the editor,
 * exactly like the prototype's text inputs.
 */
@Serializable
data class StepSpec(
    val id: String,
    val name: String = "",
    val paceMode: PaceMode = PaceMode.SINGLE,
    val pace: String = "",
    val paceMax: String = "",
    val measure: Measure = Measure.DURATION,
    val value: String = "",
    val reps: Int = 1,
    val recovery: Boolean = false,
    val recDur: String = "",
    val recPaceMode: PaceMode = PaceMode.SINGLE,
    val recPace: String = "",
    val recPaceMax: String = "",
)

@Serializable
data class RecoveryDefaults(
    val recPaceMode: PaceMode = PaceMode.RANGE,
    val recPace: String = "9.00",
    val recPaceMax: String = "11.00",
)

@Serializable
data class AppSettings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val unit: PaceUnit = PaceUnit.MIN_PER_KM,
    val halfBy: HalfBy = HalfBy.DISTANCE,
    val showHints: Boolean = true,
    val recovery: RecoveryDefaults = RecoveryDefaults(),
)

@Serializable
data class SeanceData(
    val steps: List<StepSpec> = emptyList(),
    val templates: List<StepSpec> = emptyList(),
    val settings: AppSettings = AppSettings(),
)
