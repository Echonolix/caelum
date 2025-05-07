package net.echonolix.caelum.codegen.api.generator

import com.squareup.kotlinpoet.*
import net.echonolix.caelum.codegen.api.CExpression
import net.echonolix.caelum.codegen.api.CSyntax
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.EnumEntryFixedName
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.addKdoc

public open class FlagGenerator(ctx: CodegenContext, element: CType.EnumBase) : EnumBaseGenerator(ctx, element) {
    context(ctx: CodegenContext)
    override fun buildType(): TypeSpec.Builder {
        val type = TypeSpec.classBuilder(thisCName)
        type.addKdoc(element)
        type.addSuper(element)

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

        return type
    }

    context(ctx: CodegenContext)
    override fun buildCompanionType(): TypeSpec.Builder {
        val companion = TypeSpec.companionObjectBuilder()
        entries.forEach {
            val expression = it.expression
            val code = expression.codeBlock()
            val initilizer = when (expression) {
                is CExpression.Const -> {
                    CodeBlock.of("%T(%L)", thisCName, code)
                }
                is CExpression.Reference -> {
                    assert(expression.value in entries)
                    CodeBlock.of("%N", expression.value.tags.get<EnumEntryFixedName>().name)
                }
                else -> throw IllegalArgumentException("Unsupported expression type: ${expression::class}")
            }
            val fixedName = it.tags.get<EnumEntryFixedName>().name
            companion.addProperty(
                PropertySpec.builder(fixedName, thisCName)
                    .initializer(initilizer)
                    .addKdoc(it)
                    .build()
            )
        }

        if (entries.none {
                CSyntax.intLiteralRegex.matchEntire(
                    it.expression.codeBlock().toString()
                )?.groupValues[2] == "0"
            }) {
            companion.addProperty(
                PropertySpec.builder("NONE", thisCName)
                    .initializer("%T(0)", thisCName)
                    .build()
            )
        }

        companion.addCompanionSuper(element)
        companion.addFunction(
            FunSpec.builder("fromNativeData")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("value", element.baseType.ktApiTypeTypeName)
                .returns(thisCName)
                .addStatement("return %T(value)", thisCName)
                .build()
        )
        companion.addFunction(
            FunSpec.builder("toNativeData")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("value", thisCName)
                .returns(element.baseType.ktApiTypeTypeName)
                .addStatement("return value.value")
                .build()
        )

        return companion
    }
}