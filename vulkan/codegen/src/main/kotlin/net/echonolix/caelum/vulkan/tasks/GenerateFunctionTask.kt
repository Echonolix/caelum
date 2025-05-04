package net.echonolix.caelum.vulkan.tasks

import com.squareup.kotlinpoet.*
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.filterTypeStream
import net.echonolix.caelum.codegen.api.generator.FunctionGenerator
import net.echonolix.caelum.codegen.api.task.CodegenTask
import net.echonolix.caelum.vulkan.VulkanCodegen
import net.echonolix.caelum.vulkan.OptionalTag
import net.echonolix.caelum.vulkan.OriginalFunctionNameTag
import kotlin.io.path.Path

class GenerateFunctionTask(ctx: CodegenContext) : CodegenTask<Unit>(ctx) {
    override fun CodegenContext.compute() {
        val funcPtrPath = Path("groups")
        ctx.filterTypeStream<CType.Function>()
            .filter { (_, funcType) -> funcType.name.startsWith("VkFuncPtr") }
            .map { (_, funcType) -> genFunc(funcType) }
            .forEach { ctx.writeOutput(funcPtrPath, it) }

        ctx.filterTypeStream<CType.Function>()
            .filter { (_, funcType) -> !funcType.name.startsWith("VkFuncPtr") }
            .map { (_, funcType) -> genFunc(funcType) }
            .partitionWrite("functions")
    }

    private fun CodegenContext.genFunc(funcType: CType.Function): FileSpec.Builder {
        val generator = object : FunctionGenerator(ctx, funcType) {
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
                return VulkanCodegen.vkFunctionCname
            }

            context(ctx: CodegenContext)
            override fun functionTypeDescriptorBaseCName(): ClassName {
                return VulkanCodegen.vkFunctionTypeDescriptorImplCname
            }

            context(ctx: CodegenContext)
            override fun nativeName(): String {
                return funcType.tags.get<OriginalFunctionNameTag>()!!.name
            }
        }
        return generator.build()
    }
}