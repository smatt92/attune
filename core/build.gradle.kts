plugins {
    kotlin("jvm")
}

dependencies {
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

// The CI `unit` job and SPEC use `./gradlew testDebugUnitTest` (an Android-flavored task
// name). For this pure-JVM module the real task is `test`; alias it so the one command
// runs JVM and Android unit tests alike.
tasks.register("testDebugUnitTest") {
    dependsOn("test")
}
