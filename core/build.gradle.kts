plugins {
    kotlin("jvm")
}

dependencies {
    // Pure-JVM JSON (no compiler plugin needed — we build/parse JsonElement at runtime).
    // Used by ClaudeIntentParser to assemble the emit_plan request and parse tool_use output.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test-junit"))
    // For runBlocking in the eval harness + parser tests (parse() is a suspend fun).
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    // The intent-eval harness lives in the test source set and needs the real tools
    // (their ToolDescriptors) to ground the parser. Depending on :tools from :core's TEST
    // source set is allowed — it does not create a cycle (tools depends on :core's MAIN).
    testImplementation(project(":tools"))
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

// :core:intentEval — runs the golden corpus through the real ClaudeIntentParser and reports a
// pass-rate (Tier 2 of the testing pyramid). Calls the Claude API, so it is NOT part of the
// push build — CI runs it on schedule/dispatch with ANTHROPIC_API_KEY. Locally:
//   ANTHROPIC_API_KEY=... ./gradlew :core:intentEval
tasks.register<JavaExec>("intentEval") {
    group = "verification"
    description = "Run the intent-eval harness against core/src/test/resources/intents (needs ANTHROPIC_API_KEY)."
    dependsOn("testClasses")
    mainClass.set("com.attune.core.eval.IntentEvalKt")
    classpath = sourceSets["test"].runtimeClasspath
    // Pass through the key + optional model/threshold overrides from the environment.
    environment("ANTHROPIC_API_KEY", System.getenv("ANTHROPIC_API_KEY") ?: "")
    System.getenv("ATTUNE_EVAL_MODEL")?.let { environment("ATTUNE_EVAL_MODEL", it) }
    System.getenv("ATTUNE_EVAL_THRESHOLD")?.let { environment("ATTUNE_EVAL_THRESHOLD", it) }
}
