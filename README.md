
# FassionFruit

**A binding library for Java's Foreign Function & Memory API.**

FassionFruit makes calling native functions from Java and Kotlin easier through interfaces and annotations.

Instead of manually handling native library loading, symbol lookup, function descriptors, and method handles, you can define a Java or Kotlin interface and let FassionFruit handle the binding.

## Features

- Annotation-based native library declarations
- Native symbol lookup
- Interface-based native function bindings
- Java and Kotlin API compatibility
- Built on Java's Foreign Function & Memory API

## Example

```java
@NativeLibrary("mylib")
public interface MyLibrary {

    @NativeSymbol("calculate_value")
    int calculate(int input);

    @NativeSymbol("add_values")
    int add(int a, int b);
}
```

Load the native library and invoke its functions:

```java
MyLibrary lib = FFM.load(MyLibrary.class);

int result = lib.add(3, 2);
System.out.println(result);
```

## Requirements

- JDK 26
- Gradle Wrapper

FassionFruit uses the Java Foreign Function & Memory API.

## Project Structure

| Module | Description |
|---|---|
| `api` | Public annotations and API declarations |
| `core` | Core FFM implementation |
| `utils` | Internal utilities |
| `buildSrc` | Shared Gradle build logic |
| `samples` | Native library samples |

## Building

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
.\gradlew.bat build
```

Some native integration tests may require platform-specific native libraries.

## Project Status

FassionFruit is currently under active development.

The `0.1.0` development milestone includes basic native library loading, symbol lookup, and primitive argument and return-value handling.

APIs and implementation details may change between releases.

## License

FassionFruit is licensed under the [Apache License 2.0](LICENSE).
