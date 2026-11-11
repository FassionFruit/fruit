# Examples

Build the C example library first:

```bash
cmake -S native -B native/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/build --config Release
```

The Java examples are grouped by the FassionFruit feature they demonstrate:

- `examples.basic`: primitive values and enums
- `examples.strings`: native strings
- `examples.pointers`: `MemorySegment` pointers
- `examples.structures`: native structures
- `examples.unions`: native unions
- `examples.callbacks`: Java callbacks called from C
- `examples.critical`: short critical downcalls

Build the examples JAR to build both native libraries and package them inside
the JAR:

```bash
./gradlew :examples:jar
```

`FFM.load()` extracts a packaged native library to a temporary file before
loading it. For running directly from compiled classes instead, add the native
output directories to `java.library.path`.

The `examples` project is intentionally not included in Maven publications;
only the `api` and `core` modules are published by the root build.

## Rust

Build the Rust library:

```bash
cargo build --manifest-path examples/native-rust/Cargo.toml --release
```

The Gradle `:examples:jar` task runs this Rust build automatically. To run the
Rust example directly from the build output, add the Rust output directory to
`java.library.path`:

```bash
java \
  --enable-native-access=ALL-UNNAMED \
  -Djava.library.path=examples/native-rust/target/release \
  examples.rust.RustExample
```

Rust structures must use `#[repr(C)]`. On the Java side, declare the matching
type with `@NativeStructure`; a structure argument is passed to Rust as a
pointer to that structure.
