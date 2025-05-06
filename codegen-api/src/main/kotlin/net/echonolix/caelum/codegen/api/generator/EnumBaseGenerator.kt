package net.echonolix.caelum.codegen.api.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.codegen.api.CExpression
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.CaelumCodegenHelper
import net.echonolix.caelum.codegen.api.EnumEntryFixedName
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.addKdoc
import kotlin.random.Random

public open class EnumBaseGenerator(
    ctx: CodegenContext,
    element: CType.EnumBase
) : Generator<CType.EnumBase>(ctx, element) {
    context(ctx: CodegenContext)
    protected val entries: Collection<CType.EnumBase.Entry> get() = element.entries.values

    context(ctx: CodegenContext)
    protected open fun enumBaseCName(): TypeName {
        return CaelumCodegenHelper.enumCName
    }

    context(ctx: CodegenContext)
    protected open fun buildType(): TypeSpec.Builder {
        val type = TypeSpec.enumBuilder(thisCName)
        type.addKdoc(element)
        type.addSuper(element)
        return type
    }

    context(ctx: CodegenContext)
    protected open fun buildCompanionType(type: TypeSpec.Builder): TypeSpec.Builder {
        val companionType = TypeSpec.companionObjectBuilder()
        val internalAliases = mutableListOf<PropertySpec>()
        entries.asSequence()
            .forEach { entry ->
                val expression = entry.expression
                val fixedName = entry.tags.get<EnumEntryFixedName>()!!.name
                when (expression) {
                    is CExpression.Const -> {
                        type.addEnumConstant(
                            fixedName,
                            TypeSpec.anonymousClassBuilder()
                                .addKdoc(entry)
                                .superclass(thisCName)
                                .addSuperclassConstructorParameter(entry.expression.codeBlock())
                                .build()
                        )
                    }
                    is CExpression.Reference -> {
                        assert(expression.value in entries)
                        companionType.addProperty(
                            PropertySpec.builder(fixedName, thisCName)
                                .initializer("%N", expression.value.name)
                                .addKdoc(entry)
                                .build()
                        )
                    }
                    else -> throw IllegalArgumentException("Unsupported expression type: ${expression::class}")
                }
                if (entry.name != fixedName) {
                    internalAliases += PropertySpec.builder(entry.name, thisCName)
                        .addModifiers(KModifier.INTERNAL)
                        .getter(
                            FunSpec.getterBuilder()
                                .addStatement("return %N", fixedName)
                                .build()
                        )
                        .build()
                }
            }
        companionType.addProperties(internalAliases)

        companionType.addCompanionSuper(element)
        companionType.addFunction(
            FunSpec.builder("fromNativeData")
                .addAnnotation(JvmStatic::class)
                .addParameter(
                    "value",
                    element.baseType.ktApiTypeTypeName
                )
                .returns(thisCName)
                .addCode(
                    CodeBlock.builder()
                        .beginControlFlow("return when (value)")
                        .apply {
                            entries.asSequence()
                                .filter { it.expression is CExpression.Const }
                                .forEach { entry ->
                                    addStatement(
                                        "%L -> %T.%N",
                                        entry.expression.codeBlock(),
                                        thisCName,
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
        companionType.addFunction(
            FunSpec.builder("toNativeData")
                .addAnnotation(JvmStatic::class)
                .addParameter("value", thisCName)
                .returns(element.baseType.ktApiTypeTypeName)
                .addStatement("return value.value")
                .build()
        )
        return companionType
    }

    public final override fun generate(): FileSpec.Builder {
        with(ctx) {
            val type = buildType()
            val companionType = buildCompanionType(type)
            type.addType(companionType.build())
            val file = FileSpec.builder(thisCName)
            file.addType(type.build())
            file.addNativeAccess()
            return file
        }
    }

    context(ctx: CodegenContext)
    protected fun TypeSpec.Builder.addSuper(enumBase: CType.EnumBase): TypeSpec.Builder {
        val baseType = enumBase.baseType
        val superCName = enumBaseCName()
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
                CaelumCodegenHelper.NType.descriptorCName.parameterizedBy(enumBase.typeName())
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

    context(ctx: CodegenContext)
    private fun FileSpec.Builder.addNativeAccess() {
        val baseType = element.baseType
        val random = Random(0)

        val validChars = ('a'..'z').toList()

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


    context(ctx: CodegenContext)
    protected fun TypeSpec.Builder.addCompanionSuper(enumBase: CType.EnumBase) {
        addAnnotation(
            AnnotationSpec.builder(Suppress::class)
                .addMember("%S", "UNCHECKED_CAST")
                .build()
        )
        val thisCName = enumBase.className()
        addSuperinterface(
            CaelumCodegenHelper.NType.descriptorCName.parameterizedBy(thisCName),
            CodeBlock.of(
                "%T.typeDescriptor as %T<%T>",
                enumBase.baseType.caelumCoreTypeName,
                CaelumCodegenHelper.NType.descriptorCName,
                thisCName
            )
        )
    }
}