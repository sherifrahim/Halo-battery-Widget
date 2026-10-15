plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The watch companion. It must share the phone app's applicationId and signing key, or the
// Wear OS data layer won't deliver messages between them.
android {
    namespace = "com.halo.btbattery.watch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.halo.btbattery"
        minSdk = 30
        targetSdk = 34
        versionCode = 4
        versionName = "1.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
}
