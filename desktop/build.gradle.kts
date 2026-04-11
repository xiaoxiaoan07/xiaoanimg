import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm("desktop")

    sourceSets {
        named("desktopMain").dependencies {
            implementation(projects.shared)
            implementation(compose.desktop.currentOs)
            implementation(libs.components.resources)
            implementation(libs.jna)
            implementation(libs.jna.platform)
        }
    }
}

// ============================================================
// Native library build tasks
// ============================================================

val nativeDir = rootProject.layout.projectDirectory.dir("native")
val cargoTargetDir = nativeDir.dir("target/release")
val nativeOutputDir = rootProject.layout.buildDirectory.dir("native")

val osName: String = System.getProperty("os.name").lowercase()
val nativeLibName: String = when {
    osName.contains("win") -> "payload_extract_jni.dll"
    osName.contains("mac") -> "libpayload_extract_jni.dylib"
    else -> "libpayload_extract_jni.so"
}

val buildNativeLib by tasks.registering(Exec::class) {
    group = "native"
    description = "Build the Rust JNI native library"
    workingDir = nativeDir.asFile
    commandLine("cargo", "build", "--release")
    inputs.dir(nativeDir.dir("src"))
    inputs.file(nativeDir.file("Cargo.toml"))
    outputs.file(cargoTargetDir.file(nativeLibName))
}

// Copy native lib to a known location for development
val copyNativeLibForDev by tasks.registering(Copy::class) {
    group = "native"
    description = "Copy native library for development use"
    dependsOn(buildNativeLib)
    from(cargoTargetDir.file(nativeLibName))
    into(nativeOutputDir)
}

// For packaging: copy to appResources
val appResourcesDir = layout.projectDirectory.dir("resources")

val copyNativeLibForPackage by tasks.registering(Copy::class) {
    group = "native"
    description = "Copy native library to appResources for packaging"
    dependsOn(buildNativeLib)
    from(cargoTargetDir.file(nativeLibName))
    val targetDir = when {
        osName.contains("win") -> "windows-x64"
        osName.contains("mac") -> "macos-arm64"
        else -> "linux-x64"
    }
    into(appResourcesDir.dir(targetDir))
}

// Wire into build
tasks.named("desktopProcessResources") {
    dependsOn(copyNativeLibForDev)
}

afterEvaluate {
    tasks.named("prepareAppResources") {
        dependsOn(copyNativeLibForPackage)
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"

        buildTypes.release.proguard {
            configurationFiles.from("proguard-rules-jvm.pro")
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "payload-extract-gui"
            packageVersion = "1.0.0"
            appResourcesRootDir.set(appResourcesDir.asFile)

            windows.iconFile = project.file("src/desktopMain/resources/windows/Icon.ico")
            linux.iconFile = project.file("src/desktopMain/resources/linux/Icon.png")
            macOS.iconFile = project.file("src/desktopMain/resources/macos/Icon.png")
        }
    }
}
