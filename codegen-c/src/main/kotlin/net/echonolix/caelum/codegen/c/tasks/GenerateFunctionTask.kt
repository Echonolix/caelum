package net.echonolix.caelum.codegen.c.tasks

import com.squareup.kotlinpoet.FileSpec
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.filterTypeStream
import net.echonolix.caelum.codegen.api.generator.FunctionGenerator
import net.echonolix.caelum.codegen.api.task.CodegenTask

class GenerateFunctionTask(ctx: CodegenContext) : CodegenTask<Unit>(ctx) {
    override fun CodegenContext.compute() {
        ctx.filterTypeStream<CType.Function>()
            .map { (_, funcType) -> genFunc(funcType) }
            .forEach(ctx::writeOutput)
    }

    private fun genFunc(funcType: CType.Function): FileSpec.Builder {
        val generator = FunctionGenerator(ctx, funcType)
        return generator.generate()
    }
}