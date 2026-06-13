import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}
val hasReleaseKeystore = keystorePropertiesFile.exists()

android {
    namespace = "com.monkeymischief.game"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.monkeymischief.game"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.0.12"
        manifestPlaceholders["appLabel"] = "Alpha"
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Alpha Debug"
        }
        release {
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            manifestPlaceholders["appLabel"] = "Alpha"
        }
        create("alpha") {
            initWith(getByName("debug"))
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            applicationIdSuffix = ".alpha"
            versionNameSuffix = "-alpha"
            manifestPlaceholders["appLabel"] = "Alpha"
        }
        create("beta") {
            initWith(getByName("debug"))
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            manifestPlaceholders["appLabel"] = "Alpha Beta"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.material)
    implementation(libs.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
