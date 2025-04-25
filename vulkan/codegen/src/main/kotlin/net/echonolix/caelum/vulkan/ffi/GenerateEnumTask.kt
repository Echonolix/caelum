package net.echonolix.caelum.vulkan.ffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.*
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
            val flagTypes = ctx.filterTypeStream<CType.Bitmask>()
                .filter { !it.first.contains("FlagBits") }
                .toList()
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
                        .addSuperinterface(CaelumCodegenHelper.typeCname)
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
                    TypeSpec.interfaceBuilder(VKFFI.vkFlags64CNAME)
                        .addModifiers(KModifier.SEALED)
                        .addSuperinterface(VKFFI.vkEnumBaseCname.parameterizedBy(Long::class.asTypeName()))
                        .addProperty(
                            PropertySpec.builder("nativeType", NativeType::class)
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
            ctx.writeOutput(vkFlagFile)

            val constantsFile = FileSpec.builder(VKFFI.basePkgName, "Constants")
            constantsFile.addProperties(
                ctx.filterTypeStream<CTopLevelConst>()
                    .filter { !VKFFI.vkVersionConstRegex.matches(it.first) }
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
                CaelumCodegenHelper.typeDescriptorCname.parameterizedBy(enumBase.typeName())
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
                .addModifiers(KModifier.OPERATOR)
                .addParameter("other", thisCname)
                .returns(Boolean::class)
                .addStatement("return (value and other.value == other.value)")
                .build()
        )

        val companion = TypeSpec.companionObjectBuilder()
        val flagBitEntries = flagType.entries.values.sortedBy { ctx.registry.enumValueOrders[it.name] }
        val internalAliases = mutableListOf<PropertySpec>()
        flagBitEntries.forEach {
            val expression = it.expression
            val code = expression.codeBlock()
            val initilizer = when (expression) {
                is CExpression.Const -> {
                    CodeBlock.of("%T(%L)", thisCname, code)
                }
                is CExpression.Reference -> {
                    check(expression.value.name in flagType.entries)
                    CodeBlock.of("%N", expression.value.name)
                }
                else -> throw IllegalArgumentException("Unsupported expression type: ${expression::class}")
            }
            val fixedName = it.tags.get<EnumEntryFixedName>()!!.name
            companion.addProperty(
                PropertySpec.builder(fixedName, thisCname)
                    .initializer(initilizer)
                    .tryAddKdoc(it)
                    .build()
            )
            if (it.name != fixedName) {
                internalAliases += PropertySpec.builder(it.name, thisCname)
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
                PropertySpec.builder("NONE", thisCname)
                    .initializer("%T(0)", thisCname)
                    .build()
            )
        }

        companion.addCompanionSuper(flagType)
        companion.addFunction(
            FunSpec.builder("fromNativeData")
                .addAnnotation(JvmStatic::class)
                .addParameter("value", flagType.baseType.ktApiTypeTypeName)
                .returns(thisCname)
                .addStatement("return %T(value)", thisCname)
                .build()
        )
        companion.addFunction(
            FunSpec.builder("toNativeData")
                .addAnnotation(JvmStatic::class)
                .addParameter("value", thisCname)
                .returns(flagType.baseType.ktApiTypeTypeName)
                .addStatement("return value.value")
                .build()
        )

        val file = FileSpec.builder(thisCname)
        file.addType(type.addType(companion.build()).build())
        file.addNativeAccess(thisCname, flagType.baseType)
        return file
    }

    private fun VKFFICodeGenContext.genEnumType(enumType: CType.Enum): FileSpec.Builder {
        val thisCname = enumType.className()

        val type = TypeSpec.enumBuilder(thisCname)
        type.tryAddKdoc(enumType)
        type.addSuper(enumType)

        val companion = TypeSpec.companionObjectBuilder()
        val internalAliases = mutableListOf<PropertySpec>()
        enumType.entries.values.asSequence()
            .sortedBy { ctx.registry.enumValueOrders[it.name] }
            .forEach { entry ->
                val expression = entry.expression
                val fixedName = entry.tags.get<EnumEntryFixedName>()!!.name
                when (expression) {
                    is CExpression.Const -> {
                        type.addEnumConstant(
                            fixedName,
                            TypeSpec.anonymousClassBuilder()
                                .tryAddKdoc(entry)
                                .superclass(thisCname)
                                .addSuperclassConstructorParameter(entry.expression.codeBlock())
                                .build()
                        )
                    }
                    is CExpression.Reference -> {
                        check(expression.value.name in enumType.entries)
                        companion.addProperty(
                            PropertySpec.builder(fixedName, thisCname)
                                .initializer("%N", expression.value.name)
                                .tryAddKdoc(entry)
                                .build()
                        )
                    }
                    else -> throw IllegalArgumentException("Unsupported expression type: ${expression::class}")
                }
                if (entry.name != fixedName) {
                    internalAliases += PropertySpec.builder(entry.name, thisCname)
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

        companion.addCompanionSuper(enumType)
        companion.addFunction(
            FunSpec.builder("fromNativeData")
                .addAnnotation(JvmStatic::class)
                .addParameter(
                    "value",
                    enumType.baseType.ktApiTypeTypeName
                )
                .returns(thisCname)
                .addCode(
                    CodeBlock.builder()
                        .beginControlFlow("return when (value)")
                        .apply {
                            enumType.entries.values.asSequence()
                                .sortedBy { ctx.registry.enumValueOrders[it.name] }
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
            FunSpec.builder("toNativeData")
                .addAnnotation(JvmStatic::class)
                .addParameter("value", thisCname)
                .returns(enumType.baseType.ktApiTypeTypeName)
                .addStatement("return value.value")
                .build()
        )

        val file = FileSpec.builder(thisCname)
        file.addType(type.addType(companion.build()).build())
        file.addNativeAccess(thisCname, enumType.baseType)
        return file
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
            CaelumCodegenHelper.typeDescriptorCname.parameterizedBy(thisCname),
            CodeBlock.of(
                "%T.typeDescriptor as %T<%T>",
                enumBase.baseType.caelumCoreTypeName,
                CaelumCodegenHelper.typeDescriptorCname,
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

        val pointerCnameP = CaelumCodegenHelper.pointerCname.parameterizedBy(thisCname)
        val arrayCnameP = CaelumCodegenHelper.arrayCname.parameterizedBy(thisCname)
        val valueCNameP = CaelumCodegenHelper.valueCname.parameterizedBy(thisCname)
        val nullableAny = Any::class.asClassName().copy(nullable = true)

        addFunction(
            FunSpec.builder("get")
                .receiver(arrayCnameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("index", LONG)
                .returns(thisCname)
                .addStatement(
                    "return %T.fromNativeData(%T.arrayVarHandle.get(_segment, 0L, index) as %T)",
                    thisCname,
                    baseType.caelumCoreTypeName,
                    baseType.ktApiTypeTypeName
                )
                .build()
        )
        addFunction(
            FunSpec.builder("set")
                .receiver(arrayCnameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("index", LONG)
                .addParameter("value", thisCname)
                .addStatement(
                    "%T.arrayVarHandle.set(_segment, 0L, index, %T.toNativeData(value))",
                    baseType.caelumCoreTypeName,
                    thisCname,
                )
                .build()
        )
        addFunction(
            FunSpec.builder("get")
                .receiver(pointerCnameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("index", LONG)
                .returns(thisCname)
                .addStatement(
                    "return %T.fromNativeData(%T.arrayVarHandle.get(%M, _address, index) as %T)",
                    thisCname,
                    baseType.caelumCoreTypeName,
                    CaelumCodegenHelper.omniSegment,
                    baseType.ktApiTypeTypeName
                )
                .build()
        )
        addFunction(
            FunSpec.builder("set")
                .receiver(pointerCnameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("index", LONG)
                .addParameter("value", thisCname)
                .addStatement(
                    "%T.arrayVarHandle.set(%M, _address, index, %T.toNativeData(value))",
                    baseType.caelumCoreTypeName,
                    CaelumCodegenHelper.omniSegment,
                    thisCname
                )
                .build()
        )
        addFunction(
            FunSpec.builder("getValue")
                .addAnnotation(randomName("getValue"))
                .receiver(pointerCnameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("thisRef", nullableAny)
                .addParameter("property", nullableAny)
                .returns(thisCname)
                .addStatement(
                    "return %T.fromNativeData(%T.valueVarHandle.get(%M, _address) as %T)",
                    thisCname,
                    baseType.caelumCoreTypeName,
                    CaelumCodegenHelper.omniSegment,
                    baseType.ktApiTypeTypeName
                )
                .build()
        )
        addFunction(
            FunSpec.builder("setValue")
                .addAnnotation(randomName("setValue"))
                .receiver(pointerCnameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("thisRef", nullableAny)
                .addParameter("property", nullableAny)
                .addParameter("value", thisCname)
                .addStatement(
                    "%T.valueVarHandle.set(%M, _address, %T.toNativeData(value))",
                    baseType.caelumCoreTypeName,
                    CaelumCodegenHelper.omniSegment,
                    thisCname
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
                .returns(thisCname)
                .addStatement(
                    "return %T.fromNativeData(%T.valueVarHandle.get(_segment, 0L) as %T)",
                    thisCname,
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
                .addParameter("value", thisCname)
                .addStatement(
                    "%T.valueVarHandle.set(_segment, 0L, %T.toNativeData(value))",
                    baseType.caelumCoreTypeName,
                    thisCname
                )
                .build()
        )
    }
}
