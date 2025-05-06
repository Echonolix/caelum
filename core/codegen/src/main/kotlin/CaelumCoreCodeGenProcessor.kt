import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.codegen.api.CaelumCodegenHelper
import net.echonolix.caelum.codegen.api.addSuppress
import net.echonolix.ktgen.KtgenProcessor
import java.nio.file.Path
import java.util.*
import kotlin.reflect.KClass

class CaelumCoreCodeGenProcessor : KtgenProcessor {
    override fun process(inputs: Set<Path>, outputDir: Path): Set<Path> {
        val coreNativeDataTypeFile = FileSpec.builder(CaelumCodegenHelper.basePkgName, "CoreNativeDataTypes")
            .indent("    ")
            .addSuppress()

        buildNativeData(coreNativeDataTypeFile)

        val coreNativeTypesFile = FileSpec.builder(CaelumCodegenHelper.basePkgName, "CoreNativeTypes")
            .indent("    ")
            .addSuppress()

        val coreNativeTypeAccessorsFile = FileSpec.builder(CaelumCodegenHelper.basePkgName, "CoreNativeTypeAccessors")
            .indent("    ")
            .addSuppress()

        CBasicType.entries.forEach { basicType ->
            val typeObject = TypeSpec.objectBuilder(basicType.cName)
            val nPrimitiveCName = CaelumCodegenHelper.NPrimitive.cName.parameterizedBy(
                basicType.nativeDataType.nativeDataType,
                basicType.ktApiType.cName
            )

            typeObject.addSuperinterface(
                CaelumCodegenHelper.NPrimitive.typeObjectCName.parameterizedBy(
                    nPrimitiveCName,
                    basicType.nativeDataType.nativeDataType,
                    basicType.ktApiType.cName,
                )
            )
            typeObject.addSuperinterface(
                basicType.nativeDataType.nNativeDataCName.parameterizedBy(nPrimitiveCName, basicType.ktApiType.cName),
                CodeBlock.of("%T.implOf()", basicType.nativeDataType.nNativeDataCName)
            )
            typeObject.addFunction(
                FunSpec.builder("fromNativeData")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("value", basicType.nativeDataType.nativeDataType)
                    .returns(basicType.ktApiType.cName)
                    .addStatement("return value${basicType.fromNativeData}")
                    .build()
            )
            typeObject.addFunction(
                FunSpec.builder("toNativeData")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("value", basicType.ktApiType.cName)
                    .returns(basicType.nativeDataType.nativeDataType)
                    .addStatement("return value${basicType.toNativeData}")
                    .build()
            )


            coreNativeTypesFile.addType(typeObject.build())

            val validChars = ('0'..'9').toList()
            val random = Random(0)

            fun randomName(base: String) = AnnotationSpec.builder(JvmName::class)
                .addMember(
                    "%S",
                    "${basicType.cName.simpleName}_${base}_${
                        (0..4).map { validChars[random.nextInt(validChars.size)] }.joinToString("")
                    }"
                )
                .build()

            val overloadTypes = listOf(INT, UInt::class.asTypeName(), ULong::class.asTypeName())
            val returnTypeName = basicType.ktApiType.cName

            val arrayCNameP = CaelumCodegenHelper.arrayCName.parameterizedBy(nPrimitiveCName)
            val valueCNameP = CaelumCodegenHelper.valueCName.parameterizedBy(nPrimitiveCName)
            val pointerCNameP = CaelumCodegenHelper.pointerCName.parameterizedBy(nPrimitiveCName)
            val nullableAny = Any::class.asClassName().copy(nullable = true)

            fun addGetOverloads(receiver: TypeName) {
                for (pType in overloadTypes) {
                    coreNativeTypeAccessorsFile.addFunction(
                        FunSpec.builder("get")
                            .addAnnotation(randomName("get"))
                            .receiver(receiver)
                            .addModifiers(KModifier.OPERATOR)
                            .addParameter("index", pType)
                            .returns(returnTypeName)
                            .addStatement("return get(index.toLong())")
                            .build()
                    )
                }
            }

            fun addSetOverloads(receiver: TypeName) {
                for (pType in overloadTypes) {
                    coreNativeTypeAccessorsFile.addFunction(
                        FunSpec.builder("set")
                            .addAnnotation(randomName("set"))
                            .receiver(receiver)
                            .addModifiers(KModifier.OPERATOR)
                            .addParameter("index", pType)
                            .addParameter("value", returnTypeName)
                            .addStatement("set(index.toLong(), value)")
                            .build()
                    )
                }
            }
            coreNativeTypeAccessorsFile.addProperty(
                PropertySpec.builder("value", returnTypeName)
                    .receiver(pointerCNameP)
                    .mutable(true)
                    .getter(
                        FunSpec.getterBuilder()
                            .addAnnotation(randomName("getValue"))
                            .addStatement(
                                "return %T.fromNativeData(%T.pointerGetValue(this))",
                                basicType.cName,
                                basicType.cName
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addAnnotation(randomName("setValue"))
                            .addParameter("value", returnTypeName)
                            .addStatement(
                                "(%T.pointerSetValue(this, %T.toNativeData(value)))",
                                basicType.cName,
                                basicType.cName
                            )
                            .build()
                    )
                    .build()
            )

            coreNativeTypeAccessorsFile.addFunction(
                FunSpec.builder("get")
                    .addAnnotation(randomName("get"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", LONG)
                    .returns(returnTypeName)
                    .addStatement(
                        "return %T.fromNativeData(%T.pointerGetElement(this, index))",
                        basicType.cName,
                        basicType.cName
                    )
                    .build()
            )
            addGetOverloads(pointerCNameP)

            coreNativeTypeAccessorsFile.addFunction(
                FunSpec.builder("set")
                    .addAnnotation(randomName("set"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", LONG)
                    .addParameter("value", returnTypeName)
                    .addStatement(
                        "%T.pointerSetElement(this, index, %T.toNativeData(value))",
                        basicType.cName,
                        basicType.cName
                    )
                    .build()
            )
            addSetOverloads(pointerCNameP)

            coreNativeTypeAccessorsFile.addFunction(
                FunSpec.builder("getValue")
                    .addAnnotation(randomName("getValue"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("thisRef", nullableAny)
                    .addParameter("property", nullableAny)
                    .returns(returnTypeName)
                    .addStatement(
                        "return this.value",
                        basicType.cName,
                        basicType.cName
                    )
                    .build()
            )
            coreNativeTypeAccessorsFile.addFunction(
                FunSpec.builder("setValue")
                    .addAnnotation(randomName("setValue"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("thisRef", nullableAny)
                    .addParameter("property", nullableAny)
                    .addParameter("value", returnTypeName)
                    .addStatement(
                        "this.value = value",
                        basicType.cName,
                        basicType.cName
                    )
                    .build()
            )

            coreNativeTypeAccessorsFile.addProperty(
                PropertySpec.builder("value", basicType.ktApiType.cName)
                    .receiver(valueCNameP)
                    .mutable(true)
                    .getter(
                        FunSpec.getterBuilder()
                            .addAnnotation(randomName("getValue"))
                            .addStatement(
                                "return %T.fromNativeData(%T.valueGetValue(this))",
                                basicType.cName,
                                basicType.cName
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addAnnotation(randomName("setValue"))
                            .addParameter("value", returnTypeName)
                            .addStatement(
                                "%T.valueSetValue(this, %T.toNativeData(value))",
                                basicType.cName,
                                basicType.cName
                            )
                            .build()
                    )
                    .build()
            )
            coreNativeTypeAccessorsFile.addFunction(
                FunSpec.builder("getValue")
                    .addAnnotation(randomName("getValue"))
                    .receiver(valueCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("thisRef", nullableAny)
                    .addParameter("property", nullableAny)
                    .returns(returnTypeName)
                    .addStatement("return this.value")
                    .build()
            )
            coreNativeTypeAccessorsFile.addFunction(
                FunSpec.builder("setValue")
                    .addAnnotation(randomName("setValue"))
                    .receiver(valueCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("thisRef", nullableAny)
                    .addParameter("property", nullableAny)
                    .addParameter("value", returnTypeName)
                    .addStatement("this.value = value")
                    .build()
            )


            coreNativeTypeAccessorsFile.addFunction(
                FunSpec.builder("get")
                    .addAnnotation(randomName("get"))
                    .receiver(arrayCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", LONG)
                    .returns(returnTypeName)
                    .addStatement(
                        "return %T.fromNativeData(%T.arrayGetElement(this, index))",
                        basicType.cName,
                        basicType.cName
                    )
                    .build()
            )
            addGetOverloads(arrayCNameP)
            coreNativeTypeAccessorsFile.addFunction(
                FunSpec.builder("set")
                    .addAnnotation(randomName("set"))
                    .receiver(arrayCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", LONG)
                    .addParameter("value", returnTypeName)
                    .addStatement(
                        "%T.arraySetElement(this, index, %T.toNativeData(value))",
                        basicType.cName,
                        basicType.cName
                    )
                    .build()
            )
            addSetOverloads(arrayCNameP)
        }

        return setOf(
            coreNativeDataTypeFile.build().writeTo(outputDir),
            coreNativeTypesFile.build().writeTo(outputDir),
            coreNativeTypeAccessorsFile.build().writeTo(outputDir)
        )
    }

    private fun buildNativeData(file: FileSpec.Builder) {
        NativeDataType.entries.forEach { nativeBaseType ->
            val typeK = TypeVariableName("K", ANY)
            val typeT = TypeVariableName(
                "T",
                CaelumCodegenHelper.NPrimitive.cName.parameterizedBy(
                    nativeBaseType.nativeDataType,
                    typeK
                )
            )
            val interfaceType = TypeSpec.interfaceBuilder(nativeBaseType.nNativeDataCName)
            interfaceType.addTypeVariable(typeT)
            interfaceType.addTypeVariable(typeK)
            interfaceType.addSuperinterface(
                CaelumCodegenHelper.NPrimitive.nativeDataCName.parameterizedBy(
                    typeT,
                    nativeBaseType.nativeDataType
                )
            )
            interfaceType.addFunction(
                FunSpec.builder("arrayGetElement")
                    .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                    .addParameter("array", CaelumCodegenHelper.arrayCName.parameterizedBy(typeT))
                    .addParameter("index", LONG)
                    .returns(nativeBaseType.nativeDataType)
                    .build()
            )
            interfaceType.addFunction(
                FunSpec.builder("arraySetElement")
                    .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                    .addParameter("array", CaelumCodegenHelper.arrayCName.parameterizedBy(typeT))
                    .addParameter("index", LONG)
                    .addParameter("value", nativeBaseType.nativeDataType)
                    .build()
            )
            interfaceType.addFunction(
                FunSpec.builder("pointerGetElement")
                    .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                    .addParameter(
                        "pointer",
                        CaelumCodegenHelper.pointerCName.parameterizedBy(typeT)
                    )
                    .addParameter("index", LONG)
                    .returns(nativeBaseType.nativeDataType)
                    .build()
            )
            interfaceType.addFunction(
                FunSpec.builder("pointerSetElement")
                    .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                    .addParameter(
                        "pointer",
                        CaelumCodegenHelper.pointerCName.parameterizedBy(typeT)
                    )
                    .addParameter("index", LONG)
                    .addParameter("value", nativeBaseType.nativeDataType)
                    .build()
            )
            interfaceType.addFunction(
                FunSpec.builder("valueGetValue")
                    .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                    .addParameter("value", CaelumCodegenHelper.valueCName.parameterizedBy(typeT))
                    .returns(nativeBaseType.nativeDataType)
                    .build()
            )
            interfaceType.addFunction(
                FunSpec.builder("valueSetValue")
                    .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                    .addParameter("value", CaelumCodegenHelper.valueCName.parameterizedBy(typeT))
                    .addParameter("newValue", nativeBaseType.nativeDataType)
                    .build()
            )
            interfaceType.addFunction(
                FunSpec.builder("pointerGetValue")
                    .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                    .addParameter(
                        "pointer",
                        CaelumCodegenHelper.pointerCName.parameterizedBy(typeT)
                    )
                    .returns(nativeBaseType.nativeDataType)
                    .build()
            )
            interfaceType.addFunction(
                FunSpec.builder("pointerSetValue")
                    .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                    .addParameter(
                        "pointer",
                        CaelumCodegenHelper.pointerCName.parameterizedBy(typeT)
                    )
                    .addParameter("newValue", nativeBaseType.nativeDataType)
                    .build()
            )
            val implNPrimitive = CaelumCodegenHelper.NPrimitive.cName.parameterizedBy(
                nativeBaseType.nativeDataType,
                ANY
            )
            val implCompanionObject = TypeSpec.companionObjectBuilder()
            implCompanionObject.superclass(
                CaelumCodegenHelper.NPrimitive.nativeDataImplCName.parameterizedBy(
                    implNPrimitive,
                    nativeBaseType.nativeDataType
                )
            )
            implCompanionObject.addSuperinterface(nativeBaseType.nNativeDataCName.parameterizedBy(implNPrimitive, ANY))
            implCompanionObject.addSuperclassConstructorParameter(
                "%T.%N",
                CaelumCodegenHelper.valueLayoutCName,
                nativeBaseType.valueLayoutName
            )
            implCompanionObject.addFunction(
                FunSpec.builder("arrayGetElement")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("array", CaelumCodegenHelper.arrayCName.parameterizedBy(implNPrimitive))
                    .addParameter("index", LONG)
                    .returns(nativeBaseType.nativeDataType)
                    .addStatement(
                        "return arrayVarHandle.get(array.segment, 0L, index) as %T",
                        nativeBaseType.nativeDataType
                    )
                    .build()
            )
            implCompanionObject.addFunction(
                FunSpec.builder("arraySetElement")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("array", CaelumCodegenHelper.arrayCName.parameterizedBy(implNPrimitive))
                    .addParameter("index", LONG)
                    .addParameter("value", nativeBaseType.nativeDataType)
                    .addStatement(
                        "arrayVarHandle.set(array.segment, 0L, index, value)",
                        CaelumCodegenHelper.omniSegment
                    )
                    .build()
            )
            implCompanionObject.addFunction(
                FunSpec.builder("pointerGetElement")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter(
                        "pointer",
                        CaelumCodegenHelper.pointerCName.parameterizedBy(implNPrimitive)
                    )
                    .addParameter("index", LONG)
                    .returns(nativeBaseType.nativeDataType)
                    .addStatement(
                        "return arrayVarHandle.get(%M, pointer._address, index) as %T",
                        CaelumCodegenHelper.omniSegment,
                        nativeBaseType.nativeDataType
                    )
                    .build()
            )
            implCompanionObject.addFunction(
                FunSpec.builder("pointerSetElement")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter(
                        "pointer",
                        CaelumCodegenHelper.pointerCName.parameterizedBy(implNPrimitive)
                    )
                    .addParameter("index", LONG)
                    .addParameter("value", nativeBaseType.nativeDataType)
                    .addStatement(
                        "arrayVarHandle.set(%M, pointer._address, index, value)",
                        CaelumCodegenHelper.omniSegment
                    )
                    .build()
            )
            implCompanionObject.addFunction(
                FunSpec.builder("valueGetValue")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("value", CaelumCodegenHelper.valueCName.parameterizedBy(implNPrimitive))
                    .returns(nativeBaseType.nativeDataType)
                    .addStatement(
                        "return valueVarHandle.get(value.segment, 0L) as %T",
                        nativeBaseType.nativeDataType
                    )
                    .build()
            )
            implCompanionObject.addFunction(
                FunSpec.builder("valueSetValue")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("value", CaelumCodegenHelper.valueCName.parameterizedBy(implNPrimitive))
                    .addParameter("newValue", nativeBaseType.nativeDataType)
                    .addStatement(
                        "valueVarHandle.set(value.segment, 0L, newValue)"
                    )
                    .build()
            )
            implCompanionObject.addFunction(
                FunSpec.builder("pointerGetValue")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter(
                        "pointer",
                        CaelumCodegenHelper.pointerCName.parameterizedBy(implNPrimitive)
                    )
                    .returns(nativeBaseType.nativeDataType)
                    .addStatement(
                        "return valueVarHandle.get(%M, pointer._address) as %T",
                        CaelumCodegenHelper.omniSegment,
                        nativeBaseType.nativeDataType
                    )
                    .build()
            )
            implCompanionObject.addFunction(
                FunSpec.builder("pointerSetValue")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter(
                        "pointer",
                        CaelumCodegenHelper.pointerCName.parameterizedBy(implNPrimitive)
                    )
                    .addParameter("newValue", nativeBaseType.nativeDataType)
                    .addStatement(
                        "valueVarHandle.set(%M, pointer._address, newValue)",
                        CaelumCodegenHelper.omniSegment
                    )
                    .build()
            )
            val implOfTypeName = nativeBaseType.nNativeDataCName.parameterizedBy(typeT, typeK)
            implCompanionObject.addFunction(
                FunSpec.builder("implOf")
                    .addAnnotation(JvmStatic::class)
                    .addAnnotation(
                        AnnotationSpec.builder(Suppress::class)
                            .addMember("%S", "UNCHECKED_CAST")
                            .build()
                    )
                    .addTypeVariable(typeT)
                    .addTypeVariable(typeK)
                    .returns(implOfTypeName)
                    .addStatement("return this as %T", implOfTypeName)
                    .build()
            )
            interfaceType.addType(implCompanionObject.build())
            file.addType(interfaceType.build()).build()
        }
    }

    private enum class NativeDataType(
        val nativeDataType: ClassName,
        val valueLayoutName: String,
    ) {
        Byte(BYTE, "JAVA_BYTE"),
        Short(SHORT, "JAVA_SHORT"),
        Int(INT, "JAVA_INT"),
        Long(LONG, "JAVA_LONG"),
        Float(FLOAT, "JAVA_FLOAT"),
        Double(DOUBLE, "JAVA_DOUBLE"),
        Char(CHAR, "JAVA_CHAR"),
        Boolean(BOOLEAN, "JAVA_BOOLEAN");

        val nNativeDataCName = ClassName(CaelumCodegenHelper.basePkgName, "N${name}NativeData")
        val nNativeDataCNameImplCName = nNativeDataCName.nestedClass("Impl")
    }

    @Suppress("RemoveRedundantQualifierName")
    private enum class KotlinPrimitiveType(ktApiTypeClass: KClass<*>) {
        Byte(kotlin.Byte::class),
        UByte(kotlin.UByte::class),
        Short(kotlin.Short::class),
        UShort(kotlin.UShort::class),
        Int(kotlin.Int::class),
        UInt(kotlin.UInt::class),
        Long(kotlin.Long::class),
        ULong(kotlin.ULong::class),
        Float(kotlin.Float::class),
        Double(kotlin.Double::class),
        Char(kotlin.Char::class),
        Boolean(kotlin.Boolean::class), ;

        val cName: TypeName = ktApiTypeClass.asClassName()
    }

    private enum class CBasicType(
        val ktApiType: KotlinPrimitiveType,
        val nativeDataType: NativeDataType,
        val fromNativeData: String = "",
        val toNativeData: String = "",
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

        val cName = ClassName(CaelumCodegenHelper.basePkgName, name)
    }
}