# FassionFruit

**A lightweight, annotation-driven binding library for Java's Foreign Function & Memory API (FFM).**

FassionFruit simplifies native interoperability by allowing developers to define native libraries through Java interfaces and annotations.

Instead of manually handling library loading, symbol lookup, function descriptors, and method handles, FassionFruit provides a declarative API for native function bindings.

> **Status:** Early development. APIs and implementation are subject to change.

## Why FassionFruit?

Java provides multiple ways to interact with native code. Each approach has its own trade-offs.

| Feature | JNI | Java FFM | FassionFruit |
|---|:---:|:---:|:---:|
| Native code integration | ✅ | ✅ | ✅ |
| No C/C++ boilerplate for bindings | ❌ | ✅ | ✅ |
| No manual symbol lookup | ⚠️ | ❌ | ✅ |
| No manual function handle setup | ⚠️ | ❌ | ✅ |
| Interface-based API | ❌ | ❌ | ✅ |
| Annotation-driven bindings | ❌ | ❌ | ✅ |
| Native structure mapping | ⚠️ | ⚠️ | ✅ |
| Native callbacks | ⚠️ | ✅ | ✅ |
| Pure Java implementation | ❌ | ✅ | ✅ |
| Requires native access configuration | ❌ | ✅ | ✅ |

### Legend

- ✅ Supported
- ⚠️ Possible, but requires additional implementation or manual work
- ❌ Not supported directly

## Features

- **Interface-based bindings** — Define native functions using Java interfaces.
- **Annotation-driven symbol lookup** — Map Java methods to native symbols using `@NativeSymbol`.
- **Native library loading** — Associate interfaces with native libraries using `@NativeLibrary`.
- **Type conversion** — Convert supported Java types to and from native representations.
- **Enum mapping** — Map Java enums to native values using `@NativeEnumValue`.
- **Native callbacks** — Expose Java functional interfaces to native code using `@NativeCallback`.
- **Native structures** — Map Java interfaces to native structures using `@NativeStructure` and `@NativeField`.
- **JVM structure export** — Export Java-side structure implementations to native memory using `@JvmStructure`.


## Quick Start

Define an interface representing a native library:

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

Load the native library:

```java
import io.github.fassionfruit.FFM;

MyLibrary library = FFM.load(MyLibrary.class);

int result = library.add(10, 20);
System.out.println(result);
```

FassionFruit handles the binding process internally, allowing you to call native functions through ordinary Java interface methods.

## Requirements

- JDK 26
- A compatible native library
- A native library exposing the expected symbols

Native access must be enabled when launching the JVM:

```bash
java --enable-native-access=ALL-UNNAMED ...
```

## Building from Source

Clone the repository:

```bash
git clone https://github.com/FassionFruit/fruit.git
cd fruit
```

Build the project:

```bash
./gradlew build
```

Run tests:

```bash
./gradlew test
```

On Windows:

```powershell
.\\gradlew.bat build
.\\gradlew.bat test
```

Some integration tests require a C compiler to build native test libraries.

## Project Structure

| Module | Description |
| --- | --- |
| `api` | Public API and annotations |
| `core` | Core FFM binding implementation |
| `native` | Native C test libraries |
| `buildSrc` | Shared Gradle build logic |

## Project Status

FassionFruit is currently under active development.

The public API, supported types, and implementation details may change before the first stable release.

Production stability and compatibility guarantees are not yet established.

## Contributing

Contributions are welcome!

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting changes.

All contributors are expected to follow our [Code of Conduct](CODE_OF_CONDUCT.md).

## Security

For information about reporting security vulnerabilities, see [SECURITY.md](SECURITY.md).

## License

FassionFruit is licensed under the [Apache License 2.0](LICENSE).
