package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeAliasSpec
import net.echonolix.ktffi.CType

class GenerateTypeDefTask(ctx: VKFFICodeGenContext) : VKFFITask<Unit>(ctx) {
    override fun VKFFICodeGenContext.compute() {
        val typeDefFile = FileSpec.builder(VKFFI.basePkgName, "TypeDefs")
        ctx.filterTypeStream<CType.TypeDef>().forEach { (_, typeDefType) ->
            typeDefFile.addTypeAlias(
                TypeAliasSpec.builder(typeDefType.name, typeDefType.dstType.typeName())
                    .build()
            )
        }

        ctx.writeOutput(typeDefFile)
    }
}