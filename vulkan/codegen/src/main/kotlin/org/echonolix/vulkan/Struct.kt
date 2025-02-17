package org.echonolix.vulkan

import com.squareup.kotlinpoet.*
import org.echonolix.ktffi.KTFFICodegen
import org.echonolix.vulkan.schema.Element
import org.echonolix.vulkan.schema.PatchedRegistry

context(genCtx: FFIGenContext)
fun genStruct(registry: PatchedRegistry) {
    genCtx.newFile(FileSpec.builder(VKFFI.vkStructCname))
        .addType(
            TypeSpec.interfaceBuilder(VKFFI.vkStructCname)
                .addSuperinterface(KTFFICodegen.structCname)
                .addModifiers(KModifier.SEALED)
                .build()
        )

    fun addStruct(struct: Element.Struct) {
        val structClass = TypeSpec.objectBuilder(struct.name)
        with(structClass) {
            addSuperinterface(VKFFI.vkStructCname)
        }

        genCtx.newFile(FileSpec.builder(VKFFI.structPackage, struct.name))
            .addType(structClass.build())
    }

    registry.structTypes.values.forEach { struct ->
        addStruct(struct)
    }
}