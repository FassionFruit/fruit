
# FassionFruit

[![CI](https://github.com/FassionFruit/fruit/actions/workflows/ci.yml/badge.svg)](https://github.com/FassionFruit/fruit/actions/workflows/ci.yml)
[![](https://jitpack.io/v/FassionFruit/fruit.svg)](https://jitpack.io/#FassionFruit/fruit)
![GitHub License](https://img.shields.io/github/license/FassionFruit/Fruit)

**A lightweight, annotation-driven binding library for Java's FFM.**

FassionFruit simplifies native interoperability by turning native functions into ordinary Java methods.

It eliminates repetitive FFM boilerplate such as library loading, symbol lookup, function descriptor creation, and method handle management through a declarative API.

With its Gradle plugin, FassionFruit can also automatically build native projects and generate Java bindings from native source code.

> [!WARNING]
> FassionFruit is under active development. APIs and implementation details may change.

## Features

- **Declarative bindings** — Define native functions using Java interfaces and annotations.
- **Native auto-binding** — Generate static Java methods directly from native function declarations.
- **Gradle integration** — Configure native projects, run custom build scripts, and package native libraries into your application.
- **Structure mapping** — Map C-compatible structures and unions to Java interfaces.
- **Native callbacks** — Interact with Java code through native function pointers.
- **Pure Java implementation** — Built on the standard Java FFM API without JNI.

## Why FassionFruit?

Java provides multiple approaches to native interoperability, each with different trade-offs.

| Feature | JNI | Java FFM | FassionFruit |
|---|:---:|:---:|:---:|
| Native code integration | ✅ | ✅ | ✅ |
| No C/C++ binding boilerplate | ❌ | ✅ | ✅ |
| Automatic library loading | ⚠️ | ❌ | ✅ |
| Automatic symbol lookup | ⚠️ | ❌ | ✅ |
| Automatic function descriptor creation | ⚠️ | ❌ | ✅ |
| Interface-based API | ❌ | ❌ | ✅ |
| Annotation-driven bindings | ❌ | ❌ | ✅ |
| Native structure mapping | ⚠️ | ⚠️ | ✅ |
| Native callbacks | ⚠️ | ✅ | ✅ |
| Pure Java implementation | ❌ | ✅ | ✅ |

- ✅ Supported directly
- ⚠️ Possible, but requires additional implementation or manual work
- ❌ Not supported directly

## Quick Start

### Requirements

- JDK 25 or later
- A compatible native library

### Installation

Add JitPack to your repositories:

```kotlin
repositories {
    maven("https://jitpack.io")
}
```

Add the dependency:

```kotlin
dependencies {
    implementation("com.github.fassionfruit.fruit:core:VERSION")
}
```

Replace `VERSION` with the desired release version.

### Define a Native Library

Suppose you have the following C functions:

```c
#include <stdint.h>

int32_t calculate_value(int32_t input) {
    return input * 2;
}

int32_t add_values(int32_t a, int32_t b) {
    return a + b;
}
```

Create a Java interface representing the native library:

```java
import io.github.fassionfruit.annotation.NativeLibrary;
import io.github.fassionfruit.annotation.NativeSymbol;

@NativeLibrary("mylib")
public interface MyLibrary {

    @NativeSymbol("calculate_value")
    int calculate(int input);

    @NativeSymbol("add_values")
    int add(int left, int right);
}
```

### Load and Call

```java
import io.github.fassionfruit.FFM;

MyLibrary library = FFM.load(MyLibrary.class);

int result = library.add(10, 20);
System.out.println(result);
```

FassionFruit handles library loading, symbol resolution, function descriptors, and invocation internally.

## Native Auto-Binding

FassionFruit provides a Gradle plugin that generates Java bindings directly from native source code.

Instead of manually declaring every native function in a Java interface, simply declare an empty Java class annotated with `@NativeLibrary`.

```java
import io.github.fassionfruit.annotation.NativeLibrary;

@NativeLibrary("native_example_rust")
public final class RustExample {
}
```

The Gradle plugin analyzes the native source code and generates static Java methods.

```java
int result = RustExample.add(10, 20);
```

Generated methods can be called from both Java and Kotlin.

### Supported Languages

| Language | Metadata Extraction |
|---|---|
| C | Clang JSON AST |
| C++ | Clang JSON AST (`extern "C"` exports) |
| Rust | `syn` |

Metadata is extracted from source declarations rather than relying on JNI or runtime reflection.

### Supported Types

Auto-binding currently supports:

- Scalar integers
- Floating-point values
- C strings
- Raw pointers
- C-compatible structures and unions
- One-dimensional fixed-size structure arrays

Generated structure interfaces can be used with `FFM.allocateStructure(...)`.

The following are not currently supported by auto-binding:

- Callbacks
- Bitfields
- Custom alignment
- Platform-dependent C `long`
- By-value aggregate function parameters

For unsupported signatures, use the existing `FFM.load(...)` API with `@NativeSymbol` and structure annotations.

**Current limitation:** The annotated declaration must be an empty Java class. Kotlin can consume generated Java bindings, but Kotlin classes cannot yet be used as annotated declarations.

### Gradle Plugin

The Gradle plugin provides:

- Native project directory configuration
- Customizable native build scripts
- Automatic native compilation
- Binding generation
- Packaging native libraries into the application JAR

See the [Gradle Plugin Guide](gradle-plugin/README.md) for installation, configuration, and usage.

## Native Access

Java FFM requires native access to be enabled.

Launch your application with:

```bash
java --enable-native-access=ALL-UNNAMED ...
```

For named modules, configure native access for the corresponding module instead.

## Building from Source

Clone the repository:

```bash
git clone https://github.com/FassionFruit/fruit.git
cd fruit
```

Build:

```bash
./gradlew build
```

Run tests:

```bash
./gradlew test
```

On Windows:

```powershell
.\gradlew.bat build
.\gradlew.bat test
```

Some integration tests require a C compiler to build native test libraries.

## Project Structure

| Module | Description |
|---|---|
| `api` | Public API and annotations |
| `core` | FFM binding implementation |
| `native` | Native test libraries |
| `gradle-plugin` | Gradle plugin for native builds and automatic binding generation |
| `buildSrc` | Shared Gradle build logic |

## Project Status

FassionFruit is actively developed.

The library already supports interface-based native bindings, structure mapping, callbacks, and native auto-binding through its Gradle plugin.

However, production stability and long-term compatibility guarantees have not yet been established.

## Contributing

Contributions are welcome!

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting changes.

All contributors are expected to follow our [Code of Conduct](CODE_OF_CONDUCT.md).

## Security

For information about reporting security vulnerabilities, see [SECURITY.md](SECURITY.md).

## License

FassionFruit is licensed under the [Apache License 2.0](LICENSE).
