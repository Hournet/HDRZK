import org.gradle.api.DefaultTask
        import org.gradle.api.tasks.Input
        import org.gradle.api.tasks.TaskAction
        import java.io.File
        import org.gradle.api.GradleException
        import org.gradle.api.provider.Property

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
                versionCode = 1
                versionName = "0.0.2"
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            signingConfigs {
                create("release") {
                    val ciKeystorePath = System.getenv("KEYSTORE_PATH")
                    val ciKeystorePassword = System.getenv("KEYSTORE_PASSWORD")
                    val ciKeyAlias = System.getenv("KEY_ALIAS")
                    val ciKeyPassword = System.getenv("KEY_PASSWORD")
                    val isCI = System.getenv("CI") == "true"

                    when {
                        isCI && !ciKeystorePath.isNullOrEmpty() -> {
                            val keystoreFile = File(ciKeystorePath)
                            if (keystoreFile.exists()) {
                                storeFile = keystoreFile
                                storePassword = ciKeystorePassword
                                keyAlias = ciKeyAlias
                                keyPassword = ciKeyPassword
                                println("✅ Using CI/CD signing config: ${keystoreFile.name}")
                            } else {
                                throw GradleException("❌ CI keystore not found: $ciKeystorePath")
                            }
                        }
                        else -> {
                            val keystorePath = project.findProperty("keystoreFile") as String?
                            val keystorePass = project.findProperty("keystorePassword") as String?
                            val keyAliasName = project.findProperty("keyAlias") as String?
                            val keyPass = project.findProperty("keyPassword") as String?

                            if (!keystorePath.isNullOrEmpty() && !keystorePass.isNullOrEmpty() &&
                                !keyAliasName.isNullOrEmpty() && !keyPass.isNullOrEmpty()) {

                                val keystoreFile = rootProject.file(keystorePath)
                                if (keystoreFile.exists()) {
                                    storeFile = keystoreFile
                                    storePassword = keystorePass
                                    keyAlias = keyAliasName
                                    keyPassword = keyPass
                                    println("✅ Using local signing config: ${keystoreFile.name}")
                                } else {
                                    println("❌ Local keystore not found: ${keystoreFile.absolutePath}")
                                    println("⚠️  Release APK will be unsigned")
                                }
                            } else {
                                println("⚠️  No signing configuration found in gradle.properties")
                                println("⚠️  Release APK will be unsigned")
                            }
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

                    val releaseSigningConfig = signingConfigs.getByName("release")
                    if (releaseSigningConfig.storeFile?.exists() == true) {
                        signingConfig = releaseSigningConfig
                        println("✅ Release APK will be signed")
                    } else {
                        println("⚠️  Release APK will NOT be signed - no valid keystore found")
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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20231013")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// ----------------------------
// Сериализуемая задача проверки подписи
// ----------------------------
abstract class CheckSigningTask : DefaultTask() {

    @get:Input
    abstract val keystoreFilePath: Property<String>

    @get:Input
    abstract val keystorePassword: Property<String>

    @get:Input
    abstract val keyAlias: Property<String>

    @get:Input
    abstract val keyPassword: Property<String>

    @TaskAction
    fun run() {
        println("🔍 Checking signing configuration...")

        val keystoreFile = File(keystoreFilePath.get())

        if (!keystoreFile.exists()) {
            println("❌ Keystore file does not exist: ${keystoreFile.absolutePath}")
            throw GradleException("Keystore file not found: ${keystoreFile.absolutePath}")
        }

        if (keystorePassword.get().isEmpty()) {
            println("❌ Keystore password is not set")
            throw GradleException("Keystore password is required")
        }

        if (keyAlias.get().isEmpty()) {
            println("❌ Key alias is not set")
            throw GradleException("Key alias is required")
        }

        if (keyPassword.get().isEmpty()) {
            println("❌ Key password is not set")
            throw GradleException("Key password is required")
        }

        println("✅ Signing configuration is valid")
        println("📁 Keystore: ${keystoreFile.name}")
        println("🔑 Alias: ${keyAlias.get()}")
    }
}

// Регистрируем задачу
tasks.register<CheckSigningTask>("checkSigning") {
    group = "verification"
    description = "Checks if signing configuration is properly set up"

    val releaseConfig = android.signingConfigs.getByName("release")

    keystoreFilePath.set(releaseConfig.storeFile?.absolutePath ?: "")
    keystorePassword.set(releaseConfig.storePassword ?: "")
    keyAlias.set(releaseConfig.keyAlias ?: "")
    keyPassword.set(releaseConfig.keyPassword ?: "")
}