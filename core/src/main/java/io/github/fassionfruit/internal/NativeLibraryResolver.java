package io.github.fassionfruit.internal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves platform-specific native library names and paths. */
public final class NativeLibraryResolver {

    private static final String OS = System.getProperty("os.name").toLowerCase();

    private NativeLibraryResolver() {
    }

    /** Resolves a library name to an existing native library path. */
    public static Path resolve(String libraryName) {
        return resolve(libraryName, NativeLibraryResolver.class.getClassLoader());
    }

    /** Resolves a native library from the filesystem or a classpath resource. */
    public static Path resolve(String libraryName, ClassLoader classLoader) {
        if (libraryName == null || libraryName.isBlank()) {
            throw new IllegalArgumentException("Library name cannot be empty");
        }

        boolean explicitPath = libraryName.contains("/")
                || libraryName.contains("\\")
                || Path.of(libraryName).isAbsolute();

        String fileName = Path.of(libraryName).getFileName().toString();
        boolean hasExtension = fileName.lastIndexOf('.') > 0;

        String resolvedName = libraryName;

        if (!hasExtension) {
            resolvedName = addPlatformName(libraryName, explicitPath);
        }

        Path path = Path.of(resolvedName).toAbsolutePath().normalize();

        if (Files.isRegularFile(path)) {
            return path;
        }

        if (!explicitPath) {
            for (String directory : System.getProperty("java.library.path").split(
                    File.pathSeparator
            )) {
                Path candidate = Path.of(directory)
                        .resolve(resolvedName)
                        .toAbsolutePath()
                        .normalize();

                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }

        String platformResource = "META-INF/fassionfruit/native/"
                + platformDirectory() + "/" + Path.of(resolvedName).getFileName();
        String legacyResource = "native/" + resolvedName;
        InputStream resource = getResource(classLoader, platformResource);

        if (resource == null) {
            resource = getResource(classLoader, legacyResource);
        }

        if (resource != null) {
            return extractResource(
                    resource,
                    Path.of(resolvedName).getFileName().toString()
            );
        }

        throw new IllegalArgumentException(
                "Native library not found: " + resolvedName
        );
    }


    private static InputStream getResource(ClassLoader classLoader, String name) {
        return classLoader == null
                ? ClassLoader.getSystemResourceAsStream(name)
                : classLoader.getResourceAsStream(name);
    }

    private static String platformDirectory() {
        String os = OS;
        if (os.contains("mac") || os.contains("darwin")) {
            os = "macos";
        } else if (os.contains("win")) {
            os = "windows";
        } else if (os.contains("linux") || os.contains("unix")) {
            os = "linux";
        }

        String architecture = System.getProperty("os.arch").toLowerCase();
        architecture = switch (architecture) {
            case "amd64", "x64" -> "x86_64";
            case "arm64" -> "aarch64";
            default -> architecture;
        };

        return os + "-" + architecture;
    }

    private static Path extractResource(InputStream resource, String fileName) {
        try (resource) {
            String suffix = fileName.contains(".")
                    ? fileName.substring(fileName.lastIndexOf('.'))
                    : ".native";
            Path extracted = Files.createTempFile("fassionfruit-", suffix);
            Files.copy(resource, extracted, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            extracted.toFile().deleteOnExit();
            return extracted;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to extract native library resource: " + fileName,
                    exception
            );
        }
    }

    private static String addPlatformName(String name, boolean explicitPath) {
        if (OS.contains("mac") || OS.contains("darwin")) {
            if (!explicitPath && !name.substring(name.lastIndexOf('/') + 1).startsWith("lib")) {
                name = addLibPrefix(name);
            }

            return name + ".dylib";
        }

        if (OS.contains("win")) {
            return name + ".dll";
        }

        if (OS.contains("linux") || OS.contains("unix") || OS.contains("bsd")) {
            if (!explicitPath && !name.substring(name.lastIndexOf('/') + 1).startsWith("lib")) {
                name = addLibPrefix(name);
            }

            return name + ".so";
        }

        throw new UnsupportedOperationException(
                "Unsupported operating system: " + OS
        );
    }

    private static String addLibPrefix(String name) {
        int separator = Math.max(
                name.lastIndexOf('/'),
                name.lastIndexOf('\\')
        );

        return name.substring(0, separator + 1)
                + "lib"
                + name.substring(separator + 1);
    }
}
