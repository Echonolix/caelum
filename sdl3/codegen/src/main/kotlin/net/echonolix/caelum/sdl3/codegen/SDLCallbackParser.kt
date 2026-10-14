package net.echonolix.caelum.sdl3.codegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText

internal object SDLCallbackParser {
    private val callbackRegex = Regex(
        """typedef\s+([^;{}]*?)\(\s*(?:SDLCALL\s*)?\*\s*(SDL_[A-Za-z0-9_]+)\s*\)\s*\(([^;{}]*?)\)\s*;""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val annotationRegex = Regex(
        """\b(?:SDL_(?:IN|OUT|INOUT)(?:_Z_(?:BYTE)?CAP|_(?:BYTE)?CAP)|SDL_PRINTF_FORMAT_STRING|SDL_SCANF_FORMAT_STRING|SDL_WPRINTF_FORMAT_STRING|SDL_ALLOC_SIZE2?|SDL_MALLOC)\s*(?:\([^)]*\))?""",
    )
    private val qualifierRegex = Regex("""\b(?:const|volatile|restrict|SDL_RESTRICT)\b""")
    private val whitespaceRegex = Regex("""\s+""")
    fun parse(includeDir: Path, namedTypes: Map<String, SDLNamedType>): SDLCallbackRegistry {
        require(Files.isDirectory(includeDir)) { "SDL include directory does not exist: $includeDir" }
        val headers = Files.walk(includeDir).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.extension == "h" }
                .filter { it.name != "SDL_oldnames.h" }
                .sorted()
                .toList()
        }
        val callbacks = linkedMapOf<String, SDLCallback>()
        val unsupported = linkedMapOf<String, SDLUnsupportedCallback>()
        val erasures = linkedSetOf<SDLCallbackPointeeErasure>()

        headers.forEach { header ->
            val text = stripComments(header.readText())
            callbackRegex.findAll(text).forEach { match ->
                val returnText = normalizeType(match.groupValues[1])
                val name = match.groupValues[2]
                val parameterText = match.groupValues[3].trim()
                val declaration = match.value.replace(whitespaceRegex, " ").trim()
                try {
                    val parameters = parseParameters(name, parameterText, namedTypes)
                    val callback = SDLCallback(
                        name,
                        parseType(returnText, namedTypes),
                        parameters.values,
                        declaration,
                    )
                    classifySupported(callbacks, unsupported, callback)
                    erasures += parameters.erasures
                } catch (failure: UnsupportedDeclaration) {
                    classifyUnsupported(
                        unsupported,
                        callbacks,
                        SDLUnsupportedCallback(name, "unsupported callback declaration: ${failure.message}"),
                    )
                }
            }
        }

        return SDLCallbackRegistry(
            callbacks.values.sortedBy(SDLCallback::name),
            unsupported.values.sortedBy(SDLUnsupportedCallback::name),
            erasures.sortedWith(compareBy(SDLCallbackPointeeErasure::callbackName, SDLCallbackPointeeErasure::parameterName)),
        )
    }

    private fun classifySupported(
        callbacks: MutableMap<String, SDLCallback>,
        unsupported: Map<String, SDLUnsupportedCallback>,
        callback: SDLCallback,
    ) {
        check(callback.name !in unsupported) { "Callback ${callback.name} is both supported and unsupported" }
        val previous = callbacks[callback.name]
        if (previous == null) {
            callbacks[callback.name] = callback
        } else {
            check(previous.returnType == callback.returnType && previous.parameters == callback.parameters) {
                "Conflicting declarations for callback ${callback.name}: ${previous.declaration} / ${callback.declaration}"
            }
        }
    }

    private fun classifyUnsupported(
        unsupported: MutableMap<String, SDLUnsupportedCallback>,
        callbacks: Map<String, SDLCallback>,
        callback: SDLUnsupportedCallback,
    ) {
        check(callback.name !in callbacks) { "Callback ${callback.name} is both supported and unsupported" }
        val previous = unsupported.putIfAbsent(callback.name, callback)
        check(previous == null || previous == callback) { "Conflicting unsupported reasons for ${callback.name}" }
    }

    private fun parseParameters(
        callbackName: String,
        text: String,
        namedTypes: Map<String, SDLNamedType>,
    ): ParsedParameters {
        if (text.isBlank() || normalizeType(text) == "void") return ParsedParameters(emptyList(), emptyList())
        val erasures = mutableListOf<SDLCallbackPointeeErasure>()
        val values = splitTopLevel(text).mapIndexed { index, raw ->
            parseParameter(raw, index, namedTypes).also { parsed ->
                val nativeType = normalizeType(annotationRegex.replace(parsed.typeText, " "))
                val compactType = nativeType.replace(Regex("""\s*\*\s*"""), "*")
                val depth = compactType.count { it == '*' }
                val base = normalizeType(compactType.replace("*", " "))
                if (
                    parsed.value.type is SDLType.Pointer &&
                    parsed.value.type.pointee == null &&
                    depth > 0 &&
                    base != "void" &&
                    namedTypes[base] == null
                ) {
                    erasures += SDLCallbackPointeeErasure(
                        callbackName = callbackName,
                        parameterName = parsed.value.name,
                        nativeType = nativeType,
                        exposedType = parsed.value.type.kotlinType(),
                        reason = "pointee layout is owned by platform headers and is not defined by SDL",
                    )
                }
            }.value
        }
        return ParsedParameters(values, erasures)
    }

    private fun parseParameter(raw: String, index: Int, namedTypes: Map<String, SDLNamedType>): ParsedParameter {
        var value = annotationRegex.replace(raw, " ").trim()
        value = value.replace(Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*\[\s*\]"""), "*$1")
        value = value.replace(whitespaceRegex, " ").trim()
        val match = Regex("""^(.*?)([A-Za-z_][A-Za-z0-9_]*)$""").matchEntire(value)
            ?: throw UnsupportedDeclaration("unable to split parameter '$raw'")
        val typeText = match.groupValues[1].trim()
        val name = match.groupValues[2].ifBlank { "p$index" }
        if (typeText.isBlank()) throw UnsupportedDeclaration("missing type for parameter '$raw'")
        return ParsedParameter(SDLParameter(name, parseType(typeText, namedTypes)), typeText)
    }

    private fun parseType(raw: String, namedTypes: Map<String, SDLNamedType>): SDLType {
        val normalized = normalizeType(annotationRegex.replace(raw, " "))
            .replace(Regex("""\s*\*\s*"""), "*")
        val depth = normalized.count { it == '*' }
        val base = normalizeType(normalized.replace("*", " "))
        val named = namedTypes[base]
        if (depth > 0) {
            val pointee = when {
                base == "void" -> null
                base == "char" || base == "wchar_t" -> base
                named?.kind == SDLNamedKind.SCALAR_ALIAS -> scalarMarker(requireNotNull(named.underlying))
                named?.kind == SDLNamedKind.POINTER_ALIAS -> base
                named == null -> null
                else -> base
            }
            val typedefDepth = if (named?.kind in setOf(SDLNamedKind.POINTER_ALIAS, SDLNamedKind.FUNCTION_POINTER)) 1 else 0
            return SDLType.Pointer(pointee, depth + typedefDepth)
        }
        if (base == "void") return SDLType.Void
        val scalar = named?.underlying
        if (scalar != null) return SDLType.Scalar(scalar)
        return when (named?.kind) {
            SDLNamedKind.ENUM -> SDLType.Scalar(SDLScalar.INT)
            SDLNamedKind.FUNCTION_POINTER -> SDLType.Pointer(base, 1)
            SDLNamedKind.POINTER_ALIAS -> SDLType.Pointer(base, 1)
            SDLNamedKind.GROUP, SDLNamedKind.OPAQUE -> throw UnsupportedDeclaration("group '$base' is passed by value")
            SDLNamedKind.SCALAR_ALIAS -> error("Scalar aliases must have an underlying carrier: $base")
            null -> throw UnsupportedDeclaration("unknown type '$base'")
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

    private data class ParsedParameter(val value: SDLParameter, val typeText: String)

    private data class ParsedParameters(
        val values: List<SDLParameter>,
        val erasures: List<SDLCallbackPointeeErasure>,
    )
}
