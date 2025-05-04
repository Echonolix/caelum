package net.echonolix.caelum.codegen.api.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.codegen.api.CExpression
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.CaelumCodegenHelper
import net.echonolix.caelum.codegen.api.EnumEntryFixedName
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.addKdoc

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


    context(ctx: CodegenContext)
    protected fun TypeSpec.Builder.addCompanionSuper(enumBase: CType.EnumBase) {
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
}