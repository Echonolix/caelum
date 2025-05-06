package net.echonolix.caelum.vulkan.tasks

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.*
import net.echonolix.caelum.codegen.api.*
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.addKdoc
import net.echonolix.caelum.codegen.api.ctx.filterType
import net.echonolix.caelum.codegen.api.ctx.filterTypeStream
import net.echonolix.caelum.codegen.api.generator.EnumBaseGenerator
import net.echonolix.caelum.codegen.api.task.CodegenTask
import net.echonolix.caelum.codegen.api.task.GenTypeAliasTask
import net.echonolix.caelum.vulkan.VulkanCodegen
import net.echonolix.caelum.vulkan.schema.FilteredRegistry
import kotlin.io.path.Path
import kotlin.random.Random

class GenerateEnumTask(ctx: CodegenContext, val registry: FilteredRegistry) : CodegenTask<Unit>(ctx) {
    override fun CodegenContext.compute() {
        val constants = ConstantsTask().fork()
        val enum = EnumTask().fork()
        val bitmask = BitmaskTask().fork()

        constants.join()
        enum.join()
        bitmask.join()
    }

    private inner class EnumTask : CodegenTask<Unit>(ctx) {
        override fun CodegenContext.compute() {
            val enumTypes = ctx.filterType<CType.Enum>()
            val typeAlias = GenTypeAliasTask(this, enumTypes).fork()

            enumTypes.parallelStream()
                .filter { (name, type) -> name == type.name }
                .map { (_, enumType) -> genEnumType(enumType) }
                .partitionWrite("enums")

            typeAlias.joinAndWriteOutput(Path("enums"), VulkanCodegen.enumPackageName)
        }
    }

    private inner class BitmaskTask : CodegenTask<Unit>(ctx) {
        override fun CodegenContext.compute() {
            val flagTypes = ctx.filterTypeStream<CType.Bitmask>()
                .filter { !it.first.contains("FlagBits") }
                .toList()
            val typeAlias = GenTypeAliasTask(this, flagTypes).fork()

            flagTypes.parallelStream()
                .filter { (name, type) -> name == type.name }
                .map { (_, flagType) -> genFlagType(flagType) }
                .partitionWrite("enums")

            typeAlias.joinAndWriteOutput(Path("enums"), VulkanCodegen.flagPackageName)
        }
    }

    private inner class ConstantsTask : CodegenTask<Unit>(ctx) {
        override fun CodegenContext.compute() {
            val vkEnumFile = FileSpec.Companion.builder(VulkanCodegen.vkEnumCName)
                .addType(
                    TypeSpec.Companion.interfaceBuilder(VulkanCodegen.vkEnumCName)
                        .addSuperinterface(VulkanCodegen.vkEnumBaseCName.parameterizedBy(Int::class.asTypeName()))
                        .addProperty(
                            PropertySpec.builder("nativeType", NType::class)
                                .addModifiers(KModifier.OVERRIDE)
                                .getter(
                                    FunSpec.getterBuilder()
                                        .addStatement("return %T", CBasicType.int32_t.caelumCoreTypeName)
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
            ctx.writeOutput(Path("baseSrc"), vkEnumFile)

            val vkFlagFile = FileSpec.builder(VulkanCodegen.flagPackageName, "VkFlags")
                .addType(
                    TypeSpec.Companion.interfaceBuilder(VulkanCodegen.vkFlags32CNAME)
                        .addSuperinterface(VulkanCodegen.vkEnumBaseCName.parameterizedBy(Int::class.asTypeName()))
                        .addProperty(
                            PropertySpec.builder("nativeType", NType::class)
                                .addModifiers(KModifier.OVERRIDE)
                                .getter(
                                    FunSpec.getterBuilder()
                                        .addStatement("return %T", CBasicType.int32_t.caelumCoreTypeName)
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
                    TypeSpec.Companion.interfaceBuilder(VulkanCodegen.vkFlags64CNAME)
                        .addSuperinterface(VulkanCodegen.vkEnumBaseCName.parameterizedBy(Long::class.asTypeName()))
                        .addProperty(
                            PropertySpec.builder("nativeType", NType::class)
                                .addModifiers(KModifier.OVERRIDE)
                                .getter(
                                    FunSpec.getterBuilder()
                                        .addStatement("return %T", CBasicType.int64_t.caelumCoreTypeName)
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
            ctx.writeOutput(Path("baseSrc"), vkFlagFile)

            val constantsFile = FileSpec.builder(VulkanCodegen.basePkgName, "Constants")
            constantsFile.addProperties(
                ctx.filterTypeStream<CTopLevelConst>()
                    .filter { !VulkanCodegen.vkVersionConstRegex.matches(it.first) }
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
            ctx.writeOutput(Path("baseSrc"), constantsFile)
        }
    }

    context(ctx: CodegenContext)
    private fun TypeSpec.Builder.addSuper(enumBase: CType.EnumBase): TypeSpec.Builder {
        val baseType = enumBase.baseType
        val superCName = when (enumBase) {
            is CType.Enum -> VulkanCodegen.vkEnumCName
            is CType.Bitmask -> {
                when (baseType) {
                    CBasicType.int32_t -> VulkanCodegen.vkFlags32CNAME
                    CBasicType.int64_t -> VulkanCodegen.vkFlags64CNAME
                    else -> throw IllegalArgumentException("Unsupported base type: $baseType")
                }
            }
            else -> throw IllegalArgumentException("Unsupported enum base type: $enumBase")
        }
        addSuperinterface(superCName)
        primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter("value", baseType.ktApiTypeTypeName)
                .build()
        )
        addProperty(
            PropertySpec.builder("value", baseType.ktApiTypeTypeName)
                .addModifiers(KModifier.OVERRIDE)
                .initializer("value")
                .build()
        )
        addProperty(
            PropertySpec.builder(
                "typeDescriptor",
                CaelumCodegenHelper.typeDescriptorCName.parameterizedBy(enumBase.typeName())
            ).addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return Companion")
                        .build()
                )
                .build()
        )
        return this
    }

    private fun CodegenContext.genFlagType(flagType: CType.Bitmask): FileSpec.Builder {
        val thisCName = flagType.className()

        val type = TypeSpec.classBuilder(thisCName)
        type.addKdoc(flagType)
        type.addSuper(flagType)

        type.addAnnotation(JvmInline::class)
        type.addModifiers(KModifier.VALUE)
        type.addFunction(
            FunSpec.builder("plus")
                .addModifiers(KModifier.OPERATOR)
                .addParameter("other", thisCName)
                .returns(thisCName)
                .addStatement("return %T(value or other.value)", thisCName)
                .build()
        )
        type.addFunction(
            FunSpec.builder("minus")
                .addModifiers(KModifier.OPERATOR)
                .addParameter("other", thisCName)
                .returns(thisCName)
                .addStatement("return %T(value and other.value.inv())", thisCName)
                .build()
        )
        type.addFunction(
            FunSpec.builder("contains")
                .addModifiers(KModifier.OPERATOR)
                .addParameter("other", thisCName)
                .returns(Boolean::class)
                .addStatement("return (value and other.value == other.value)")
                .build()
        )

        val companion = TypeSpec.companionObjectBuilder()
        val flagBitEntries = flagType.entries.values.sortedBy { registry.enumValueOrders[it.name] }
        val internalAliases = mutableListOf<PropertySpec>()
        flagBitEntries.forEach {
            val expression = it.expression
            val code = expression.codeBlock()
            val initilizer = when (expression) {
                is CExpression.Const -> {
                    CodeBlock.of("%T(%L)", thisCName, code)
                }
                is CExpression.Reference -> {
                    check(expression.value.name in flagType.entries)
                    CodeBlock.of("%N", expression.value.name)
                }
                else -> throw IllegalArgumentException("Unsupported expression type: ${expression::class}")
            }
            val fixedName = it.tags.get<EnumEntryFixedName>()!!.name
            companion.addProperty(
                PropertySpec.builder(fixedName, thisCName)
                    .initializer(initilizer)
                    .addKdoc(it)
                    .build()
            )
            if (it.name != fixedName) {
                internalAliases += PropertySpec.builder(it.name, thisCName)
                    .addModifiers(KModifier.INTERNAL)
                    .getter(
                        FunSpec.getterBuilder()
                            .addStatement("return %N", fixedName)
                            .build()
                    )
                    .build()
            }
        }
        companion.addProperties(internalAliases)
        if (flagBitEntries.none {
                CSyntax.intLiteralRegex.matchEntire(it.expression.codeBlock().toString())?.groupValues[2] == "0"
            }) {
            companion.addProperty(
                PropertySpec.builder("NONE", thisCName)
                    .initializer("%T(0)", thisCName)
                    .build()
            )
        }

        companion.addCompanionSuper(flagType)
        companion.addFunction(
            FunSpec.builder("fromNativeData")
                .addAnnotation(JvmStatic::class)
                .addParameter("value", flagType.baseType.ktApiTypeTypeName)
                .returns(thisCName)
                .addStatement("return %T(value)", thisCName)
                .build()
        )
        companion.addFunction(
            FunSpec.builder("toNativeData")
                .addAnnotation(JvmStatic::class)
                .addParameter("value", thisCName)
                .returns(flagType.baseType.ktApiTypeTypeName)
                .addStatement("return value.value")
                .build()
        )

        val file = FileSpec.builder(thisCName)
        file.addType(type.addType(companion.build()).build())
        file.addNativeAccess(thisCName, flagType.baseType)
        return file
    }

    private fun genEnumType(enumType: CType.Enum): FileSpec.Builder {
        val generator = object : EnumBaseGenerator(ctx, enumType) {
            context(ctx: CodegenContext)
            override fun enumBaseCName(): TypeName {
                return VulkanCodegen.vkEnumCName
            }
        }
        return generator.generate()
    }

    private val validChars = ('a'..'z').toList()

    context(ctx: CodegenContext)
    private fun TypeSpec.Builder.addCompanionSuper(enumBase: CType.EnumBase) {
        addAnnotation(
            AnnotationSpec.builder(Suppress::class)
                .addMember("%S", "UNCHECKED_CAST")
                .build()
        )
        val thisCName = enumBase.className()
        addSuperinterface(
            CaelumCodegenHelper.typeDescriptorCName.parameterizedBy(thisCName),
            CodeBlock.of(
                "%T.typeDescriptor as %T<%T>",
                enumBase.baseType.caelumCoreTypeName,
                CaelumCodegenHelper.typeDescriptorCName,
                thisCName
            )
        )
    }

    private fun FileSpec.Builder.addNativeAccess(thisCName: ClassName, baseType: CBasicType<*>) {
        val random = Random(0)

        fun randomName(base: String): AnnotationSpec {
            val randomChars = (0..4).map { validChars[random.nextInt(validChars.size)] }.joinToString("")
            return AnnotationSpec.builder(JvmName::class)
                .addMember("%S", "${thisCName.simpleName}_${base}_$randomChars")
                .build()
        }

        val pointerCNameP = CaelumCodegenHelper.pointerCName.parameterizedBy(thisCName)
        val arrayCNameP = CaelumCodegenHelper.arrayCName.parameterizedBy(thisCName)
        val valueCNameP = CaelumCodegenHelper.valueCName.parameterizedBy(thisCName)
        val nullableAny = Any::class.asClassName().copy(nullable = true)

        addFunction(
            FunSpec.builder("get")
                .receiver(arrayCNameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("index", LONG)
                .returns(thisCName)
                .addStatement(
                    "return %T.fromNativeData(%T.arrayVarHandle.get(segment, 0L, index) as %T)",
                    thisCName,
                    baseType.caelumCoreTypeName,
                    baseType.ktApiTypeTypeName
                )
                .build()
        )
        addFunction(
            FunSpec.builder("set")
                .receiver(arrayCNameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("index", LONG)
                .addParameter("value", thisCName)
                .addStatement(
                    "%T.arrayVarHandle.set(segment, 0L, index, %T.toNativeData(value))",
                    baseType.caelumCoreTypeName,
                    thisCName,
                )
                .build()
        )
        addFunction(
            FunSpec.builder("get")
                .receiver(pointerCNameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("index", LONG)
                .returns(thisCName)
                .addStatement(
                    "return %T.fromNativeData(%T.arrayVarHandle.get(%M, _address, index) as %T)",
                    thisCName,
                    baseType.caelumCoreTypeName,
                    CaelumCodegenHelper.omniSegment,
                    baseType.ktApiTypeTypeName
                )
                .build()
        )
        addFunction(
            FunSpec.builder("set")
                .receiver(pointerCNameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("index", LONG)
                .addParameter("value", thisCName)
                .addStatement(
                    "%T.arrayVarHandle.set(%M, _address, index, %T.toNativeData(value))",
                    baseType.caelumCoreTypeName,
                    CaelumCodegenHelper.omniSegment,
                    thisCName
                )
                .build()
        )
        addFunction(
            FunSpec.builder("getValue")
                .addAnnotation(randomName("getValue"))
                .receiver(pointerCNameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("thisRef", nullableAny)
                .addParameter("property", nullableAny)
                .returns(thisCName)
                .addStatement(
                    "return %T.fromNativeData(%T.valueVarHandle.get(%M, _address) as %T)",
                    thisCName,
                    baseType.caelumCoreTypeName,
                    CaelumCodegenHelper.omniSegment,
                    baseType.ktApiTypeTypeName
                )
                .build()
        )
        addFunction(
            FunSpec.builder("setValue")
                .addAnnotation(randomName("setValue"))
                .receiver(pointerCNameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("thisRef", nullableAny)
                .addParameter("property", nullableAny)
                .addParameter("value", thisCName)
                .addStatement(
                    "%T.valueVarHandle.set(%M, _address, %T.toNativeData(value))",
                    baseType.caelumCoreTypeName,
                    CaelumCodegenHelper.omniSegment,
                    thisCName
                )
                .build()
        )
        addFunction(
            FunSpec.builder("getValue")
                .addAnnotation(randomName("getValue"))
                .receiver(valueCNameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("thisRef", nullableAny)
                .addParameter("property", nullableAny)
                .returns(thisCName)
                .addStatement(
                    "return %T.fromNativeData(%T.valueVarHandle.get(segment, 0L) as %T)",
                    thisCName,
                    baseType.caelumCoreTypeName,
                    baseType.ktApiTypeTypeName
                )
                .build()
        )
        addFunction(
            FunSpec.builder("setValue")
                .addAnnotation(randomName("setValue"))
                .receiver(valueCNameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("thisRef", nullableAny)
                .addParameter("property", nullableAny)
                .addParameter("value", thisCName)
                .addStatement(
                    "%T.valueVarHandle.set(segment, 0L, %T.toNativeData(value))",
                    baseType.caelumCoreTypeName,
                    thisCName
                )
                .build()
        )
    }
}
