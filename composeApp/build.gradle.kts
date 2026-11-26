import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.application)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.material.icons.extended)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            // Decodificador OGG Vorbis para javax.sound (la música del juego va en .ogg)
            implementation(libs.vorbisspi)
        }
    }
}

android {
    namespace = "com.petbattle"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.petbattle"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }

    // Dos compilaciones de la app Android: "normal" (móvil y tableta, con todas las pantallas) y
    // "watch" (reloj Wear OS, solo el modo cuidado). Cada una añade su propio manifiesto
    // (src/androidNormal y src/androidWatch) y le dice al código común qué variante es con
    // BuildConfig.APP_VARIANT (ver core/config/AppVariant.kt).
    flavorDimensions += "device"
    productFlavors {
        create("normal") {
            dimension = "device"
            buildConfigField("String", "APP_VARIANT", "\"normal\"")
        }
        create("watch") {
            dimension = "device"
            // Mismo minSdk que el móvil: también va en relojes con Android normal (no Wear OS),
            // como el SaveWatch 2 (Android 8.1, API 27).
            buildConfigField("String", "APP_VARIANT", "\"watch\"")
        }
    }
    // Con Kotlin Multiplatform, AGP no fusiona por sí solo los manifiestos de los flavors
    // (comprobado: sin esto el APK sale sin ellos); se indican a mano.
    sourceSets["normal"].manifest.srcFile("src/androidNormal/AndroidManifest.xml")
    sourceSets["watch"].manifest.srcFile("src/androidWatch/AndroidManifest.xml")
}

compose.desktop {
    application {
        mainClass = "com.petbattle.MainKt"
        // `./gradlew :composeApp:run -Pwatch` arranca como el reloj (solo modo cuidado, ventana pequeña).
        if (project.hasProperty("watch")) jvmArgs += "-Dpetbattle.variant=watch"
        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "pet-battle"
            packageVersion = "1.0.0"
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.petbattle.resources"
    generateResClass = always
}
