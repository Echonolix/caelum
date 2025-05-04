package net.echonolix.caelum.vulkan.ffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.CBasicType
import net.echonolix.caelum.CType
import net.echonolix.caelum.CaelumCodegenHelper
import net.echonolix.caelum.FunctionGenerator
import kotlin.io.path.Path

class GenerateFunctionTask(ctx: VulkanCodeGenContext) : VKFFITask<Unit>(ctx) {
    override fun VulkanCodeGenContext.compute() {
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

    private fun VulkanCodeGenContext.genFunc(funcType: CType.Function): FileSpec.Builder {
        val generator = object : FunctionGenerator<VulkanCodeGenContext>(ctx, funcType) {
            context(ctx: VulkanCodeGenContext)
            override fun toKtType(type: CType.Function.Parameter): TypeName {
                var pType = type.type.ktApiType()
                if (type.type is CType.Pointer && type.tags.has<OptionalTag>()) {
                    pType = pType.copy(nullable = true)
                }
                return pType
            }

            context(ctx: VulkanCodeGenContext)
            override fun functionBaseCName(): ClassName {
                return VKFFI.vkFunctionCname
            }

            context(ctx: VulkanCodeGenContext)
            override fun functionTypeDescriptorBaseCName(): ClassName {
                return VKFFI.vkFunctionTypeDescriptorImplCname
            }

            context(ctx: VulkanCodeGenContext)
            override fun nativeName(): String {
                return funcType.tags.get<OriginalFunctionNameTag>()!!.name
            }
        }
        return generator.build()
    }
}