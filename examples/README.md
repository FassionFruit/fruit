# Examples

The examples project demonstrates the FassionFruit APIs and the native auto-binding Gradle workflow.

- `examples.basic`: primitive values and enums
- `examples.strings`: native strings
- `examples.pointers`: `MemorySegment` pointers
- `examples.structures`: native structures
- `examples.unions`: native unions
- `examples.callbacks`: Java callbacks called from C
- `examples.critical`: short critical downcalls

## Native auto-binding

The `native/` CMake project and `native-rust/` Cargo project are built automatically. `RustExample` and `CExample` are empty Java classes annotated with `@NativeLibrary`; the Gradle plugin generates their static methods and C-compatible structure/union interfaces from Clang and Rust AST metadata.

```java
System.out.println(RustExample.add(10, 20));
System.out.println(RustExample.multiply(6, 7));
System.out.println(RustExample.subtract(30, 12));
System.out.println(RustExample.get_version());
```

Build and test the generated bindings:

```bash
./gradlew :examples:compileJava
./gradlew :examples:test
./gradlew :examples:jar
```

`NativeAutoBindingTest` verifies Rust and C downcalls, strings, structures, unions, and fixed-size arrays. The test task enables Java native access for the FFM linker. The JAR task packages native libraries under `META-INF/fassionfruit/native/<os>-<architecture>/` and metadata under `META-INF/fassionfruit/metadata/`.

For a direct run from compiled classes on Linux, use the generated native output directories:

```bash
java \
  --enable-native-access=ALL-UNNAMED \
  -Djava.library.path=build/fassionfruit/native:build/fassionfruit/native/cargo-1-native-rust/release \
  -cp build/classes/java/main:build/resources/main \
  examples.rust.AutoBindingExample
```

The examples project is not included in Maven publications; only `api` and `core` are published by the root build.
