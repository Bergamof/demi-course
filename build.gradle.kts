// Kotlin plugins are declared once here (apply false) so every module shares the same copy on the
// classpath, which AGP's built-in Kotlin requires. The Android Gradle Plugin is deliberately NOT
// declared here: `:domain:test` must configure and run without resolving it (see CLAUDE.md,
// « Environnement »).
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
