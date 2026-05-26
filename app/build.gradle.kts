import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
    }

    namespace = "com.hawatri.pinit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hawatri.pinit"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // CameraX
    val camerax_version = "1.3.3"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")

    // ML Kit
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // Coil
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Jsoup
    implementation("org.jsoup:jsoup:1.17.2")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // Room
    val room_version = "2.7.2"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Biometric
    implementation("androidx.biometric:biometric:1.1.0")

    // OpenStreetMap (osmdroid) — free, no API key
    implementation("org.osmdroid:osmdroid-android:6.1.18")

    // ZXing — QR code generation
    implementation("com.google.zxing:core:3.5.3")

    // Drag-to-reorder for LazyVerticalStaggeredGrid (manual sort mode)
    implementation("sh.calvin.reorderable:reorderable:2.4.0")

    // Google Sign-In (Drive scope)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Google Drive REST v3 + the OAuth2 credential helper for Android
    implementation("com.google.api-client:google-api-client-android:2.2.0") {
        exclude(group = "org.apache.httpcomponents")
        exclude(group = "com.google.http-client", module = "google-http-client-jackson2")
    }
    implementation("com.google.apis:google-api-services-drive:v3-rev20231128-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
        exclude(group = "com.google.http-client", module = "google-http-client-jackson2")
    }
    implementation("com.google.http-client:google-http-client-gson:1.43.3")
}

// Tame the duplicate-class noise the Google API client transitively pulls in
configurations.all {
    exclude(group = "org.apache.httpcomponents", module = "httpclient")
}