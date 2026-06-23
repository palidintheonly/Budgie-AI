import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
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
    namespace = "com.budgieai.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.budgieai.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.1-beta"
        buildConfigField("String", "RELEASE_LABEL", "\"v1\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"${localString("OPENROUTER_API_KEY")}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${localString("GEMINI_API_KEY")}\"")
        buildConfigField("String", "BACKEND_SYNC_URL", "\"${localString("BACKEND_SYNC_URL")}\"")
        buildConfigField("String", "BACKEND_SYNC_KEY", "\"${localString("BACKEND_SYNC_KEY")}\"")
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
        release {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.15.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.android.gms:play-services-ads:25.1.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation(libs.compose.material3)
    implementation(libs.compose.material)
    implementation(libs.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
