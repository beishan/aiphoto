plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val appVersion = rootProject.file("../VERSION").readText().trim()
val parts = appVersion.split('.').map(String::toInt)
android {
    namespace = "com.aiphoto.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.aiphoto.android"
        minSdk = 29
        targetSdk = 36
        versionCode = parts[0] * 1000000 + parts[1] * 1000 + parts[2]
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    val releaseStore = providers.environmentVariable("AIPHOTO_KEYSTORE").orNull
    if (releaseStore != null) signingConfigs.create("privateRelease") {
        storeFile = file(releaseStore)
        storePassword = providers.environmentVariable("AIPHOTO_STORE_PASSWORD").orNull
        keyAlias = providers.environmentVariable("AIPHOTO_KEY_ALIAS").orNull
        keyPassword = providers.environmentVariable("AIPHOTO_KEY_PASSWORD").orNull
    }
    buildTypes { release {
        if (releaseStore != null) signingConfig = signingConfigs.getByName("privateRelease")
        isMinifyEnabled = false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt")) } }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("io.coil-kt.coil3:coil-gif:3.3.0")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.8.0")
    implementation("androidx.room:room-runtime:2.7.2")
    annotationProcessor("androidx.room:room-compiler:2.7.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
