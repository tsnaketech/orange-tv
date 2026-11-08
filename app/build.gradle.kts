plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.tsnaketech.orangetv"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.tsnaketech.orangetv"
        minSdk = 25 // Fire OS 6+; the tested Fire TV is on API 30
        targetSdk = 35
        versionCode = 5
        versionName = "2.0"
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
    implementation("com.google.zxing:core:3.5.3") // QR code generation
    testImplementation("junit:junit:4.13.2")
}
