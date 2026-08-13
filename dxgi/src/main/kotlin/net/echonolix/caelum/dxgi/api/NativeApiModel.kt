package net.echonolix.caelum.dxgi.api

import java.lang.foreign.FunctionDescriptor

/** A value retained from the non-declaration portions of a native API schema. */
public sealed interface NativeSchemaValue

public data class NativeSchemaObject(
    public val properties: Map<String, NativeSchemaValue>,
) : NativeSchemaValue {
    public operator fun get(name: String): NativeSchemaValue? = properties[name]
}

public data class NativeSchemaArray(
    public val elements: List<NativeSchemaValue>,
) : NativeSchemaValue

public data class NativeSchemaString(public val value: String) : NativeSchemaValue

/** JSON numbers are retained losslessly because macro values can exceed signed 64-bit ranges. */
public data class NativeSchemaNumber(public val source: String) : NativeSchemaValue

public data class NativeSchemaBoolean(public val value: Boolean) : NativeSchemaValue

public data object NativeSchemaNull : NativeSchemaValue

public data class NativeParameterDeclaration(
    public val name: String,
    public val type: String,
)

public data class NativeMethodDeclaration(
    public val slot: Int,
    public val name: String,
    public val returnType: String?,
    public val parameters: List<NativeParameterDeclaration>,
    public val type: String,
) {
    public fun functionDescriptor(types: NativeTypeParser): FunctionDescriptor =
        types.functionDescriptor(this)
}

public data class NativeInterfaceDeclaration(
    public val name: String,
    public val iid: String?,
    public val parent: String?,
    public val methods: List<NativeMethodDeclaration>,
    public val header: String?,
    public val sourceLine: Int?,
) {
    private val methodsBySlot: Map<Int, NativeMethodDeclaration> = methods.associateBy { it.slot }
    private val methodsByName: Map<String, NativeMethodDeclaration> = methods.associateBy { it.name }

    init {
        require(name.isNotBlank()) { "Native interface name must not be blank" }
        require(methods.all { it.slot >= 0 }) { "$name contains a negative vtable slot" }
        require(methodsBySlot.size == methods.size) { "$name contains duplicate vtable slots" }
        require(methodsByName.size == methods.size) { "$name contains duplicate method names" }
    }

    /** Total number of slots described by the flattened C vtable. */
    public val vtableSize: Int = methods.maxOfOrNull { it.slot + 1 } ?: 0

    public fun method(slot: Int): NativeMethodDeclaration? = methodsBySlot[slot]

    public fun method(name: String): NativeMethodDeclaration? = methodsByName[name]

    public fun requireMethod(slot: Int): NativeMethodDeclaration = method(slot)
        ?: throw NoSuchElementException("$this has no method at vtable slot $slot")

    public fun requireMethod(name: String): NativeMethodDeclaration = method(name)
        ?: throw NoSuchElementException("$this has no method named $name")
}

public data class NativeEnumEntry(
    public val name: String,
    public val value: String,
)

public data class NativeEnumDeclaration(
    public val name: String,
    public val underlyingType: String?,
    public val entries: List<NativeEnumEntry>,
    public val header: String?,
    public val sourceLine: Int?,
)

public enum class NativeRecordKind {
    STRUCT,
    UNION,
}

public data class NativeRecordField(
    /** Null for an anonymous struct/union member retained by Clang. */
    public val name: String?,
    public val type: String,
    public val canonicalType: String? = null,
    public val offsetBits: Long?,
    public val bitWidth: Long?,
    public val anonymousRecord: NativeRecordDeclaration? = null,
)

public data class NativeRecordDeclaration(
    public val name: String,
    public val kind: NativeRecordKind,
    public val size: Long?,
    public val alignment: Long?,
    public val fields: List<NativeRecordField>,
    public val header: String?,
    public val sourceLine: Int?,
) {
    init {
        require(name.isNotBlank()) { "Native record name must not be blank" }
        require(size == null || size >= 0L) { "$name has a negative byte size" }
        require(alignment == null || alignment > 0L && alignment.countOneBits() == 1) {
            "$name has invalid byte alignment $alignment"
        }
        require(size == null || alignment == null || size == 0L || size % alignment == 0L) {
            "$name size $size is not a multiple of alignment $alignment"
        }
    }
}

public data class NativeTypedefDeclaration(
    public val name: String,
    public val type: String,
    public val canonicalType: String?,
    public val header: String?,
    public val sourceLine: Int?,
)

public data class NativeFunctionDeclaration(
    public val name: String,
    public val returnType: String?,
    public val parameters: List<NativeParameterDeclaration>,
    public val type: String,
    public val dll: String?,
    public val header: String?,
    public val sourceLine: Int?,
) {
    public fun functionDescriptor(types: NativeTypeParser): FunctionDescriptor =
        types.functionDescriptor(this)
}

public data class NativeConstantDeclaration(
    public val name: String,
    public val type: String?,
    public val value: String?,
    public val valueText: String?,
    public val header: String?,
    public val sourceLine: Int?,
)

public data class NativeReviewedExclusion(
    public val pattern: String,
    public val reason: String,
)

public data class NativeApiDeclarations(
    public val interfaces: List<NativeInterfaceDeclaration>,
    public val enums: List<NativeEnumDeclaration>,
    public val records: List<NativeRecordDeclaration>,
    public val typedefs: List<NativeTypedefDeclaration>,
    public val functions: List<NativeFunctionDeclaration>,
    public val constants: List<NativeConstantDeclaration>,
)

public class NativeSchemaException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

public class NativeApiResourceException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

public class NativeTypeResolutionException(
    public val nativeType: String,
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)
