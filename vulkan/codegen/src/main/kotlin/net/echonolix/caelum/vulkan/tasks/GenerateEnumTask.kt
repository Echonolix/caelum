package net.echonolix.caelum.vulkan.tasks

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
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
        val thisCName = enumBase.className()
        addSuperinterface(superCName.parameterizedBy(thisCName))
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
                CaelumCodegenHelper.NEnum.descriptorCName.parameterizedBy(
                    thisCName,
                    baseType.nNativeDataType.nativeDataType
                )
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
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("value", flagType.baseType.ktApiTypeTypeName)
                .returns(thisCName)
                .addStatement("return %T(value)", thisCName)
                .build()
        )
        companion.addFunction(
            FunSpec.builder("toNativeData")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("value", thisCName)
                .returns(flagType.baseType.ktApiTypeTypeName)
                .addStatement("return value.value")
                .build()
        )

        val file = FileSpec.builder(thisCName)
        file.addType(type.addType(companion.build()).build())
        file.addNativeAccess(flagType)
        return file
    }

    private fun genEnumType(enumType: CType.Enum): FileSpec.Builder {
        val generator = object : EnumBaseGenerator(ctx, enumType) {
            context(ctx: CodegenContext)
            override fun enumBaseCName(): ClassName {
                return VulkanCodegen.vkEnumCName
            }
        }
        return generator.generate()
    }

    private val validChars = ('a'..'z').toList()

    context(ctx: CodegenContext)
    private fun TypeSpec.Builder.addCompanionSuper(enumBase: CType.EnumBase) {
        val thisCName = enumBase.className()
        addSuperinterface(
            CaelumCodegenHelper.NEnum.typeObjectCName.parameterizedBy(
                thisCName,
                enumBase.baseType.nNativeDataType.nativeDataType
            )
        )
        addSuperinterface(
            enumBase.baseType.nNativeDataType.nNativeDataCName.parameterizedBy(thisCName, thisCName),
            CodeBlock.of("%T.implOf()", enumBase.baseType.nNativeDataType.nNativeDataCName)
        )
    }

    context(ctx: CodegenContext)
    private fun FileSpec.Builder.addNativeAccess(enumBase: CType.EnumBase) {
        val thisCName = enumBase.className()
        val random = Random(0)

        val validChars = ('a'..'z').toList()

        fun randomName(base: String): AnnotationSpec {
            val randomChars = (0..4).map { validChars[random.nextInt(validChars.size)] }.joinToString("")
            return AnnotationSpec.builder(JvmName::class)
                .addMember("%S", "${thisCName.simpleName}_${base}_$randomChars")
                .build()
        }
        val overloadTypes = listOf(INT, UInt::class.asTypeName(), ULong::class.asTypeName())

        val returnTypeName = thisCName

        val arrayCNameP = CaelumCodegenHelper.arrayCName.parameterizedBy(thisCName)
        val valueCNameP = CaelumCodegenHelper.valueCName.parameterizedBy(thisCName)
        val pointerCNameP = CaelumCodegenHelper.pointerCName.parameterizedBy(thisCName)
        val nullableAny = Any::class.asClassName().copy(nullable = true)

        fun addGetOverloads(receiver: TypeName) {
            for (pType in overloadTypes) {
                addFunction(
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
                addFunction(
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
        addProperty(
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
                            "%T.pointerSetValue(this, %T.toNativeData(value))",
                            thisCName,
                            thisCName
                        )
                        .build()
                )
                .build()
        )

        addFunction(
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
        addGetOverloads(pointerCNameP)

        addFunction(
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
        addSetOverloads(pointerCNameP)

        addFunction(
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
        addFunction(
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

        addProperty(
            PropertySpec.builder("value", thisCName)
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
        addFunction(
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
        addFunction(
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


        addFunction(
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
        addGetOverloads(arrayCNameP)
        addFunction(
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
        addSetOverloads(arrayCNameP)
    }
}
