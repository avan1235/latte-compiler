import org.gradle.api.tasks.testing.logging.TestLogEvent.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.6.10"
  antlr
  id("com.palantir.graal") version "0.10.0"
}

val jarName = "latte.jar"

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
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "${JavaVersion.VERSION_1_8}"
  dependsOn(tasks.generateGrammarSource)
}

tasks.generateGrammarSource {
  arguments = listOf("-visitor", "-package", "ml.dev.kotlin.syntax")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

graal {
  javaVersion("8")
  graalVersion("21.2.0")
  outputName("latte")
  mainClass("ml.dev.kotlin.latte.MainKt")
  option("--verbose")
  option("--no-fallback")
}

tasks.nativeImage {
  doLast {
    copy {
      from("$buildDir/graal/latte")
      into("$projectDir")
    }
  }
}
