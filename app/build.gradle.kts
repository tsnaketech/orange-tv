plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Version MAJOR.MINOR.PATCH: the only number to change for a release. versionCode follows it (2.0.0 -> 20000),
// so it always grows with the version.
// shortcut: MINOR and PATCH must stay below 100; widen the multipliers if that day comes.
val appVersion = "2.0.0"
val appVersionCode = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(appVersion)?.destructured
    ?.let { (major, minor, patch) ->
        require(minor.toInt() < 100 && patch.toInt() < 100) { "MINOR and PATCH must be below 100: $appVersion" }
        major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
    }
    ?: error("appVersion must look like 2.0.0, not $appVersion")

android {
    namespace = "io.github.tsnaketech.orangetv"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.tsnaketech.orangetv"
        minSdk = 25 // Fire OS 6+; the tested Fire TV is on API 30
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
    }

    // Release signing only where the keystore is given (the release workflow, from GitHub secrets);
    // elsewhere assembleRelease gives an unsigned APK and debug builds are unchanged.
    signingConfigs {
        System.getenv("SIGNING_KEYSTORE")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release { signingConfig = signingConfigs.findByName("release") }
        // Own app id: the debug build installs next to the signed release on the TV, each with its own session.
        debug { applicationIdSuffix = ".debug" }
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
