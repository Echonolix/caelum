package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.ktffi.CBasicType
import net.echonolix.ktffi.KTFFICodegenHelper
import net.echonolix.ktffi.NativeType
import net.echonolix.vulkan.schema.Element
import net.echonolix.vulkan.schema.PatchedRegistry
import java.lang.foreign.MemoryLayout
import java.lang.invoke.MethodHandle
import java.util.concurrent.RecursiveAction
import kotlin.collections.get

class GenerateCEnumTask(private val genCtx: FFIGenContext, private val registry: PatchedRegistry) : RecursiveAction() {
    override fun compute() {
//    val skipped = setOf("VkFormat")

        val enumTypeList = registry.enumTypes.asSequence()
//            .filter { (name, _) -> name !in skipped }
            .filter { (_, type) -> genCtx.filter(type) }
            .map { it.toPair() }
            .toList()
        val genEnumTypeAliasTask = GenTypeAliasTask(genCtx, enumTypeList).fork()

        val flagTypeList = registry.flagTypes.asSequence()
//            .filter { (name, _) -> name !in skipped }
            .filter { (_, type) -> genCtx.filter(type) }
            .map { it.toPair() }
            .toList()
        val genFlagTypeAliasTask = GenTypeAliasTask(genCtx, flagTypeList).fork()

        val vkEnumFile = FileSpec.builder(VKFFI.vkEnumsCname)
            .addType(
                TypeSpec.interfaceBuilder(VKFFI.vkEnumBaseCname)
                    .addSuperinterface(KTFFICodegenHelper.typeCname)
                    .addTypeVariable(TypeVariableName("T"))
                    .addProperty(
                        PropertySpec.builder("value", TypeVariableName("T"))
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("nativeType", NativeType::class)
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("layout", MemoryLayout::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .getter(
                                FunSpec.getterBuilder()
                                    .addStatement("return nativeType.layout")
                                    .build()
                            )
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("arrayLayout", MemoryLayout::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .getter(
                                FunSpec.getterBuilder()
                                    .addStatement("return nativeType.arrayLayout")
                                    .build()
                            )
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("arrayByteOffsetHandle", MethodHandle::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .getter(
                                FunSpec.getterBuilder()
                                    .addStatement("return nativeType.arrayByteOffsetHandle")
                                    .build()
                            )
                            .build()
                    )
                    .addModifiers(KModifier.SEALED)
                    .build()
            )
            .addType(
                TypeSpec.interfaceBuilder(VKFFI.vkEnumsCname)
                    .addModifiers(KModifier.SEALED)
                    .addSuperinterface(VKFFI.vkEnumBaseCname.parameterizedBy(Int::class.asTypeName()))
                    .addProperty(
                        PropertySpec.builder("nativeType", NativeType::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .getter(
                                FunSpec.getterBuilder()
                                    .addStatement("return %T", CBasicType.int32_t.nativeTypeName)
                                    .build()
                            )
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("value", Int::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .build()
                    )
                    .build()
            )
            .addType(
                TypeSpec.interfaceBuilder(VKFFI.vkFlags32CNAME)
                    .addModifiers(KModifier.SEALED)
                    .addSuperinterface(VKFFI.vkEnumBaseCname.parameterizedBy(Int::class.asTypeName()))
                    .addProperty(
                        PropertySpec.builder("nativeType", NativeType::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .getter(
                                FunSpec.getterBuilder()
                                    .addStatement("return %T", CBasicType.int32_t.nativeTypeName)
                                    .build()
                            )
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("value", Int::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .build()
                    )
                    .build()
            )
            .addType(
                TypeSpec.interfaceBuilder(VKFFI.vkFlags64CNAME)
                    .addModifiers(KModifier.SEALED)
                    .addSuperinterface(VKFFI.vkEnumBaseCname.parameterizedBy(Long::class.asTypeName()))
                    .addProperty(
                        PropertySpec.builder("nativeType", NativeType::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .getter(
                                FunSpec.getterBuilder()
                                    .addStatement("return %T", CBasicType.int64_t.nativeTypeName)
                                    .build()
                            )
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("value", Long::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .build()
                    )
                    .build()
            )
        genCtx.writeOutput(vkEnumFile)

        val constantsFile = FileSpec.builder(genCtx.enumPackageName, "Constants")
        registry.constantElements.values.forEach {
            constantsFile.addProperty(
                PropertySpec.builder(it.name, it.type.kotlinTypeName)
                    .tryAddKdoc(it)
                    .apply {
                        if (!it.value.toString().contains(".inv()")) {
                            addModifiers(KModifier.CONST)
                        }
                    }
                    .initializer(it.value)
                    .build()
            )
        }
        genCtx.writeOutput(constantsFile)

        flagTypeList.parallelStream()
            .filter { (name, type) -> name == type.name }
            .map { (_, flagType) -> genFlagType(flagType) }
            .forEach { genCtx.writeOutput(it) }

        enumTypeList.parallelStream()
            .filter { (name, type) -> name == type.name }
            .map { (_, enumType) -> genEnumType(enumType) }
            .forEach { genCtx.writeOutput(it) }

        val enumTypeAliasesFile = FileSpec.builder(genCtx.enumPackageName, "EnumTypeAliases")
        genEnumTypeAliasTask.join().forEach {
            enumTypeAliasesFile.addTypeAlias(it)
        }
        genCtx.writeOutput(enumTypeAliasesFile)

        val flagTypeAliasesFile = FileSpec.builder(genCtx.enumPackageName, "FlagTypeAliases")
        genFlagTypeAliasTask.join().forEach {
            flagTypeAliasesFile.addTypeAlias(it)
        }
        genCtx.writeOutput(flagTypeAliasesFile)
    }

    private enum class EnumKind(val cname: ClassName, val dataType: CBasicType) {
        ENUM(VKFFI.vkEnumsCname, CBasicType.int32_t),
        FLAG32(VKFFI.vkFlags32CNAME, CBasicType.int32_t),
        FLAG64(VKFFI.vkFlags64CNAME, CBasicType.int64_t)
    }

    private fun TypeSpec.Builder.addVkEnum(kind: EnumKind): TypeSpec.Builder {
        addSuperinterface(kind.cname)

        primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter("value", kind.dataType.kotlinTypeName)
                .build()
        )
        addProperty(
            PropertySpec.builder("value", kind.dataType.kotlinTypeName)
                .addModifiers(KModifier.OVERRIDE)
                .initializer("value")
                .build()
        )
        return this
    }

    private fun TypeSpec.Builder.addMethodHandleFields(thisCname: ClassName, enumKind: EnumKind): TypeSpec.Builder {
        val type = enumKind.dataType.kotlinType
        addProperty(
            PropertySpec.builder("\$fromIntMH", MethodHandle::class)
                .addModifiers(KModifier.PRIVATE)
                .initializer(
                    CodeBlock.builder()
                        .add(
                            "%T.lookup().findStatic(%T::class.java, %S, %T.methodType(%T::class.java, %T::class.javaPrimitiveType))",
                            VKFFI.methodHandlesCname,
                            thisCname,
                            "fromInt",
                            VKFFI.methodTypeCname,
                            thisCname,
                            type
                        )
                        .build()
                )
                .build()
        )
        addProperty(
            PropertySpec.builder("\$toIntMH", MethodHandle::class)
                .addModifiers(KModifier.PRIVATE)
                .initializer(
                    CodeBlock.builder()
                        .add(
                            "%T.lookup().findStatic(%T::class.java, %S, %T.methodType(%T::class.javaPrimitiveType, %T::class.java))",
                            VKFFI.methodHandlesCname,
                            thisCname,
                            "toInt",
                            VKFFI.methodTypeCname,
                            type,
                            thisCname
                        )
                        .build()
                )
                .build()
        )
        return this
    }

    private fun genFlagType(flagType: Element.FlagType): FileSpec.Builder {
        val flagBitType = registry.flagBitTypes[flagType.bitType]
        val thisCname = ClassName(genCtx.enumPackageName, flagType.name)
        val enumKind = if (flagBitType?.type == CBasicType.int64_t) EnumKind.FLAG64 else EnumKind.FLAG32

        val type = TypeSpec.classBuilder(thisCname)
        type.tryAddKdoc(flagType)
        if (flagBitType != null) {
            type.tryAddKdoc(flagBitType)
            type.addVkEnum(enumKind)
        } else {
            type.addVkEnum(enumKind)
        }
        type.addAnnotation(JvmInline::class)
        type.addModifiers(KModifier.VALUE)
        type.addFunction(
            FunSpec.builder("plus")
                .addModifiers(KModifier.OPERATOR)
                .addParameter("other", thisCname)
                .returns(thisCname)
                .addStatement("return %T(value or other.value)", thisCname)
                .build()
        )
        type.addFunction(
            FunSpec.builder("minus")
                .addModifiers(KModifier.OPERATOR)
                .addParameter("other", thisCname)
                .returns(thisCname)
                .addStatement("return %T(value and other.value.inv())", thisCname)
                .build()
        )
        type.addFunction(
            FunSpec.builder("contains")
                .addParameter("other", thisCname)
                .returns(Boolean::class)
                .addStatement("return (value and other.value == other.value)")
                .build()
        )

        val companion = TypeSpec.companionObjectBuilder()
        companion.addSuperinterface(KTFFICodegenHelper.typeCname, CodeBlock.of("%T", enumKind.dataType.nativeTypeName))
        flagBitType?.entries?.values?.let { flagBitTypes ->
            flagBitTypes.forEach {
                companion.addProperty(
                    PropertySpec.builder(it.fixedName, thisCname)
                        .initializer(
                            "%T(%L)",
                            thisCname,
                            it.valueCode.toString().replace("U", "")
                        )
                        .tryAddKdoc(it)
                        .build()
                )
            }
            if (flagBitTypes.none { it.valueCode.toString().startsWith("0") }) {
                companion.addProperty(
                    PropertySpec.builder("EMPTY", thisCname)
                        .initializer("%T(0)", thisCname)
                        .build()
                )
            }
        }

        companion.addFunction(
            FunSpec.builder("toInt")
                .addAnnotation(JvmStatic::class)
                .addModifiers(KModifier.INLINE)
                .addParameter("value", thisCname)
                .returns(enumKind.dataType.kotlinType)
                .addStatement("return value.value")
                .build()
        )
        companion.addFunction(
            FunSpec.builder("fromInt")
                .addAnnotation(JvmStatic::class)
                .addModifiers(KModifier.INLINE)
                .addParameter("value", enumKind.dataType.kotlinType)
                .returns(thisCname)
                .addStatement("return %T(value)", thisCname)
                .build()
        )
        companion.addMethodHandleFields(thisCname, enumKind)

        val file = FileSpec.builder(thisCname)
        file.addNativeAccess(thisCname, enumKind.dataType)
        return file.addType(type.addType(companion.build()).build())
    }

    private fun genEnumType(enumType: Element.EnumType): FileSpec.Builder {
        val thisCname = ClassName(genCtx.enumPackageName, enumType.name)
        val type = TypeSpec.enumBuilder(thisCname)
        type.tryAddKdoc(enumType)
        type.addVkEnum(EnumKind.ENUM)
            .apply {
                enumType.entries.values.forEach { entry ->
                    addEnumConstant(
                        entry.fixedName,
                        TypeSpec.anonymousClassBuilder()
                            .tryAddKdoc(entry)
                            .superclass(ClassName(genCtx.enumPackageName, enumType.name))
                            .addSuperclassConstructorParameter(entry.valueCode)
                            .build()
                    )
                }
            }

        val companion = TypeSpec.companionObjectBuilder()
        companion.addSuperinterface(KTFFICodegenHelper.typeCname, CodeBlock.of("%T", CBasicType.int32_t.nativeTypeName))
        companion.addFunction(
            FunSpec.builder("fromInt")
                .addAnnotation(JvmStatic::class)
                .addParameter(
                    "value",
                    EnumKind.ENUM.dataType.kotlinType
                )
                .returns(thisCname)
                .addCode(
                    CodeBlock.builder()
                        .beginControlFlow("return when (value)")
                        .apply {
                            enumType.entries.values.forEach { entry ->
                                addStatement(
                                    "%L -> %T.%N",
                                    entry.valueNum,
                                    thisCname,
                                    entry.fixedName
                                )
                            }
                        }
                        .addStatement("else -> throw IllegalArgumentException(\"Unknown value: \$value\")")
                        .endControlFlow()
                        .build()
                )
                .build()
        )
        companion.addFunction(
            FunSpec.builder("toInt")
                .addAnnotation(JvmStatic::class)
                .addParameter("value", thisCname)
                .returns(EnumKind.ENUM.dataType.kotlinType)
                .addStatement("return value.value")
                .build()
        )
        companion.addMethodHandleFields(thisCname, EnumKind.ENUM)

        val file = FileSpec.builder(thisCname)
        file.addNativeAccess(thisCname, CBasicType.int32_t)
        return file.addType(type.addType(companion.build()).build())
    }

    private fun FileSpec.Builder.addNativeAccess(thisCname: ClassName, baseType: CBasicType) {
        val pointerCnameP = KTFFICodegenHelper.pointerCname.parameterizedBy(thisCname)
        val arrayCnameP = KTFFICodegenHelper.arrayCname.parameterizedBy(thisCname)
        addFunction(
            FunSpec.builder("get")
                .receiver(arrayCnameP)
                .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                .addParameter("index", LONG)
                .returns(thisCname)
                .addStatement(
                    "return %T.fromInt(%T.arrayVarHandle.get(_segment, 0L, index) as %T)",
                    thisCname,
                    baseType.nativeTypeName,
                    baseType.kotlinTypeName
                )
                .build()
        )
        addFunction(
            FunSpec.builder("set")
                .receiver(arrayCnameP)
                .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                .addParameter("index", LONG)
                .addParameter("value", thisCname)
                .addStatement(
                    "%T.arrayVarHandle.set(_segment, 0L, index, %T.toInt(value))",
                    baseType.nativeTypeName,
                    thisCname,
                )
                .build()
        )
        addFunction(
            FunSpec.builder("get")
                .receiver(pointerCnameP)
                .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                .addParameter("index", LONG)
                .returns(thisCname)
                .addStatement(
                    "return %T.fromInt(%T.arrayVarHandle.get(%M, _address, index) as %T)",
                    thisCname,
                    baseType.nativeTypeName,
                    KTFFICodegenHelper.omniSegment,
                    baseType.kotlinTypeName
                )
                .build()
        )
        addFunction(
            FunSpec.builder("set")
                .receiver(pointerCnameP)
                .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                .addParameter("index", LONG)
                .addParameter("value", thisCname)
                .addStatement(
                    "%T.arrayVarHandle.set(%M, _address, index, %T.toInt(value))",
                    baseType.nativeTypeName,
                    KTFFICodegenHelper.omniSegment,
                    thisCname
                )
                .build()
        )
    }
}
