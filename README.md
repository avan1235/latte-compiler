# Latte Compiler
[![Tests badge](https://img.shields.io/github/workflow/status/avan1235/latte-compiler/Test?label=Tests)](https://github.com/avan1235/latte-compiler/actions/workflows/test.yaml)
[![Build badge](https://img.shields.io/github/workflow/status/avan1235/latte-compiler/Build%20Native?label=Build)](https://github.com/avan1235/latte-compiler/actions/workflows/build-native.yaml)
[![Download badge](https://img.shields.io/github/downloads/avan1235/latte-compiler/latest/total?color=bright-green&label=Release)](https://github.com/avan1235/latte-compiler/releases/latest)

[Latte](https://latte-lang.org/) is originally a JVM language that is fully interoperable with Java.
This project implements a native x64 compiler for this language with no support for interoperability with other languages
but tries to provide an optimized version of native version of this language.

## Running project

Use `make` tool to easily run the defined commands:

- `make latte` compiles project and produces `latc_x86` binary in project directory
- `make test` runs unit tests defined in project
- `make clean` cleans build files

## Dependencies

- [junit](https://junit.org/junit5/) as testing framework for tests launching and model
- [antlr](https://www.antlr.org/) as parser generator framework (with Gradle plugin)
- [gradle-graal](https://github.com/palantir/gradle-graal) for using GraalVM from Gradle to generate native binaries

https://github.com/avan1235/latte-compiler/releases/latest
