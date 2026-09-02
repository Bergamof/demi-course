package com.demicourse.domain

enum class SegmentKind { RUN, REC }

data class Segment(
    val stepIndex: Int,
    val rep: Int,
    val reps: Int,
    val kind: SegmentKind,
    val distance: Double,
    val time: Double,
)

data class TurnPoint(
    val stepIndex: Int,
    val rep: Int,
    val reps: Int,
    val kind: SegmentKind,
    /** Cumulative distance/time from the start of the session at the turnaround. */
    val distance: Double,
    val time: Double,
    /** How far into this particular segment the turnaround falls. */
    val intoDistance: Double,
    val intoTime: Double,
)

data class SessionResult(
    val dMin: Double,
    val dMax: Double,
    val tMin: Double,
    val tMax: Double,
    val dMid: Double,
    val tMid: Double,
    val turn: TurnPoint?,
    val segments: List<Segment>,
)

object SessionCalculator {

    /**
     * Totals the session and locates the turnaround point at half of the total
     * distance or time (per [halfBy]), walking step repetitions (and their
     * recovery, if any) in order as a flat list of segments.
     *
     * Segment "mid" values (the midpoint of a pace range's distance/time envelope)
     * are used for placing the turnaround, matching the prototype: a pace range
     * doesn't widen the turnaround search, it's resolved to its middle estimate.
     */
    fun compute(steps: List<StepSpec>, halfBy: HalfBy): SessionResult {
        var dMin = 0.0; var dMax = 0.0; var tMin = 0.0; var tMax = 0.0
        val segments = mutableListOf<Segment>()

        steps.forEachIndexed { index, step ->
            val m = PaceMath.metrics(step)
            if (!m.ok) return@forEachIndexed
            dMin += m.dMin; dMax += m.dMax; tMin += m.tMin; tMax += m.tMax
            val run = m.run!!
            for (rep in 1..m.reps) {
                segments += Segment(index, rep, m.reps, SegmentKind.RUN, run.dMid, run.tMid)
                m.rec?.let { rec -> segments += Segment(index, rep, m.reps, SegmentKind.REC, rec.dMid, rec.tMid) }
            }
        }

        val dMid = (dMin + dMax) / 2
        val tMid = (tMin + tMax) / 2
        val byDistance = halfBy != HalfBy.DURATION
        val target = (if (byDistance) dMid else tMid) / 2

        var acc = 0.0
        var accTime = 0.0
        var accDist = 0.0
        var turn: TurnPoint? = null
        for (seg in segments) {
            val step = if (byDistance) seg.distance else seg.time
            if (acc + step >= target && step > 0) {
                val f = (target - acc) / step
                turn = TurnPoint(
                    stepIndex = seg.stepIndex, rep = seg.rep, reps = seg.reps, kind = seg.kind,
                    distance = accDist + f * seg.distance, time = accTime + f * seg.time,
                    intoDistance = f * seg.distance, intoTime = f * seg.time,
                )
                break
            }
            acc += step; accTime += seg.time; accDist += seg.distance
        }

        return SessionResult(dMin, dMax, tMin, tMax, dMid, tMid, turn, segments)
    }
}
