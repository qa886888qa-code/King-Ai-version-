plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
fun configValue(name: String): String = (providers.gradleProperty(name).orNull ?: "")
    .replace("\\", "\\\\").replace("\"", "\\\"")
android {
    namespace = "com.kingai.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kingai.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "SUPABASE_URL", "\"" + configValue("SUPABASE_URL") + "\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"" + configValue("SUPABASE_ANON_KEY") + "\"")
        buildConfigField("String", "KING_AI_CHAT_FUNCTION_URL", "\"" + configValue("KING_AI_CHAT_FUNCTION_URL") + "\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")
    implementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
