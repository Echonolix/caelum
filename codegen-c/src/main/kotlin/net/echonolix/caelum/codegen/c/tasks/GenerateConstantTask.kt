package net.echonolix.caelum.codegen.c.tasks

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import net.echonolix.caelum.codegen.api.CTopLevelConst
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.filterTypeStream
import net.echonolix.caelum.codegen.api.task.CodegenTask

class GenerateConstantTask(ctx: CodegenContext) : CodegenTask<Unit>(ctx) {
    override fun CodegenContext.compute() {
        val file = FileSpec.builder("${ctx.basePackageName}.consts", "Constants")
        file.addProperties(
            ctx.filterTypeStream<CTopLevelConst>()
            .map { (_, const) -> genConstantProperty(const) }
                .sequential()
            .toList()
        )
        ctx.writeOutput(file)
    }

    context(_: CodegenContext)
    private fun genConstantProperty(const: CTopLevelConst): PropertySpec {
        var codeBlock = const.expression.codeBlock()
        codeBlock.toString().removePrefix("0x").toLongOrNull(16)?.let { num ->
            if (num > 0x7FFFFFFF)  {
                codeBlock = CodeBlock.builder()
                    .add(codeBlock)
                    .add(".toInt()")
                    .build()
            }
        }
        return PropertySpec.builder(const.name, const.type.ktApiType())
            .addModifiers(KModifier.CONST)
            .initializer(
                CodeBlock.builder()
                    .add(codeBlock)
                    .build()
            )
            .build()
    }
}