package org.echonolix.vulkan

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import org.echonolix.ktffi.KTFFICodegen
import org.echonolix.vulkan.schema.Element
import org.echonolix.vulkan.schema.PatchedRegistry
import java.lang.foreign.MemoryLayout
import java.lang.foreign.UnionLayout

context(genCtx: FFIGenContext)
fun genUnion(registry: PatchedRegistry) {
    genCtx.newFile(FileSpec.builder(VKFFI.vkUnionCname))
        .addType(
            TypeSpec.classBuilder(VKFFI.vkUnionCname)
                .addModifiers(KModifier.SEALED)
                .superclass(KTFFICodegen.unionCname)
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter("layout", UnionLayout::class)
                        .build()
                )
                .addSuperclassConstructorParameter("layout")
                .build()
        )

    fun addUnion(struct: Element.Union) {
        val structClass = TypeSpec.objectBuilder(struct.name)
        with(structClass) {
            val structInfo = genCtx.getUnionInfo(registry, struct.name)
            superclass(VKFFI.vkUnionCname)
            addSuperclassConstructorParameter(
                CodeBlock.builder()
                    .add("\n")
                    .indent()
                    .addStatement("%M(", MemoryLayout::class.member("unionLayout"))
                    .add(structInfo.memoryLayoutInitializer.build())
                    .add(")\n")
                    .unindent()
                    .build()
            )
        }

        genCtx.newFile(FileSpec.builder(VKFFI.unionPackageName, struct.name))
            .addType(structClass.build())
    }

    val aliasesFile = genCtx.newFile(FileSpec.builder(VKFFI.unionPackageName, "UnionAliases"))

    registry.unionTypes.forEach { (name, struct) ->
        val aliasType = registry.aliasTypes[struct.name]
        if (aliasType != null) {
            aliasesFile.addTypeAlias(
                TypeAliasSpec.builder(struct.name, ClassName(VKFFI.unionPackageName, aliasType.name))
                    .build()
            )
        } else {
            addUnion(struct)
        }
    }
}