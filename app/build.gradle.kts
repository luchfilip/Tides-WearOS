import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val keystorePropertiesFile = rootProject.file(".keys/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "dev.tidesapp.wearos"
    compileSdk = 36

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "dev.tidesapp.wearos"
        minSdk = 30
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"

        // Use findProperty(...) rather than property(...) so fresh clones without a
        // populated gradle.properties still build (the SDK will fail at runtime on
        // empty credentials, which is a clearer failure mode than a Gradle crash).
        buildConfigField(
            "String",
            "TIDAL_CLIENT_ID",
            "\"${project.findProperty("TIDAL_CLIENT_ID")?.toString().orEmpty()}\"",
        )
        buildConfigField(
            "String",
            "TIDAL_CLIENT_SECRET",
            "\"${project.findProperty("TIDAL_CLIENT_SECRET")?.toString().orEmpty()}\"",
        )
        buildConfigField(
            "String",
            "TIDAL_API_BASE_URL",
            "\"${project.findProperty("TIDAL_API_BASE_URL")?.toString() ?: "https://api.tidal.com/"}\"",
        )
        buildConfigField(
            "String",
            "TIDAL_AUTH_BASE_URL",
            "\"${project.findProperty("TIDAL_AUTH_BASE_URL")?.toString() ?: "https://auth.tidal.com/v1/"}\"",
        )
        buildConfigField(
            "String",
            "TIDAL_CLIENT_VERSION",
            "\"${project.findProperty("TIDAL_CLIENT_VERSION") ?: "2.187.0"}\""
        )
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Tidal SDK bundles its own media3 fork (com.tidal.androidx.media3).
// Exclude the standard androidx.media3 to avoid duplicate classes.
configurations.all {
    exclude(group = "androidx.media3")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":feature-auth"))
    implementation(project(":feature-player"))
    implementation(project(":feature-library"))
    implementation(project(":feature-settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)

    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
