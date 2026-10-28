plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.posfingerprint.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.posfingerprint.app"
        minSdk = 30
        targetSdk = 34
        versionCode = 3
        versionName = "2.1"
    }

    // Release signing comes from environment variables (set by the GitHub workflow from
    // repository secrets, or by you locally). Never commit a keystore or its passwords.
    signingConfigs {
        create("release") {
            val ks = System.getenv("POSFP_KEYSTORE")
            if (!ks.isNullOrEmpty()) {
                storeFile = file(ks)
                storePassword = System.getenv("POSFP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("POSFP_KEY_ALIAS")
                keyPassword = System.getenv("POSFP_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Release builds are not debuggable, so app data can't be read over USB.
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (!System.getenv("POSFP_KEYSTORE").isNullOrEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.biometric:biometric:1.1.0")
}
