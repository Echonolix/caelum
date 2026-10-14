package net.echonolix.caelum.sdl3.codegen

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal object SDLTypeGenerator {
    private const val PACKAGE_NAME = "net.echonolix.caelum.sdl3"

    fun generate(registry: SDLRegistry, outputDir: Path): Set<Path> {
        val packageDir = outputDir.resolve(PACKAGE_NAME.replace('.', '/')).createDirectories()
        val types = packageDir.resolve("SDLTypes.kt")
        val accessors = packageDir.resolve("SDLTypeAccessors.kt")
        types.writeText(typesFile(registry))
        accessors.writeText(accessorsFile())
        return setOf(types, accessors)
    }

    private fun typesFile(registry: SDLRegistry): String = buildString {
        appendLine("@file:Suppress(\"ObjectPropertyName\", \"unused\")")
        appendLine("package $PACKAGE_NAME")
        appendLine()
        appendLine("import java.lang.foreign.MemoryLayout")
        appendLine("import java.lang.foreign.ValueLayout")
        appendLine("import net.echonolix.caelum.*")
        appendLine()
        appendMetadataTypes()
        appendLine()
        appendLine("public object SDLRecordLayouts {")
        appendLine("    @JvmField")
        appendLine("    public val records: List<SDLRecordInfo> = listOf(")
        SDLWindowsX64Layouts.records.forEach { record -> appendRecordInfo(record) }
        appendRecordInfo(TLS_ID_LAYOUT)
        appendLine("    )")
        appendLine()
        appendLine("    @JvmField")
        appendLine("    public val byName: Map<String, SDLRecordInfo> = records.associateBy(SDLRecordInfo::name)")
        appendLine("}")
        appendLine()

        SDLWindowsX64Layouts.records.forEach { record ->
            appendRecordObject(record)
            appendLine()
        }
        appendRecordObject(TLS_ID_LAYOUT)
        appendLine()

        opaquePointees(registry).forEach { name ->
            appendLine("public interface $name : NStruct")
        }
    }

    private fun StringBuilder.appendMetadataTypes() {
        appendLine("public enum class SDLRecordKind { STRUCT, UNION }")
        appendLine()
        appendLine("public enum class SDLFieldCarrier {")
        SDLFieldCarrier.entries.forEach { appendLine("    ${it.name},") }
        appendLine("}")
        appendLine()
        appendLine("public data class SDLFieldInfo(")
        appendLine("    public val name: String,")
        appendLine("    public val offset: Long,")
        appendLine("    public val size: Long,")
        appendLine("    public val carrier: SDLFieldCarrier,")
        appendLine("    public val elementCount: Long = 1,")
        appendLine("    public val nativeType: String,")
        appendLine("    public val recordType: String? = null,")
        appendLine("    public val pointerType: String? = null,")
        appendLine("    public val unsupportedReason: String? = null,")
        appendLine(")")
        appendLine()
        appendLine("public data class SDLRecordInfo(")
        appendLine("    public val name: String,")
        appendLine("    public val kind: SDLRecordKind,")
        appendLine("    public val size: Long,")
        appendLine("    public val alignment: Long,")
        appendLine("    public val fields: List<SDLFieldInfo>,")
        appendLine(")")
        appendLine()
        appendLine("public data class SDLUnsupportedField(")
        appendLine("    public val record: String,")
        appendLine("    public val field: String,")
        appendLine("    public val nativeType: String,")
        appendLine("    public val reason: String,")
        appendLine(")")
    }

    private fun StringBuilder.appendRecordInfo(record: SDLRecordLayout) {
        appendLine("        SDLRecordInfo(")
        appendLine("            \"${record.name}\",")
        appendLine("            SDLRecordKind.${record.kind.name},")
        appendLine("            ${record.size}L,")
        appendLine("            ${record.alignment}L,")
        appendLine("            listOf(")
        record.fields.forEach { field ->
            append("                SDLFieldInfo(\"${field.name}\", ${field.offset}L, ${field.size}L, ")
            append("SDLFieldCarrier.${field.carrier.name}, ${field.elementCount}L, \"${escape(field.nativeType)}\"")
            append(", ${field.recordType?.let { "\"${escape(it)}\"" } ?: "null"}")
            append(", ${field.pointerType?.let { "\"${escape(it)}\"" } ?: "null"}")
            append(", ${field.unsupportedReason?.let { "\"${escape(it)}\"" } ?: "null"}")
            appendLine("),")
        }
        appendLine("            ),")
        appendLine("        ),")
    }

    private fun accessorsFile(): String = buildString {
        appendLine("@file:Suppress(\"ObjectPropertyName\", \"unused\")")
        appendLine("package $PACKAGE_NAME")
        appendLine()
        appendLine("import java.lang.foreign.ValueLayout")
        appendLine("import net.echonolix.caelum.*")
        appendLine("import net.echonolix.caelum.APIHelper.`$OMNI_SEGMENT_IDENTIFIER`")
        appendLine()
        (SDLWindowsX64Layouts.records + TLS_ID_LAYOUT).forEach { record ->
            record.fields.forEach { field -> appendAccessors(record, field) }
        }
        appendLine("public object SDLUnsupportedFields {")
        appendLine("    @JvmField")
        appendLine("    public val fields: List<SDLUnsupportedField> = listOf(")
        SDLWindowsX64Layouts.records.forEach { record ->
            record.fields.filter { it.unsupportedReason != null }.forEach { field ->
                appendLine(
                    "        SDLUnsupportedField(\"${record.name}\", \"${field.name}\", " +
                        "\"${escape(field.nativeType)}\", \"${escape(field.unsupportedReason!!)}\"),",
                )
            }
        }
        appendLine("    )")
        appendLine("}")
    }

    private fun StringBuilder.appendAccessors(record: SDLRecordLayout, field: SDLFieldLayout) {
        val fieldName = escapeIdentifier(field.name)
        val prefix = "${record.name}_${field.name}".replace(Regex("[^A-Za-z0-9_]"), "_")
        when {
            field.carrier == SDLFieldCarrier.STORAGE -> Unit
            field.elementCount > 1L -> {
                val type = fieldElementType(field)
                appendLine("public val NValue<${record.name}>.$fieldName: NArray<$type>")
                appendLine("    @JvmName(\"${prefix}_valueView\")")
                appendLine("    get() = NArray(_address + ${field.offset}L, ${field.elementCount}L)")
                appendLine()
                appendLine("public val NPointer<${record.name}>.$fieldName: NArray<$type>")
                appendLine("    @JvmName(\"${prefix}_pointerView\")")
                appendLine("    get() = NArray(_address + ${field.offset}L, ${field.elementCount}L)")
                appendLine()
            }
            field.carrier == SDLFieldCarrier.RECORD -> {
                val type = requireNotNull(field.recordType)
                appendLine("public val NValue<${record.name}>.$fieldName: NValue<$type>")
                appendLine("    @JvmName(\"${prefix}_valueView\")")
                appendLine("    get() = NValue(_address + ${field.offset}L)")
                appendLine()
                appendLine("public val NPointer<${record.name}>.$fieldName: NValue<$type>")
                appendLine("    @JvmName(\"${prefix}_pointerView\")")
                appendLine("    get() = NValue(_address + ${field.offset}L)")
                appendLine()
            }
            else -> appendScalarAccessors(record, field, fieldName, prefix)
        }
    }

    private fun StringBuilder.appendScalarAccessors(
        record: SDLRecordLayout,
        field: SDLFieldLayout,
        fieldName: String,
        prefix: String,
    ) {
        val apiType = fieldApiType(field)
        val descriptor = fieldDescriptor(field)
        val read = if (field.carrier == SDLFieldCarrier.POINTER) {
            "NPointer.fromNativeData<$apiType>(`$OMNI_SEGMENT_IDENTIFIER`.get(ValueLayout.JAVA_LONG, _address + ${field.offset}L))"
        } else {
            "$descriptor.fromNativeData($descriptor.pointerGetValue(NPointer<$descriptor>(_address + ${field.offset}L)))"
        }
        val write = if (field.carrier == SDLFieldCarrier.POINTER) {
            "`$OMNI_SEGMENT_IDENTIFIER`.set(ValueLayout.JAVA_LONG, _address + ${field.offset}L, NPointer.toNativeData(value))"
        } else {
            "$descriptor.pointerSetValue(NPointer<$descriptor>(_address + ${field.offset}L), $descriptor.toNativeData(value))"
        }
        appendLine("public var NValue<${record.name}>.$fieldName: ${renderedApiType(field)}")
        appendLine("    @JvmName(\"${prefix}_valueGet\")")
        appendLine("    get() = $read")
        appendLine("    @JvmName(\"${prefix}_valueSet\")")
        appendLine("    set(value) { $write }")
        appendLine()
        appendLine("public var NPointer<${record.name}>.$fieldName: ${renderedApiType(field)}")
        appendLine("    @JvmName(\"${prefix}_pointerGet\")")
        appendLine("    get() = $read")
        appendLine("    @JvmName(\"${prefix}_pointerSet\")")
        appendLine("    set(value) { $write }")
        appendLine()
    }

    private fun renderedApiType(field: SDLFieldLayout): String =
        if (field.carrier == SDLFieldCarrier.POINTER) "NPointer<${fieldApiType(field)}>" else fieldApiType(field)

    private fun fieldApiType(field: SDLFieldLayout): String = when (field.carrier) {
        SDLFieldCarrier.BOOL -> "Boolean"
        SDLFieldCarrier.INT8 -> "Byte"
        SDLFieldCarrier.UINT8 -> "UByte"
        SDLFieldCarrier.INT16 -> "Short"
        SDLFieldCarrier.UINT16 -> "UShort"
        SDLFieldCarrier.INT32 -> "Int"
        SDLFieldCarrier.UINT32 -> "UInt"
        SDLFieldCarrier.INT64 -> "Long"
        SDLFieldCarrier.UINT64 -> "ULong"
        SDLFieldCarrier.FLOAT -> "Float"
        SDLFieldCarrier.DOUBLE -> "Double"
        SDLFieldCarrier.POINTER -> pointerElementType(field.pointerType)
        SDLFieldCarrier.RECORD -> requireNotNull(field.recordType)
        SDLFieldCarrier.STORAGE -> error("Storage fields have no typed accessor")
    }

    private fun fieldElementType(field: SDLFieldLayout): String = when (field.carrier) {
        SDLFieldCarrier.BOOL -> "NBool"
        SDLFieldCarrier.INT8 -> "NInt8"
        SDLFieldCarrier.UINT8 -> "NUInt8"
        SDLFieldCarrier.INT16 -> "NInt16"
        SDLFieldCarrier.UINT16 -> "NUInt16"
        SDLFieldCarrier.INT32 -> "NInt"
        SDLFieldCarrier.UINT32 -> "NUInt32"
        SDLFieldCarrier.INT64 -> "NInt64"
        SDLFieldCarrier.UINT64 -> "NUInt64"
        SDLFieldCarrier.FLOAT -> "NFloat"
        SDLFieldCarrier.DOUBLE -> "NDouble"
        SDLFieldCarrier.POINTER -> "NPointer<${pointerElementType(field.pointerType)}>"
        SDLFieldCarrier.RECORD -> requireNotNull(field.recordType)
        SDLFieldCarrier.STORAGE -> error("Storage fields have no typed element view")
    }

    private fun fieldDescriptor(field: SDLFieldLayout): String = when (field.carrier) {
        SDLFieldCarrier.BOOL -> "NBool"
        SDLFieldCarrier.INT8 -> "NInt8"
        SDLFieldCarrier.UINT8 -> "NUInt8"
        SDLFieldCarrier.INT16 -> "NInt16"
        SDLFieldCarrier.UINT16 -> "NUInt16"
        SDLFieldCarrier.INT32 -> "NInt"
        SDLFieldCarrier.UINT32 -> "NUInt32"
        SDLFieldCarrier.INT64 -> "NInt64"
        SDLFieldCarrier.UINT64 -> "NUInt64"
        SDLFieldCarrier.FLOAT -> "NFloat"
        SDLFieldCarrier.DOUBLE -> "NDouble"
        SDLFieldCarrier.POINTER -> "NPointer"
        else -> error("No scalar descriptor for ${field.carrier}")
    }

    private fun pointerElementType(pointerType: String?): String {
        if (pointerType?.startsWith("NPointer<") == true && pointerType.endsWith('>')) {
            return "NPointer<${pointerElementType(pointerType.removePrefix("NPointer<").dropLast(1))}>"
        }
        return when (pointerType) {
            null, "void" -> "NChar"
            "char" -> "NChar"
            "wchar_t" -> "NUInt16"
            "Uint8" -> "NUInt8"
            "Uint16" -> "NUInt16"
            else -> pointerType
        }
    }

    private fun StringBuilder.appendRecordObject(record: SDLRecordLayout) {
        val base = if (record.kind == SDLRecordKind.STRUCT) "NStruct" else "NUnion"
        appendLine("public object ${record.name} : $base.Impl<${record.name}>(")
        val members = if (record.kind == SDLRecordKind.UNION) {
            record.fields.map { field ->
                require(field.offset == 0L) { "Union field ${record.name}.${field.name} has non-zero offset" }
                fieldLayout(field)
            }
        } else {
            buildList {
                var cursor = 0L
                record.fields.sortedBy(SDLFieldLayout::offset).forEach { field ->
                    require(field.offset >= cursor) { "Overlapping field ${record.name}.${field.name}" }
                    if (field.offset > cursor) add(paddingLayout(field.offset - cursor))
                    add(fieldLayout(field))
                    cursor = field.offset + field.size
                }
                require(cursor <= record.size) { "Fields exceed ${record.name} size" }
                if (cursor < record.size) add(paddingLayout(record.size - cursor))
            }
        }
        members.forEach { appendLine("    $it,") }
        appendLine(")")
    }

    private fun fieldLayout(field: SDLFieldLayout): String {
        val element = when (field.carrier) {
            SDLFieldCarrier.BOOL -> "NBool.layout"
            SDLFieldCarrier.INT8 -> "NInt8.layout"
            SDLFieldCarrier.UINT8 -> "NUInt8.layout"
            SDLFieldCarrier.INT16 -> "NInt16.layout"
            SDLFieldCarrier.UINT16 -> "NUInt16.layout"
            SDLFieldCarrier.INT32 -> "NInt.layout"
            SDLFieldCarrier.UINT32 -> "NUInt32.layout"
            SDLFieldCarrier.INT64 -> "NInt64.layout"
            SDLFieldCarrier.UINT64 -> "NUInt64.layout"
            SDLFieldCarrier.FLOAT -> "NFloat.layout"
            SDLFieldCarrier.DOUBLE -> "NDouble.layout"
            SDLFieldCarrier.POINTER -> "NPointer.layout"
            SDLFieldCarrier.RECORD -> requireNotNull(field.recordType) { "Missing record type for ${field.name}" } + ".layout"
            SDLFieldCarrier.STORAGE -> return storageLayout(field.size, 1, field.name)
        }
        return if (field.elementCount == 1L) {
            "$element.withName(\"${field.name}\")"
        } else {
            "MemoryLayout.sequenceLayout(${field.elementCount}L, $element).withName(\"${field.name}\")"
        }
    }

    private fun storageLayout(size: Long, alignment: Long, name: String): String =
        "MemoryLayout.sequenceLayout(${size}L, ValueLayout.JAVA_BYTE).withByteAlignment(${alignment}L).withName(\"$name\")"

    private fun paddingLayout(size: Long): String = "MemoryLayout.paddingLayout(${size}L)"

    private fun opaquePointees(registry: SDLRegistry): Set<String> {
        val functionPointees = registry.functions.asSequence()
            .flatMap { sequenceOf(it.returnType) + it.parameters.asSequence().map(SDLParameter::type) }
            .filterIsInstance<SDLType.Pointer>()
            .mapNotNull(SDLType.Pointer::pointee)
        val fieldPointees = SDLWindowsX64Layouts.records.asSequence()
            .flatMap { it.fields.asSequence() }
            .mapNotNull(SDLFieldLayout::pointerType)
            .flatMap { Regex("SDL_[A-Za-z0-9_]+").findAll(it).map(MatchResult::value) }
        return (functionPointees + fieldPointees)
            .filter { it.startsWith("SDL_") }
            .filterNot { it in SDLWindowsX64Layouts.byName || it == TLS_ID_LAYOUT.name }
            .filterNot { registry.namedTypes[it]?.kind in setOf(SDLNamedKind.ENUM, SDLNamedKind.FUNCTION_POINTER) }
            .toSortedSet()
    }

    private val TLS_ID_LAYOUT = SDLRecordLayout(
        name = "SDL_TLSID",
        kind = SDLRecordKind.STRUCT,
        size = 4,
        alignment = 4,
        fields = listOf(SDLFieldLayout("value", 0, 4, SDLFieldCarrier.INT32, nativeType = "SDL_AtomicInt")),
    )

    private fun escapeIdentifier(name: String): String = if (name in KOTLIN_KEYWORDS) "`$name`" else name

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
        "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true",
        "try", "typealias", "typeof", "val", "var", "when", "while",
    )

    private const val OMNI_SEGMENT_IDENTIFIER = "_\$OMNI_SEGMENT\$_"
}
