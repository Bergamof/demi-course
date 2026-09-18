import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// --- Versioning -------------------------------------------------------------
//
// Prod: publication date plus that day's publication rank — "2026.09.18-1" for
// the first release of 18 September 2026, "2026.09.18-2" for the second. The
// rank is worked out by .github/workflows/release.yml (from the tags already
// published) and handed over as Gradle properties. A local release build, with
// no such property, gets rank 0: it was never published.
//
// Dev: build date and time, "dev-2026.09.18-12.23". The dev variant has its own
// applicationId (".dev" suffix) and its own displayed name, so it sits next to
// the prod version on the phone instead of overwriting it.

val RELEASE_VERSION_PATTERN = Regex("""^(\d{4})\.(\d{2})\.(\d{2})-(\d{1,2})$""")

/** yy mm dd nn → an increasing integer: 2026.09.18-1 becomes 26_09_18_01. */
fun releaseVersionCodeOf(versionName: String): Int {
    val parts = RELEASE_VERSION_PATTERN.find(versionName)
        ?: error("Invalid release version: \"$versionName\" (expected yyyy.MM.dd-n, n ≤ 99).")
    val (year, month, day, seq) = parts.destructured
    return ((year.toInt() % 100) * 10_000 + month.toInt() * 100 + day.toInt()) * 100 + seq.toInt()
}

val buildTime: LocalDateTime = LocalDateTime.now()

val releaseVersionName: String = (findProperty("appVersionName") as String?)?.takeIf { it.isNotBlank() }
    ?: "${buildTime.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))}-0"
val releaseVersionCode: Int = (findProperty("appVersionCode") as String?)?.takeIf { it.isNotBlank() }?.toInt()
    ?: releaseVersionCodeOf(releaseVersionName)

val devVersionName: String = "dev-" + buildTime.format(DateTimeFormatter.ofPattern("yyyy.MM.dd-HH.mm"))
// Minutes since 2020: grows from one dev build to the next, and stays small
// enough for the signed integer Android expects.
val devVersionCode: Int = Duration.between(LocalDateTime.of(2020, 1, 1, 0, 0), buildTime).toMinutes().toInt()

// --- Release signing --------------------------------------------------------
//
// Read from keystore.properties (local, untracked) or from the environment
// variables the workflow sets from the repository secrets. With neither, the
// release APK comes out unsigned: it builds, but it will not install.

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingSecret(property: String, environmentVariable: String): String? =
    (keystoreProperties.getProperty(property) ?: System.getenv(environmentVariable))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingSecret("storeFile", "ANDROID_KEYSTORE_FILE")

android {
    namespace = "com.demicourse.seance"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.demicourse.seance"
        minSdk = 26
        targetSdk = 35
        // Defaults; androidComponents.onVariants (below) replaces them with the
        // values of whichever variant is actually being built.
        versionCode = releaseVersionCode
        versionName = releaseVersionName
    }

    signingConfigs {
        releaseStoreFile?.let { keystorePath ->
            create("release") {
                storeFile = file(keystorePath)
                storePassword = signingSecret("storePassword", "ANDROID_KEYSTORE_PASSWORD")
                keyAlias = signingSecret("keyAlias", "ANDROID_KEY_ALIAS")
                keyPassword = signingSecret("keyPassword", "ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Installs alongside prod, and tells itself apart at a glance on the
            // home screen.
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "Demi Course (dev)")
            buildConfigField("String", "APP_VERSION_NAME", "\"$devVersionName\"")
        }
        release {
            resValue("string", "app_name", "Demi Course")
            buildConfigField("String", "APP_VERSION_NAME", "\"$releaseVersionName\"")
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants { variant ->
        val isRelease = variant.buildType == "release"
        variant.outputs.forEach { output ->
            output.versionName.set(if (isRelease) releaseVersionName else devVersionName)
            output.versionCode.set(if (isRelease) releaseVersionCode else devVersionCode)
        }
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.ui.tooling.preview)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
}
