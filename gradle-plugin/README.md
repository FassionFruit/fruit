# FassionFruit Gradle Plugin

A Gradle plugin for simplifying native library development and integration in Java and Kotlin projects using [FassionFruit](https://github.com/FassionFruit/fruit).

It automates native library builds and packages the resulting binaries into your application JAR, making native library integration easier without manually configuring every build step.

## Features

- **Automated Native Builds** - Build native libraries as part of your Gradle build.
- **Multiple Build Systems** - Supports CMake, Cargo, Make, and custom commands.
- **Custom Build Commands** - Use your own scripts and build tools.
- **Automatic Packaging** - Packages native binaries into your application JAR.
- **Configurable Build Profiles** - Configure build profiles such as Debug and Release.
- **Gradle Task Integration** - Native builds are integrated into the Gradle task lifecycle.

## Requirements

- Gradle 9.0 or later
- JDK 25 or later
- A supported native build tool (depending on your project)

## Installation

Apply the plugin in your `build.gradle.kts`:

```kotlin
plugins {
    java
    id("io.github.fassionfruit") version "0.5.0"
}
```

## Project Structure

The plugin uses the `native/` directory as the default native source directory.

```text
your-project/
├── native/
│   ├── CMakeLists.txt
│   ├── Cargo.toml
│   └── src/
│       └── lib.rs
├── src/
│   └── main/
│       └── java/
├── build.gradle.kts
└── settings.gradle.kts
```

You can customize the source and output directories through the plugin configuration.

## Configuration

### Basic Configuration

```kotlin
import io.github.fassionfruit.gradle.NativeBuildSystem

plugins {
    java
    id("io.github.fassionfruit") version "0.4.0"
}

fassionfruit {
    native {
        sourceDirectory.set(
            layout.projectDirectory.dir("native")
        )

        build {
            system.set(NativeBuildSystem.AUTO)
            profile.set("Release")
        }
    }
}
```

### Build Systems

The plugin supports the following build systems:

| Build System | Description |
|---|---|
| `AUTO` | Automatically detects the build system |
| `CMAKE` | Uses CMake |
| `CARGO` | Uses Cargo |
| `MAKE` | Uses Make |
| `CUSTOM` | Executes a custom command |

When using `AUTO`, the plugin detects the build system based on files in the native source directory:

- `CMakeLists.txt` → CMake
- `Cargo.toml` → Cargo
- `Makefile` → Make

### Custom Build Commands

You can use your own build scripts instead of the built-in build systems.

```kotlin
fassionfruit {
    native {
        build {
            system.set(NativeBuildSystem.CUSTOM)

            command.set("./build-native.sh")
            arguments.set(listOf("--release"))
        }
    }
}
```

The custom command receives the following environment variables:

| Variable | Description |
|---|---|
| `FASSIONFRUIT_OUTPUT_DIR` | Directory where native binaries should be placed |
| `FASSIONFRUIT_PROFILE` | Selected build profile |

Example `build-native.sh`:

```bash
#!/usr/bin/env bash
set -e

mkdir -p "$FASSIONFRUIT_OUTPUT_DIR"

cargo build --release

cp target/release/libexample.so \
   "$FASSIONFRUIT_OUTPUT_DIR/"
```

Make sure your custom build command places the resulting shared libraries in `FASSIONFRUIT_OUTPUT_DIR`.


## Native auto-binding

An empty Java class annotated with `@NativeLibrary` is replaced at compile time by a generated class exposing public static methods. The generated methods delegate to the existing FFM interface loader, so the runtime still uses Java FFM and does not use JNI.

```java
package example;

import io.github.fassionfruit.annotation.NativeLibrary;

@NativeLibrary("native_example_rust")
public final class RustExample {
}
```

After applying the plugin, the generated API can be called directly from Java or Kotlin:

```java
int result = RustExample.add(10, 20);
```

The default `native/` directory is built automatically. Additional native projects can be registered without replacing the default directory:

```kotlin
fassionfruit {
    native {
        additionalSourceDirectories.from(layout.projectDirectory.dir("native-rust"))
    }
}
```

### Metadata generation

- Rust projects use a Cargo `build.rs` generator. It reads `#[no_mangle] pub extern "C"` functions with `syn` and writes language-independent `*.ffm.json` metadata into `FASSIONFRUIT_METADATA_DIR`.
- C and C++ projects use Clang's JSON AST (`clang -Xclang -ast-dump=json`). The plugin associates source files listed in CMake `add_library(...)` declarations with their library targets. C++ functions must have C linkage (`extern "C"`) so the exported symbol is unambiguous.
- The metadata records the library, C ABI/calling convention, target and pointer width, exported symbol, Java-facing method name, return type, parameter names/types, and source-derived structure/union fields and fixed array lengths. Function signatures are read from source ASTs, never guessed from binary symbols.

Auto-generated methods currently support `void`, 8/16/32/64-bit integers, `float`, `double`, C strings, raw pointers, C-compatible structures/unions, and one-dimensional fixed-size structure arrays. Structure and union interfaces are generated from source metadata and can be allocated with `FFM.allocateStructure(...)`. Platform-dependent C `long`, callbacks, bitfields, custom alignment, and by-value aggregate function parameters are rejected rather than assigned an incorrect ABI. Use `FFM.load(...)` with `@NativeSymbol` and the existing structure annotations for unsupported signatures.

The annotated declaration itself must be an empty Java class. Kotlin source can call the generated Java static API; Kotlin classes cannot currently be used as the annotated declaration. Clang must be available on `PATH` for C/C++ metadata extraction. Configure a custom executable or extra include/define arguments when needed:

```kotlin
fassionfruit {
    native {
        build {
            clangCommand.set("clang")
            clangArguments.set(listOf("-Inative/include", "-DMY_FEATURE=1"))
        }
    }
}
```

Rust metadata generation requires Cargo and the Rust dependencies declared by the crate's build script.

The generation task is `fassionfruitGenerateBindings`; it runs as part of Java compilation. Native binaries are packaged under `META-INF/fassionfruit/native/<os>-<architecture>/` and extracted by the FassionFruit runtime when loaded from a JAR. Source-derived metadata is packaged under `META-INF/fassionfruit/metadata/`.

## Build Tasks

The plugin registers the following Gradle tasks:

| Task | Description |
|---|---|
| `fassionfruitNativeBuild` | Builds native libraries |
| `fassionfruitPackageNative` | Packages native binaries and metadata |
| `fassionfruitGenerateBindings` | Generates Java static binding classes from metadata |

The native packaging task is integrated with the Java `processResources` task.

You can build and package everything with:

```bash
./gradlew build
```

## JAR Packaging

Native libraries are packaged under:

```text
META-INF/fassionfruit/
```

The resulting JAR contains platform-specific native binaries and source-derived metadata.

Example:

```text
your-application.jar
└── META-INF/
    └── fassionfruit/
        ├── metadata/
        │   ├── native_example.ffm.json
        │   └── native_example_rust.ffm.json
        └── native/
            └── linux-x86_64/
                ├── libnative_example.so
                └── libnative_example_rust.so
```

## Supported Native Libraries

The plugin currently recognizes the following shared library extensions:

- Windows: `.dll`
- Linux: `.so`, `.so.*`
- macOS: `.dylib`

Static libraries are not packaged as runtime native libraries.

## License

This project is licensed under the MIT License.
