package net.echonolix.caelum.vulkan.ffi

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeAliasSpec
import net.echonolix.caelum.CType
import kotlin.io.path.Path

class GenerateTypeDefTask(ctx: VKFFICodeGenContext) : VKFFITask<Unit>(ctx) {
    override fun VKFFICodeGenContext.compute() {
        val typeDefFileBase = FileSpec.builder(VKFFI.basePkgName, "TypeDefs")
        val typeDefFileFuncPtr = FileSpec.builder(VKFFI.basePkgName, "FuncPtrTypeDefs")
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