plugins {
    id("com.android.application")
}

android {
    namespace = "com.voicelib.vox"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.voicelib.vox"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
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

    lint {
        // Le prototype V0.1 ne doit pas échouer sur des avertissements lint.
        abortOnError = false
    }
}
