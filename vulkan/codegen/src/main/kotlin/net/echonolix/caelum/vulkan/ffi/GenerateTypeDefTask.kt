package net.echonolix.caelum.vulkan.ffi

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeAliasSpec
import net.echonolix.caelum.codegen.api.CType
import kotlin.io.path.Path

class GenerateTypeDefTask(ctx: VulkanCodeGenContext) : CaelumVulkanCodegenTask<Unit>(ctx) {
    override fun VulkanCodeGenContext.compute() {
        val typeDefFileBase = FileSpec.builder(CaelumVulkanCodegen.basePkgName, "TypeDefs")
        val typeDefFileFuncPtr = FileSpec.builder(CaelumVulkanCodegen.basePkgName, "FuncPtrTypeDefs")
        ctx.filterTypeStream<CType.TypeDef>().forEach { (_, typeDefType) ->
            val dstFile = if (typeDefType.dstType is CType.FunctionPointer) {
                typeDefFileFuncPtr
            } else {
                typeDefFileBase
            }
            dstFile.addTypeAlias(
                TypeAliasSpec.builder(typeDefType.name, typeDefType.dstType.typeName())
                    .build()
            )
        }

        ctx.writeOutput(Path("baseSrc"), typeDefFileBase)
        ctx.writeOutput(Path("groups"), typeDefFileFuncPtr)
    }
}