plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.hournet.hdrzk"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hournet.hdrzk"
        minSdk = 30
        targetSdk = 36
        versionCode = 6
        versionName = "1.3.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Проверяем environment variables для CI/CD
            val ciKeystorePath = System.getenv("KEYSTORE_PATH")
            val ciKeystorePassword = System.getenv("KEYSTORE_PASSWORD")
            val ciKeyAlias = System.getenv("KEY_ALIAS")
            val ciKeyPassword = System.getenv("KEY_PASSWORD")

            if (!ciKeystorePath.isNullOrEmpty() && File(ciKeystorePath).exists()) {
                // CI/CD environment
                storeFile = file(ciKeystorePath)
                storePassword = ciKeystorePassword
                keyAlias = ciKeyAlias
                keyPassword = ciKeyPassword
                println("Using CI/CD signing config")
            } else {
                // Локальная разработка - используем properties
                val keystorePath = findProperty("keystoreFile")?.toString()
                val keystorePass = findProperty("keystorePassword")?.toString()
                val keyAliasName = findProperty("keyAlias")?.toString() ?: "hdrzk-key"
                val keyPass = findProperty("keyPassword")?.toString()

                if (!keystorePath.isNullOrEmpty() && File(keystorePath).exists()) {
                    storeFile = file(keystorePath)
                    storePassword = keystorePass
                    keyAlias = keyAliasName
                    keyPassword = keyPass
                    println("Using local signing config")
                } else {
                    println("No signing config found - APK will not be signed")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Подписываем только если есть конфигурация
            val releaseConfig = signingConfigs.getByName("release")
            if (releaseConfig.storeFile?.exists() == true) {
                signingConfig = releaseConfig
                println("Release APK will be signed")
            } else {
                println("Warning: Release APK will NOT be signed - update system may not work")
            }

            // Настройки для оптимизации APK
            packaging {
                resources {
                    excludes += setOf(
                        "/META-INF/{AL2.0,LGPL2.1}",
                        "/META-INF/DEPENDENCIES",
                        "/META-INF/LICENSE",
                        "/META-INF/LICENSE.txt",
                        "/META-INF/NOTICE",
                        "/META-INF/NOTICE.txt"
                    )
                }
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // HTTP клиент для автообновления
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Для работы с JSON (GitHub API)
    implementation("org.json:json:20231013")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Задача для проверки подписи
tasks.register("checkSigning") {
    doLast {
        val releaseConfig = android.signingConfigs.getByName("release")
        if (releaseConfig.storeFile?.exists() == true) {
            println("✅ Signing configuration is valid")
            println("Keystore: ${releaseConfig.storeFile}")
            println("Alias: ${releaseConfig.keyAlias}")
        } else {
            println("❌ Signing configuration is missing or invalid")
        }
    }
}