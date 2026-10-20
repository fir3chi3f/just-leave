plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.fir3chi3f.justleave"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fir3chi3f.justleave"
        // Android 16+ only.
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.window)

    testImplementation(libs.junit)
}
