plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "fi.skillit"
    compileSdk = 35
    defaultConfig {
        applicationId = "fi.skillit"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    kotlinOptions { jvmTarget = "21" }
    // Compose UI tests of the math boards run on the JVM (Robolectric), so CI and workers need no emulator.
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    implementation(project(":engine"))
    implementation(project(":mathgen"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.material3)
    implementation(libs.compose.ui)
    implementation(libs.datastore)
    implementation(libs.ink.authoring)
    implementation(libs.ink.brush)
    implementation(libs.ink.rendering)
    implementation(libs.ink.strokes)
    implementation(libs.mlkit.digital.ink)
    debugImplementation(libs.compose.tooling)
    testImplementation(libs.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.compose.test.manifest)
}
