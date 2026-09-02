// Intentionally empty: each module applies its own plugins (see app/build.gradle.kts and
// domain/build.gradle.kts). Keeping the root build script free of plugin declarations means
// `:domain:test` can configure and run without ever touching the Android Gradle Plugin
// classpath (relevant if google()'s Maven repo isn't reachable in a given environment,
// e.g. a network-restricted CI sandbox — see README-BUILD.md).
