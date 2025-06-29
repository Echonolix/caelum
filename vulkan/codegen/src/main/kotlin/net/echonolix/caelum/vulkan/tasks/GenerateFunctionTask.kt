package net.echonolix.caelum.vulkan.tasks

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeName
import net.echonolix.caelum.codegen.api.CElement
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.OriginalNameTag
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.filterTypeStream
import net.echonolix.caelum.codegen.api.generator.FuncGenerator
import net.echonolix.caelum.codegen.api.generator.FuncPtrGenerator
import net.echonolix.caelum.codegen.api.task.CodegenTask
import net.echonolix.caelum.vulkan.OptionalTag
import net.echonolix.caelum.vulkan.VulkanCodegen
import kotlin.io.path.Path

class GenerateFunctionTask(ctx: CodegenContext) : CodegenTask<Unit>(ctx) {
    context(ctx: CodegenContext)
    private fun CElement.nativeName(): String {
        return tags.getOrNull<OriginalNameTag>()?.name ?: name
    }

    override fun CodegenContext.compute() {
        val funcPtrPath = Path("groups")
        ctx.filterTypeStream<CType.Function>()
            .filter { (_, funcType) -> funcType.name.startsWith("VkFuncPtr") }
            .map { (_, funcType) -> genFuncPtr(funcType) }
            .forEach { ctx.writeOutput(funcPtrPath, it) }

        val funcTypesFile = FileSpec.builder("${basePackageName}.functions", "FuncDescs")
        val globalFuncsFile = FileSpec.builder("${basePackageName}.functions", "GlobalFuncs")
        ctx.filterTypeStream<CType.Function>()
            .filter { (name, funcType) -> name == funcType.nativeName() }
            .filter { (_, funcType) -> !funcType.name.startsWith("VkFuncPtr") }
            .map { (_, funcType) -> val generator = FuncGenerator(ctx, funcType)
                generator.generateFuncDesc()  to generator.generateGlobalFunc()
            }
            .sequential()
            .forEach { (funcDesc, globalFuncs) ->
                funcTypesFile.addProperty(funcDesc)
                globalFuncsFile.members.addAll(globalFuncs)
            }
        val objectBasePath = Path("objectBase")
        ctx.writeOutput(objectBasePath, funcTypesFile)
        ctx.writeOutput(objectBasePath,globalFuncsFile)
    }

    private fun CodegenContext.genFuncPtr(funcType: CType.Function): FileSpec.Builder {
        val generator = object : FuncPtrGenerator(ctx, funcType) {
            context(ctx: CodegenContext)
            override fun toKtType(type: CType.Function.Parameter): TypeName {
                var pType = type.type.ktApiType()
                if (type.type is CType.Pointer && type.tags.has<OptionalTag>()) {
                    pType = pType.copy(nullable = true)
                }
                return pType
            }

            context(ctx: CodegenContext)
            override fun functionBaseCName(): ClassName {
                return VulkanCodegen.vkFunctionCName
            }

            context(ctx: CodegenContext)
            override fun functionTypeDescriptorBaseCName(): ClassName {
                return VulkanCodegen.vkFunctionTypeDescriptorCName
            }
        }
        return generator.generate()
    }
}