plugins {
    kotlin("jvm")
}

dependencies {
    // :tools depends only on the contracts in :core (SettingsContext, ConfigTool, …).
    implementation(project(":core"))
    testImplementation(kotlin("test-junit"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// See :core — alias so `./gradlew testDebugUnitTest` runs this module's JVM unit tests.
tasks.register("testDebugUnitTest") {
    dependsOn("test")
}
