import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.einkmemo.app"
    compileSdk = 34

    defaultConfig {
        // 与旧包名保持一致，保证已安装版本可覆盖升级、数据不丢；对外展示名已改为 EinkMemo
        applicationId = "com.elinkmemo.app"
        minSdk = 26
        targetSdk = 30
        versionCode = 1100
        versionName = "0.11.0"
    }

    signingConfigs {
        getByName("debug") {
            val custom = System.getenv("EINKMEMO_DEBUG_KEYSTORE")
            if (!custom.isNullOrBlank() && File(custom).exists()) {
                storeFile = File(custom)
                storePassword = System.getenv("EINKMEMO_DEBUG_KEYSTORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("EINKMEMO_DEBUG_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("EINKMEMO_DEBUG_KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-text")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}