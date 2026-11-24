package io.github.fassionfruit.gradle

import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class GenerateNativeBindingsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val metadataFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        val declarations = sourceFiles.asFileTree.files
            .filter { it.isFile && it.extension in setOf("java", "kt") }
            .sortedBy { it.absolutePath }
            .mapNotNull(::findDeclaration)

        val metadata = metadataFiles.asFileTree.files
            .filter { it.isFile && it.extension.equals("json", true) }
            .sortedBy { it.absolutePath }
            .flatMap(::readMetadata)
            .associateBy { it.library }

        if (metadata.isEmpty() && declarations.isEmpty()) {
            logger.lifecycle("FassionFruit: no native binding declarations found")
            return
        }

        var generated = 0
        declarations.forEach { declaration ->
            val library = metadata[declaration.library]
                ?: throw GradleException(
                    "No native metadata for '${declaration.library}' used by ${declaration.qualifiedName}. " +
                        "Make sure the native build generates FassionFruit metadata."
                )

            val javaFile = output.resolve(
                declaration.qualifiedName.replace('.', '/') + ".java"
            )
            javaFile.parentFile.mkdirs()
            javaFile.writeText(generateJava(declaration, library))
            generated++
            logger.lifecycle("FassionFruit: generated ${declaration.qualifiedName} (${library.functions.size} metadata functions)")
        }

        logger.lifecycle("FassionFruit: generated $generated native binding class(es)")
    }

    private fun readMetadata(file: File): List<LibraryMetadata> {
        val root = try {
            JsonSlurper().parse(file) as? Map<*, *>
        } catch (exception: Exception) {
            throw GradleException("Invalid FassionFruit metadata: ${file.absolutePath}", exception)
        } ?: throw GradleException("Invalid FassionFruit metadata root: ${file.absolutePath}")

        val entries = root["libraries"] as? List<*> ?: listOf(root)
        return entries.map { raw ->
            val item = raw as? Map<*, *>
                ?: throw GradleException("Invalid library entry in ${file.absolutePath}")
            val version = (item["version"] as? Number)?.toInt()
            if (version != null && version != 1) {
                throw GradleException("Unsupported FassionFruit metadata version $version in ${file.absolutePath}")
            }
            val abi = item["abi"] as? String
            if (abi != null && abi != "C") {
                throw GradleException("Unsupported native ABI '$abi' for ${file.absolutePath}; only C ABI is supported")
            }
            val name = item["library"] as? String
                ?: item["name"] as? String
                ?: throw GradleException("Missing 'library' in ${file.absolutePath}")
            val functions = item["functions"] as? List<*>
                ?: throw GradleException("Missing 'functions' for $name in ${file.absolutePath}")
            val structures = (item["structures"] as? List<*>).orEmpty()
                .map { parseStructure(it, name) }
            LibraryMetadata(
                name,
                functions.map { parseFunction(it, name) },
                structures
            )
        }
    }

    private fun parseFunction(raw: Any?, library: String): FunctionMetadata {
        val item = raw as? Map<*, *>
            ?: throw GradleException("Invalid function entry for $library")
        val symbol = item["symbol"] as? String
            ?: item["name"] as? String
            ?: throw GradleException("Missing function symbol for $library")
        val name = item["name"] as? String ?: symbol
        val returnType = item["return"] as? String
            ?: item["returnType"] as? String
            ?: throw GradleException("Missing return type for $library.$symbol")
        val parameters = (item["parameters"] as? List<*> ?: emptyList<Any>()).mapIndexed { index, value ->
            val parameter = value as? Map<*, *>
                ?: throw GradleException("Invalid parameter $index for $library.$symbol")
            ParameterMetadata(
                parameter["name"] as? String ?: "arg$index",
                parameter["type"] as? String
                    ?: throw GradleException("Missing type for parameter $index of $library.$symbol")
            )
        }
        return FunctionMetadata(name, symbol, returnType, parameters)
    }

    private fun parseStructure(raw: Any?, library: String): StructureMetadata {
        val item = raw as? Map<*, *>
            ?: throw GradleException("Invalid structure entry for $library")
        val name = item["name"] as? String
            ?: throw GradleException("Missing structure name for $library")
        val kind = item["kind"] as? String ?: "struct"
        val fields = (item["fields"] as? List<*> ?: emptyList<Any>()).mapIndexed { index, value ->
            val field = value as? Map<*, *>
                ?: throw GradleException("Invalid field $index in $library.$name")
            FieldMetadata(
                field["name"] as? String ?: "_$index",
                field["type"] as? String ?: "unsupported"
            )
        }
        return StructureMetadata(
            name,
            kind,
            item["packed"] as? Boolean ?: false,
            fields
        )
    }

    private fun findDeclaration(file: File): BindingDeclaration? {
        val source = file.readText()
        val annotation = Regex("""@NativeLibrary\s*\(\s*(?:value\s*=\s*)?"([^"]+)"\s*\)""")
            .find(source) ?: return null
        val following = source.substring(annotation.range.last + 1)
        val declaration = Regex(
            """(?s)^\s*(?:(?:public|protected|private|static|final|abstract|sealed|non-sealed|internal|open|data)\s+)*(class|interface)\s+([A-Za-z_$][A-Za-z0-9_$]*)"""
        ).find(following) ?: return null
        if (declaration.groupValues[1] == "interface") return null
        if (file.extension != "java") {
            throw GradleException(
                "The @NativeLibrary auto-binding declaration must be a Java class. " +
                    "Kotlin source can call generated Java bindings: ${file.absolutePath}"
            )
        }

        val className = declaration.groupValues[2]
        val emptyClass = Regex("\\bclass\\s+${Regex.escape(className)}\\s*\\{\\s*}")
        if (!emptyClass.containsMatchIn(source)) {
            throw GradleException("@NativeLibrary class must be empty because it is replaced by generated methods: ${file.absolutePath}")
        }
        val packageName = Regex("(?m)^\\s*package\\s+([A-Za-z_][A-Za-z0-9_.]*)")
            .find(source)?.groupValues?.get(1).orEmpty()
        val qualifiedName = if (packageName.isBlank()) className else "$packageName.$className"
        return BindingDeclaration(packageName, className, qualifiedName, annotation.groupValues[1])
    }

    private fun generateJava(declaration: BindingDeclaration, library: LibraryMetadata): String {
        val structures = library.structures.associateBy { it.name }
        val structureCandidates = library.structures.mapNotNull { structure ->
            if (structure.kind == "union" && structure.packed) {
                logger.warn("FassionFruit: skipped packed union ${structure.name}; @NativeUnion has no packed layout option")
                return@mapNotNull null
            }
            val fields = structure.fields.mapIndexedNotNull { _, field ->
                createFieldBinding(field, structures)
            }
            if (fields.size != structure.fields.size || fields.isEmpty()) {
                logger.warn("FassionFruit: skipped structure ${structure.name} because it contains unsupported fields")
                null
            } else {
                GeneratedStructure(structure, fields)
            }
        }
        var supportedStructures = structureCandidates
        while (true) {
            val names = supportedStructures.mapTo(mutableSetOf()) { it.metadata.name }
            val filtered = supportedStructures.filter { structure ->
                structure.fields.all { field ->
                    structureDependencies(field.metadata.type).all(names::contains)
                }
            }
            if (filtered.size == supportedStructures.size) break
            supportedStructures = filtered
        }
        val availableStructures = supportedStructures.associateBy { it.metadata.name }
        val functions = library.functions.mapNotNull { function ->
            try {
                val returnType = javaType(function.returnType, availableStructures, true)
                val parameters = function.parameters.map { parameter ->
                    javaType(parameter.type, availableStructures, true)
                }
                GeneratedFunction(function, returnType, parameters)
            } catch (exception: GradleException) {
                logger.warn("FassionFruit: skipped ${library.library}.${function.symbol}: ${exception.message}")
                null
            }
        }

        if (functions.isEmpty()) {
            throw GradleException("No supported native functions were found for ${library.library}")
        }

        val output = StringBuilder()
        if (declaration.packageName.isNotBlank()) output.append("package ${declaration.packageName};\n\n")
        output.append("import io.github.fassionfruit.FFM;\n")
        output.append("import io.github.fassionfruit.annotation.NativeLibrary;\n")
        output.append("import io.github.fassionfruit.annotation.NativeSymbol;\n")
        output.append("import io.github.fassionfruit.annotation.NativePointer;\n")
        output.append("import io.github.fassionfruit.annotation.structure.NativeField;\n")
        output.append("import io.github.fassionfruit.annotation.structure.NativeStructure;\n")
        output.append("import io.github.fassionfruit.annotation.structure.NativeUnion;\n")
        output.append("import io.github.fassionfruit.structure.NativeArray;\n\n")
        output.append("@NativeLibrary(\"${escapeJava(library.library)}\")\n")
        output.append("public final class ${declaration.className} {\n")

        supportedStructures.forEach { generated ->
            val structure = generated.metadata
            if (structure.kind == "union") {
                output.append("    @NativeUnion\n")
            } else if (structure.packed) {
                output.append("    @NativeStructure(packed = true)\n")
            } else {
                output.append("    @NativeStructure\n")
            }
            output.append("    public interface ${safeIdentifier(structure.name)} {\n")
            generated.fields.forEachIndexed { index, field ->
                val fieldName = safeIdentifier(field.metadata.name)
                val suffix = fieldName.replaceFirstChar { it.uppercase() }
                val fieldAnnotation = "@NativeField(value = \"${escapeJava(field.metadata.name)}\", order = $index"
                val withLength = if (field.length != null) "$fieldAnnotation, length = ${field.length})" else "$fieldAnnotation)"
                output.append("        $withLength\n")
                if (field.pointer) output.append("        @NativePointer\n")
                output.append("        ${field.javaType} get$suffix();\n")
                if (field.length == null) {
                    output.append("        $withLength\n")
                    if (field.pointer) output.append("        @NativePointer\n")
                    output.append("        void set$suffix(${field.javaType} value);\n")
                }
            }
            output.append("    }\n\n")
        }

        output.append("    @NativeLibrary(\"${escapeJava(library.library)}\")\n")
        output.append("    public interface NativeBindings {\n")
        functions.forEach { function ->
            val parameters = function.metadata.parameters.mapIndexed { index, parameter ->
                "${function.parameterTypes[index]} ${safeIdentifier(parameter.name)}"
            }.joinToString(", ")
            output.append("        @NativeSymbol(\"${escapeJava(function.metadata.symbol)}\")\n")
            output.append("        ${function.returnType} ${safeIdentifier(function.metadata.name)}($parameters);\n")
        }
        output.append("    }\n\n")
        output.append("    private static final NativeBindings NATIVE = FFM.load(NativeBindings.class);\n\n")
        output.append("    private ${declaration.className}() { }\n\n")
        functions.forEach { function ->
            val parameters = function.metadata.parameters.mapIndexed { index, parameter ->
                "${function.parameterTypes[index]} ${safeIdentifier(parameter.name)}"
            }.joinToString(", ")
            val arguments = function.metadata.parameters.joinToString(", ") { safeIdentifier(it.name) }
            output.append("    public static ${function.returnType} ${safeIdentifier(function.metadata.name)}($parameters) {\n")
            if (function.returnType == "void") {
                output.append("        NATIVE.${safeIdentifier(function.metadata.name)}($arguments);\n")
            } else {
                output.append("        return NATIVE.${safeIdentifier(function.metadata.name)}($arguments);\n")
            }
            output.append("    }\n\n")
        }
        output.append("}\n")
        return output.toString()
    }

    private fun structureDependencies(type: String): Set<String> {
        if (type.startsWith("pointer<")) return emptySet()
        if (type.startsWith("struct:")) return setOf(type.removePrefix("struct:"))
        val array = Regex("^array<(.+),\\d+>$").matchEntire(type) ?: return emptySet()
        return structureDependencies(array.groupValues[1])
    }

    private fun createFieldBinding(
        field: FieldMetadata,
        structures: Map<String, StructureMetadata>
    ): GeneratedField? {
        val array = Regex("^array<(.+),(\\d+)>$").matchEntire(field.type)
        if (array != null) {
            val componentType = fieldJavaType(array.groupValues[1], structures, false) ?: return null
            if (componentType == "String" || componentType == "void") return null
            val boxed = boxedType(componentType)
            return GeneratedField(field, "NativeArray<$boxed>", false, array.groupValues[2].toInt())
        }

        val pointer = field.type.startsWith("pointer<") || field.type == "pointer"
        val javaType = fieldJavaType(field.type, structures, false) ?: return null
        return GeneratedField(field, javaType, pointer, null)
    }

    private fun fieldJavaType(
        type: String,
        structures: Map<String, StructureMetadata>,
        allowPointerStructure: Boolean
    ): String? {
        if (type.startsWith("pointer<")) {
            val pointee = type.removePrefix("pointer<").removeSuffix(">")
            if (pointee.startsWith("struct:")) {
                val name = pointee.removePrefix("struct:")
                if (allowPointerStructure && structures.containsKey(name)) {
                    return safeIdentifier(name)
                }
                if (allowPointerStructure) {
                    throw GradleException(
                        "Missing structure metadata for '$name'. " +
                                "The native metadata must describe every structure used by a function."
                    )
                }
            }
            return "java.lang.foreign.MemorySegment"
        }
        if (type == "pointer") return "java.lang.foreign.MemorySegment"
        if (type.startsWith("struct:")) {
            val name = type.removePrefix("struct:")
            if (structures.containsKey(name)) return safeIdentifier(name)
            return null
        }
        return when (type.lowercase()) {
            "i8", "u8", "int8", "uint8", "byte" -> "byte"
            "i16", "u16", "int16", "uint16", "short" -> "short"
            "i32", "u32", "int32", "uint32", "int" -> "int"
            "i64", "u64", "int64", "uint64", "long" -> "long"
            "f32", "float" -> "float"
            "f64", "double" -> "double"
            "string", "cstring", "const char*" -> "String"
            else -> null
        }
    }

    private fun javaType(
        type: String,
        structures: Map<String, GeneratedStructure>,
        allowPointerStructure: Boolean
    ): String {
        if (type.startsWith("pointer<")) {
            val pointee = type.removePrefix("pointer<").removeSuffix(">")
            if (allowPointerStructure && pointee.startsWith("struct:")) {
                val name = pointee.removePrefix("struct:")
                if (structures.containsKey(name)) return safeIdentifier(name)
            }
            return "java.lang.foreign.MemorySegment"
        }
        if (type == "pointer") return "java.lang.foreign.MemorySegment"
        if (type.startsWith("struct:")) {
            throw GradleException("Passing ${type.removePrefix("struct:")} by value is not supported by the FFM carrier mapper")
        }
        if (type.startsWith("array<")) {
            throw GradleException("Array parameters and return values must be represented as pointers")
        }
        return when (type.lowercase()) {
            "void", "()" -> "void"
            "i8", "u8", "int8", "uint8", "byte" -> "byte"
            "i16", "u16", "int16", "uint16", "short" -> "short"
            "i32", "u32", "int32", "uint32", "int" -> "int"
            "i64", "u64", "int64", "uint64", "long" -> "long"
            "f32", "float" -> "float"
            "f64", "double" -> "double"
            "string", "cstring", "const char*" -> "String"
            else -> throw GradleException("Unsupported native type '$type'")
        }
    }

    private fun boxedType(type: String): String = when (type) {
        "byte" -> "Byte"
        "short" -> "Short"
        "int" -> "Integer"
        "long" -> "Long"
        "float" -> "Float"
        "double" -> "Double"
        else -> type
    }

    private fun safeIdentifier(value: String): String {
        val cleaned = value.replace(Regex("[^A-Za-z0-9_$]"), "_")
        val identifier = if (cleaned.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*"))) cleaned else "_$cleaned"
        val keywords = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null",
            "record", "var", "yield", "permits", "sealed", "when", "_"
        )
        return if (identifier in keywords) "${identifier}_" else identifier
    }

    private fun escapeJava(value: String): String = value.replace("\\", "\\").replace("\"", "\"")

    private data class BindingDeclaration(
        val packageName: String,
        val className: String,
        val qualifiedName: String,
        val library: String
    )

    private data class LibraryMetadata(
        val library: String,
        val functions: List<FunctionMetadata>,
        val structures: List<StructureMetadata>
    )
    private data class FunctionMetadata(
        val name: String,
        val symbol: String,
        val returnType: String,
        val parameters: List<ParameterMetadata>
    )
    private data class ParameterMetadata(val name: String, val type: String)
    private data class StructureMetadata(
        val name: String,
        val kind: String,
        val packed: Boolean,
        val fields: List<FieldMetadata>
    )
    private data class FieldMetadata(val name: String, val type: String)
    private data class GeneratedStructure(
        val metadata: StructureMetadata,
        val fields: List<GeneratedField>
    )
    private data class GeneratedField(
        val metadata: FieldMetadata,
        val javaType: String,
        val pointer: Boolean,
        val length: Int?
    )
    private data class GeneratedFunction(
        val metadata: FunctionMetadata,
        val returnType: String,
        val parameterTypes: List<String>
    )
}
