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

    // Release signing reads Gradle properties: ~/.gradle/gradle.properties on your machine, or
    // ORG_GRADLE_PROJECT_* environment variables in CI. Without them, release builds are unsigned.
    val releaseStoreFile = providers.gradleProperty("justLeaveStoreFile").orNull
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("justLeaveStorePassword").get()
                keyAlias = providers.gradleProperty("justLeaveKeyAlias").get()
                keyPassword = providers.gradleProperty("justLeaveKeyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
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
