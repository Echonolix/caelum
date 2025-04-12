package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asTypeName
import net.echonolix.ktffi.CBasicType
import net.echonolix.ktffi.CExpression
import net.echonolix.ktffi.CTopLevelConst
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.KTFFICodegenHelper
import net.echonolix.ktffi.NativeType
import java.lang.invoke.MethodHandle

class GenerateEnumTask(ctx: VKFFICodeGenContext) : VKFFITask<Unit>(ctx) {
    override fun VKFFICodeGenContext.compute() {
        val constants = ConstantsTask().fork()
        val enum = EnumTask().fork()

//    val skipped = setOf("VkFormat")

//        val enumTypeList = registry.enumTypes.asSequence()
////            .filter { (name, _) -> name !in skipped }
//            .filter { (_, type) -> ctx.filter(type) }
//            .map { it.toPair() }
//            .toList()
//        val genEnumTypeAliasTask = GenTypeAliasTaskOld(ctx, enumTypeList).fork()
//
//        val flagTypeList = registry.flagTypes.asSequence()
////            .filter { (name, _) -> name !in skipped }
//            .filter { (_, type) -> ctx.filter(type) }
//            .map { it.toPair() }
//            .toList()
//        val genFlagTypeAliasTask = GenTypeAliasTaskOld(ctx, flagTypeList).fork()
//
//        flagTypeList.parallelStream()
//            .filter { (name, type) -> name == type.name }
//            .map { (_, flagType) -> genFlagType(flagType) }
//            .forEach { ctx.writeOutput(it) }
//
//        enumTypeList.parallelStream()
//            .filter { (name, type) -> name == type.name }
//            .map { (_, enumType) -> genEnumType(enumType) }
//            .forEach { ctx.writeOutput(it) }
//
//        val enumTypeAliasesFile = FileSpec.builder(ctx.enumPackageName, "EnumTypeAliases")
//        genEnumTypeAliasTask.join().forEach {
//            enumTypeAliasesFile.addTypeAlias(it)
//        }
//        ctx.writeOutput(enumTypeAliasesFile)
//
//        val flagTypeAliasesFile = FileSpec.builder(ctx.enumPackageName, "FlagTypeAliases")
//        genFlagTypeAliasTask.join().forEach {
//            flagTypeAliasesFile.addTypeAlias(it)
//        }
//        ctx.writeOutput(flagTypeAliasesFile)

        constants.join()
        enum.join()
    }

    private inner class EnumTask : VKFFITask<Unit>(ctx) {
        override fun VKFFICodeGenContext.compute() {
            val enumTypes = ctx.filterType<CType.Enum>()
            val typeAlias = GenTypeAliasTask(this, enumTypes).fork()

            enumTypes.parallelStream()
                .filter { (name, type) -> name == type.name }
                .map { (_, enumType) ->
                    genEnumType(enumType)
                }
                .forEach(ctx::writeOutput)

            typeAlias.joinAndWriteOutput(ctx.enumPackageName)
        }
    }

    private inner class ConstantsTask : VKFFITask<Unit>(ctx) {
        override fun VKFFICodeGenContext.compute() {
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
            ctx.writeOutput(vkEnumFile)

            val constantsFile = FileSpec.builder(ctx.enumPackageName, "Constants")
            constantsFile.addProperties(
                ctx.filterType<CTopLevelConst>().parallelStream()
                    .sorted(
                        compareBy<Pair<String, CTopLevelConst>> { (_, const) ->
                            const.expression is CExpression.StringLiteral
                        }.thenBy { (_, const) ->
                            const.expression is CExpression.Const
                        }.reversed()
                    )
                    .filter { (_, const) -> const.javaClass == CTopLevelConst::class.java }
                    .map { (_, const) ->
                        val initCode = const.expression.codeBlock()
                        val constType = const.type
                        val type = if (constType is CType.Pointer) {
                            STRING
                        } else {
                            constType.ktApiType()
                        }
                        PropertySpec.builder(const.name, type)
                            .addModifiers(KModifier.CONST)
                            .initializer(initCode)
                            .build()
                    }
                    .toList()
            )
            ctx.writeOutput(constantsFile)
        }
    }

    private enum class EnumKind(val cname: ClassName) {
        ENUM(VKFFI.vkEnumsCname),
        FLAG32(VKFFI.vkFlags32CNAME),
        FLAG64(VKFFI.vkFlags64CNAME)
    }

    private fun TypeSpec.Builder.addVkEnum(kind: EnumKind, baseType: CBasicType<*>): TypeSpec.Builder {
        addSuperinterface(kind.cname)

        primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter("value", baseType.kotlinTypeName)
                .build()
        )
        addProperty(
            PropertySpec.builder("value", baseType.kotlinTypeName)
                .addModifiers(KModifier.OVERRIDE)
                .initializer("value")
                .build()
        )
        return this
    }

    private fun TypeSpec.Builder.addMethodHandleFields(): TypeSpec.Builder {
        addProperty(
            PropertySpec.builder("\$fromIntMH", MethodHandle::class)
                .addModifiers(KModifier.PRIVATE)
                .initializer(
                    CodeBlock.builder()
                        .add(
                            "%T.lookup().unreflect(::fromInt.%M)",
                            VKFFI.methodHandlesCname,
                            KTFFICodegenHelper.javaMethodMemberName
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
                            "%T.lookup().unreflect(::toInt.%M)",
                            VKFFI.methodHandlesCname,
                            KTFFICodegenHelper.javaMethodMemberName
                        )
                        .build()
                )
                .build()
        )
        return this
    }

//    private fun genFlagType(flagType: Element.FlagType): FileSpec.Builder {
//        val flagBitType = registry.flagBitTypes[flagType.bitType]
//        val thisCname = ClassName(ctx.enumPackageName, flagType.name)
//        val enumKind = if (flagBitType?.type == CBasicType.int64_t) EnumKind.FLAG64 else EnumKind.FLAG32
//
//        val type = TypeSpec.classBuilder(thisCname)
//        type.tryAddKdoc(flagType)
//        if (flagBitType != null) {
//            type.tryAddKdoc(flagBitType)
//            type.addVkEnum(enumKind)
//        } else {
//            type.addVkEnum(enumKind)
//        }
//        type.addAnnotation(JvmInline::class)
//        type.addModifiers(KModifier.VALUE)
//        type.addFunction(
//            FunSpec.builder("plus")
//                .addModifiers(KModifier.OPERATOR)
//                .addParameter("other", thisCname)
//                .returns(thisCname)
//                .addStatement("return %T(value or other.value)", thisCname)
//                .build()
//        )
//        type.addFunction(
//            FunSpec.builder("minus")
//                .addModifiers(KModifier.OPERATOR)
//                .addParameter("other", thisCname)
//                .returns(thisCname)
//                .addStatement("return %T(value and other.value.inv())", thisCname)
//                .build()
//        )
//        type.addFunction(
//            FunSpec.builder("contains")
//                .addParameter("other", thisCname)
//                .returns(Boolean::class)
//                .addStatement("return (value and other.value == other.value)")
//                .build()
//        )
//
//        val companion = TypeSpec.companionObjectBuilder()
//        companion.addSuperinterface(KTFFICodegenHelper.typeCname, CodeBlock.of("%T", enumKind.dataType.nativeTypeName))
//        flagBitType?.entries?.values?.let { flagBitTypes ->
//            flagBitTypes.forEach {
//                companion.addProperty(
//                    PropertySpec.builder(it.fixedName, thisCname)
//                        .initializer(
//                            "%T(%L)",
//                            thisCname,
//                            it.valueCode.toString().replace("U", "")
//                        )
//                        .tryAddKdoc(it)
//                        .build()
//                )
//            }
//            if (flagBitTypes.none { it.valueCode.toString().startsWith("0") }) {
//                companion.addProperty(
//                    PropertySpec.builder("EMPTY", thisCname)
//                        .initializer("%T(0)", thisCname)
//                        .build()
//                )
//            }
//        }
//
//        companion.addFunction(
//            FunSpec.builder("toInt")
//                .addAnnotation(JvmStatic::class)
//                .addModifiers(KModifier.INLINE)
//                .addParameter("value", thisCname)
//                .returns(enumKind.dataType.kotlinType)
//                .addStatement("return value.value")
//                .build()
//        )
//        companion.addFunction(
//            FunSpec.builder("fromInt")
//                .addAnnotation(JvmStatic::class)
//                .addModifiers(KModifier.INLINE)
//                .addParameter("value", enumKind.dataType.kotlinType)
//                .returns(thisCname)
//                .addStatement("return %T(value)", thisCname)
//                .build()
//        )
//        companion.addMethodHandleFields(thisCname, enumKind)
//
//        val file = FileSpec.builder(thisCname)
//        file.addNativeAccess(thisCname, enumKind.dataType)
//        return file.addType(type.addType(companion.build()).build())
//    }

    private fun VKFFICodeGenContext.genEnumType(enumType: CType.Enum): FileSpec.Builder {
        val thisCname = ClassName(ctx.enumPackageName, enumType.name)
        val type = TypeSpec.enumBuilder(thisCname)
        val enumEntryBaseType = enumType.baseType
        val companion = TypeSpec.companionObjectBuilder()

        type.tryAddKdoc(enumType)
        type.addVkEnum(EnumKind.ENUM, enumEntryBaseType)
        type.addProperty(
            PropertySpec.builder("descriptor", KTFFICodegenHelper.typeDescriptorCname.parameterizedBy(thisCname))
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return Companion")
                        .build()
                )
                .build()
        )
        enumType.entries.values.asSequence()
            .forEach { entry ->
                val expression = entry.expression
                when (expression) {
                    is CExpression.Const -> {
                        type.addEnumConstant(
                            entry.name,
                            TypeSpec.anonymousClassBuilder()
                                .tryAddKdoc(entry)
                                .superclass(ClassName(ctx.enumPackageName, enumType.name))
                                .addSuperclassConstructorParameter(entry.expression.codeBlock())
                                .build()
                        )
                    }
                    is CExpression.Reference -> {
                        companion.addProperty(
                            PropertySpec.builder(entry.name, thisCname)
                                .initializer("%N", expression.value.name)
                                .tryAddKdoc(entry)
                                .build()
                        )
                    }
                    else -> throw IllegalArgumentException("Unsupported expression type: ${expression::class}")
                }
            }

        companion.addAnnotation(
            AnnotationSpec.builder(Suppress::class)
                .addMember("%S", "UNCHECKED_CAST")
                .build()
        )
        companion.addSuperinterface(
            KTFFICodegenHelper.typeDescriptorCname.parameterizedBy(thisCname),
            CodeBlock.of(
                "%T.descriptor as %T<%T>",
                enumEntryBaseType.nativeTypeName,
                KTFFICodegenHelper.typeDescriptorCname,
                thisCname
            )
        )
        companion.addFunction(
            FunSpec.builder("fromInt")
                .addAnnotation(JvmStatic::class)
                .addParameter(
                    "value",
                    enumEntryBaseType.kotlinTypeName
                )
                .returns(thisCname)
                .addCode(
                    CodeBlock.builder()
                        .beginControlFlow("return when (value)")
                        .apply {
                            enumType.entries.values.asSequence()
                                .filter { it.expression is CExpression.Const }
                                .forEach { entry ->
                                    addStatement(
                                        "%L -> %T.%N",
                                        entry.expression.codeBlock(),
                                        thisCname,
                                        entry.name
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
                .returns(enumEntryBaseType.kotlinTypeName)
                .addStatement("return value.value")
                .build()
        )
        companion.addMethodHandleFields()

        val file = FileSpec.builder(thisCname)
        file.addNativeAccess(thisCname, enumEntryBaseType)
        return file.addType(type.addType(companion.build()).build())
    }

    private fun FileSpec.Builder.addNativeAccess(thisCname: ClassName, baseType: CBasicType<*>) {
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
