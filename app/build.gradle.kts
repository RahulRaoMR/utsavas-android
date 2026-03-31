import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun resolveConfigValue(key: String, fallback: String): String =
    (
        localProperties.getProperty(key)
            ?: providers.gradleProperty(key).orNull
            ?: System.getenv(key)
            ?: fallback
        ).trim()

fun String.toBuildConfigValue(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val releaseWebUrl = resolveConfigValue("UTSAVAS_WEB_URL", "https://utsavas.com")
val debugWebUrl = resolveConfigValue("UTSAVAS_DEBUG_WEB_URL", releaseWebUrl)

android {
    namespace = "com.talme.utsavas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.talme.utsavas"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "WEB_URL", debugWebUrl.toBuildConfigValue())
            manifestPlaceholders["usesCleartextTraffic"] =
                debugWebUrl.startsWith("http://")
        }

        release {
            isMinifyEnabled = false
            buildConfigField("String", "WEB_URL", releaseWebUrl.toBuildConfigValue())
            manifestPlaceholders["usesCleartextTraffic"] =
                releaseWebUrl.startsWith("http://")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.12.0")
}
