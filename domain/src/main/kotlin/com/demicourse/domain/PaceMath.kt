package com.demicourse.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Outcome of parsing a user-entered field: either a value, or a French error message. */
data class ParseOutcome<T>(val value: T? = null, val error: String? = null) {
    val ok: Boolean get() = error == null
}

private val MMSS_RE = Regex("^\\d{1,3}([.:]\\d{1,2})?$")
private val DIST_RE = Regex("^\\d{1,3}(\\.\\d{1,3})?$")

object PaceMath {

    /** Parses an "mm.ss" (or "mm:ss") duration/pace field into whole seconds. */
    fun parseMmSs(raw: String?): ParseOutcome<Int> {
        val s = (raw ?: "").trim().replace(',', '.')
        if (s.isEmpty()) return ParseOutcome(error = "Requis")
        if (!MMSS_RE.matches(s)) return ParseOutcome(error = "Format attendu : mm.ss")
        val parts = s.split('.', ':')
        val m = parts[0].toInt()
        val ss = if (parts.size < 2) 0 else parts[1].toInt()
        if (ss > 59) return ParseOutcome(error = "Les secondes doivent être inférieures à 60")
        val sec = m * 60 + ss
        if (sec <= 0) return ParseOutcome(error = "Doit être supérieur à zéro")
        return ParseOutcome(value = sec)
    }

    /** Parses a distance field (kilometers or miles, unit-agnostic) as a decimal. */
    fun parseDistance(raw: String?): ParseOutcome<Double> {
        val s = (raw ?: "").trim().replace(',', '.')
        if (s.isEmpty()) return ParseOutcome(error = "Requis")
        if (!DIST_RE.matches(s)) return ParseOutcome(error = "Nombre attendu, ex. 1,5")
        val v = s.toDouble()
        if (!(v > 0)) return ParseOutcome(error = "Doit être supérieur à zéro")
        return ParseOutcome(value = v)
    }

    private fun pad2(n: Int) = n.toString().padStart(2, '0')

    /** Formats whole/fractional seconds back as "m.ss". */
    fun formatSeconds(sec: Double): String {
        val t = sec.roundToInt()
        return "${floor(t / 60.0).toInt()}.${pad2(t % 60)}"
    }

    /** Formats a distance with two decimals, French locale (comma separator). */
    fun formatDistance(km: Double): String = String.format(Locale.FRANCE, "%.2f", km)

    /** Renders a min/max pair as a single value when they're within eps of each other, else a range. */
    fun formatRange(a: Double, b: Double, fmt: (Double) -> String, eps: Double): String =
        if (abs(b - a) < eps) fmt(a) else "${fmt(a)}–${fmt(b)}"

    fun unitDistanceLabel(unit: PaceUnit) = if (unit == PaceUnit.MIN_PER_MILE) "mi" else "km"
    fun unitPaceLabel(unit: PaceUnit) = if (unit == PaceUnit.MIN_PER_MILE) "/mi" else "/km"

    /** Result of resolving one pace+length pair (a step's run, or its recovery) into distance/time bounds. */
    data class PieceResult(
        val ok: Boolean,
        val dMin: Double = 0.0,
        val dMax: Double = 0.0,
        val tMin: Double = 0.0,
        val tMax: Double = 0.0,
        val dMid: Double = 0.0,
        val tMid: Double = 0.0,
    )

    private val FAILED_PIECE = PieceResult(ok = false)

    /**
     * Resolves a pace (single or range) and a length (distance or duration) into
     * a distance/time envelope. Pace is seconds-per-unit-distance; when the length
     * is a distance, time = distance × pace; when it's a duration, distance = time ÷ pace.
     */
    fun piece(paceMode: PaceMode, pace: String, paceMax: String, measure: Measure, value: String): PieceResult {
        val a = parseMmSs(pace)
        if (!a.ok) return FAILED_PIECE
        val b = if (paceMode == PaceMode.RANGE) parseMmSs(paceMax) else a
        if (!b.ok) return FAILED_PIECE
        val fast = min(a.value!!, b.value!!).toDouble()
        val slow = max(a.value, b.value).toDouble()
        val dMin: Double; val dMax: Double; val tMin: Double; val tMax: Double
        if (measure == Measure.DISTANCE) {
            val d = parseDistance(value)
            if (!d.ok) return FAILED_PIECE
            dMin = d.value!!; dMax = d.value
            tMin = d.value * fast; tMax = d.value * slow
        } else {
            val t = parseMmSs(value)
            if (!t.ok) return FAILED_PIECE
            tMin = t.value!!.toDouble(); tMax = tMin
            dMin = tMin / slow; dMax = tMin / fast
        }
        return PieceResult(ok = true, dMin = dMin, dMax = dMax, tMin = tMin, tMax = tMax, dMid = (dMin + dMax) / 2, tMid = (tMin + tMax) / 2)
    }

    /** Result of resolving a full step (its run piece, repeated `reps` times, plus optional recovery). */
    data class StepMetrics(
        val ok: Boolean,
        val reps: Int = 1,
        val run: PieceResult? = null,
        val rec: PieceResult? = null,
        val dMin: Double = 0.0,
        val dMax: Double = 0.0,
        val tMin: Double = 0.0,
        val tMax: Double = 0.0,
        val dMid: Double = 0.0,
        val tMid: Double = 0.0,
    )

    private val FAILED_METRICS = StepMetrics(ok = false)

    fun metrics(step: StepSpec): StepMetrics {
        val run = piece(step.paceMode, step.pace, step.paceMax, step.measure, step.value)
        if (!run.ok) return FAILED_METRICS
        val reps = max(1, step.reps)
        var rec: PieceResult? = null
        if (step.recovery) {
            rec = piece(step.recPaceMode, step.recPace, step.recPaceMax, Measure.DURATION, step.recDur)
            if (!rec.ok) return FAILED_METRICS
        }
        val r = rec ?: PieceResult(ok = true)
        return StepMetrics(
            ok = true, reps = reps, run = run, rec = rec,
            dMin = (run.dMin + r.dMin) * reps, dMax = (run.dMax + r.dMax) * reps,
            tMin = (run.tMin + r.tMin) * reps, tMax = (run.tMax + r.tMax) * reps,
            dMid = (run.dMid + (rec?.dMid ?: 0.0)) * reps, tMid = (run.tMid + (rec?.tMid ?: 0.0)) * reps,
        )
    }

    /** Default display name for a step with no custom name: its length, e.g. "5 km" or "10.00 min". */
    fun autoName(step: StepSpec, unit: PaceUnit): String {
        if (step.name.isNotBlank()) return step.name
        return if (step.measure == Measure.DISTANCE) {
            val d = parseDistance(step.value)
            if (!d.ok) "Étape" else formatDistance(d.value!!).replaceFirst(",00", "") + " " + unitDistanceLabel(unit)
        } else {
            val t = parseMmSs(step.value)
            if (!t.ok) "Étape" else formatSeconds(t.value!!.toDouble()) + " min"
        }
    }
}
