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

Replace `0.5.0` with the version you want to use.

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
    id("io.github.fassionfruit") version "0.5.0"
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

## Build Tasks

The plugin registers the following Gradle tasks:

| Task | Description |
|---|---|
| `fassionfruitNativeBuild` | Builds native libraries |
| `fassionfruitPackageNative` | Prepares native binaries for packaging |

The native packaging task is integrated with the Java `processResources` task.

You can build and package everything with:

```bash
./gradlew build
```

## JAR Packaging

Native libraries are packaged under:

```text
META-INF/fassionfruit/native/
```

The resulting JAR contains the native binaries produced by the configured build system.

Example:

```text
your-application.jar
└── META-INF/
    └── fassionfruit/
        └── native/
            └── libexample.so
```

## Supported Native Libraries

The plugin currently recognizes the following shared library extensions:

- Windows: `.dll`
- Linux: `.so`, `.so.*`
- macOS: `.dylib`

Static libraries are not packaged as runtime native libraries.

## License

This project is licensed under the MIT License.
