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

        // ── Release identity (the F-Droid channel) ──────────────────────────
        // F-Droid builds the tagged source with NO environment set, so it ships
        // exactly these literals. They are ALSO what F-Droid's metadata scanner
        // reads to detect new releases (UpdateCheckMode: Tags), so they MUST stay
        // plain literals here — `versionCode = <int>` and `versionName = "<str>"`.
        // Moving them into a variable or a getenv/`?:` expression makes F-Droid's
        // parser report no version and breaks auto-update. Bump BOTH for every
        // F-Droid release, commit, then tag vX.Y.Z matching versionName;
        // versionCode must increase by at least 1 each release. See RELEASING.md.
        versionCode = 1
        versionName = "1.0.0"

        // CI experimentation overrides the literals above via env (APP_VERSION_CODE
        // / APP_VERSION_NAME, derived from github.run_number) so every branch build
        // installs as a distinct version on a dev device. These run AFTER the
        // literals, so the literals stay the value F-Droid reads. Such builds are
        // signed with our key and never reach F-Droid.
        System.getenv("APP_VERSION_CODE")?.toIntOrNull()?.let { versionCode = it }
        System.getenv("APP_VERSION_NAME")?.let { versionName = it }
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
        create("release") {
            // The release variant signs with the SAME CI keystore as debug. On
            // F-Droid's build servers the SIGNING_* secrets are absent, so no
            // keystore is applied and assembleRelease stays UNSIGNED there — which
            // is exactly what reproducible builds needs: F-Droid builds the same
            // source, byte-compares it to our signed APK (Binaries /
            // AllowedAPKSigningKeys in the fdroiddata recipe), then ships OUR
            // signature. See SIGNING.md / RELEASING.md.
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
        getByName("release") {
            // Attach our signature only when the keystore is present (CI / local
            // release builds). With no keystore (F-Droid) the release stays
            // unsigned. minify stays OFF (AGP default) to keep output deterministic.
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }

    // Reproducible builds: omit AGP's dependency-metadata block (a Google-signed,
    // non-reproducible blob) from the APK so F-Droid's build byte-matches ours.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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
        // VERSION_NAME is surfaced in Settings so the user can see which
        // build they're on and tap straight to the releases page.
        buildConfig = true
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
    // Many of the icons referenced from contact / dialer / in-call screens
    // (PersonAdd, Contacts, Cake, CalendarMonth, Business, Notes, etc.) live
    // outside the curated -core set, so the extended catalogue is pulled in.
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
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
    // Installs the bundled baseline profile on first run and lets the Play
    // Store apply cloud profiles, improving cold-start / first-frame jank.
    // The profile itself is generated separately on a device/emulator via
    // ./gradlew :app:generateBaselineProfile.
    implementation(libs.androidx.profileinstaller)

    testImplementation(libs.junit)
}
