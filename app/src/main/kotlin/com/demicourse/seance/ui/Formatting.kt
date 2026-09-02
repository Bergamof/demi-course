package com.demicourse.seance.ui

import com.demicourse.domain.Measure
import com.demicourse.domain.PaceMath
import com.demicourse.domain.PaceMath.formatDistance
import com.demicourse.domain.PaceMath.formatRange
import com.demicourse.domain.PaceMath.formatSeconds
import com.demicourse.domain.PaceMode
import com.demicourse.domain.PaceUnit
import com.demicourse.domain.SegmentKind
import com.demicourse.domain.SessionResult
import com.demicourse.domain.StepSpec
import com.demicourse.domain.TurnPoint

/** Display strings for the main screen and editor sheet — a direct port of the prototype's renderVals(). */
object Formatting {

    fun stepCountText(count: Int) = "$count " + if (count > 1) "étapes" else "étape"

    fun paceText(step: StepSpec, unit: PaceUnit): String {
        val base = if (step.paceMode == PaceMode.RANGE) "${step.pace}–${step.paceMax}" else step.pace
        return "$base ${PaceMath.unitPaceLabel(unit)}"
    }

    fun measureText(step: StepSpec, unit: PaceUnit): String = if (step.measure == Measure.DISTANCE) {
        val km = PaceMath.parseDistance(step.value).value ?: 0.0
        "${formatDistance(km)} ${PaceMath.unitDistanceLabel(unit)}"
    } else {
        val sec = PaceMath.parseMmSs(step.value).value ?: 0
        "${formatSeconds(sec.toDouble())} min"
    }

    fun recoveryChipText(step: StepSpec, unit: PaceUnit): String {
        val pace = if (step.recPaceMode == PaceMode.RANGE) "${step.recPace}–${step.recPaceMax}" else step.recPace
        return "récup ${step.recDur} à $pace ${PaceMath.unitPaceLabel(unit)}"
    }

    fun totalsText(step: StepSpec, unit: PaceUnit): String {
        val m = PaceMath.metrics(step)
        if (!m.ok) return "Valeurs incomplètes"
        val uD = PaceMath.unitDistanceLabel(unit)
        val dist = formatRange(m.dMin, m.dMax, { formatDistance(it) }, 0.005)
        val dur = formatRange(m.tMin, m.tMax, { formatSeconds(it) }, 1.0)
        return "≈ $dist $uD  ·  $dur min"
    }

    fun turnaroundMarkerText(turn: TurnPoint, stepCount: Int, unit: PaceUnit): String {
        val repSuffix = if (turn.reps > 1) " de la répétition ${turn.rep} sur ${turn.reps}" else ""
        val where = if (turn.kind == SegmentKind.REC) "pendant la récupération$repSuffix" else "pendant l’effort$repSuffix"
        val uD = PaceMath.unitDistanceLabel(unit)
        return "Demi‑tour $where — ${formatDistance(turn.intoDistance)} $uD après son début (${formatSeconds(turn.intoTime)})."
    }

    fun totalDistanceText(session: SessionResult, unit: PaceUnit): String =
        if (session.dMid > 0) formatRange(session.dMin, session.dMax, { formatDistance(it) }, 0.005) else "—"

    fun totalDurationText(session: SessionResult): String =
        if (session.tMid > 0) formatRange(session.tMin, session.tMax, { formatSeconds(it) }, 1.0) else "—"

    fun turnMainText(session: SessionResult, unit: PaceUnit): String =
        session.turn?.let { "${formatDistance(it.distance)} ${PaceMath.unitDistanceLabel(unit)}" } ?: "—"

    fun turnSubText(session: SessionResult, stepCount: Int): String {
        val turn = session.turn ?: return "Ajoutez des étapes pour connaître le point de demi‑tour."
        return "après ${formatSeconds(turn.time)} min de course · étape ${turn.stepIndex + 1} sur $stepCount"
    }

    fun templateHint(template: StepSpec, unit: PaceUnit): String = if (template.pace.isNotBlank()) {
        val pace = if (template.paceMode == PaceMode.RANGE) "${template.pace}–${template.paceMax}" else template.pace
        "$pace ${PaceMath.unitPaceLabel(unit)}"
    } else if (template.measure == Measure.DISTANCE) {
        "${template.value} ${PaceMath.unitDistanceLabel(unit)}"
    } else {
        "${template.value} min"
    }

    fun templateDetail(template: StepSpec, unit: PaceUnit): String {
        val parts = mutableListOf<String>()
        if (template.pace.isNotBlank()) {
            val pace = if (template.paceMode == PaceMode.RANGE) "${template.pace}–${template.paceMax}" else template.pace
            parts += "$pace ${PaceMath.unitPaceLabel(unit)}"
        }
        if (template.value.isNotBlank()) {
            parts += if (template.measure == Measure.DISTANCE) "${template.value} ${PaceMath.unitDistanceLabel(unit)}" else "${template.value} min"
        }
        if (template.recovery) parts += "récup ${template.recDur}"
        return parts.joinToString("  ·  ")
    }

    /** The hint line at the bottom of the step/template editor sheet. */
    fun previewLine(draft: Draft, isTemplate: Boolean, unit: PaceUnit): String {
        if (isTemplate) return "Un modèle peut ne définir que l’allure, que la longueur, ou les deux — laissez vide ce que vous voulez remplir plus tard."
        val step = StepSpec(
            id = "", paceMode = draft.paceMode, pace = draft.pace, paceMax = draft.paceMax,
            measure = draft.measure, value = draft.value, reps = draft.reps.toIntOrNull() ?: 1,
            recovery = draft.recovery, recDur = draft.recDur,
            recPaceMode = draft.recPaceMode, recPace = draft.recPace, recPaceMax = draft.recPaceMax,
        )
        val m = PaceMath.metrics(step)
        if (!m.ok) return "Allures et durées au format mm.ss — 1.30 = 1 min 30 s, 2.1 = 2 min 1 s."
        val uD = PaceMath.unitDistanceLabel(unit)
        val dist = formatRange(m.dMin, m.dMax, { formatDistance(it) }, 0.005)
        val dur = formatRange(m.tMin, m.tMax, { formatSeconds(it) }, 1.0)
        val recSuffix = if (m.rec != null) ", récupération comprise après chaque répétition." else "."
        return "Cette étape : $dist $uD en $dur min$recSuffix"
    }
}
