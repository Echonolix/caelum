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

    fun addUnion(union: Element.Union) {
        val unionInfo = genCtx.getUnionInfo(registry, union.name)
        val file = FileSpec.builder(VKFFI.unionPackageName, union.name)
        file.addFunctions(unionInfo.topLevelFunctions)
        file.addProperties(unionInfo.topLevelProperties)

        val structClass = TypeSpec.objectBuilder(unionInfo.cname)
        structClass.superclass(VKFFI.vkUnionCname)
        structClass.addSuperclassConstructorParameter(
            CodeBlock.builder()
                .add("\n")
                .indent()
                .addStatement("%M(", MemoryLayout::class.member("unionLayout"))
                .add(unionInfo.memoryLayoutInitializer.build())
                .add(")\n")
                .unindent()
                .build()
        )
        structClass.addProperties(unionInfo.properties)

        file.addType(structClass.build())
        genCtx.newFile(file)
    }

    val aliasesFile = genCtx.newFile(FileSpec.builder(VKFFI.unionPackageName, "UnionAliases"))

    registry.unionTypes.forEach { (name, struct) ->
        val aliasType = registry.aliasTypes[name]
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