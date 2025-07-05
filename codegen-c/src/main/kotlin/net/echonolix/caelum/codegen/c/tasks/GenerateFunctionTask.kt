package net.echonolix.caelum.codegen.c.tasks

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.CaelumCodegenHelper
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.filterTypeStream
import net.echonolix.caelum.codegen.api.generator.FuncGenerator
import net.echonolix.caelum.codegen.api.generator.FuncPtrGenerator
import net.echonolix.caelum.codegen.api.task.CodegenTask

class GenerateFunctionTask(ctx: CodegenContext) : CodegenTask<Unit>(ctx) {
    override fun CodegenContext.compute() {
        val functionBaseCName: ClassName
        val functionTypeDescriptorBaseCName: ClassName

        val baseTypeNameStr = System.getProperty("codegenc.functionBaseTypeName")
        if (baseTypeNameStr.isNullOrBlank()) {
            functionBaseCName = CaelumCodegenHelper.NFunction.cName
            functionTypeDescriptorBaseCName = CaelumCodegenHelper.NFunction.typeDescriptorCName
        } else {
            val dotIndex = baseTypeNameStr.lastIndexOf('.')
            functionBaseCName =
                ClassName(baseTypeNameStr.substring(0, dotIndex), baseTypeNameStr.substring(dotIndex + 1))
            functionTypeDescriptorBaseCName = functionBaseCName.nestedClass("Descriptor")
        }

        ctx.filterTypeStream<CType.FunctionPointer>()
            .map { (_, funcType) ->
                val generator = object : FuncPtrGenerator(ctx, funcType.elementType) {
                    context(ctx: CodegenContext)
                    override fun functionBaseCName(): ClassName {
                        return functionBaseCName
                    }

                    context(ctx: CodegenContext)
                    override fun functionTypeDescriptorBaseCName(): ClassName {
                        return functionTypeDescriptorBaseCName
                    }
                }
                generator.generate()
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