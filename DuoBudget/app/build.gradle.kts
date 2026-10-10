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
        versionCode = 10103
        versionName = "1.1.3"
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

val patchDuoBudgetUi by tasks.registering {
    outputs.upToDateWhen { false }
    doLast {
        val uiFile = file("src/main/java/com/duobudget/app/ui/App.kt")
        var text = uiFile.readText()

        val oldHeader = "Surface(onClick=onSettings,shape=CircleShape,color=MaterialTheme.colorScheme.surfaceContainer,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)){Text(if(sync==SyncState.SYNCING)\"↻\"else\"⌂\",Modifier.padding(12.dp),fontSize=19.sp)}"
        val newHeader = "Surface(onClick=onSettings,shape=CircleShape,color=MaterialTheme.colorScheme.surfaceContainer,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.semantics{contentDescription=\"Настройки\"}){Text(if(sync==SyncState.SYNCING)\"↻\"else\"⚙\",Modifier.padding(12.dp),fontSize=19.sp)}"
        if (text.contains(oldHeader)) text = text.replace(oldHeader, newHeader)

        val oldAppearance = "GlassCard(Modifier.fillMaxWidth()){Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(\"Оформление\",fontWeight=FontWeight.SemiBold);Text(\"Светлая или тёмная тема включается автоматически вместе с темой телефона.\",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}"
        val newAppearance = """GlassCard(Modifier.fillMaxWidth()){Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(\"Оформление\",fontWeight=FontWeight.SemiBold);val themeMode=com.duobudget.app.ui.theme.ThemePreferences.currentMode;FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){FilterChip(themeMode==com.duobudget.app.ui.theme.ThemeMode.SYSTEM,{com.duobudget.app.ui.theme.ThemePreferences.set(context,com.duobudget.app.ui.theme.ThemeMode.SYSTEM)},label={Text(\"Система\")});FilterChip(themeMode==com.duobudget.app.ui.theme.ThemeMode.LIGHT,{com.duobudget.app.ui.theme.ThemePreferences.set(context,com.duobudget.app.ui.theme.ThemeMode.LIGHT)},label={Text(\"Светлая\")});FilterChip(themeMode==com.duobudget.app.ui.theme.ThemeMode.DARK,{com.duobudget.app.ui.theme.ThemePreferences.set(context,com.duobudget.app.ui.theme.ThemeMode.DARK)},label={Text(\"Тёмная\")})};Text(\"Выбор сохраняется на телефоне и применяется ко всему интерфейсу.\",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}"""
        if (text.contains(oldAppearance)) text = text.replace(oldAppearance, newAppearance)

        check(text.contains("contentDescription=\"Настройки\"")) { "Header settings icon patch was not applied" }
        check(text.contains("ThemePreferences.currentMode")) { "Theme selector patch was not applied" }
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
