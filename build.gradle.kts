import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.6.10"
  antlr
  id("com.palantir.graal") version "0.10.0"
}

group = "ml.dev.kotlin"
version = "1.0"

repositories {
  mavenCentral()
}

dependencies {
  implementation(kotlin("stdlib"))

  implementation("org.antlr:antlr4-runtime:4.9.3")
  antlr("org.antlr:antlr4:4.9.3")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
  testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.2")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

tasks.getByName<Test>("test") {
  useJUnitPlatform()
  ignoreFailures = true
  testLogging { events = setOf(PASSED, SKIPPED, FAILED) }
  afterSuite(closure<TestDescriptor, TestResult> { suite, result ->
    if (suite.parent == null) println(
      "TEST RESULTS: ${suite.displayName}\n" +
        "Passed: ${result.successfulTestCount}/${result.testCount}\t" +
        "Failed: ${result.failedTestCount}/${result.testCount}\t" +
        "Skipped: ${result.skippedTestCount}/${result.testCount}"
    )
  })
}

val buildRuntime = task<Exec>("buildRuntime") {
  commandLine("gcc", "-m32", "-c", "lib/runtime.c", "-o", "lib/runtime.o")
}

tasks.withType<KotlinCompile> {
  dependsOn(buildRuntime)
  kotlinOptions.jvmTarget = "${JavaVersion.VERSION_1_8}"
  dependsOn(tasks.generateGrammarSource)
}

tasks.generateGrammarSource {
  arguments = listOf("-visitor", "-package", "ml.dev.kotlin.latte.syntax")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

graal {
  javaVersion("8")
  graalVersion("21.2.0")
  outputName("latc_x86")
  mainClass("ml.dev.kotlin.latte.MainKt")
  option("--verbose")
  option("--no-fallback")
}

tasks.nativeImage {
  doLast {
    copy {
      from("$buildDir/graal/latc_x86")
      into("$projectDir")
    }
  }
}

fun <T, U> closure(c: (T, U) -> Unit): KotlinClosure2<T, U, Unit> = KotlinClosure2(c)
