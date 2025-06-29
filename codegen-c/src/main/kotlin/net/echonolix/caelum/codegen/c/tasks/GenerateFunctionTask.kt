package net.echonolix.caelum.codegen.c.tasks

import com.squareup.kotlinpoet.FileSpec
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.filterTypeStream
import net.echonolix.caelum.codegen.api.generator.FuncGenerator
import net.echonolix.caelum.codegen.api.generator.FuncPtrGenerator
import net.echonolix.caelum.codegen.api.task.CodegenTask

class GenerateFunctionTask(ctx: CodegenContext) : CodegenTask<Unit>(ctx) {
    override fun CodegenContext.compute() {
        ctx.filterTypeStream<CType.FunctionPointer>()
            .map { (_, funcType) ->
                FuncPtrGenerator(ctx, funcType.elementType).generate()
            }
            .forEach(ctx::writeOutput)
        val funcTypesFile = FileSpec.builder("${basePackageName}.functions", "FuncDescs")
        val globalFuncsFile = FileSpec.builder("${basePackageName}.functions", "GlobalFuncs")
        ctx.filterTypeStream<CType.Function>()
            .map { (_, funcType) ->
                val generator = FuncGenerator(ctx, funcType)
                generator.generateFuncDesc() to generator.generateGlobalFunc()
            }
            .sequential()
            .forEach { (funcDesc, globalFuncs) ->
                funcTypesFile.addProperty(funcDesc)
                globalFuncsFile.members.addAll(globalFuncs)
            }
        ctx.writeOutput(funcTypesFile)
        ctx.writeOutput(globalFuncsFile)
    }
}