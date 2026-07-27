plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.example.serenitydroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.serenitydroid"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf("-DANDROID_STL=c++_shared", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    kotlin {
        jvmToolchain(21)
        target {
            compilerOptions {
                optIn.add("kotlin.RequiresOptIn")
            }
        }
    }

    // --- STEP 2: THIS IS THE CRITICAL CONFIGURATION ---
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    androidResources {
        noCompress += "litertlm"
    }

    buildFeatures {
        compose = true
        viewBinding = true // You can keep this if you use both
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    buildToolsVersion = "37.0.0"

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
} // This closes the android block

dependencies {
    // Core & UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.documentfile)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)

    // Hilt Core
    implementation(libs.hilt.android)
    // Hilt Compiler (must use ksp for code generation)
    ksp(libs.hilt.compiler)

    // ADD THIS LINE FOR JSON PARSING
    implementation(libs.gson)

    // ADD THIS LINE FOR PREFERENCE MANAGER
    implementation(libs.androidx.preference.ktx)
    // ViewModel & LiveData for state management
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Coroutines for background tasks
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // LiteRT-LM & Qualcomm QNN NPU Delegate for on-device inference
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.14.0")
    implementation(libs.qnn.runtime)
    implementation(libs.qnn.litert.delegate)

    // Jetpack Compose Dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Android Studio Preview support
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)

    // UI Tests (optional but recommended)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    implementation(libs.glide)
    // ksp(libs.ksp)
    // Optional: Hilt for testing
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)

    // Unit Tests
    testImplementation(libs.junit)
}
