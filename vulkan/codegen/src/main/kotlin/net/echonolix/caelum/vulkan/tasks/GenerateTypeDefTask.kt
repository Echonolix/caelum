package net.echonolix.caelum.vulkan.tasks

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeAliasSpec
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.filterTypeStream
import net.echonolix.caelum.codegen.api.task.CodegenTask
import net.echonolix.caelum.vulkan.VulkanCodegen
import kotlin.io.path.Path

class GenerateTypeDefTask(ctx: CodegenContext) : CodegenTask<Unit>(ctx) {
    override fun CodegenContext.compute() {
        val typeDefFileBase = FileSpec.builder(VulkanCodegen.basePkgName, "TypeDefs")
        val typeDefFileFuncPtr = FileSpec.builder(VulkanCodegen.basePkgName, "FuncPtrTypeDefs")
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