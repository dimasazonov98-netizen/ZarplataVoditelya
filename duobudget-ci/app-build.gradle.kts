plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun quoteBuildConfig(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val firebaseApiKey = providers.gradleProperty("FIREBASE_API_KEY").orNull ?: ""
val firebaseApplicationId = providers.gradleProperty("FIREBASE_APPLICATION_ID").orNull ?: ""
val firebaseProjectId = providers.gradleProperty("FIREBASE_PROJECT_ID").orNull ?: ""

android {
    namespace = "com.duobudget.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.duobudget.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"

        buildConfigField("String", "FIREBASE_API_KEY", quoteBuildConfig(firebaseApiKey))
        buildConfigField("String", "FIREBASE_APPLICATION_ID", quoteBuildConfig(firebaseApplicationId))
        buildConfigField("String", "FIREBASE_PROJECT_ID", quoteBuildConfig(firebaseProjectId))
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    val firebaseBom = platform("com.google.firebase:firebase-bom:35.0.0")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
