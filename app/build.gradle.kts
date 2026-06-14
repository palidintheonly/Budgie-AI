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
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
fun localString(name: String): String = localProperties.getProperty(name, "")
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "app.alpha.chat"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.alpha.chat"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.3-alpha"
        buildConfigField("String", "OPENROUTER_API_KEY", "\"${localString("OPENROUTER_API_KEY")}\"")
        buildConfigField("String", "OPENROUTER_MODEL", "\"google/gemma-4-31b-it:free\"")
        for (slot in 2..4) {
            buildConfigField("String", "PROVIDER_${slot}_NAME", "\"${localString("PROVIDER_${slot}_NAME")}\"")
            buildConfigField("String", "PROVIDER_${slot}_ENDPOINT", "\"${localString("PROVIDER_${slot}_ENDPOINT")}\"")
            buildConfigField("String", "PROVIDER_${slot}_MODEL", "\"${localString("PROVIDER_${slot}_MODEL")}\"")
            buildConfigField("String", "PROVIDER_${slot}_API_KEY", "\"${localString("PROVIDER_${slot}_API_KEY")}\"")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
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
        }
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
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
    debugImplementation(libs.androidx.compose.ui.tooling)
}
