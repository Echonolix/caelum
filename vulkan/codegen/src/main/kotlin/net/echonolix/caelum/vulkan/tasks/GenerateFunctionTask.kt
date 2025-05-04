package net.echonolix.caelum.vulkan.tasks

import com.squareup.kotlinpoet.*
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.generator.FunctionGenerator
import net.echonolix.caelum.vulkan.VulkanCodegen
import net.echonolix.caelum.vulkan.OptionalTag
import net.echonolix.caelum.vulkan.OriginalFunctionNameTag
import net.echonolix.caelum.vulkan.VulkanCodegenContext
import kotlin.io.path.Path

class GenerateFunctionTask(ctx: VulkanCodegenContext) : VulkanCodegenTask<Unit>(ctx) {
    override fun VulkanCodegenContext.compute() {
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

    private fun VulkanCodegenContext.genFunc(funcType: CType.Function): FileSpec.Builder {
        val generator = object : FunctionGenerator<VulkanCodegenContext>(ctx, funcType) {
            context(ctx: VulkanCodegenContext)
            override fun toKtType(type: CType.Function.Parameter): TypeName {
                var pType = type.type.ktApiType()
                if (type.type is CType.Pointer && type.tags.has<OptionalTag>()) {
                    pType = pType.copy(nullable = true)
                }
                return pType
            }

            context(ctx: VulkanCodegenContext)
            override fun functionBaseCName(): ClassName {
                return VulkanCodegen.vkFunctionCname
            }

            context(ctx: VulkanCodegenContext)
            override fun functionTypeDescriptorBaseCName(): ClassName {
                return VulkanCodegen.vkFunctionTypeDescriptorImplCname
            }

            context(ctx: VulkanCodegenContext)
            override fun nativeName(): String {
                return funcType.tags.get<OriginalFunctionNameTag>()!!.name
            }
        }
        return generator.build()
    }
}