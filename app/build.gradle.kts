plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "dev.tidesapp.wearos"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.tidesapp.wearos"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "TIDAL_CLIENT_ID", "\"${project.property("TIDAL_CLIENT_ID")}\"")
        buildConfigField("String", "TIDAL_CLIENT_SECRET", "\"${project.property("TIDAL_CLIENT_SECRET")}\"")
        buildConfigField("String", "TIDAL_API_BASE_URL", "\"${project.property("TIDAL_API_BASE_URL")}\"")
        buildConfigField("String", "TIDAL_AUTH_BASE_URL", "\"${project.property("TIDAL_AUTH_BASE_URL")}\"")
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
    implementation(project(":feature-offline"))
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
