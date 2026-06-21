pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // Versions live here so modules apply plugins by id with no version, and — crucially —
    // a plugin is only *resolved* when a module actually applies it. That keeps AGP (from
    // the Google repo) out of the build on JDK-only machines/CI jobs that build :core/:tools.
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("com.android.application") version "8.7.3"
        id("org.jetbrains.kotlin.android") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "attune"

// Pure-Kotlin/JVM modules — always part of the build (fast, no Android SDK needed).
include(":core")
include(":tools")

// The Android app requires the Android SDK + AGP. Only include it when an SDK is
// actually present (local.properties `sdk.dir`, or ANDROID_HOME / ANDROID_SDK_ROOT).
// This keeps `./gradlew testDebugUnitTest` green on machines/CI jobs that only have a
// JDK (the `unit` and `intent-eval` paths), while the Mac and the emulator CI job —
// which do have the SDK — build the full three-module project.
val androidSdkAvailable: Boolean = run {
    val fromEnv = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    val fromLocalProps = file("local.properties").takeIf { it.exists() }?.let { props ->
        java.util.Properties().apply { props.inputStream().use { load(it) } }.getProperty("sdk.dir")
    }
    !fromEnv.isNullOrBlank() || !fromLocalProps.isNullOrBlank()
}

if (androidSdkAvailable) {
    include(":app")
} else {
    logger.lifecycle(
        "Attune: no Android SDK detected — building :core and :tools only. " +
            "Set ANDROID_HOME or add local.properties(sdk.dir) to include :app."
    )
}
