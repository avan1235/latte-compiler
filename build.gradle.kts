import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.6.10"
  antlr
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
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

tasks.getByName<Test>("test") {
  useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "${JavaVersion.VERSION_1_8}"
  dependsOn(tasks.generateGrammarSource)
}

tasks.generateGrammarSource {
  arguments = listOf("-visitor", "-package", "ml.dev.kotlin.syntax")
}
