plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.duobudget.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.duobudget.family"
        minSdk = 26
        targetSdk = 36
        versionCode = 10107
        versionName = "1.1.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "CLOUD_URL", "\"https://duobudget-family-sync.wirylemur8.chatgpt.site\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    val testKey = System.getenv("DUOBUDGET_TEST_KEYSTORE")
    if (testKey != null) {
        signingConfigs {
            create("ciDebug") {
                storeFile = file(testKey)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    buildTypes {
        debug { if (testKey != null) signingConfig = signingConfigs.getByName("ciDebug") }
        release { isDebuggable = false; isMinifyEnabled = false }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

val patchDuoBudgetUi = tasks.register("patchDuoBudgetUi") {
    outputs.upToDateWhen { false }
    doLast {
        val uiFile = file("src/main/java/com/duobudget/app/ui/App.kt")
        var text = uiFile.readText()

        val oldHeader = "Surface(onClick=onSettings,shape=CircleShape,color=MaterialTheme.colorScheme.surfaceContainer,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Text(if(sync==SyncState.SYNCING)\"↻\"else\"⌂\",Modifier.padding(12.dp),fontSize=19.sp)}"
        if (text.contains(oldHeader)) text = text.replace(oldHeader, "")

        val oldAppearance = "GlassCard(Modifier.fillMaxWidth()){Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(\"Оформление\",fontWeight=FontWeight.SemiBold);Text(\"Светлая или тёмная тема включается автоматически вместе с темой телефона.\",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}"
        val newAppearance = "GlassCard(Modifier.fillMaxWidth()){AppearanceSettingsContent(context)}"
        if (text.contains(oldAppearance)) text = text.replace(oldAppearance, newAppearance)

        val backgroundPattern = Regex("""(?s)@Composable\s+private fun BotanicalBackground\(content:@Composable BoxScope\.\(\)->Unit\)\{.*?\n\}\n\n@Composable\nprivate fun GlassCard""")
        if (backgroundPattern.containsMatchIn(text)) {
            text = backgroundPattern.replace(
                text,
                """@Composable
private fun BotanicalBackground(content:@Composable BoxScope.()->Unit){
    com.duobudget.app.ui.theme.DuoBudgetBackground(content)
}

@Composable
private fun GlassCard"""
            )
        }

        text = text.replace(
            "colors=CardDefaults.cardColors(containerColor=if(dark)Color(0x99332B27)else Color(0x70FFFCF7)),",
            "colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=if(dark).76f else .66f)),"
        )
        text = text.replace(
            "border=BorderStroke(1.dp,if(dark)Color.White.copy(.16f)else Color.White.copy(.72f)),",
            "border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=if(dark).78f else .68f)),"
        )
        text = text.replace(
            "color=if(dark)Color(0xCC302824)else Color(0xB8FFFDF8),",
            "color=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=if(dark).88f else .82f),"
        )
        text = text.replace(
            "border=BorderStroke(1.dp,if(dark)Color.White.copy(.16f)else Color.White.copy(.8f)),",
            "border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=if(dark).82f else .72f)),"
        )

        check(!text.contains("Surface(onClick=onSettings")) { "Home header settings button was not removed" }
        check(text.contains("AppearanceSettingsContent(context)")) { "Theme selector patch was not applied" }
        check(text.contains("DuoBudgetBackground(content)")) { "Themed background patch was not applied" }
        check(text.contains("surfaceContainer.copy(alpha=if(dark).76f else .66f)")) { "Themed card patch was not applied" }
        check(text.contains("surfaceContainer.copy(alpha=if(dark).88f else .82f)")) { "Themed bottom bar patch was not applied" }
        uiFile.writeText(text)
    }
}

tasks.configureEach {
    if (name.startsWith("compile") && name.endsWith("Kotlin")) dependsOn(patchDuoBudgetUi)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom); androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.compose.ui:ui"); implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation"); implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
