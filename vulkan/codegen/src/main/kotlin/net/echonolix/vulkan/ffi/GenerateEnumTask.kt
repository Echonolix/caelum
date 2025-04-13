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
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import net.echonolix.ktffi.CBasicType
import net.echonolix.ktffi.CExpression
import net.echonolix.ktffi.CTopLevelConst
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.KTFFICodegenHelper
import net.echonolix.ktffi.NativeType
import net.echonolix.ktffi.className
import java.lang.invoke.MethodHandle
import kotlin.random.Random

class GenerateEnumTask(ctx: VKFFICodeGenContext) : VKFFITask<Unit>(ctx) {
    override fun VKFFICodeGenContext.compute() {
        val constants = ConstantsTask().fork()
        val enum = EnumTask().fork()
        val bitmask = BitmaskTask().fork()

        constants.join()
        enum.join()
        bitmask.join()
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

            typeAlias.joinAndWriteOutput(VKFFI.enumPackageName)
        }
    }

    private inner class BitmaskTask : VKFFITask<Unit>(ctx) {
        override fun VKFFICodeGenContext.compute() {
            val flagTypes = ctx.filterType<CType.Bitmask>()
            val typeAlias = GenTypeAliasTask(this, flagTypes).fork()

            flagTypes.parallelStream()
                .filter { (name, type) -> name == type.name }
                .map { (_, flagType) ->
                    genFlagType(flagType)
                }
                .forEach(ctx::writeOutput)

            typeAlias.joinAndWriteOutput(VKFFI.flagPackageName)
        }
    }

    private inner class ConstantsTask : VKFFITask<Unit>(ctx) {
        override fun VKFFICodeGenContext.compute() {
            val vkEnumBaseFile = FileSpec.builder(VKFFI.vkEnumBaseCname)
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
                        .build()
                )
            ctx.writeOutput(vkEnumBaseFile)
            val vkEnumFile = FileSpec.builder(VKFFI.vkEnumCname)
                .addType(
                    TypeSpec.interfaceBuilder(VKFFI.vkEnumCname)
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
            ctx.writeOutput(vkEnumFile)

            val vkFlagFile = FileSpec.builder(VKFFI.flagPackageName, "VkFlags")
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
            ctx.writeOutput(vkFlagFile)

            val constantsFile = FileSpec.builder(VKFFI.basePkgName, "Constants")
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

    context(ctx: VKFFICodeGenContext)
    private fun TypeSpec.Builder.addSuper(enumBase: CType.EnumBase): TypeSpec.Builder {
        val baseType = enumBase.baseType
        val superCname = when (enumBase) {
            is CType.Enum -> VKFFI.vkEnumCname
            is CType.Bitmask -> {
                when (baseType) {
                    CBasicType.int32_t -> VKFFI.vkFlags32CNAME
                    CBasicType.int64_t -> VKFFI.vkFlags64CNAME
                    else -> throw IllegalArgumentException("Unsupported base type: $baseType")
                }
            }
            else -> throw IllegalArgumentException("Unsupported enum base type: $enumBase")
        }
        addSuperinterface(superCname)
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
        addProperty(
            PropertySpec.builder("descriptor", KTFFICodegenHelper.typeDescriptorCname.parameterizedBy(enumBase.className()))
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return Companion")
                        .build()
                )
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

    private fun VKFFICodeGenContext.genFlagType(flagType: CType.Bitmask): FileSpec.Builder {
        val thisCname = flagType.className()

        val type = TypeSpec.classBuilder(thisCname)
        type.tryAddKdoc(flagType)
        type.addSuper(flagType)

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
        flagType.entries.values.let { flagBitTypes ->
            flagBitTypes.forEach {
                val code = it.expression.codeBlock()
                companion.addProperty(
                    PropertySpec.builder(it.name, thisCname)
                        .initializer(
                            "%T(%L)",
                            thisCname,
                            code
                        )
                        .tryAddKdoc(it)
                        .build()
                )
            }
            if (flagBitTypes.none { it.expression.codeBlock().toString() == "0" }) {
                companion.addProperty(
                    PropertySpec.builder("EMPTY", thisCname)
                        .initializer("%T(0)", thisCname)
                        .build()
                )
            }
        }

        companion.addCompanionSuper(flagType)
        companion.addFunction(
            FunSpec.builder("toInt")
                .addAnnotation(JvmStatic::class)
                .addModifiers(KModifier.INLINE)
                .addParameter("value", thisCname)
                .returns(flagType.baseType.kotlinTypeName)
                .addStatement("return value.value")
                .build()
        )
        companion.addFunction(
            FunSpec.builder("fromInt")
                .addAnnotation(JvmStatic::class)
                .addModifiers(KModifier.INLINE)
                .addParameter("value", flagType.baseType.kotlinTypeName)
                .returns(thisCname)
                .addStatement("return %T(value)", thisCname)
                .build()
        )
        companion.addMethodHandleFields()

        val file = FileSpec.builder(thisCname)
        file.addNativeAccess(thisCname, flagType.baseType)
        return file.addType(type.addType(companion.build()).build())
    }

    private fun VKFFICodeGenContext.genEnumType(enumType: CType.Enum): FileSpec.Builder {
        val thisCname = enumType.className()

        val type = TypeSpec.enumBuilder(thisCname)
        type.tryAddKdoc(enumType)
        type.addSuper(enumType)

        val companion = TypeSpec.companionObjectBuilder()
        enumType.entries.values.asSequence()
            .forEach { entry ->
                val expression = entry.expression
                when (expression) {
                    is CExpression.Const -> {
                        type.addEnumConstant(
                            entry.name,
                            TypeSpec.anonymousClassBuilder()
                                .tryAddKdoc(entry)
                                .superclass(thisCname)
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

        companion.addCompanionSuper(enumType)
        companion.addFunction(
            FunSpec.builder("fromInt")
                .addAnnotation(JvmStatic::class)
                .addParameter(
                    "value",
                    enumType.baseType.kotlinTypeName
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
                .returns(enumType.baseType.kotlinTypeName)
                .addStatement("return value.value")
                .build()
        )
        companion.addMethodHandleFields()

        val file = FileSpec.builder(thisCname)
        file.addNativeAccess(thisCname, enumType.baseType)
        return file.addType(type.addType(companion.build()).build())
    }

    private val validChars = ('a'..'z').toList()

    context(ctx: VKFFICodeGenContext)
    private fun TypeSpec.Builder.addCompanionSuper(enumBase: CType.EnumBase) {
        addAnnotation(
            AnnotationSpec.builder(Suppress::class)
                .addMember("%S", "UNCHECKED_CAST")
                .build()
        )
        val thisCname = enumBase.className()
        addSuperinterface(
            KTFFICodegenHelper.typeDescriptorCname.parameterizedBy(thisCname),
            CodeBlock.of(
                "%T.descriptor as %T<%T>",
                enumBase.baseType.nativeTypeName,
                KTFFICodegenHelper.typeDescriptorCname,
                thisCname
            )
        )
    }

    private fun FileSpec.Builder.addNativeAccess(thisCname: ClassName, baseType: CBasicType<*>) {
        val random = Random(0)

        fun randomName(base: String): AnnotationSpec {
            val randomChars = (0..4).map { validChars[random.nextInt(validChars.size)] }.joinToString("")
            return AnnotationSpec.builder(JvmName::class)
                .addMember("%S", "${thisCname.simpleName}_${base}_$randomChars")
                .build()
        }

        val pointerCnameP = KTFFICodegenHelper.pointerCname.parameterizedBy(thisCname)
        val arrayCnameP = KTFFICodegenHelper.arrayCname.parameterizedBy(thisCname)
        val valueCNameP = KTFFICodegenHelper.valueCname.parameterizedBy(thisCname)
        val nullableAny = Any::class.asClassName().copy(nullable = true)

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
        addFunction(
            FunSpec.builder("getValue")
                .addAnnotation(randomName("getValue"))
                .receiver(pointerCnameP)
                .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                .addParameter("thisRef", nullableAny)
                .addParameter("property", nullableAny)
                .returns(thisCname)
                .addStatement(
                    "return %T.fromInt(%T.valueVarHandle.get(%M, _address) as %T)",
                    thisCname,
                    baseType.nativeTypeName,
                    KTFFICodegenHelper.omniSegment,
                    baseType.kotlinTypeName
                )
                .build()
        )
        addFunction(
            FunSpec.builder("setValue")
                .addAnnotation(randomName("setValue"))
                .receiver(pointerCnameP)
                .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                .addParameter("thisRef", nullableAny)
                .addParameter("property", nullableAny)
                .addParameter("value", thisCname)
                .addStatement(
                    "%T.valueVarHandle.set(%M, _address, %T.toInt(value))",
                    baseType.nativeTypeName,
                    KTFFICodegenHelper.omniSegment,
                    thisCname
                )
                .build()
        )
        addFunction(
            FunSpec.builder("getValue")
                .addAnnotation(randomName("getValue"))
                .receiver(valueCNameP)
                .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                .addParameter("thisRef", nullableAny)
                .addParameter("property", nullableAny)
                .returns(thisCname)
                .addStatement(
                    "return %T.fromInt(%T.valueVarHandle.get(_segment, 0L) as %T)",
                    thisCname,
                    baseType.nativeTypeName,
                    baseType.kotlinTypeName
                )
                .build()
        )
        addFunction(
            FunSpec.builder("setValue")
                .addAnnotation(randomName("setValue"))
                .receiver(valueCNameP)
                .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                .addParameter("thisRef", nullableAny)
                .addParameter("property", nullableAny)
                .addParameter("value", thisCname)
                .addStatement(
                    "%T.valueVarHandle.set(_segment, 0L, %T.toInt(value))",
                    baseType.nativeTypeName,
                    thisCname
                )
                .build()
        )
    }
}
