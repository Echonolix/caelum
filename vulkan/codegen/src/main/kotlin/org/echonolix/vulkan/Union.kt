package org.echonolix.vulkan

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.TypeSpec
import org.echonolix.ktffi.KTFFICodegen
import org.echonolix.vulkan.schema.Element
import org.echonolix.vulkan.schema.PatchedRegistry
import java.lang.foreign.MemoryLayout

context(genCtx: FFIGenContext)
fun genUnion(registry: PatchedRegistry) {
    genCtx.newFile(FileSpec.builder(VKFFI.vkStructCname))
        .addType(
            TypeSpec.interfaceBuilder(VKFFI.vkStructCname)
                .addSuperinterface(KTFFICodegen.structCname)
                .addModifiers(KModifier.SEALED)
                .build()
        )

    fun addUnion(struct: Element.Union) {
        val structClass = TypeSpec.objectBuilder(struct.name)
        with(structClass) {
            val structInfo = genCtx.getUnionInfo(registry, struct.name)
            superclass(VKFFI.vkStructCname)
            addSuperclassConstructorParameter(
                CodeBlock.builder()
                    .addStatement("%M(", MemoryLayout::class.member("unionLayout"))
                    .add(structInfo.memoryLayoutInitializer.build())
                    .addStatement(")")
                    .build()
            )
        }

        genCtx.newFile(FileSpec.builder(VKFFI.structPackageName, struct.name))
            .addType(structClass.build())
    }

    registry.unionTypes.values.forEach { struct ->
        addUnion(struct)
    }
}