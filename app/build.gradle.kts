plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.pushupanalyzer.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.pushupanalyzer.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 4
        versionName = "1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs.useLegacyPackaging = false }
    testOptions { animationsDisabled = true }
}
dependencies {
    // Bundled offline model, not a Play Services downloadable model.
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
