package io.github.fassionfruit.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

abstract class NativeBuildTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    @get:Internal
    abstract val sourceDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val additionalSourceDirectories: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val buildDirectory: DirectoryProperty

    @get:Input
    abstract val buildSystem: Property<NativeBuildSystem>

    @get:Input
    abstract val profile: Property<String>

    @get:Input
    abstract val command: Property<String>

    @get:Input
    abstract val arguments: ListProperty<String>

    @get:Input
    abstract val clangArguments: ListProperty<String>

    @get:Input
    abstract val clangCommand: Property<String>

    @get:Input
    abstract val environment: MapProperty<String, String>

    @get:Input
    abstract val target: Property<String>

    @TaskAction
    fun buildNative() {
        val roots = (listOf(sourceDirectory.get().asFile) + additionalSourceDirectories.files)
            .distinctBy { it.absoluteFile.toPath().normalize() }
            .filter { it.isDirectory && it.walkTopDown().any { file -> file.isFile } }
        val output = outputDirectory.get().asFile
        val build = buildDirectory.get().asFile

        output.deleteRecursively()
        output.mkdirs()
        build.mkdirs()

        if (roots.isEmpty()) {
            logger.lifecycle("FassionFruit: no native sources found, skipping native build")
            return
        }

        roots.forEachIndexed { index, source ->
            val buildSystem = resolveBuildSystem(source)
            val buildRoot = build.resolve("${index}-${source.name}")
            logger.lifecycle("FassionFruit: building ${source.absolutePath} with $buildSystem")
            when (buildSystem) {
                NativeBuildSystem.CMAKE -> {
                    buildWithCMake(source, buildRoot, output)
                    generateClangMetadata(source, buildRoot, output)
                }
                NativeBuildSystem.CARGO -> buildWithCargo(source, output, buildRoot)
                NativeBuildSystem.MAKE -> buildWithMake(source, output)
                NativeBuildSystem.CUSTOM -> buildWithCustomCommand(source, output)
                NativeBuildSystem.AUTO -> error("Unresolved build system")
            }
        }
    }


    private fun generateClangMetadata(source: File, build: File, output: File) {
        val cmake = File(source, "CMakeLists.txt")
        if (!cmake.isFile) return

        val librarySources = linkedMapOf<String, MutableSet<String>>()
        val targetPattern = Regex("(?is)add_library\\s*\\(\\s*([A-Za-z0-9_]+)\\s+([^)]*)\\)")
        targetPattern.findAll(cmake.readText()).forEach { match ->
            val targetName = match.groupValues[1]
            val files = match.groupValues[2]
                .split(Regex("\\s+"))
                .filter { it.endsWith(".c", true) || it.endsWith(".cc", true) ||
                    it.endsWith(".cpp", true) || it.endsWith(".cxx", true) }
            if (files.isNotEmpty()) librarySources.getOrPut(targetName) { linkedSetOf() }.addAll(files)
        }
        if (librarySources.isEmpty()) return

        val sourceFiles = source.walkTopDown().filter {
            it.isFile && (it.extension.lowercase() in setOf("c", "cc", "cpp", "cxx"))
        }.toList()
        sourceFiles.forEach { sourceFile ->
            val relative = sourceFile.relativeTo(source).path.replace(File.separatorChar, '/')
            val libraries = librarySources.filterValues { relative in it || sourceFile.name in it }.keys
            if (libraries.isEmpty()) return@forEach

            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            try {
                execOperations.exec {
                    workingDir(source)
                    commandLine(
                        listOf(clangCommand.get(), "-Xclang", "-ast-dump=json", "-fsyntax-only", "-I${source.absolutePath}", "-I${build.absolutePath}") +
                            clangArguments.get() + sourceFile.absolutePath
                    )
                    standardOutput = stdout
                    errorOutput = stderr
                }
            } catch (exception: Exception) {
                logger.warn("FassionFruit: Clang AST extraction failed for ${sourceFile.name}: ${stderr.toString(Charsets.UTF_8)}")
                return@forEach
            }

            val root = try {
                JsonSlurper().parseText(stdout.toString(Charsets.UTF_8)) as? Map<*, *>
            } catch (exception: Exception) {
                logger.warn("FassionFruit: could not parse Clang AST for ${sourceFile.name}")
                null
            } ?: return@forEach

            val functions = collectNodes(root, setOf("FunctionDecl"))
                .mapNotNull { node ->
                    val name = node["name"] as? String ?: return@mapNotNull null
                    if (node["storageClass"] == "static" || node["variadic"] == true || node["inline"] == true) {
                        return@mapNotNull null
                    }
                    val callingConvention = node["cc"] as? String
                    if (callingConvention != null && callingConvention !in setOf("cdecl", "C")) {
                        return@mapNotNull null
                    }
                    val mangled = node["mangledName"] as? String
                    if (mangled != null && mangled != name) return@mapNotNull null
                    val children = node["inner"] as? List<*> ?: emptyList<Any>()
                    if (children.none { (it as? Map<*, *>)?.get("kind") == "CompoundStmt" }) {
                        return@mapNotNull null
                    }
                    val functionType = (node["type"] as? Map<*, *>)?.let(::clangType)
                        ?: return@mapNotNull null
                    val returnType = mapClangType(functionType.substringBefore('(').trim())
                        ?: return@mapNotNull null
                    val rawParameters = children.mapNotNull { it as? Map<*, *> }
                        .filter { it["kind"] == "ParmVarDecl" }
                    val parameters = mutableListOf<Map<String, String>>()
                    for ((index, parameter) in rawParameters.withIndex()) {
                        val type = (parameter["type"] as? Map<*, *>)?.let(::clangType)
                            ?: return@mapNotNull null
                        val mapped = mapClangType(type) ?: return@mapNotNull null
                        parameters.add(
                            mapOf("name" to (parameter["name"] as? String ?: "arg$index"), "type" to mapped)
                        )
                    }
                    mapOf("name" to name, "symbol" to name, "abi" to "C", "return" to returnType, "parameters" to parameters)
                }

            val typedefNames = linkedMapOf<String, String>()

            collectNodes(root, setOf("TypedefDecl")).forEach { typedef ->
                val name = typedef["name"] as? String ?: return@forEach

                collectTagReferenceIds(typedef).forEach { tagId ->
                    typedefNames.putIfAbsent(tagId, name)
                }
            }
            val structures = collectNodes(root, setOf("RecordDecl", "CXXRecordDecl"))
                .mapNotNull { node ->
                    if (node["completeDefinition"] != true || node["isImplicit"] == true) return@mapNotNull null
                    val location = node["loc"] as? Map<*, *>
                    val locationFile = location?.get("file") as? String
                    val includedFrom = location?.get("includedFrom") as? Map<*, *>
                    if (includedFrom != null) {
                        if (locationFile == null) return@mapNotNull null
                        val declaredFile = File(locationFile)
                        val resolvedFile = when {
                            declaredFile.isAbsolute -> declaredFile
                            declaredFile.exists() -> declaredFile.absoluteFile
                            source.resolve(locationFile).exists() -> source.resolve(locationFile)
                            else -> declaredFile.absoluteFile
                        }
                        if (!resolvedFile.toPath().toAbsolutePath().normalize().startsWith(
                                source.toPath().toAbsolutePath().normalize()
                            )) return@mapNotNull null
                    }
                    val recordId = node["id"] as? String
                    val name = (node["name"] as? String)?.takeIf { it.isNotBlank() }
                        ?: recordId?.let(typedefNames::get)
                        ?: return@mapNotNull null
                    val children = node["inner"] as? List<*> ?: emptyList<Any>()
                    if (children.any { (it as? Map<*, *>)?.get("kind") == "AlignedAttr" ||
                            (it as? Map<*, *>)?.get("kind") == "MaxFieldAlignmentAttr" }) {
                        return@mapNotNull null
                    }
                    if ((node["bases"] as? List<*>)?.isNotEmpty() == true) return@mapNotNull null
                    if (children.any { child ->
                            val member = child as? Map<*, *>
                            member?.get("kind") == "CXXMethodDecl" && member["isVirtual"] == true
                        }) return@mapNotNull null
                    val fields = children.mapNotNull { it as? Map<*, *> }
                        .filter { it["kind"] == "FieldDecl" }
                        .mapIndexed { index, field ->
                            val type = (field["type"] as? Map<*, *>)?.let(::clangType)
                                ?: "unsupported"
                            mapOf(
                                "name" to (field["name"] as? String ?: "_$index"),
                                "type" to if (field["isBitfield"] == true) "unsupported" else (mapClangType(type) ?: "unsupported")
                            )
                        }
                    if (fields.isEmpty()) return@mapNotNull null
                    mapOf(
                        "name" to name,
                        "kind" to (node["tagUsed"] as? String ?: "struct"),
                        "representation" to "C",
                        "packed" to children.any { (it as? Map<*, *>)?.get("kind") == "PackedAttr" },
                        "fields" to fields
                    )
                }

            libraries.forEach { library ->
                val metadataFile = output.resolve("$library.ffm.json")
                val existing = if (metadataFile.isFile) {
                    try { JsonSlurper().parse(metadataFile) as? Map<*, *> } catch (_: Exception) { null }
                } else null
                val previousFunctions = (existing?.get("functions") as? List<*>).orEmpty()
                val previousStructures = (existing?.get("structures") as? List<*>).orEmpty()
                val mergedFunctions = (previousFunctions + functions)
                    .distinctBy { (it as? Map<*, *>)?.get("symbol") }
                val mergedStructures = (previousStructures + structures)
                    .distinctBy { (it as? Map<*, *>)?.get("name") }
                metadataFile.writeText(
                    JsonOutput.prettyPrint(
                        JsonOutput.toJson(
                            mapOf(
                                "version" to 1,
                                "abi" to "C",
                                "target" to target.get().ifBlank { System.getProperty("os.arch") + "-" + System.getProperty("os.name") },
                                "pointerWidth" to targetPointerWidth(),
                                "library" to library,
                                "functions" to mergedFunctions,
                                "structures" to mergedStructures
                            )
                        )
                    )
                )
            }
        }
    }

    private fun collectTagReferenceIds(node: Map<*, *>): Set<String> {
        val result = linkedSetOf<String>()

        val ownedTag = node["ownedTagDecl"] as? Map<*, *>
        (ownedTag?.get("id") as? String)?.let(result::add)

        val declaration = node["decl"] as? Map<*, *>
        if ((declaration?.get("kind") as? String) in setOf("RecordDecl", "CXXRecordDecl")) {
            (declaration?.get("id") as? String)?.let(result::add)
        }

        (node["inner"] as? List<*>)?.forEach { child ->
            val childNode = child as? Map<*, *> ?: return@forEach
            result.addAll(collectTagReferenceIds(childNode))
        }

        return result
    }

    private fun collectNodes(node: Map<*, *>, kinds: Set<String>): List<Map<*, *>> {
        val result = mutableListOf<Map<*, *>>()
        if ((node["kind"] as? String) in kinds) result.add(node)
        (node["inner"] as? List<*>)?.forEach { child ->
            val childNode = child as? Map<*, *> ?: return@forEach
            result.addAll(collectNodes(childNode, kinds))
        }
        return result
    }

    private fun clangType(type: Map<*, *>): String? =
        type["desugaredQualType"] as? String ?: type["qualType"] as? String

    private fun targetPointerWidth(): Int {
        if (target.get().isBlank()) return System.getProperty("sun.arch.data.model").toIntOrNull() ?: 0
        return when (target.get().substringBefore('-').lowercase()) {
            "i386", "i486", "i586", "i686", "x86", "arm", "armv7" -> 32
            "x86_64", "amd64", "aarch64", "arm64", "riscv64" -> 64
            else -> System.getProperty("sun.arch.data.model").toIntOrNull() ?: 0
        }
    }

    private fun mapClangType(rawType: String): String? {
        val isConst = Regex("\\bconst\\b").containsMatchIn(rawType)
        val type = rawType.replace(Regex("\\b(const|volatile|restrict|__restrict)\\b"), "")
            .replace(Regex("\\s+"), " ").trim()
        val array = Regex("^(.+)\\[(\\d+)]$").matchEntire(type)
        if (array != null) {
            val element = mapClangType(array.groupValues[1]) ?: return null
            return "array<$element,${array.groupValues[2]}>"
        }
        if (type.endsWith("*")) {
            val pointee = type.removeSuffix("*").trim()
            if (isConst && pointee == "char") return "string"
            val mapped = mapClangType(pointee) ?: "struct:${pointee.removePrefix("struct ").removePrefix("union ")}"
            return "pointer<$mapped>"
        }
        if (type.startsWith("struct ") || type.startsWith("union ")) {
            return "struct:${type.substringAfter(' ')}"
        }
        return when (type) {
            "void" -> "void"
            "char", "signed char" -> "i8"
            "unsigned char" -> "u8"
            "short", "short int", "signed short", "signed short int" -> "i16"
            "unsigned short", "unsigned short int" -> "u16"
            "int", "signed", "signed int" -> "i32"
            "unsigned", "unsigned int" -> "u32"
            "long long", "long long int", "signed long long", "signed long long int" -> "i64"
            "unsigned long long", "unsigned long long int" -> "u64"
            "float" -> "f32"
            "double" -> "f64"
            else -> if (type.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) "struct:$type" else null
        }
    }

    private fun isNativeLibrary(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".dll") || name.endsWith(".dylib") ||
            name.endsWith(".so") || Regex(".*\\.so\\..+").matches(name)
    }

    private fun resolveBuildSystem(source: File): NativeBuildSystem {
        val configured = buildSystem.get()

        if (configured != NativeBuildSystem.AUTO) {
            return configured
        }

        if (File(source, "CMakeLists.txt").isFile) {
            return NativeBuildSystem.CMAKE
        }

        if (File(source, "Cargo.toml").isFile) {
            return NativeBuildSystem.CARGO
        }

        if (
            File(source, "Makefile").isFile ||
            File(source, "makefile").isFile
        ) {
            return NativeBuildSystem.MAKE
        }

        if (command.get().isNotBlank()) {
            return NativeBuildSystem.CUSTOM
        }

        throw GradleException(
            "Native sources exist in $source, but no supported build system was detected. " +
                    "Add CMakeLists.txt, Cargo.toml, Makefile, or configure a custom command."
        )
    }

    private fun buildWithCMake(
        source: File,
        build: File,
        output: File
    ) {
        if (!File(source, "CMakeLists.txt").isFile) {
            throw GradleException("CMakeLists.txt not found in $source")
        }

        val outputPath = output.absolutePath
        val config = profile.get().uppercase()

        execute(
            source,
            listOf(
                "cmake",
                "-S", source.absolutePath,
                "-B", build.absolutePath,
                "-DCMAKE_BUILD_TYPE=${profile.get()}",
                "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=$outputPath",
                "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=$outputPath",
                "-DCMAKE_ARCHIVE_OUTPUT_DIRECTORY=$outputPath",
                "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_$config=$outputPath",
                "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_$config=$outputPath",
                "-DCMAKE_ARCHIVE_OUTPUT_DIRECTORY_$config=$outputPath"
            )
        )

        val args = mutableListOf(
            "cmake",
            "--build", build.absolutePath,
            "--config", profile.get()
        )

        if (target.get().isNotBlank()) {
            args.addAll(listOf("--target", target.get()))
        }

        args.addAll(arguments.get())

        execute(source, args)
    }

    private fun buildWithCargo(
        source: File,
        output: File,
        buildRoot: File
    ) {
        if (!File(source, "Cargo.toml").isFile) {
            throw GradleException("Cargo.toml not found in $source")
        }

        val cargoTargetDirectory = buildRoot.resolve("cargo-target")
        val env = environment.get().toMutableMap()
        env["CARGO_TARGET_DIR"] = cargoTargetDirectory.absolutePath
        env["FASSIONFRUIT_METADATA_DIR"] = output.absolutePath

        val selectedProfile = profile.get().lowercase()
        val args = mutableListOf("cargo", "build")

        when (selectedProfile) {
            "release" -> args.add("--release")
            "debug" -> Unit
            else -> args.addAll(listOf("--profile", selectedProfile))
        }

        if (target.get().isNotBlank()) {
            args.addAll(listOf("--target", target.get()))
        }

        args.addAll(arguments.get())
        execute(source, args, env)

        val profileDirectory = cargoTargetDirectory
            .resolve(target.get().takeIf { it.isNotBlank() } ?: "")
            .resolve(selectedProfile)
        val libraries = profileDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && isNativeLibrary(it) }

        if (libraries.isEmpty()) {
            throw GradleException(
                "Cargo completed successfully but produced no shared native library in $profileDirectory"
            )
        }

        libraries.forEach { library ->
            library.copyTo(output.resolve(library.name), overwrite = true)
            logger.lifecycle("FassionFruit: collected Cargo library ${library.name}")
        }
    }

    private fun buildWithMake(
        source: File,
        output: File
    ) {
        val args = mutableListOf("make")

        if (target.get().isNotBlank()) {
            args.add(target.get())
        }

        args.addAll(arguments.get())

        execute(source, args, buildEnvironment(output))
    }

    private fun buildWithCustomCommand(
        source: File,
        output: File
    ) {
        val executable = command.get().trim()

        if (executable.isEmpty()) {
            throw GradleException("Custom build command is empty")
        }

        execute(
            source,
            listOf(executable) + arguments.get(),
            buildEnvironment(output)
        )
    }

    private fun buildEnvironment(output: File): Map<String, String> {
        return environment.get().toMutableMap().apply {
            put("FASSIONFRUIT_OUTPUT_DIR", output.absolutePath)
            put("FASSIONFRUIT_METADATA_DIR", output.absolutePath)
            put("FASSIONFRUIT_PROFILE", profile.get())
        }
    }

    private fun execute(
        workingDirectory: File,
        commandLine: List<String>,
        extraEnvironment: Map<String, String> = environment.get()
    ) {
        execOperations.exec {
            workingDir(workingDirectory)
            commandLine(commandLine)
            environment("JAVA_HOME", System.getProperty("java.home"))
            environment(extraEnvironment)
        }
    }
}