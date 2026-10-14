plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.paysplit.app"
    compileSdk = 36 // Android 16

    defaultConfig {
        applicationId = "com.paysplit.app"
        minSdk = 35 // Android 15 и выше (приложение собирается под Android 16)
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Приложение полностью русскоязычное.
        resourceConfigurations += setOf("ru")
    }

    signingConfigs {
        // Сборка release подписывается отладочным ключом, чтобы
        // `./gradlew assembleRelease` сразу выдавал устанавливаемый APK.
        // Для публикации замените на свой релизный ключ (см. README).
        getByName("debug")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Настройки компилятора Kotlin (проект использует классический плагин
// org.jetbrains.kotlin.android, см. android.builtInKotlin=false в gradle.properties).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Страховка: позволяет компилятору Kotlin 2.3.x читать библиотеки,
        // собранные более новой версией Kotlin (например, свежие AndroidX-библиотеки).
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    // --- Базовые AndroidX ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // --- Jetpack Compose ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    // Иконки Material Icons. Начиная с Compose Material3 1.4.0 эта библиотека
    // больше не поставляется транзитивно, поэтому подключается вручную.
    // В проекте используются только базовые иконки (material-icons-core),
    // material-icons-extended не нужен.
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // --- Lifecycle & Navigation ---
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // --- Room (локальная база данных) ---
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // --- WorkManager (уведомления-напоминания) ---
    implementation(libs.androidx.work.runtime.ktx)

    // --- Биометрия (BiometricPrompt требует FragmentActivity) ---
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)

    // --- Графики Vico ---
    implementation(libs.vico.compose.m3)

    // --- Тесты ---
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
