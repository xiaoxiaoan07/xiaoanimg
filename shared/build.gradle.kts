plugins {
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        androidResources.enable = true
        compileSdk {
            version = release(37) {
                minorApiLevel = 0
            }
        }
        minSdk = 26
        namespace = "top.yukonga.payload_extract_gui.shared"
    }

    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(libs.miuix.ui)
            implementation(libs.miuix.preference)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.components.resources)
        }

        named("androidMain").dependencies {
            implementation(libs.androidx.activity)
        }

        named("desktopMain").dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose.resources {
    publicResClass = true
}
