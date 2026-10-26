plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.example.serenitydroid"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.serenitydroid"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    "-DGGML_OPENCL=ON",
                    "-DGGML_VULKAN=OFF",
                    "-DGGML_HEXAGON=OFF",
                    "-DHEXAGON_SDK_ROOT=${System.getenv("HEXAGON_SDK_ROOT") ?: "C:/Qualcomm/AIStack/QAIRT/2.31.0.250130"}",
                    "-DGGML_OPENMP=ON",
                    "-DPython3_EXECUTABLE=C:/Users/ccrg6/AppData/Local/Microsoft/WindowsApps/python.exe"
                )
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
        compilerOptions {
            optIn.add("kotlin.RequiresOptIn")
            languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
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
            excludes += "**/libOpenCL.so"
            pickFirsts.add("**/libQnn*.so")
            pickFirsts.add("**/libGenie*.so")
            pickFirsts.add("**/libomp.so")
            pickFirsts.add("**/liblitert_dispatch.so")
            pickFirsts.add("**/liblitert_npu_compiler.so")
            pickFirsts.add("**/libLiteRtDispatch_Qualcomm.so")
            pickFirsts.add("**/liblitertlm_jni.so")
            pickFirsts.add("**/liblitert_jni.so")
            pickFirsts.add("**/libggml-htp-*.so")
        }
    }

    ndkVersion = "29.0.13113456"
    dynamicFeatures.add(":serenity_npu")

    val libsDir = layout.buildDirectory.dir("libs")

    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("build/libs")
        }
    }

    tasks.register("prepareNativeLibs") {
        description = "Consolidates all native libraries (QAIRT SDK + LiteRT AARs) into the build folder with proper ARM64 filtering."
        
        // Source paths
        val toolsDir = File("C:/Users/ccrg6/Desktop/Desktop/Tools")
        val qnnSDKLocalPath = "C:\\Qualcomm\\AIStack\\QAIRT\\2.31.0.250130"
        val jitV79LibsPath = File(toolsDir, "litert_npu_runtime_libraries_jit/qualcomm_runtime_v79/src/main/jni/arm64-v8a")
        val jitV81LibsPath = File(toolsDir, "litert_npu_runtime_libraries_jit/qualcomm_runtime_v81/src/main/jni/arm64-v8a")
        val arm64Dir = libsDir.get().dir("arm64-v8a").asFile

        doLast {
            // 1. Clean the destination to avoid architecture mismatches (EM_X86_64)
            if (arm64Dir.exists()) {
                println("[PrepareNative] Cleaning old JNI libs...")
                arm64Dir.deleteRecursively()
            }
            arm64Dir.mkdirs()

            // 2. Extract from AARs and JARs (LiteRT, LiteRT-LM, etc.)
            val preferredMarkers = listOf("arm64-v8a", "linux-aarch64", "android-arm64", "arm64")
            val excludedMarkers = listOf("x86_64", "x86", "armeabi-v7a", "linux-x86_64", "i386", "darwin")

            configurations.getByName("debugRuntimeClasspath").incoming.artifacts.forEach { artifact ->
                val file = artifact.file
                if (file.name.endsWith(".aar") || file.name.endsWith(".jar")) {
                    zipTree(file).matching {
                        include("**/*.so")
                        excludedMarkers.forEach { exclude("**/$it/**") }
                    }.files.forEach { soFile ->
                        val path = soFile.path.replace("\\", "/")
                        val isArm64 = preferredMarkers.any { path.contains(it) }
                        val isGenericJni = path.contains("jni/") && !excludedMarkers.any { path.contains(it) }
                        
                        if (isArm64 || isGenericJni) {
                            val destFile = File(arm64Dir, soFile.name)
                            if (!destFile.exists()) {
                                println("[PrepareNative] Extracting ARM64: ${soFile.name} from ${file.name}")
                                soFile.copyTo(destFile, overwrite = false)
                            } else {
                                println("[PrepareNative] Skipping ${soFile.name} from ${file.name} (already exists)")
                            }
                        }
                    }
                }
            }

            // 3. Copy from Qualcomm QAIRT SDK (Standard Stubs & HTP binaries)
            copy {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                from(qnnSDKLocalPath) {
                    include("**/lib/aarch64-android/libQnnHtp.so")
                    include("**/lib/aarch64-android/libQnnHtpPrepare.so")
                    include("**/lib/aarch64-android/libQnnSystem.so")
                    include("**/lib/aarch64-android/libQnnSaver.so")
                    include("**/lib/aarch64-android/libQnnTFLiteDelegate.so")
                    include("**/lib/aarch64-android/libomp.so")
                    
                    // V79 Support (Snapdragon 8 Gen 3)
                    include("**/lib/hexagon-v79/unsigned/libQnnHtpV79Skel.so")
                    include("**/lib/aarch64-android/libQnnHtpV79Stub.so")
                    
                    // V81 Support (Snapdragon 8 Elite / SM8750)
                    include("**/lib/hexagon-v81/unsigned/libQnnHtpV81Skel.so")
                    include("**/lib/aarch64-android/libQnnHtpV81Stub.so")
                    
                    eachFile { path = name }

                }
                into(arm64Dir)
                includeEmptyDirs = false
            }

            // 4. Copy and Rename LiteRT JIT Libraries (Prefer V81 if available, otherwise V79)
            val selectedJitPath = if (jitV81LibsPath.exists()) {
                println("[PrepareNative] Using V81 JIT Dispatcher (Snapdragon 8 Elite)")
                jitV81LibsPath
            } else {
                println("[PrepareNative] Falling back to V79 JIT Dispatcher")
                jitV79LibsPath
            }

            copy {
                duplicatesStrategy = DuplicatesStrategy.EXCLUDE
                from(selectedJitPath) {
                    include("libLiteRtDispatch_Qualcomm.so")
                    include("libLiteRtCompilerPlugin_Qualcomm.so")
                    
                    rename { fileName ->
                        when (fileName) {
                            "libLiteRtDispatch_Qualcomm.so" -> "liblitert_dispatch.so"
                            "libLiteRtCompilerPlugin_Qualcomm.so" -> "liblitert_npu_compiler.so"
                            else -> fileName
                        }
                    }
                }
                into(arm64Dir)
            }

            println("[PrepareNative] Sync complete. ARM64 directory: ${arm64Dir.absolutePath}")
        }
    }

    project.tasks.named("preBuild") {
        dependsOn("prepareNativeLibs")
    }

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
    implementation(libs.play.feature.delivery.ktx)
    implementation(libs.geniex.android)

    // Coroutines for background tasks
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Qualcomm QNN & NPU Support
    implementation(libs.qnn.runtime)
    implementation(libs.qnn.litert.delegate)

    // LiteRT-LM for on-device inference
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")
    implementation("com.google.ai.edge.litert:litert:2.1.6")
    implementation("com.google.ai.edge.litert:litert-gpu:1.4.2")

    // Jetpack Compose Dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation("io.coil-kt:coil-compose:2.7.0")

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
