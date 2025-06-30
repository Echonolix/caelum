import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.codegen.api.CaelumCodegenHelper
import net.echonolix.caelum.codegen.api.CoreNativeTypes
import net.echonolix.caelum.codegen.api.NativeDataType
import net.echonolix.caelum.codegen.api.addSuppress
import net.echonolix.ktgen.KtgenProcessor
import java.nio.file.Path
import java.util.*

class CaelumCoreCodeGenProcessor : KtgenProcessor {
    override fun process(inputs: Set<Path>, outputDir: Path): Set<Path> {
        fun FileSpec.Builder.buildAndWrite() = this.build().writeTo(outputDir)

        return setOf(
            buildNativeData().buildAndWrite(),
            buildNativeTypes().buildAndWrite(),
            buildNativeTypeAccessors().buildAndWrite(),
            buildNativeDataPointerAccessorsFile().buildAndWrite(),
        )
    }

    private fun makeFile(name: String): FileSpec.Builder {
        return FileSpec.builder(CaelumCodegenHelper.basePkgName, name)
            .indent("    ")
            .addSuppress()
    }

    private fun buildNativeData(): FileSpec.Builder {
        val file = makeFile("CoreNativeDataTypes")
        NativeDataType.entries.forEach { nativeBaseType ->
            val typeK = TypeVariableName("K", ANY)
            val typeT = TypeVariableName(
                "T",
                CaelumCodegenHelper.NPrimitive.cName.parameterizedBy(
                    nativeBaseType.nativeDataType,
                    typeK
                )
            )
            val outT = WildcardTypeName.producerOf(typeT)
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
                    .addParameter("array", CaelumCodegenHelper.arrayCName.parameterizedBy(outT))
                    .addParameter("index", LONG)
                    .returns(nativeBaseType.nativeDataType)
                    .build()
            )
            interfaceType.addFunction(
                FunSpec.builder("arraySetElement")
                    .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                    .addParameter("array", CaelumCodegenHelper.arrayCName.parameterizedBy(outT))
                    .addParameter("index", LONG)
                    .addParameter("value", nativeBaseType.nativeDataType)
                    .build()
            )
            interfaceType.addFunction(
                FunSpec.builder("pointerGetElement")
                    .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                    .addParameter(
                        "pointer",
                        CaelumCodegenHelper.pointerCName.parameterizedBy(outT)
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
                        CaelumCodegenHelper.pointerCName.parameterizedBy(outT)
                    )
                    .addParameter("index", LONG)
                    .addParameter("value", nativeBaseType.nativeDataType)
                    .build()
            )
            interfaceType.addFunction(
                FunSpec.builder("valueGetValue")
                    .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                    .addParameter("value", CaelumCodegenHelper.valueCName.parameterizedBy(outT))
                    .returns(nativeBaseType.nativeDataType)
                    .build()
            )
            interfaceType.addFunction(
                FunSpec.builder("valueSetValue")
                    .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                    .addParameter("value", CaelumCodegenHelper.valueCName.parameterizedBy(outT))
                    .addParameter("newValue", nativeBaseType.nativeDataType)
                    .build()
            )
            interfaceType.addFunction(
                FunSpec.builder("pointerGetValue")
                    .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                    .addParameter(
                        "pointer",
                        CaelumCodegenHelper.pointerCName.parameterizedBy(outT)
                    )
                    .returns(nativeBaseType.nativeDataType)
                    .build()
            )
            interfaceType.addFunction(
                FunSpec.builder("pointerSetValue")
                    .addModifiers(KModifier.OVERRIDE, KModifier.ABSTRACT)
                    .addParameter(
                        "pointer",
                        CaelumCodegenHelper.pointerCName.parameterizedBy(outT)
                    )
                    .addParameter("newValue", nativeBaseType.nativeDataType)
                    .build()
            )
            val implNPrimitive = CaelumCodegenHelper.NPrimitive.cName.parameterizedBy(
                nativeBaseType.nativeDataType,
                ANY
            )
            val outImplNPrimitive = WildcardTypeName.producerOf(implNPrimitive)
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
                    .addParameter("array", CaelumCodegenHelper.arrayCName.parameterizedBy(outImplNPrimitive))
                    .addParameter("index", LONG)
                    .returns(nativeBaseType.nativeDataType)
                    .addStatement(
                        "return arrayVarHandle.get(%M, array._address, index) as %T",
                        CaelumCodegenHelper.omniSegment,
                        nativeBaseType.nativeDataType
                    )
                    .build()
            )
            implCompanionObject.addFunction(
                FunSpec.builder("arraySetElement")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("array", CaelumCodegenHelper.arrayCName.parameterizedBy(outImplNPrimitive))
                    .addParameter("index", LONG)
                    .addParameter("value", nativeBaseType.nativeDataType)
                    .addStatement(
                        "arrayVarHandle.set(%M, array._address, index, value)",
                        CaelumCodegenHelper.omniSegment
                    )
                    .build()
            )
            implCompanionObject.addFunction(
                FunSpec.builder("pointerGetElement")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter(
                        "pointer",
                        CaelumCodegenHelper.pointerCName.parameterizedBy(outImplNPrimitive)
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
                        CaelumCodegenHelper.pointerCName.parameterizedBy(outImplNPrimitive)
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
                    .addParameter("value", CaelumCodegenHelper.valueCName.parameterizedBy(outImplNPrimitive))
                    .returns(nativeBaseType.nativeDataType)
                    .addStatement(
                        "return valueVarHandle.get(%M, value._address) as %T",
                        CaelumCodegenHelper.omniSegment,
                        nativeBaseType.nativeDataType
                    )
                    .build()
            )
            implCompanionObject.addFunction(
                FunSpec.builder("valueSetValue")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("value", CaelumCodegenHelper.valueCName.parameterizedBy(outImplNPrimitive))
                    .addParameter("newValue", nativeBaseType.nativeDataType)
                    .addStatement(
                        "valueVarHandle.set(%M, value._address, newValue)",
                        CaelumCodegenHelper.omniSegment
                    )
                    .build()
            )
            implCompanionObject.addFunction(
                FunSpec.builder("pointerGetValue")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter(
                        "pointer",
                        CaelumCodegenHelper.pointerCName.parameterizedBy(outImplNPrimitive)
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
                        CaelumCodegenHelper.pointerCName.parameterizedBy(outImplNPrimitive)
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
        return file
    }

    private fun buildNativeTypes(): FileSpec.Builder {
        val file = makeFile("CoreNativeTypes")
        CoreNativeTypes.entries.forEach { basicType ->
            val thisCName = basicType.cName
            val typeObject = TypeSpec.objectBuilder(basicType.cName)

            typeObject.addSuperinterface(
                CaelumCodegenHelper.NPrimitive.typeObjectCName.parameterizedBy(
                    thisCName,
                    basicType.nativeDataType.nativeDataType,
                    basicType.ktApiType.cName,
                )
            )
            typeObject.addSuperinterface(
                basicType.nativeDataType.nNativeDataCName.parameterizedBy(thisCName, basicType.ktApiType.cName),
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

            file.addType(typeObject.build())
        }
        return file
    }

    private fun buildNativeTypeAccessors(): FileSpec.Builder {
        val file = makeFile("CoreNativeTypeAccessors")
        CoreNativeTypes.entries.forEach { basicType ->
            val thisCName = basicType.cName
            val outNPrimitiveType = WildcardTypeName.producerOf(thisCName)

            val validChars = ('0'..'9').toList()
            val random = Random(0)

            fun randomName(base: String) = AnnotationSpec.builder(JvmName::class)
                .addMember(
                    "%S",
                    "${thisCName.simpleName}_${base}_${
                        (0..4).map { validChars[random.nextInt(validChars.size)] }.joinToString("")
                    }"
                )
                .build()

            val returnTypeName = basicType.ktApiType.cName

            val arrayCNameP = CaelumCodegenHelper.arrayCName.parameterizedBy(outNPrimitiveType)
            val valueCNameP = CaelumCodegenHelper.valueCName.parameterizedBy(outNPrimitiveType)
            val pointerCNameP = CaelumCodegenHelper.pointerCName.parameterizedBy(outNPrimitiveType)
            val nullableAny = Any::class.asClassName().copy(nullable = true)

            file.addProperty(
                PropertySpec.builder("value", returnTypeName)
                    .receiver(pointerCNameP)
                    .mutable(true)
                    .getter(
                        FunSpec.getterBuilder()
                            .addAnnotation(randomName("getValue"))
                            .addStatement(
                                "return %T.fromNativeData(%T.pointerGetValue(this))",
                                thisCName,
                                thisCName
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addAnnotation(randomName("setValue"))
                            .addParameter("value", returnTypeName)
                            .addStatement(
                                "(%T.pointerSetValue(this, %T.toNativeData(value)))",
                                thisCName,
                                thisCName
                            )
                            .build()
                    )
                    .build()
            )

            file.addFunction(
                FunSpec.builder("get")
                    .addAnnotation(randomName("get"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", LONG)
                    .returns(returnTypeName)
                    .addStatement(
                        "return %T.fromNativeData(%T.pointerGetElement(this, index))",
                        thisCName,
                        thisCName
                    )
                    .build()
            )

            file.addFunction(
                FunSpec.builder("set")
                    .addAnnotation(randomName("set"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", LONG)
                    .addParameter("value", returnTypeName)
                    .addStatement(
                        "%T.pointerSetElement(this, index, %T.toNativeData(value))",
                        thisCName,
                        thisCName
                    )
                    .build()
            )

            file.addFunction(
                FunSpec.builder("getValue")
                    .addAnnotation(randomName("getValue"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("thisRef", nullableAny)
                    .addParameter("property", nullableAny)
                    .returns(returnTypeName)
                    .addStatement(
                        "return this.value",
                        thisCName,
                        thisCName
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("setValue")
                    .addAnnotation(randomName("setValue"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("thisRef", nullableAny)
                    .addParameter("property", nullableAny)
                    .addParameter("value", returnTypeName)
                    .addStatement(
                        "this.value = value",
                        thisCName,
                        thisCName
                    )
                    .build()
            )

            file.addProperty(
                PropertySpec.builder("value", basicType.ktApiType.cName)
                    .receiver(valueCNameP)
                    .mutable(true)
                    .getter(
                        FunSpec.getterBuilder()
                            .addAnnotation(randomName("getValue"))
                            .addStatement(
                                "return %T.fromNativeData(%T.valueGetValue(this))",
                                thisCName,
                                thisCName
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addAnnotation(randomName("setValue"))
                            .addParameter("value", returnTypeName)
                            .addStatement(
                                "%T.valueSetValue(this, %T.toNativeData(value))",
                                thisCName,
                                thisCName
                            )
                            .build()
                    )
                    .build()
            )
            file.addFunction(
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
            file.addFunction(
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


            file.addFunction(
                FunSpec.builder("get")
                    .addAnnotation(randomName("get"))
                    .receiver(arrayCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", LONG)
                    .returns(returnTypeName)
                    .addStatement(
                        "return %T.fromNativeData(%T.arrayGetElement(this, index))",
                        thisCName,
                        thisCName
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("set")
                    .addAnnotation(randomName("set"))
                    .receiver(arrayCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", LONG)
                    .addParameter("value", returnTypeName)
                    .addStatement(
                        "%T.arraySetElement(this, index, %T.toNativeData(value))",
                        thisCName,
                        thisCName
                    )
                    .build()
            )
        }
        return file
    }

    private fun buildNativeDataPointerAccessorsFile(): FileSpec.Builder {
        val file = makeFile("CoreNativeDataPointerAccessors")

        val validChars = ('0'..'9').toList()
        val random = Random(0)

        fun randomName(base: String) = AnnotationSpec.builder(JvmName::class)
            .addMember(
                "%S",
                "${base}_${
                    (0..4).map { validChars[random.nextInt(validChars.size)] }.joinToString("")
                }"
            )
            .build()

        NativeDataType.entries.forEach { nativeDataType ->
            val typeK = TypeVariableName("K", ANY)
            val typeT = TypeVariableName(
                "T",
                CaelumCodegenHelper.NPrimitive.cName.parameterizedBy(nativeDataType.nativeDataType, typeK)
            )
            val pointerT = CaelumCodegenHelper.pointerCName.parameterizedBy(typeT)
            file.addFunction(
                FunSpec.builder("plus")
                    .addAnnotation(randomName("plus"))
                    .addTypeVariable(typeK)
                    .addTypeVariable(typeT)
                    .addModifiers(KModifier.OPERATOR, KModifier.PUBLIC)
                    .receiver(pointerT)
                    .addParameter("indexDelta", LONG)
                    .returns(pointerT)
                    .addStatement(
                        "return %T(_address + indexDelta * %T.layout.byteSize())",
                        CaelumCodegenHelper.pointerCName,
                        nativeDataType.nNativeDataCName
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("minus")
                    .addAnnotation(randomName("minus"))
                    .addTypeVariable(typeK)
                    .addTypeVariable(typeT)
                    .addModifiers(KModifier.OPERATOR, KModifier.PUBLIC)
                    .receiver(pointerT)
                    .addParameter("indexDelta", LONG)
                    .returns(pointerT)
                    .addStatement(
                        "return %T(_address - indexDelta * %T.layout.byteSize())",
                        CaelumCodegenHelper.pointerCName,
                        nativeDataType.nNativeDataCName
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("inc")
                    .addAnnotation(randomName("inc"))
                    .addTypeVariable(typeK)
                    .addTypeVariable(typeT)
                    .addModifiers(KModifier.OPERATOR, KModifier.PUBLIC)
                    .receiver(pointerT)
                    .returns(pointerT)
                    .addStatement(
                        "return %T(_address + %T.layout.byteSize())",
                        CaelumCodegenHelper.pointerCName,
                        nativeDataType.nNativeDataCName
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("dec")
                    .addAnnotation(randomName("dec"))
                    .addTypeVariable(typeK)
                    .addTypeVariable(typeT)
                    .addModifiers(KModifier.OPERATOR, KModifier.PUBLIC)
                    .receiver(pointerT)
                    .returns(pointerT)
                    .addStatement(
                        "return %T(_address - %T.layout.byteSize())",
                        CaelumCodegenHelper.pointerCName,
                        nativeDataType.nNativeDataCName
                    )
                    .build()
            )
            val nPointerStar = CaelumCodegenHelper.pointerCName.parameterizedBy(
                CaelumCodegenHelper.NPrimitive.cName.parameterizedBy(nativeDataType.nativeDataType, STAR)
            )
            file.addFunction(
                FunSpec.builder("copyTo")
                    .addAnnotation(randomName("copyTo"))
                    .receiver(nPointerStar)
                    .addParameter("dst", nPointerStar)
                    .addParameter("count", LONG)
                    .addStatement(
                        "%T.copy(%M, %T.layout, _address, %M, %T.layout, dst._address, count)",
                        CaelumCodegenHelper.memorySegmentCName,
                        CaelumCodegenHelper.omniSegment,
                        nativeDataType.nNativeDataCName,
                        CaelumCodegenHelper.omniSegment,
                        nativeDataType.nNativeDataCName
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("copyTo")
                    .addAnnotation(randomName("copyTo"))
                    .receiver(nPointerStar)
                    .addParameter("dst", nativeDataType.nativeDataArrayType)
                    .addParameter(
                        ParameterSpec.builder("dstIndex", INT)
                            .defaultValue("0")
                            .build()
                    )
                    .addParameter(
                        ParameterSpec.builder("count", INT)
                            .defaultValue("dst.size - dstIndex")
                            .build()
                    )
                    .addStatement(
                        "%T.copy(%M, %T.layout, _address, dst, dstIndex, count)",
                        CaelumCodegenHelper.memorySegmentCName,
                        CaelumCodegenHelper.omniSegment,
                        nativeDataType.nNativeDataCName
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("copyTo")
                    .addAnnotation(randomName("copyTo"))
                    .receiver(nativeDataType.nativeDataArrayType)
                    .addParameter("dst", nPointerStar)
                    .addParameter(
                        ParameterSpec.builder("srcIndex", INT)
                            .defaultValue("0")
                            .build()
                    )
                    .addParameter(
                        ParameterSpec.builder("count", INT)
                            .defaultValue("this.size - srcIndex")
                            .build()
                    )
                    .addStatement(
                        "%T.copy(this, srcIndex, %M, %T.layout, dst._address, count)",
                        CaelumCodegenHelper.memorySegmentCName,
                        CaelumCodegenHelper.omniSegment,
                        nativeDataType.nNativeDataCName
                    )
                    .build()
            )
        }

        return file
    }
}
