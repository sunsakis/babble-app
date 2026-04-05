import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) props.load(f.inputStream())
}

fun localProp(key: String) = localProps.getProperty(key, "")

android {
    namespace = "com.guardian.dialer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.guardian.dialer"
        minSdk = 29 // Android 10 — CallScreeningService stable
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "ELEVENLABS_API_KEY", "\"${localProp("ELEVENLABS_API_KEY")}\"")
        buildConfigField("String", "ELEVENLABS_AGENT_ID", "\"${localProp("ELEVENLABS_AGENT_ID")}\"")
        buildConfigField("String", "TEXTBEE_API_KEY",     "\"${localProp("TEXTBEE_API_KEY")}\"")
        buildConfigField("String", "TEXTBEE_DEVICE_ID",   "\"${localProp("TEXTBEE_DEVICE_ID")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.preference:preference-ktx:1.2.1")

    // OkHttp for WebSocket (ElevenLabs Conversational AI)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson for JSON
    implementation("com.google.code.gson:gson:2.11.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
