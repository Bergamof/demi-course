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

// --- Versionnement ----------------------------------------------------------
//
// Prod : date de publication + rang de la publication ce jour-là, « 2026.09.18-1 »
// pour la première release du 18 septembre 2026, « 2026.09.18-2 » pour la
// deuxième. Le rang est calculé par .github/workflows/release.yml (à partir des
// tags déjà publiés) et passé ici en propriétés Gradle. Un build release local,
// sans ces propriétés, prend le rang 0 : il n'a jamais été publié.
//
// Dev : date et heure du build, « dev-2026.09.18-12.23 ». La variante dev a son
// propre applicationId (suffixe « .dev ») et son propre nom affiché, donc elle
// cohabite avec la version de prod sur le téléphone au lieu de l'écraser.

val RELEASE_VERSION_PATTERN = Regex("""^(\d{4})\.(\d{2})\.(\d{2})-(\d{1,2})$""")

/** aa mm jj rr → un entier croissant : 2026.09.18-1 devient 26_09_18_01. */
fun releaseVersionCodeOf(versionName: String): Int {
    val parts = RELEASE_VERSION_PATTERN.find(versionName)
        ?: error("Version release invalide : « $versionName » (attendu aaaa.mm.jj-n, n ≤ 99).")
    val (year, month, day, seq) = parts.destructured
    return ((year.toInt() % 100) * 10_000 + month.toInt() * 100 + day.toInt()) * 100 + seq.toInt()
}

val buildTime: LocalDateTime = LocalDateTime.now()

val releaseVersionName: String = (findProperty("appVersionName") as String?)?.takeIf { it.isNotBlank() }
    ?: "${buildTime.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))}-0"
val releaseVersionCode: Int = (findProperty("appVersionCode") as String?)?.takeIf { it.isNotBlank() }?.toInt()
    ?: releaseVersionCodeOf(releaseVersionName)

val devVersionName: String = "dev-" + buildTime.format(DateTimeFormatter.ofPattern("yyyy.MM.dd-HH.mm"))
// Minutes écoulées depuis 2020 : croissant d'un build dev au suivant, et assez
// petit pour tenir dans l'entier signé attendu par Android.
val devVersionCode: Int = Duration.between(LocalDateTime.of(2020, 1, 1, 0, 0), buildTime).toMinutes().toInt()

// --- Signature de release ---------------------------------------------------
//
// Lue depuis keystore.properties (local, non versionné) ou depuis les variables
// d'environnement posées par le workflow à partir des secrets du dépôt. Sans
// elle, l'APK release sort non signé : il se construit mais ne s'installe pas.

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
        // Valeurs par défaut ; androidComponents.onVariants (plus bas) les
        // remplace par celles de la variante réellement construite.
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
            // Installée à côté de la prod, et identifiable d'un coup d'œil sur
            // l'écran d'accueil.
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
