package net.echonolix.caelum.sdl3.codegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText

internal object SDLHeaderParser {
    private val functionRegex = Regex(
        """extern\s+SDL_DECLSPEC\s+([^;]+?)\s*SDLCALL\s+(SDL_[A-Za-z0-9_]+)\s*\((.*?)\)\s*(?:[A-Z][A-Z0-9_]*(?:\([^;]*\))?\s*)*;""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    private val typedefEnumRegex = Regex("""typedef\s+enum\s+(SDL_[A-Za-z0-9_]+)\b""")
    private val typedefGroupRegex = Regex("""typedef\s+(struct|union)\s+(SDL_[A-Za-z0-9_]+)\b(?:[^;{]*\{)?""")
    private val opaqueRegex = Regex("""typedef\s+(?:struct|union)\s+(SDL_[A-Za-z0-9_]+)\s+\1\s*;""")
    private val functionPointerRegex = Regex(
        """typedef\s+.+?\(\s*(?:SDLCALL\s*)?\*\s*(SDL_[A-Za-z0-9_]+)\s*\)\s*\(""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val scalarTypedefRegex = Regex(
        """typedef\s+([A-Za-z_][A-Za-z0-9_]*(?:\s+[A-Za-z_][A-Za-z0-9_]*)*)\s+(SDL_[A-Za-z0-9_]+)\s*;""",
    )
    private val pointerAliasRegex = Regex(
        """typedef\s+[^;()]*\*\s*(SDL_[A-Za-z0-9_]+)\s*;""",
    )
    private val annotationRegex = Regex(
        """\b(?:SDL_(?:IN|OUT|INOUT)(?:_Z_(?:BYTE)?CAP|_(?:BYTE)?CAP)|SDL_PRINTF_FORMAT_STRING|SDL_SCANF_FORMAT_STRING|SDL_WPRINTF_FORMAT_STRING|SDL_ALLOC_SIZE2?|SDL_MALLOC)\s*(?:\([^)]*\))?""",
    )
    private val qualifierRegex = Regex("""\b(?:const|volatile|restrict|SDL_RESTRICT)\b""")
    private val whitespaceRegex = Regex("""\s+""")
    private val aggregateByValueTypes = setOf("SDL_GUID", "SDL_FColor")
    private val supportedAggregateFunctions = setOf(
        "SDL_GUIDToString",
        "SDL_GetGamepadGUIDForID",
        "SDL_GetGamepadMappingForGUID",
        "SDL_GetJoystickGUID",
        "SDL_GetJoystickGUIDForID",
        "SDL_GetJoystickGUIDInfo",
        "SDL_SetGPUBlendConstants",
        "SDL_StringToGUID",
    )
    private val macroOnlyFunctions = setOf("SDL_CreateThread", "SDL_CreateThreadWithProperties")
    private val platformAbiTypes = Regex(
        """\b(?:JavaVM|JNIEnv|XTaskQueueHandle|XUserHandle|VkInstance|VkSurfaceKHR|VkAllocationCallbacks)\b""",
    )
    private val scalarRoots = mapOf(
        "bool" to SDLScalar.BOOL,
        "char" to SDLScalar.BYTE,
        "signed char" to SDLScalar.BYTE,
        "unsigned char" to SDLScalar.UBYTE,
        "wchar_t" to SDLScalar.USHORT,
        "short" to SDLScalar.SHORT,
        "short int" to SDLScalar.SHORT,
        "signed short" to SDLScalar.SHORT,
        "signed short int" to SDLScalar.SHORT,
        "unsigned short" to SDLScalar.USHORT,
        "unsigned short int" to SDLScalar.USHORT,
        "int" to SDLScalar.INT,
        "signed" to SDLScalar.INT,
        "signed int" to SDLScalar.INT,
        "unsigned" to SDLScalar.UINT,
        "unsigned int" to SDLScalar.UINT,
        // SDL's supported 64-bit targets use 32-bit C long on Windows and 64-bit C long elsewhere.
        // The vendored binding currently targets the Windows SDL ABI used by Caelum's smoke tests.
        "long" to SDLScalar.INT,
        "long int" to SDLScalar.INT,
        "unsigned long" to SDLScalar.UINT,
        "unsigned long int" to SDLScalar.UINT,
        "long long" to SDLScalar.LONG,
        "long long int" to SDLScalar.LONG,
        "unsigned long long" to SDLScalar.ULONG,
        "unsigned long long int" to SDLScalar.ULONG,
        "float" to SDLScalar.FLOAT,
        "double" to SDLScalar.DOUBLE,
        "int8_t" to SDLScalar.BYTE,
        "uint8_t" to SDLScalar.UBYTE,
        "int16_t" to SDLScalar.SHORT,
        "uint16_t" to SDLScalar.USHORT,
        "int32_t" to SDLScalar.INT,
        "uint32_t" to SDLScalar.UINT,
        "int64_t" to SDLScalar.LONG,
        "uint64_t" to SDLScalar.ULONG,
        "intptr_t" to SDLScalar.LONG,
        "uintptr_t" to SDLScalar.ULONG,
        "size_t" to SDLScalar.ULONG,
        "ssize_t" to SDLScalar.LONG,
        "Sint8" to SDLScalar.BYTE,
        "Uint8" to SDLScalar.UBYTE,
        "Sint16" to SDLScalar.SHORT,
        "Uint16" to SDLScalar.USHORT,
        "Sint32" to SDLScalar.INT,
        "Uint32" to SDLScalar.UINT,
        "Sint64" to SDLScalar.LONG,
        "Uint64" to SDLScalar.ULONG,
    )

    fun parse(includeDir: Path): SDLRegistry {
        require(Files.isDirectory(includeDir)) { "SDL include directory does not exist: $includeDir" }
        val headers = Files.walk(includeDir).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.extension == "h" }
                .filter { it.name != "SDL_oldnames.h" }
                .sorted()
                .toList()
        }
        val texts = headers.associateWith { stripComments(it.readText()) }
        val namedTypes = parseNamedTypes(texts.values)
        val constants = parseCoreConstants(texts.values)
        val functions = mutableListOf<SDLFunction>()
        val skipped = mutableListOf<SDLSkipped>()

        texts.forEach { (header, text) ->
            functionRegex.findAll(text).forEach { match ->
                val returnText = normalizeType(match.groupValues[1])
                val name = match.groupValues[2]
                val parameterText = match.groupValues[3].trim()
                val declaration = match.value.replace(whitespaceRegex, " ").trim()
                if (name in macroOnlyFunctions) {
                    skipped += SDLSkipped(name, "header macro; use the exported Runtime variant")
                    return@forEach
                }
                if (parameterText.contains("...")) {
                    skipped += SDLSkipped(name, "variadic declaration")
                    return@forEach
                }
                if (returnText.contains("va_list") || parameterText.contains(Regex("""\bva_list\b"""))) {
                    skipped += SDLSkipped(name, "va_list ABI is platform-specific")
                    return@forEach
                }
                if (platformAbiTypes.containsMatchIn(returnText) || platformAbiTypes.containsMatchIn(parameterText)) {
                    skipped += SDLSkipped(name, "platform-specific native type")
                    return@forEach
                }
                try {
                    val returnType = parseType(returnText, namedTypes)
                    val parameters = parseParameters(parameterText, namedTypes)
                    val byValue = buildList {
                        if (returnType is SDLType.Aggregate) add("return ${returnType.name}")
                        parameters.filter { it.type is SDLType.Aggregate }
                            .forEach { add("parameter ${it.name}: ${(it.type as SDLType.Aggregate).name}") }
                    }
                    if (byValue.isNotEmpty() && name !in supportedAggregateFunctions) {
                        skipped += SDLSkipped(name, "aggregate passed by value (${byValue.joinToString()})")
                    } else {
                        functions += SDLFunction(name, returnType, parameters, declaration)
                    }
                } catch (failure: UnsupportedDeclaration) {
                    throw IllegalArgumentException(
                        "Unsupported SDL declaration in ${header.fileName}: $declaration (${failure.message})",
                        failure,
                    )
                }
            }
        }

        val duplicates = functions.groupingBy(SDLFunction::name).eachCount().filterValues { it > 1 }
        require(duplicates.isEmpty()) { "Duplicate SDL function declarations: ${duplicates.keys.sorted()}" }
        val skippedDuplicates = skipped.groupingBy(SDLSkipped::name).eachCount().filterValues { it > 1 }
        require(skippedDuplicates.isEmpty()) { "Duplicate skipped SDL declarations: ${skippedDuplicates.keys.sorted()}" }
        return SDLRegistry(
            functions.sortedBy(SDLFunction::name),
            skipped.sortedBy(SDLSkipped::name),
            namedTypes,
            constants,
        )
    }

    private fun parseCoreConstants(texts: Collection<String>): List<SDLConstant> {
        val allHeaders = texts.joinToString("\n")
        val macroConstants = listOf(
            Triple("SDL_MAJOR_VERSION", SDLConstantKind.INT, macroValue(allHeaders, "SDL_MAJOR_VERSION")),
            Triple("SDL_MINOR_VERSION", SDLConstantKind.INT, macroValue(allHeaders, "SDL_MINOR_VERSION")),
            Triple("SDL_MICRO_VERSION", SDLConstantKind.INT, macroValue(allHeaders, "SDL_MICRO_VERSION")),
            Triple("SDL_INIT_VIDEO", SDLConstantKind.UINT, macroValue(allHeaders, "SDL_INIT_VIDEO")),
            Triple("SDL_WINDOW_HIDDEN", SDLConstantKind.ULONG, macroValue(allHeaders, "SDL_WINDOW_HIDDEN")),
        )
        val eventQuit = Regex("""\bSDL_EVENT_QUIT\s*=\s*([^,}\r\n]+)""")
            .find(allHeaders)
            ?.groupValues
            ?.get(1)
            ?: error("Missing SDL_EVENT_QUIT in SDL headers")
        return (macroConstants + Triple("SDL_EVENT_QUIT", SDLConstantKind.INT, eventQuit))
            .map { (name, kind, expression) -> SDLConstant(name, kind, parseIntegerConstant(name, expression)) }
    }

    private fun macroValue(headers: String, name: String): String =
        Regex("""(?m)^\s*#\s*define\s+$name\s+([^\r\n]+)""")
            .find(headers)
            ?.groupValues
            ?.get(1)
            ?: error("Missing $name in SDL headers")

    private fun parseIntegerConstant(name: String, expression: String): ULong {
        var value = expression.trim()
        val wrapper = Regex("""SDL_UINT64_C\s*\(\s*([^()]+)\s*\)""").matchEntire(value)
        if (wrapper != null) value = wrapper.groupValues[1].trim()
        value = value.removeSurrounding("(", ")").trim().replace(Regex("""[uUlL]+$"""), "")
        return try {
            if (value.startsWith("0x", ignoreCase = true)) value.substring(2).toULong(16) else value.toULong()
        } catch (failure: NumberFormatException) {
            throw IllegalArgumentException("Unsupported integer expression for $name: '$expression'", failure)
        }
    }

    private fun parseNamedTypes(texts: Collection<String>): Map<String, SDLNamedType> {
        val result = linkedMapOf<String, SDLNamedType>()
        scalarRoots.forEach { (name, scalar) -> result[name] = SDLNamedType(name, SDLNamedKind.SCALAR_ALIAS, scalar) }
        result["void"] = SDLNamedType("void", SDLNamedKind.OPAQUE)
        texts.forEach { text ->
            typedefEnumRegex.findAll(text).forEach { match ->
                val name = match.groupValues[1]
                result[name] = SDLNamedType(name, SDLNamedKind.ENUM, SDLScalar.INT)
            }
            typedefGroupRegex.findAll(text).forEach { match ->
                val name = match.groupValues[2]
                result.putIfAbsent(name, SDLNamedType(name, SDLNamedKind.GROUP))
            }
            opaqueRegex.findAll(text).forEach { match ->
                val name = match.groupValues[1]
                result[name] = SDLNamedType(name, SDLNamedKind.OPAQUE)
            }
            functionPointerRegex.findAll(text).forEach { match ->
                val name = match.groupValues[1]
                result[name] = SDLNamedType(name, SDLNamedKind.FUNCTION_POINTER)
            }
            pointerAliasRegex.findAll(text).forEach { match ->
                val name = match.groupValues[1]
                if (result[name]?.kind != SDLNamedKind.FUNCTION_POINTER) {
                    result[name] = SDLNamedType(name, SDLNamedKind.POINTER_ALIAS)
                }
            }
        }
        var changed: Boolean
        do {
            changed = false
            texts.forEach { text ->
                scalarTypedefRegex.findAll(text).forEach { match ->
                    val source = normalizeType(match.groupValues[1])
                    val name = match.groupValues[2]
                    if (name !in result || result[name]?.kind == SDLNamedKind.GROUP) {
                        val sourceType = result[source]
                        val scalar = scalarRoots[source] ?: sourceType?.underlying
                        if (scalar != null) {
                            val resolved = SDLNamedType(name, SDLNamedKind.SCALAR_ALIAS, scalar)
                            if (result[name] != resolved) {
                                result[name] = resolved
                                changed = true
                            }
                        } else if (sourceType != null && sourceType.kind in setOf(SDLNamedKind.GROUP, SDLNamedKind.OPAQUE)) {
                            val resolved = SDLNamedType(name, sourceType.kind)
                            if (result[name] != resolved) {
                                result[name] = resolved
                                changed = true
                            }
                        }
                    }
                }
            }
        } while (changed)
        return result
    }

    private fun parseParameters(text: String, namedTypes: Map<String, SDLNamedType>): List<SDLParameter> {
        if (text.isBlank() || normalizeType(text) == "void") return emptyList()
        return splitTopLevel(text).mapIndexed { index, raw -> parseParameter(raw, index, namedTypes) }
    }

    private fun parseParameter(raw: String, index: Int, namedTypes: Map<String, SDLNamedType>): SDLParameter {
        var value = annotationRegex.replace(raw, " ").trim()
        value = value.replace(Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*\[\s*\]"""), "*$1")
        value = value.replace(whitespaceRegex, " ").trim()
        val match = Regex("""^(.*?)([A-Za-z_][A-Za-z0-9_]*)$""").matchEntire(value)
            ?: throw UnsupportedDeclaration("unable to split parameter '$raw'")
        val typeText = match.groupValues[1].trim()
        val name = match.groupValues[2].ifBlank { "p$index" }
        if (typeText.isBlank()) throw UnsupportedDeclaration("missing type for parameter '$raw'")
        return SDLParameter(name, parseType(typeText, namedTypes))
    }

    private fun parseType(raw: String, namedTypes: Map<String, SDLNamedType>): SDLType {
        val normalized = normalizeType(annotationRegex.replace(raw, " "))
            .replace(Regex("""\s*\*\s*"""), "*")
        val depth = normalized.count { it == '*' }
        val base = normalizeType(normalized.replace("*", " "))
        if (depth > 0) {
            val named = namedTypes[base]
            if (base !in scalarRoots && named == null && base !in setOf("void", "char", "wchar_t", "JavaVM")) {
                throw UnsupportedDeclaration("unknown pointer pointee '$base'")
            }
            val pointee = when {
                base == "void" -> null
                named?.kind == SDLNamedKind.SCALAR_ALIAS -> scalarMarker(named.underlying!!)
                named?.kind == SDLNamedKind.ENUM -> scalarMarker(requireNotNull(named.underlying))
                named?.kind == SDLNamedKind.POINTER_ALIAS -> base
                else -> base
            }
            val typedefDepth = if (named?.kind in setOf(SDLNamedKind.POINTER_ALIAS, SDLNamedKind.FUNCTION_POINTER)) 1 else 0
            return SDLType.Pointer(pointee, depth + typedefDepth)
        }
        if (base == "void") return SDLType.Void
        val scalar = scalarRoots[base] ?: namedTypes[base]?.underlying
        if (scalar != null) return SDLType.Scalar(scalar)
        val named = namedTypes[base] ?: throw UnsupportedDeclaration("unknown type '$base'")
        return when (named.kind) {
            SDLNamedKind.ENUM -> SDLType.Scalar(SDLScalar.INT)
            SDLNamedKind.FUNCTION_POINTER -> SDLType.Pointer(base, 1)
            SDLNamedKind.POINTER_ALIAS -> SDLType.Pointer(base, 1)
            SDLNamedKind.GROUP, SDLNamedKind.OPAQUE -> {
                if (base in aggregateByValueTypes) SDLType.Aggregate(base)
                else throw UnsupportedDeclaration("group '$base' is passed by value")
            }
            SDLNamedKind.SCALAR_ALIAS -> SDLType.Scalar(requireNotNull(named.underlying))
        }
    }

    private fun scalarMarker(scalar: SDLScalar): String = when (scalar) {
        SDLScalar.BOOL -> "NBool"
        SDLScalar.BYTE -> "NInt8"
        SDLScalar.UBYTE -> "NUInt8"
        SDLScalar.SHORT -> "NInt16"
        SDLScalar.USHORT -> "NUInt16"
        SDLScalar.INT -> "NInt"
        SDLScalar.UINT -> "NUInt32"
        SDLScalar.LONG -> "NInt64"
        SDLScalar.ULONG -> "NUInt64"
        SDLScalar.FLOAT -> "NFloat"
        SDLScalar.DOUBLE -> "NDouble"
    }

    private fun normalizeType(value: String): String = qualifierRegex.replace(value, " ")
        .replace(whitespaceRegex, " ")
        .trim()

    private fun splitTopLevel(value: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        value.forEachIndexed { index, char ->
            when (char) {
                '(' -> depth++
                ')' -> depth--
                ',' -> if (depth == 0) {
                    result += value.substring(start, index).trim()
                    start = index + 1
                }
            }
            if (depth < 0) throw UnsupportedDeclaration("unbalanced parameter list '$value'")
        }
        if (depth != 0) throw UnsupportedDeclaration("unbalanced parameter list '$value'")
        result += value.substring(start).trim()
        return result
    }

    private fun stripComments(text: String): String = text
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .replace(Regex("""//[^\r\n]*"""), " ")

    private class UnsupportedDeclaration(message: String) : RuntimeException(message)
}
