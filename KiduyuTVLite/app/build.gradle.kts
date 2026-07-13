import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun buildConfigString(name: String): String {
    val value = localProperties.getProperty(name, "").trim()
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    namespace = "com.kiduyuk.klausk.kiduyutv.lite"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kiduyuk.klausk.kiduyutv.lite.tv"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0-lite"

        buildConfigField("String", "TMDB_API_KEY", buildConfigString("TMDB_API_KEY"))
        buildConfigField("String", "PLAYER_BASE_URL", buildConfigString("LITE_PLAYER_BASE_URL"))
    }

    val releaseSigning = if (keystorePropertiesFile.exists()) {
        signingConfigs.create("release") {
            storeFile = rootProject.file(requireNotNull(keystoreProperties.getProperty("storeFile")))
            storePassword = requireNotNull(keystoreProperties.getProperty("storePassword"))
            keyAlias = requireNotNull(keystoreProperties.getProperty("keyAlias"))
            keyPassword = requireNotNull(keystoreProperties.getProperty("keyPassword"))
        }
    } else {
        null
    }

    buildTypes {
        release {
            releaseSigning?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil:2.7.0")
}
