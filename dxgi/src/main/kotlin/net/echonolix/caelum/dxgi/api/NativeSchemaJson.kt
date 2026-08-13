package net.echonolix.caelum.dxgi.api

private sealed interface JsonValue

private data class JsonObject(val properties: Map<String, JsonValue>) : JsonValue

private data class JsonArray(val elements: List<JsonValue>) : JsonValue

private data class JsonString(val value: String) : JsonValue

private data class JsonNumber(val source: String) : JsonValue

private data class JsonBoolean(val value: Boolean) : JsonValue

private data object JsonNull : JsonValue

internal object NativeCatalogDecoder {
    fun decode(source: String): NativeApiCatalog {
        val root = SchemaJsonParser(source).parse().expectObject("root")
        val schemaVersion = root.requiredInt("schemaVersion")
        if (schemaVersion != NativeApiCatalog.SUPPORTED_SCHEMA_VERSION) {
            throw NativeSchemaException(
                "Unsupported native API schema version $schemaVersion; " +
                    "expected ${NativeApiCatalog.SUPPORTED_SCHEMA_VERSION}",
            )
        }
        val declarations = root.requiredObject("declarations")
        return NativeApiCatalog.create(
            schemaVersion = schemaVersion,
            api = root.requiredString("api"),
            namespace = root.optionalString("namespace"),
            target = root.optionalValue("target")?.toPublicValue(),
            sourceSet = root.optionalObject("sourceSet")?.toPublicObject(),
            reviewedExclusions = root.optionalArray("reviewedExclusions").orEmpty().mapIndexed { index, value ->
                val exclusion = value.expectObject("reviewedExclusions[$index]")
                NativeReviewedExclusion(
                    pattern = exclusion.requiredString("pattern"),
                    reason = exclusion.requiredString("reason"),
                )
            },
            declarations = NativeApiDeclarations(
                interfaces = declarations.optionalArray("interfaces").orEmpty().mapIndexed(::decodeInterface),
                enums = declarations.optionalArray("enums").orEmpty().mapIndexed(::decodeEnum),
                records = declarations.optionalArray("records").orEmpty().mapIndexed(::decodeRecord),
                typedefs = declarations.optionalArray("typedefs").orEmpty().mapIndexed(::decodeTypedef),
                functions = declarations.optionalArray("functions").orEmpty().mapIndexed(::decodeFunction),
                constants = declarations.optionalArray("constants").orEmpty().mapIndexed(::decodeConstant),
            ),
            statistics = root.optionalObject("statistics")?.toPublicObject(),
        )
    }

    private fun decodeInterface(index: Int, value: JsonValue): NativeInterfaceDeclaration {
        val item = value.expectObject("declarations.interfaces[$index]")
        return NativeInterfaceDeclaration(
            name = item.requiredString("name"),
            iid = item.optionalString("iid"),
            parent = item.optionalString("parent"),
            methods = item.optionalArray("methods").orEmpty().mapIndexed { methodIndex, method ->
                val decoded = method.expectObject("declarations.interfaces[$index].methods[$methodIndex]")
                NativeMethodDeclaration(
                    slot = decoded.requiredInt("slot"),
                    name = decoded.requiredString("name"),
                    returnType = decoded.optionalString("returnType"),
                    parameters = decodeParameters(decoded, "declarations.interfaces[$index].methods[$methodIndex]"),
                    type = decoded.requiredString("type"),
                )
            },
            header = item.optionalString("header"),
            sourceLine = item.optionalInt("sourceLine"),
        )
    }

    private fun decodeEnum(index: Int, value: JsonValue): NativeEnumDeclaration {
        val item = value.expectObject("declarations.enums[$index]")
        return NativeEnumDeclaration(
            name = item.requiredString("name"),
            underlyingType = item.optionalString("underlyingType"),
            entries = item.optionalArray("entries").orEmpty().mapIndexed { entryIndex, entry ->
                val decoded = entry.expectObject("declarations.enums[$index].entries[$entryIndex]")
                NativeEnumEntry(decoded.requiredString("name"), decoded.scalarText("value"))
            },
            header = item.optionalString("header"),
            sourceLine = item.optionalInt("sourceLine"),
        )
    }

    private fun decodeRecord(index: Int, value: JsonValue): NativeRecordDeclaration {
        val item = value.expectObject("declarations.records[$index]")
        return decodeRecordObject(item, "declarations.records[$index]")
    }

    private fun decodeRecordObject(item: JsonObject, path: String): NativeRecordDeclaration {
        val kind = when (val sourceKind = item.requiredString("kind").lowercase()) {
            "struct" -> NativeRecordKind.STRUCT
            "union" -> NativeRecordKind.UNION
            else -> throw NativeSchemaException(
                "$path.kind must be 'struct' or 'union', got '$sourceKind'",
            )
        }
        return NativeRecordDeclaration(
            name = item.requiredStringOrNumber("name"),
            kind = kind,
            size = item.optionalLong("size"),
            alignment = item.optionalLong("align") ?: item.optionalLong("alignment"),
            fields = item.optionalArray("fields").orEmpty().mapIndexed { fieldIndex, field ->
                val decoded = field.expectObject("$path.fields[$fieldIndex]")
                NativeRecordField(
                    name = decoded.optionalString("name"),
                    type = decoded.requiredString("type"),
                    canonicalType = decoded.optionalString("canonicalType"),
                    offsetBits = decoded.optionalLong("offsetBits"),
                    bitWidth = decoded.optionalLong("bitWidth"),
                    anonymousRecord = decoded.optionalObject("anonymousRecord")?.let {
                        decodeRecordObject(it, "$path.fields[$fieldIndex].anonymousRecord")
                    },
                )
            },
            header = item.optionalString("header"),
            sourceLine = item.optionalInt("sourceLine"),
        )
    }

    private fun decodeTypedef(index: Int, value: JsonValue): NativeTypedefDeclaration {
        val item = value.expectObject("declarations.typedefs[$index]")
        return NativeTypedefDeclaration(
            name = item.requiredString("name"),
            type = item.requiredString("type"),
            canonicalType = item.optionalString("canonicalType"),
            header = item.optionalString("header"),
            sourceLine = item.optionalInt("sourceLine"),
        )
    }

    private fun decodeFunction(index: Int, value: JsonValue): NativeFunctionDeclaration {
        val item = value.expectObject("declarations.functions[$index]")
        return NativeFunctionDeclaration(
            name = item.requiredString("name"),
            returnType = item.optionalString("returnType"),
            parameters = decodeParameters(item, "declarations.functions[$index]"),
            type = item.requiredString("type"),
            dll = item.optionalString("dll"),
            header = item.optionalString("header"),
            sourceLine = item.optionalInt("sourceLine"),
        )
    }

    private fun decodeConstant(index: Int, value: JsonValue): NativeConstantDeclaration {
        val item = value.expectObject("declarations.constants[$index]")
        return NativeConstantDeclaration(
            name = item.requiredString("name"),
            type = item.optionalString("type"),
            value = item.optionalScalarText("value"),
            valueText = item.optionalScalarText("valueText"),
            header = item.optionalString("header"),
            sourceLine = item.optionalInt("sourceLine"),
        )
    }

    private fun decodeParameters(item: JsonObject, path: String): List<NativeParameterDeclaration> =
        item.optionalArray("params").orEmpty().mapIndexed { parameterIndex, parameter ->
            val decoded = parameter.expectObject("$path.params[$parameterIndex]")
            NativeParameterDeclaration(
                name = decoded.requiredString("name"),
                type = decoded.requiredString("type"),
            )
        }
}

internal class SchemaJsonObjectAccess private constructor(private val value: JsonObject) {
    fun requiredInt(name: String): Int = value.requiredInt(name)

    fun requiredStringMap(name: String): Map<String, String> {
        val objectValue = value.requiredObject(name)
        return objectValue.properties.mapValues { (key, item) ->
            (item as? JsonString)?.value
                ?: throw NativeSchemaException("Property '$name.$key' must be a string")
        }
    }

    fun optionalStringMap(name: String): Map<String, String>? {
        val objectValue = value.optionalObject(name) ?: return null
        return objectValue.properties.mapValues { (key, item) ->
            (item as? JsonString)?.value
                ?: throw NativeSchemaException("Property '$name.$key' must be a string")
        }
    }

    fun optionalPublicObject(name: String): NativeSchemaObject? = value.optionalObject(name)?.toPublicObject()

    companion object {
        fun parse(source: String): SchemaJsonObjectAccess =
            SchemaJsonObjectAccess(SchemaJsonParser(source).parse().expectObject("root"))
    }
}

internal object SchemaJsonAccess {
    fun parseObject(source: String): SchemaJsonObjectAccess =
        SchemaJsonObjectAccess.parse(source)
}

/* Kept internal so malformed or future schemas cannot bypass catalog validation. */
private fun NativeApiCatalog.Companion.create(
    schemaVersion: Int,
    api: String,
    namespace: String?,
    target: NativeSchemaValue?,
    sourceSet: NativeSchemaObject?,
    reviewedExclusions: List<NativeReviewedExclusion>,
    declarations: NativeApiDeclarations,
    statistics: NativeSchemaObject?,
): NativeApiCatalog = NativeApiCatalog(
    schemaVersion,
    api,
    namespace,
    target,
    sourceSet,
    reviewedExclusions,
    declarations,
    statistics,
)

private class SchemaJsonParser(private val source: String) {
    private var index = 0

    fun parse(): JsonValue {
        skipWhitespace()
        val result = readValue("root")
        skipWhitespace()
        if (index != source.length) fail("Unexpected trailing content")
        return result
    }

    private fun readValue(path: String): JsonValue {
        if (index >= source.length) fail("Expected a value at $path")
        return when (source[index]) {
            '{' -> readObject(path)
            '[' -> readArray(path)
            '"' -> JsonString(readString())
            't' -> readLiteral("true", JsonBoolean(true))
            'f' -> readLiteral("false", JsonBoolean(false))
            'n' -> readLiteral("null", JsonNull)
            '-', in '0'..'9' -> JsonNumber(readNumber())
            else -> fail("Unexpected '${source[index]}' at $path")
        }
    }

    private fun readObject(path: String): JsonObject {
        index++
        skipWhitespace()
        val properties = linkedMapOf<String, JsonValue>()
        if (consume('}')) return JsonObject(properties)
        while (true) {
            if (index >= source.length || source[index] != '"') fail("Expected an object key at $path")
            val name = readString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            val value = readValue("$path.$name")
            if (properties.put(name, value) != null) fail("Duplicate object key '$name' at $path")
            skipWhitespace()
            if (consume('}')) return JsonObject(properties)
            expect(',')
            skipWhitespace()
        }
    }

    private fun readArray(path: String): JsonArray {
        index++
        skipWhitespace()
        val elements = mutableListOf<JsonValue>()
        if (consume(']')) return JsonArray(elements)
        while (true) {
            elements += readValue("$path[${elements.size}]")
            skipWhitespace()
            if (consume(']')) return JsonArray(elements)
            expect(',')
            skipWhitespace()
        }
    }

    private fun readString(): String {
        expect('"')
        val result = StringBuilder()
        while (index < source.length) {
            val character = source[index++]
            when (character) {
                '"' -> return result.toString()
                '\\' -> result.append(readEscape())
                in '\u0000'..'\u001f' -> fail("Unescaped control character in string")
                else -> result.append(character)
            }
        }
        fail("Unterminated string")
    }

    private fun readEscape(): Char {
        if (index >= source.length) fail("Unterminated escape sequence")
        return when (val escaped = source[index++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000c'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> readUnicodeEscape()
            else -> fail("Invalid escape sequence \\$escaped")
        }
    }

    private fun readUnicodeEscape(): Char {
        if (index + 4 > source.length) fail("Incomplete unicode escape")
        val digits = source.substring(index, index + 4)
        index += 4
        return digits.toIntOrNull(16)?.toChar() ?: fail("Invalid unicode escape \\u$digits")
    }

    private fun readNumber(): String {
        val start = index
        if (consume('-') && index >= source.length) fail("Incomplete number")
        if (consume('0')) {
            if (index < source.length && source[index].isDigit()) fail("Leading zero in number")
        } else {
            readDigits(requireAtLeastOne = true)
        }
        if (consume('.')) readDigits(requireAtLeastOne = true)
        if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
            index++
            if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
            readDigits(requireAtLeastOne = true)
        }
        return source.substring(start, index)
    }

    private fun readDigits(requireAtLeastOne: Boolean) {
        val start = index
        while (index < source.length && source[index].isDigit()) index++
        if (requireAtLeastOne && start == index) fail("Expected a digit")
    }

    private fun <T : JsonValue> readLiteral(text: String, result: T): T {
        if (!source.regionMatches(index, text, 0, text.length)) fail("Expected '$text'")
        index += text.length
        return result
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index] in " \t\r\n") index++
    }

    private fun consume(expected: Char): Boolean {
        if (index < source.length && source[index] == expected) {
            index++
            return true
        }
        return false
    }

    private fun expect(expected: Char) {
        if (!consume(expected)) fail("Expected '$expected'")
    }

    private fun fail(message: String): Nothing {
        val line = source.take(index).count { it == '\n' } + 1
        val lastLine = source.lastIndexOf('\n', (index - 1).coerceAtLeast(0))
        val column = index - lastLine
        throw NativeSchemaException("$message at line $line, column $column")
    }
}

private fun JsonValue.expectObject(path: String): JsonObject = this as? JsonObject
    ?: throw NativeSchemaException("$path must be an object")

private fun JsonObject.requiredValue(name: String): JsonValue = properties[name]
    ?: throw NativeSchemaException("Required property '$name' is missing")

private fun JsonObject.requiredString(name: String): String =
    (requiredValue(name) as? JsonString)?.value
        ?: throw NativeSchemaException("Property '$name' must be a string")

/* Anonymous Clang records can be assigned a stable numeric synthetic name by the extractor. */
private fun JsonObject.requiredStringOrNumber(name: String): String = when (val value = requiredValue(name)) {
    is JsonString -> value.value
    is JsonNumber -> value.source
    else -> throw NativeSchemaException("Property '$name' must be a string or number")
}

private fun JsonObject.optionalString(name: String): String? = when (val value = properties[name]) {
    null, JsonNull -> null
    is JsonString -> value.value
    else -> throw NativeSchemaException("Property '$name' must be a string or null")
}

private fun JsonObject.optionalValue(name: String): JsonValue? = when (val value = properties[name]) {
    null, JsonNull -> null
    else -> value
}

private fun JsonObject.requiredObject(name: String): JsonObject = requiredValue(name).expectObject(name)

private fun JsonObject.optionalObject(name: String): JsonObject? = when (val value = properties[name]) {
    null, JsonNull -> null
    is JsonObject -> value
    else -> throw NativeSchemaException("Property '$name' must be an object or null")
}

private fun JsonObject.optionalArray(name: String): List<JsonValue>? = when (val value = properties[name]) {
    null, JsonNull -> null
    is JsonArray -> value.elements
    else -> throw NativeSchemaException("Property '$name' must be an array or null")
}

private fun JsonObject.requiredInt(name: String): Int = requiredLong(name).also {
    if (it !in Int.MIN_VALUE..Int.MAX_VALUE) throw NativeSchemaException("Property '$name' is outside Int range")
}.toInt()

private fun JsonObject.optionalInt(name: String): Int? = optionalLong(name)?.also {
    if (it !in Int.MIN_VALUE..Int.MAX_VALUE) throw NativeSchemaException("Property '$name' is outside Int range")
}?.toInt()

private fun JsonObject.requiredLong(name: String, fallback: String? = null): Long {
    val value = properties[name] ?: fallback?.let(properties::get)
        ?: throw NativeSchemaException("Required property '$name' is missing")
    return value.integralLong(name)
}

private fun JsonObject.optionalLong(name: String): Long? = when (val value = properties[name]) {
    null, JsonNull -> null
    else -> value.integralLong(name)
}

private fun JsonValue.integralLong(name: String): Long = when (this) {
    is JsonNumber -> source.takeUnless { '.' in it || 'e' in it.lowercase() }?.toLongOrNull()
    is JsonString -> value.toLongOrNull()
    else -> null
} ?: throw NativeSchemaException("Property '$name' must be an integral 64-bit number")

private fun JsonObject.scalarText(name: String): String = optionalScalarText(name)
    ?: throw NativeSchemaException("Required scalar property '$name' is missing")

private fun JsonObject.optionalScalarText(name: String): String? = when (val value = properties[name]) {
    null, JsonNull -> null
    is JsonString -> value.value
    is JsonNumber -> value.source
    is JsonBoolean -> value.value.toString()
    else -> throw NativeSchemaException("Property '$name' must be a string, number, boolean, or null")
}

private fun JsonObject.toPublicObject(): NativeSchemaObject = NativeSchemaObject(
    properties.mapValues { (_, value) -> value.toPublicValue() },
)

private fun JsonValue.toPublicValue(): NativeSchemaValue = when (this) {
    is JsonObject -> toPublicObject()
    is JsonArray -> NativeSchemaArray(elements.map(JsonValue::toPublicValue))
    is JsonString -> NativeSchemaString(value)
    is JsonNumber -> NativeSchemaNumber(source)
    is JsonBoolean -> NativeSchemaBoolean(value)
    JsonNull -> NativeSchemaNull
}
