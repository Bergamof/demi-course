# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

An Android app (Kotlin + Jetpack Compose) for planning a single running session ("séance") — steps with target pace, distance/duration, repetitions, and optional recovery — and calculating where the halfway ("demi-tour") turnaround point falls. No history: one session at a time, persisted locally.

It was ported from a Claude Design HTML/JS prototype. The original design bundle is kept for reference:
- `project/Séance.dc.html` — the prototype's full UI + logic (single-file, uses a tiny custom reactive runtime in `support.js`). Useful as a visual/behavioral reference when in doubt about exact copy, spacing, or a formula.
- `chats/chat1.md`, `chats/chat2.md` — the design conversation. **This is the source of truth for *why* things work the way they do** (e.g. why recovery applies after every rep including the last, why Tab doesn't select text but Enter does, the exact mm.ss parsing rules). Read these before changing behavior that seems arbitrary.

## Commands

```
./gradlew :domain:test                                    # run all domain unit tests
./gradlew :domain:test --tests "com.demicourse.domain.PaceMathTest"   # one test class
./gradlew :domain:test --tests "*SessionCalculatorTest"    # by class name pattern
./gradlew :app:assembleDebug                               # build the app
```

There is no linter configured (no ktlint/detekt).

**Sandbox limitation:** this environment's network policy blocks `dl.google.com`, which is where the Android Gradle Plugin and all AndroidX/Compose artifacts live. `:domain:test` works here (pure Kotlin, only needs Maven Central). `:app:*` tasks cannot be resolved or built here — they need Android Studio or a CI runner with normal network access. Keep domain logic changes covered by `:domain:test` since that's the only thing verifiable in this environment; treat any `app/` change as unverified-by-build until it's compiled elsewhere.

## Architecture

Two Gradle modules, deliberately split so the math is testable without the Android toolchain:

- **`domain/`** — pure Kotlin, no Android dependency. All parsing, formatting, and the turnaround calculation live here as plain functions/objects, unit-tested in `domain/src/test`.
- **`app/`** — Android/Compose UI, ViewModel, and DataStore persistence. Depends on `domain/`.

### `domain/` — the math

- `Models.kt` — `StepSpec` is the one shape used for **both a session step and a saved template** (same fields, templates just add `id`/`name`). Don't create a separate `Template` type; anything that needs to distinguish "is this a template" does so by context, not by type.
- `PaceMath.kt` — `mm.ss` / distance parsing and formatting, and `piece()`/`metrics()` which resolve a pace (single or range) + length (distance or duration) into a distance/time envelope for one step. Pace is always seconds-per-distance-unit; distance-measured steps have `dMin == dMax` (only time varies across a pace range), duration-measured steps have `tMin == tMax` (only distance varies).
- `SessionCalculator.kt` — flattens all steps into an ordered list of run/recovery segments (repeated per `reps`), sums totals, then walks the segments to find the turnaround at half of total distance **or** total duration (`HalfBy`, a user setting). The segment "mid" value (midpoint of a pace range's envelope) is what's used to place the turnaround — a pace range doesn't widen the search, it collapses to its midpoint.

### `app/` — the UI

- `data/SeanceRepository.kt` — the only persistence: one DataStore Preferences file holding steps/templates/settings each as a JSON blob (kotlinx.serialization). No Room, no history — by design, there's only ever one current session.
- `ui/SeanceViewModel.kt` + `ui/UiState.kt` — a single `SheetState` drives one bottom sheet reused for three purposes (`SheetType.STEP`, `TEMPLATE`, `SETTINGS`); which fields render and what "submit" does both branch on `sheet.type`. `fieldOrder(sheet)` is the list of currently-relevant fields (it changes as the user toggles pace-range/recovery/save-as-template) and is the single source of truth for the Tab/Enter focus chain — don't hardcode a different field order in the UI.
- `ui/components/FocusChain.kt` (`SheetFocusController`) + `SeanceTextField.kt` — reproduce the prototype's keyboard behavior exactly: Enter (or the soft keyboard's "next") advances to the next field in `fieldOrder()` **and selects its text**; submits if it's the last field. Tab also advances but does **not** select — this asymmetry is intentional (ported from the prototype, see `chats/chat1.md`), not a bug. Selection only ever happens on sheet-open or on an explicit advance, never on a plain click-into-field (that was a prototype bug that got fixed — don't reintroduce it by wiring select-all to focus-gain in general).
- `ui/theme/Theme.kt` — `SeanceColors` is a custom `CompositionLocal` mirroring the prototype's CSS custom properties 1:1 (not Material3's `ColorScheme` roles). When adding UI, pull colors from `LocalSeanceColors.current`, not `MaterialTheme.colorScheme`.
- `ui/Formatting.kt` — display-string helpers (chip text, totals, turnaround marker text); a direct port of the prototype's `renderVals()`. Keep these in sync with `PaceMath`/`SessionCalculator` if the underlying math changes.

### Functional rules worth knowing before changing behavior

- `mm.ss` format: `1.30` = 1m30s; a single digit after the separator is the *ones* digit of seconds, not tens (`2.1` = `2.01` = 2m1s). Seconds ≥ 60 is rejected.
- A template requires at least one of pace or length (not both); the other can be left blank and is only applied if the template defines it (`applyTemplate` in the ViewModel copies reps/name unconditionally, pace/length/recovery only if the template set them).
- Recovery (when enabled) applies after **every** repetition, including the last.
- Default recovery pace is a 9.00–11.00 range, stored in `AppSettings.recovery` and used to prefill new step drafts; editable from the settings sheet.
- Theme is `SYSTEM` / `LIGHT` / `DARK`, persisted in `AppSettings.theme`.

## Package / version notes

- Packages: `com.demicourse.domain`, `com.demicourse.seance`.
- `minSdk 26`, `compileSdk`/`targetSdk 35`, Kotlin `2.0.21`, AGP `8.7.3`, Compose BOM `2024.12.01`.
- Version catalog: `gradle/libs.versions.toml`. Each module applies its own plugins directly (no shared `apply false` block in the root `build.gradle.kts`) — this is what lets `:domain:test` configure and run without ever touching the Android Gradle Plugin classpath.
