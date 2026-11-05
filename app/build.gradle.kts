val releaseStoreFile = providers.environmentVariable("SYSBATTERY_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("SYSBATTERY_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("SYSBATTERY_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("SYSBATTERY_RELEASE_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val hasReleaseSigningValues = releaseSigningValues.any { !it.isNullOrBlank() }
val hasCompleteReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }
if (hasReleaseSigningValues && !hasCompleteReleaseSigning) {
    throw GradleException("Set all SYSBATTERY_RELEASE_* signing environment variables or leave all of them unset.")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.nlab.sysbattery"
    compileSdk = 35
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        aidl = true
    }
    defaultConfig {
        applicationId = "com.nlab.sysbattery"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"
    }
    if (hasCompleteReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        debug {
            // Use AGP's standard per-user debug keystore; never require a repo-local key.
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Without release credentials, assembleRelease intentionally produces an unsigned APK.
        }
    }
}
dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
