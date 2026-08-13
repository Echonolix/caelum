package net.echonolix.caelum.dxgi.api

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout
import java.util.concurrent.ConcurrentHashMap

/** Strict Windows-x64 C type to JDK 24 FFM layout resolver. */
public class NativeTypeParser(
    public val catalog: NativeApiCatalog,
    public val dependencies: List<NativeApiCatalog> = emptyList(),
) {
    private val resolutionStack: ThreadLocal<MutableSet<String>> = ThreadLocal.withInitial(::mutableSetOf)
    private val failures: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Types which failed strict resolution through this parser instance. */
    public val unresolvedTypes: Set<String>
        get() = failures.toSet()

    public fun layout(type: String): MemoryLayout = try {
        resolve(normalize(type))
    } catch (error: NativeTypeResolutionException) {
        failures += error.nativeType
        throw error
    }

    public fun functionDescriptor(function: NativeFunctionDeclaration): FunctionDescriptor =
        descriptor(function.name, function.returnType, function.parameters, function.type, comMethod = false)

    public fun functionDescriptor(method: NativeMethodDeclaration): FunctionDescriptor =
        descriptor(method.name, method.returnType, method.parameters, method.type, comMethod = true)

    /** Reads one unsigned C bit-field from an enclosing record segment. */
    public fun readUnsignedBitField(
        segment: java.lang.foreign.MemorySegment,
        recordName: String,
        fieldName: String,
    ): Long {
        val (field, carrier) = bitField(recordName, fieldName)
        val bitOffset = requireNotNull(field.offsetBits)
        val carrierBits = carrier.byteSize() * 8L
        val carrierOffset = bitOffset / carrierBits * carrier.byteSize()
        val shift = (bitOffset % carrierBits).toInt()
        val mask = if (field.bitWidth == 64L) -1L else (1L shl requireNotNull(field.bitWidth).toInt()) - 1L
        return (readCarrier(segment, carrierOffset, carrier).ushr(shift)) and mask
    }

    /** Replaces one unsigned C bit-field without modifying adjacent fields in the same storage unit. */
    public fun writeUnsignedBitField(
        segment: java.lang.foreign.MemorySegment,
        recordName: String,
        fieldName: String,
        value: Long,
    ) {
        val (field, carrier) = bitField(recordName, fieldName)
        val width = requireNotNull(field.bitWidth).toInt()
        val valueMask = if (width == 64) -1L else (1L shl width) - 1L
        require(value and valueMask == value) { "$recordName.$fieldName value $value exceeds its $width-bit unsigned range" }
        val bitOffset = requireNotNull(field.offsetBits)
        val carrierBits = carrier.byteSize() * 8L
        val carrierOffset = bitOffset / carrierBits * carrier.byteSize()
        val shift = (bitOffset % carrierBits).toInt()
        val shiftedMask = valueMask shl shift
        val updated = (readCarrier(segment, carrierOffset, carrier) and shiftedMask.inv()) or (value shl shift)
        writeCarrier(segment, carrierOffset, carrier, updated)
    }

    private fun descriptor(
        name: String,
        declaredReturnType: String?,
        parameters: List<NativeParameterDeclaration>,
        functionType: String,
        comMethod: Boolean,
    ): FunctionDescriptor {
        val signature = if (declaredReturnType == null || parameters.isEmpty() && hasArguments(functionType)) {
            parseFunctionPointer(functionType, name)
        } else {
            ParsedSignature(declaredReturnType, parameters.map { it.type })
        }
        val returnType = signature.returnType.trim()
        val parameterLayouts = signature.parameterTypes.map(::layout).toTypedArray()
        if (comMethod && parameterLayouts.isEmpty()) {
            throw NativeTypeResolutionException(functionType, "COM method $name has no explicit This parameter")
        }
        return if (normalize(returnType) == "void") {
            FunctionDescriptor.ofVoid(*parameterLayouts)
        } else {
            FunctionDescriptor.of(layout(returnType), *parameterLayouts)
        }
    }

    private fun resolve(type: String): MemoryLayout {
        if (isPointer(type)) return ValueLayout.ADDRESS
        parseArray(type)?.let { (elementType, count) -> return MemoryLayout.sequenceLayout(count, layout(elementType)) }
        primitive(type)?.let { return it }

        val named = removeTag(type)
        findRecord(named)?.let { return recordLayout(it) }
        findEnum(named)?.let { declaration ->
            return layout(declaration.underlyingType ?: "int")
        }
        val alias = findTypedef(named)
        if (alias == null) {
            inferExternalEnum(named)?.let { return it }
            throw NativeTypeResolutionException(type, "Unmapped native type '$type' in ${catalog.api} schema")
        }
        val resolving = resolutionStack.get()
        if (!resolving.add(named)) {
            throw NativeTypeResolutionException(type, "Cyclic typedef while resolving '$named'")
        }
        return try {
            layout(alias.canonicalType?.takeUnless { normalize(it) == named } ?: alias.type)
        } finally {
            resolving.remove(named)
        }
    }

    private fun inferExternalEnum(name: String): MemoryLayout? {
        val canonicalEvidence = (listOf(catalog) + dependencies).asSequence()
            .flatMap { it.declarations.records.asSequence() }
            .flatMap { it.fields.asSequence() }
            .any { it.type == name && normalize(it.canonicalType.orEmpty()) == "enum $name" }
        return if (canonicalEvidence) ValueLayout.JAVA_INT else null
    }

    private fun findRecord(name: String): NativeRecordDeclaration? =
        compatibleDeclaration(name, "record") { it.records[name] }

    private fun findEnum(name: String): NativeEnumDeclaration? =
        compatibleDeclaration(name, "enum") { it.enums[name] }

    private fun findTypedef(name: String): NativeTypedefDeclaration? =
        compatibleDeclaration(name, "typedef") { it.typedefs[name] }

    private fun <T> compatibleDeclaration(
        name: String,
        kind: String,
        lookup: (NativeApiCatalog) -> T?,
    ): T? {
        val matches = (listOf(catalog) + dependencies).mapNotNull { source -> lookup(source)?.let { source.api to it } }
        if (matches.size > 1) {
            val first = matches.first().second
            if (matches.drop(1).any { !canonicallyCompatible(first, it.second) }) {
                throw NativeTypeResolutionException(
                    name,
                    "Conflicting $kind '$name' definitions across ${matches.joinToString { it.first }}",
                )
            }
        }
        return matches.firstOrNull()?.second
    }

    private fun <T> canonicallyCompatible(left: T, right: T): Boolean = when {
        left is NativeRecordDeclaration && right is NativeRecordDeclaration ->
            left.copy(header = null, sourceLine = null) == right.copy(header = null, sourceLine = null)
        left is NativeEnumDeclaration && right is NativeEnumDeclaration ->
            left.copy(header = null, sourceLine = null) == right.copy(header = null, sourceLine = null)
        left is NativeTypedefDeclaration && right is NativeTypedefDeclaration ->
            left.copy(header = null, sourceLine = null) == right.copy(header = null, sourceLine = null)
        else -> left == right
    }

    private fun recordLayout(record: NativeRecordDeclaration): MemoryLayout {
        val resolving = resolutionStack.get()
        if (!resolving.add("record:${record.name}")) {
            throw NativeTypeResolutionException(record.name, "Recursive by-value record '${record.name}'")
        }
        try {
            val size = record.size ?: throw NativeTypeResolutionException(
                record.name,
                "Record '${record.name}' has no audited size in the schema; refusing to infer ABI layout",
            )
            val alignment = record.alignment ?: throw NativeTypeResolutionException(
                record.name,
                "Record '${record.name}' has no audited alignment in the schema; refusing to infer ABI layout",
            )
            if (record.fields.any { it.offsetBits == null }) {
                throw NativeTypeResolutionException(
                    record.name,
                    "Record '${record.name}' has fields without audited offsets; refusing to infer ABI layout",
                )
            }
            if (record.fields.any { it.name == null && it.anonymousRecord == null }) {
                throw NativeTypeResolutionException(record.name, "Record '${record.name}' contains an unresolved anonymous member")
            }
            if (size == 0L) {
                throw NativeTypeResolutionException(record.name, "Incomplete record '${record.name}' has no FFM layout")
            }
            val members = mutableListOf<MemoryLayout>()
            if (record.kind == NativeRecordKind.UNION) {
                for (field in record.fields) {
                    val fieldLayout = fieldLayout(record, field)
                    if (fieldLayout.byteSize() > size) {
                        throw NativeTypeResolutionException(field.type, "Field ${record.name}.${field.name} exceeds union size")
                    }
                    members += fieldLayout.withName(field.name ?: requireNotNull(field.anonymousRecord).name)
                }
                if (members.isEmpty() || members.maxOf { it.byteSize() } < size) {
                    members += MemoryLayout.sequenceLayout(size, ValueLayout.JAVA_BYTE)
                }
                return MemoryLayout.unionLayout(*members.toTypedArray()).withByteAlignment(alignment)
            }

            var cursor = 0L
            var syntheticIndex = 0
            for (field in record.fields.sortedBy { it.offsetBits!! }) {
                val offsetBits = requireNotNull(field.offsetBits)
                if (field.bitWidth == null && offsetBits % 8L != 0L) {
                    throw NativeTypeResolutionException(field.type, "Field ${record.name}.${field.name} has non-byte offset ${field.offsetBits}")
                }
                val offset = offsetBits / 8L
                if (field.bitWidth != null) {
                    val carrier = bitFieldCarrier(record, field)
                    val storageStart = offset - (offset % carrier.byteSize())
                    if (storageStart < cursor) continue
                    if (storageStart > cursor) members += MemoryLayout.paddingLayout(storageStart - cursor)
                    members += carrier.withName("${field.name ?: "anonymous"}\$bits${syntheticIndex++}")
                    cursor = storageStart + carrier.byteSize()
                    continue
                }
                if (offset < cursor) {
                    throw NativeTypeResolutionException(field.type, "Field ${record.name}.${field.name} overlaps a prior struct field")
                }
                if (offset > cursor) members += MemoryLayout.paddingLayout(offset - cursor)
                var fieldLayout = fieldLayout(record, field)
                val permittedAlignment = alignmentAtOffset(offset, fieldLayout.byteAlignment())
                if (permittedAlignment < fieldLayout.byteAlignment()) {
                    fieldLayout = fieldLayout.withByteAlignment(permittedAlignment)
                }
                fieldLayout = fieldLayout.withName(field.name ?: requireNotNull(field.anonymousRecord).name)
                members += fieldLayout
                cursor = offset + fieldLayout.byteSize()
            }
            if (cursor > size) {
                throw NativeTypeResolutionException(record.name, "Fields exceed declared size $size for ${record.name}")
            }
            if (cursor < size) members += MemoryLayout.paddingLayout(size - cursor)
            return MemoryLayout.structLayout(*members.toTypedArray()).withByteAlignment(alignment)
        } finally {
            resolving.remove("record:${record.name}")
        }
    }

    private fun fieldLayout(record: NativeRecordDeclaration, field: NativeRecordField): MemoryLayout =
        field.anonymousRecord?.let(::recordLayout) ?: layout(field.type)

    private fun bitFieldCarrier(record: NativeRecordDeclaration, field: NativeRecordField): MemoryLayout {
        val width = requireNotNull(field.bitWidth)
        val offset = requireNotNull(field.offsetBits)
        val carrier = layout(field.type)
        if (carrier !is ValueLayout || width <= 0 || width > carrier.byteSize() * 8 ||
            offset % (carrier.byteSize() * 8) + width > carrier.byteSize() * 8
        ) {
            throw NativeTypeResolutionException(
                field.type,
                "Bit-field ${record.name}.${field.name} does not fit its declared ${field.type} storage unit",
            )
        }
        return carrier
    }

    private fun bitField(recordName: String, fieldName: String): Pair<NativeRecordField, ValueLayout> {
        val record = findRecord(removeTag(recordName))
            ?: throw NativeTypeResolutionException(recordName, "Unknown record '$recordName'")
        val field = record.fields.firstOrNull { it.name == fieldName }
            ?: throw NativeTypeResolutionException(recordName, "Record '$recordName' has no field '$fieldName'")
        require(field.bitWidth != null) { "$recordName.$fieldName is not a bit-field" }
        val carrier = bitFieldCarrier(record, field) as? ValueLayout
            ?: throw NativeTypeResolutionException(field.type, "Bit-field carrier '${field.type}' is not a scalar value layout")
        return field to carrier
    }

    private fun readCarrier(segment: java.lang.foreign.MemorySegment, offset: Long, carrier: ValueLayout): Long =
        when (carrier.byteSize()) {
            1L -> segment.get(ValueLayout.JAVA_BYTE, offset).toLong() and 0xffL
            2L -> segment.get(ValueLayout.JAVA_SHORT, offset).toLong() and 0xffffL
            4L -> segment.get(ValueLayout.JAVA_INT, offset).toLong() and 0xffff_ffffL
            8L -> segment.get(ValueLayout.JAVA_LONG, offset)
            else -> throw NativeTypeResolutionException(carrier.toString(), "Unsupported bit-field carrier width ${carrier.byteSize()}")
        }

    private fun writeCarrier(segment: java.lang.foreign.MemorySegment, offset: Long, carrier: ValueLayout, value: Long) {
        when (carrier.byteSize()) {
            1L -> segment.set(ValueLayout.JAVA_BYTE, offset, value.toByte())
            2L -> segment.set(ValueLayout.JAVA_SHORT, offset, value.toShort())
            4L -> segment.set(ValueLayout.JAVA_INT, offset, value.toInt())
            8L -> segment.set(ValueLayout.JAVA_LONG, offset, value)
            else -> throw NativeTypeResolutionException(carrier.toString(), "Unsupported bit-field carrier width ${carrier.byteSize()}")
        }
    }

    private fun alignmentAtOffset(offset: Long, naturalAlignment: Long): Long {
        if (offset == 0L) return naturalAlignment
        val offsetAlignment = java.lang.Long.lowestOneBit(offset)
        return minOf(naturalAlignment, offsetAlignment)
    }

    private fun primitive(type: String): MemoryLayout? = when (removeTag(type)) {
        "HANDLE", "SC_HANDLE", "HMODULE", "HINSTANCE", "HWND", "HMONITOR", "HDC", "HICON", "HCURSOR", "HBRUSH", "HMENU", "HGLOBAL", "HLOCAL", "HKEY", "LPVOID", "LPCVOID", "PVOID", "PCVOID", "LPSTR", "LPCSTR", "PSTR", "PCSTR", "LPWSTR", "LPCWSTR", "PWSTR", "PCWSTR", "BSTR", "REFGUID", "REFIID", "REFCLSID", "LPD3D10INCLUDE" -> ValueLayout.ADDRESS
        "_Bool", "bool", "BOOLEAN", "BYTE", "UCHAR", "CHAR", "char", "INT8", "UINT8", "__int8", "unsigned __int8", "int8_t", "uint8_t", "signed char", "unsigned char" -> ValueLayout.JAVA_BYTE
        "SHORT", "INT16", "__int16", "int16_t", "short", "short int", "signed short", "signed short int" -> ValueLayout.JAVA_SHORT
        "USHORT", "UINT16", "WORD", "WCHAR", "wchar_t", "unsigned __int16", "uint16_t", "unsigned short", "unsigned short int" -> ValueLayout.JAVA_SHORT
        "FLOAT", "float" -> ValueLayout.JAVA_FLOAT
        "DOUBLE", "double", "long double" -> ValueLayout.JAVA_DOUBLE
        "INT", "LONG", "HRESULT", "APP_DEPRECATED_HRESULT", "BOOL", "NTSTATUS", "INT32", "__int32", "int", "signed", "signed int", "long", "long int", "signed long", "signed long int", "int32_t" -> ValueLayout.JAVA_INT
        "UINT", "ULONG", "DWORD", "LCID", "LCTYPE", "COLORREF", "UINT32", "unsigned __int32", "unsigned", "unsigned int", "unsigned long", "unsigned long int", "uint32_t" -> ValueLayout.JAVA_INT
        "LONGLONG", "LONG64", "INT64", "__int64", "long long", "long long int", "signed long long", "signed long long int", "int64_t" -> ValueLayout.JAVA_LONG
        "ULONGLONG", "DWORDLONG", "ULONG64", "DWORD64", "UINT64", "SIZE_T", "SSIZE_T", "INT_PTR", "UINT_PTR", "LONG_PTR", "ULONG_PTR", "WPARAM", "LPARAM", "LRESULT", "unsigned __int64", "unsigned long long", "unsigned long long int", "uint64_t", "size_t", "intptr_t", "uintptr_t" -> ValueLayout.JAVA_LONG
        "POINT", "SIZE" -> MemoryLayout.structLayout(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT)
        "GUID", "_GUID", "IID", "CLSID" -> MemoryLayout.structLayout(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_SHORT,
            ValueLayout.JAVA_SHORT,
            MemoryLayout.sequenceLayout(8, ValueLayout.JAVA_BYTE),
        ).withByteAlignment(4)
        "LUID" -> MemoryLayout.structLayout(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT).withByteAlignment(4)
        "LARGE_INTEGER", "ULARGE_INTEGER" -> MemoryLayout.unionLayout(
            ValueLayout.JAVA_LONG,
            MemoryLayout.structLayout(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
        ).withByteAlignment(8)
        "RECT", "tagRECT" -> MemoryLayout.structLayout(
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
        ).withByteAlignment(4)
        "D3D_VERSION_NUMBER" -> MemoryLayout.unionLayout(
            ValueLayout.JAVA_LONG,
            MemoryLayout.structLayout(
                ValueLayout.JAVA_SHORT,
                ValueLayout.JAVA_SHORT,
                ValueLayout.JAVA_SHORT,
                ValueLayout.JAVA_SHORT,
            ),
        ).withByteAlignment(8)
        else -> null
    }

    private fun normalize(source: String): String = source
        .replace(Regex("\\b(const|volatile|restrict|__restrict|__restrict__)\\b"), " ")
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s*\\*\\s*"), " *")
        .replace(Regex("\\s*\\[\\s*"), "[")
        .replace(Regex("\\s*]"), "]")
        .trim()

    private fun removeTag(type: String): String = type
        .removePrefix("struct ")
        .removePrefix("union ")
        .removePrefix("enum ")
        .trim()

    private fun isPointer(type: String): Boolean = type.endsWith("*") ||
        type.contains("(*)") ||
        type.contains(Regex("\\(\\s*[*]\\s*[^)]*\\)"))

    private fun parseArray(type: String): Pair<String, Long>? {
        val match = ARRAY.matchEntire(type) ?: return null
        val count = match.groupValues[2].toLongOrNull()
            ?: throw NativeTypeResolutionException(type, "Array bound is not an unsigned decimal integer: '$type'")
        return match.groupValues[1].trim() to count
    }

    private fun parseFunctionPointer(type: String, name: String): ParsedSignature {
        val cleaned = type.replace(Regex("__attribute__\\s*\\(\\(.*?\\)\\)"), "").trim()
        val marker = cleaned.indexOf("(*")
        if (marker < 1) throw NativeTypeResolutionException(type, "Cannot parse function signature for $name")
        val parameterStart = cleaned.indexOf('(', marker + 2)
        val parameterEnd = cleaned.lastIndexOf(')')
        if (parameterStart < 0 || parameterEnd <= parameterStart) {
            throw NativeTypeResolutionException(type, "Cannot parse parameter list for $name")
        }
        val parameters = splitTopLevel(cleaned.substring(parameterStart + 1, parameterEnd))
            .filterNot { normalize(it) == "void" || it.isBlank() }
        return ParsedSignature(cleaned.substring(0, marker).trim(), parameters)
    }

    private fun splitTopLevel(source: String): List<String> {
        var parentheses = 0
        var brackets = 0
        var start = 0
        val result = mutableListOf<String>()
        for (index in source.indices) {
            when (source[index]) {
                '(' -> parentheses++
                ')' -> parentheses--
                '[' -> brackets++
                ']' -> brackets--
                ',' -> if (parentheses == 0 && brackets == 0) {
                    result += source.substring(start, index).trim()
                    start = index + 1
                }
            }
        }
        result += source.substring(start).trim()
        return result
    }

    private fun hasArguments(type: String): Boolean = !type.contains(Regex("\\(\\s*void\\s*\\)"))

    private data class ParsedSignature(val returnType: String, val parameterTypes: List<String>)

    private companion object {
        val ARRAY: Regex = Regex("^(.+)\\[([0-9]+)]$")
    }
}
