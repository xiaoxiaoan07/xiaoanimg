import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity)
}

android {
    val properties = Properties()
    runCatching { properties.load(project.rootProject.file("local.properties").inputStream()) }
    val keystorePath = properties.getProperty("KEYSTORE_PATH") ?: System.getenv("KEYSTORE_PATH")
    val keystorePwd = properties.getProperty("KEYSTORE_PASS") ?: System.getenv("KEYSTORE_PASS")
    val alias = properties.getProperty("KEY_ALIAS") ?: System.getenv("KEY_ALIAS")
    val pwd = properties.getProperty("KEY_PASSWORD") ?: System.getenv("KEY_PASSWORD")
    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePwd
                keyAlias = alias
                keyPassword = pwd
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    defaultConfig {
        applicationId = "top.yukonga.payload_extract_gui"
        minSdk = 26
        targetSdk = 37
        versionName = "1.0.0"
        versionCode = 1
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    namespace = "top.yukonga.payload_extract_gui"
}

// ============================================================
// Native library build tasks
// ============================================================

val nativeDir = rootProject.layout.projectDirectory.dir("native")

data class AndroidTarget(val rustTriple: String, val abi: String, val clangPrefix: String)

val androidTargets = listOf(
    AndroidTarget("aarch64-linux-android", "arm64-v8a", "aarch64-linux-android"),
    AndroidTarget("x86_64-linux-android", "x86_64", "x86_64-linux-android"),
)

val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs")

val hostTag = when {
    System.getProperty("os.name").lowercase().contains("win") -> "windows-x86_64"
    System.getProperty("os.name").lowercase().contains("mac") -> "darwin-x86_64"
    else -> "linux-x86_64"
}

val clangSuffix = if (hostTag.startsWith("windows")) ".cmd" else ""

val sdkDir: String by lazy {
    val props = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.reader().use { reader -> props.load(reader) }
    }
    props.getProperty("sdk.dir")
        ?: System.getenv("ANDROID_HOME")
        ?: error("Cannot find Android SDK: set sdk.dir in local.properties or ANDROID_HOME env var")
}

val ndkVersion: String by lazy {
    val version = android.ndkVersion
    version.ifEmpty {
        File(sdkDir, "ndk").listFiles()?.maxByOrNull { it.name }?.name
        ?: error("No NDK found in $sdkDir/ndk/")
    }
}

val ndkDir = File(sdkDir, "ndk/$ndkVersion")
val toolchainBin = File(ndkDir, "toolchains/llvm/prebuilt/$hostTag/bin")
val minApi = android.defaultConfig.minSdk!!

val arBin = File(toolchainBin, "llvm-ar${if (hostTag.startsWith("windows")) ".exe" else ""}")

val buildTasks = androidTargets.map { target ->
    val clang = File(toolchainBin, "${target.clangPrefix}${minApi}-clang${clangSuffix}")
    val clangxx = File(toolchainBin, "${target.clangPrefix}${minApi}-clang++${clangSuffix}")
    val envTarget = target.rustTriple.replace('-', '_')
    tasks.register<Exec>("buildNativeLib_${target.abi}") {
        group = "native"
        description = "Build Rust JNI library for ${target.abi}"
        workingDir = nativeDir.asFile
        commandLine("cargo", "build", "--release", "--target", target.rustTriple, "--no-default-features", "--features", "vendored-tls")
        environment("CARGO_TARGET_${envTarget.uppercase()}_LINKER", clang.absolutePath)
        environment("CC_$envTarget", clang.absolutePath)
        environment("CXX_$envTarget", clangxx.absolutePath)
        environment("AR_$envTarget", arBin.absolutePath)
        environment("ANDROID_NDK_HOME", ndkDir.absolutePath)
        environment("PATH", "${toolchainBin.absolutePath}${File.pathSeparator}${System.getenv("PATH")}")
        inputs.dir(nativeDir.dir("src"))
        inputs.file(nativeDir.file("Cargo.toml"))
        outputs.file(nativeDir.dir("target/${target.rustTriple}/release/libpayload_extract_jni.so"))
    }
}

val buildNativeLibForAndroid by tasks.registering {
    group = "native"
    description = "Build Rust JNI library for all Android targets"
    dependsOn(buildTasks)
}

val copyNativeLibsForAndroid by tasks.registering {
    group = "native"
    description = "Copy built .so files to jniLibs"
    dependsOn(buildNativeLibForAndroid)

    val nativeDirFile = nativeDir.asFile
    val jniLibsDirFile = jniLibsDir.asFile
    val targets = androidTargets.map { it.rustTriple to it.abi }
    doLast {
        targets.forEach { (rustTriple, abi) ->
            val src = File(nativeDirFile, "target/$rustTriple/release/libpayload_extract_jni.so")
            val destDir = File(jniLibsDirFile, abi)
            destDir.mkdirs()
            src.copyTo(File(destDir, "libpayload_extract_jni.so"), overwrite = true)
        }
    }
}

tasks.named("preBuild") {
    dependsOn(copyNativeLibsForAndroid)
}
