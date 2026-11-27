plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.digitalku.roost.remote"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.digitalku.roost.remote"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"
    }

    // The upload key is the owner's and never in this repository. Its path and passwords come
    // from ~/.gradle/gradle.properties; without them a release builds unsigned, which Play refuses
    // and which is all a build for anybody else needs (README, "Releasing").
    val uploadStore = providers.gradleProperty("roostRemoteUploadStoreFile").orNull
    if (uploadStore != null) {
        signingConfigs {
            create("upload") {
                storeFile = file(uploadStore)
                storePassword = providers.gradleProperty("roostRemoteUploadStorePassword").get()
                keyAlias = providers.gradleProperty("roostRemoteUploadKeyAlias").get()
                keyPassword = providers.gradleProperty("roostRemoteUploadKeyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            if (uploadStore != null) signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.work.runtime)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
