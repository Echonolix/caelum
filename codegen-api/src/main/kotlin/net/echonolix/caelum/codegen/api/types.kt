package net.echonolix.caelum.codegen.api

import com.squareup.kotlinpoet.*
import net.echonolix.caelum.codegen.api.ctx.CodegenContext

public enum class NativeDataType(
    public val nativeDataType: ClassName,
    public val nativeDataArrayType: ClassName,
    public val valueLayoutName: String,
) {
    Byte(BYTE, BYTE_ARRAY, "JAVA_BYTE"),
    Short(SHORT, SHORT_ARRAY, "JAVA_SHORT"),
    Int(INT, INT_ARRAY, "JAVA_INT"),
    Long(LONG, LONG_ARRAY, "JAVA_LONG"),
    Float(FLOAT, FLOAT_ARRAY, "JAVA_FLOAT"),
    Double(DOUBLE, DOUBLE_ARRAY, "JAVA_DOUBLE"),
    Char(CHAR, CHAR_ARRAY, "JAVA_CHAR"),
    Boolean(BOOLEAN, BOOLEAN_ARRAY, "JAVA_BOOLEAN");

    public val nNativeDataCName: ClassName = ClassName(CaelumCodegenHelper.basePkgName, "N${name}NativeData")
    public val nNativeDataCNameImplCName: ClassName = nNativeDataCName.nestedClass("Impl")
}

public enum class KotlinPrimitiveType(public val cName: ClassName) {
    Byte(BYTE),
    UByte(U_BYTE),
    Short(SHORT),
    UShort(U_SHORT),
    Int(INT),
    UInt(U_INT),
    Long(LONG),
    ULong(U_LONG),
    Float(FLOAT),
    Double(DOUBLE),
    Char(CHAR),
    Boolean(BOOLEAN);
}

public enum class CoreNativeTypes(
    public val ktApiType: KotlinPrimitiveType,
    public val nativeDataType: NativeDataType,
    public val fromNativeData: String = "",
    public val toNativeData: String = "",
) {
    NFloat(
        KotlinPrimitiveType.Float,
        NativeDataType.Float
    ),
    NDouble(
        KotlinPrimitiveType.Double,
        NativeDataType.Double
    ),
    NChar(
        KotlinPrimitiveType.Char,
        NativeDataType.Char
    ),
    NInt8(
        KotlinPrimitiveType.Byte,
        NativeDataType.Byte
    ),
    NUInt8(
        KotlinPrimitiveType.UByte,
        NativeDataType.Byte,
        ".toUByte()",
        ".toByte()"
    ),
    NInt16(
        KotlinPrimitiveType.Short,
        NativeDataType.Short
    ),
    NUInt16(
        KotlinPrimitiveType.UShort,
        NativeDataType.Short,
        ".toUShort()",
        ".toShort()"
    ),
    NInt32(
        KotlinPrimitiveType.Int,
        NativeDataType.Int
    ),
    NUInt32(
        KotlinPrimitiveType.UInt,
        NativeDataType.Int,
        ".toUInt()",
        ".toInt()"
    ),
    NInt64(
        KotlinPrimitiveType.Long,
        NativeDataType.Long
    ),
    NUInt64(
        KotlinPrimitiveType.ULong,
        NativeDataType.Long,
        ".toULong()",
        ".toLong()"
    ),
    NBool(
        KotlinPrimitiveType.Boolean,
        NativeDataType.Boolean
    ),
    NBool16(
        KotlinPrimitiveType.Boolean,
        NativeDataType.Short,
        ".toBoolean()",
        ".toShort()"
    ),
    NBool32(
        KotlinPrimitiveType.Boolean,
        NativeDataType.Int,
        ".toBoolean()",
        ".toInt()"
    ),
    NBool64(
        KotlinPrimitiveType.Boolean,
        NativeDataType.Long,
        ".toBoolean()",
        ".toLong()"
    );

    public val cName: ClassName = ClassName(CaelumCodegenHelper.basePkgName, name)
}

context(ctx: CodegenContext)
public inline fun List<CType.Function.Parameter>.toParamSpecs(
    annotations: Boolean,
    typeMapper: (CType.Function.Parameter) -> TypeName
): List<ParameterSpec> = map {
    val builder = ParameterSpec.builder(it.name, typeMapper(it))
    if (annotations) {
        builder.addAnnotation(CaelumCoreAnnotation.cTypeName(it.type.toSimpleString()))
    }
    builder.build()
}