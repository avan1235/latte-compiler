# Latte Compiler
[![Tests badge](https://img.shields.io/github/actions/workflow/status/avan1235/latte-compiler/test.yaml?branch=master)](https://img.shields.io/github/actions/workflow/status/avan1235/latte-compiler/test.yaml?branch=master)
[![Build badge](https://img.shields.io/github/actions/workflow/status/avan1235/latte-compiler/build-native.yaml?branch=master)](https://img.shields.io/github/actions/workflow/status/avan1235/latte-compiler/build-native.yaml?branch=master)
[![Github All Releases](https://img.shields.io/github/downloads/avan1235/latte-compiler/total.svg?label=downloads)](https://github.com/avan1235/latte-compiler/releases/latest)

[Latte](https://latte-lang.org/) is originally a JVM language that is fully interoperable with Java.
This project implements a native x86 compiler for this language with no support for interoperability with other
languages but tries to provide an optimized version of native version of this language.

## Building project

### Project requirements

To build project you need:

- [Java 11](https://adoptopenjdk.net/) to build compiler from Kotlin sources and run Gradle in Java 11 environment
- [gcc](https://gcc.gnu.org/)  with `gcc-multilib` to compile [runtime.c](./lib/runtime.c) library file in x86 version

### Build commands

#### Make

- `make latte` compiles project and produces `latc_x86` binary in project directory
- `make test` runs unit tests defined in project
- `make clean` cleans build files

#### Gradle

- `./gradlew nativeImage` compiles project and produces `latc_x86` binary in project directory
- `./gradlew test` runs unit tests defined in project

## Compile-time dependencies

- [junit](https://junit.org/junit5/) as testing framework for tests launching and model
- [antlr](https://www.antlr.org/) as parser generator framework (with Gradle plugin)
- [gradle-graal](https://github.com/palantir/gradle-graal) for using GraalVM from Gradle to generate native binaries

