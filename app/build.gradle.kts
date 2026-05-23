plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.phonepvr.friends"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.phonepvr.friends"
        minSdk = 26
        targetSdk = 35
        versionCode = project.findProperty("versionCode")?.toString()?.toIntOrNull() ?: 1
        versionName = project.findProperty("versionName")?.toString() ?: "1.0.0"
    }

    signingConfigs {
        getByName("debug") {
            // Stable signing uses a keystore supplied by CI secrets so each
            // build installs as an update of the previous one. When secrets
            // are absent (initial bring-up, local dev), fall back to AGP's
            // auto-generated debug key. See SIGNING.md.
            val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
            val storePass = System.getenv("SIGNING_STORE_PASSWORD")
            val alias = System.getenv("SIGNING_KEY_ALIAS")
            val keyPass = System.getenv("SIGNING_KEY_PASSWORD")
            if (!keystorePath.isNullOrBlank() &&
                !storePass.isNullOrBlank() &&
                !alias.isNullOrBlank() &&
                !keyPass.isNullOrBlank() &&
                file(keystorePath).exists()
            ) {
                storeFile = file(keystorePath)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Explicit: shipping APK keeps R8 off so stack traces match
            // source lines and the build stays reproducible.
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
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
