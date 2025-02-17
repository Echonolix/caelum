package org.echonolix.vulkan

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import org.echonolix.ktffi.KTFFICodegen
import org.echonolix.vulkan.schema.Element
import org.echonolix.vulkan.schema.PatchedRegistry
import java.lang.foreign.MemoryLayout
import java.lang.foreign.StructLayout

context(genCtx: FFIGenContext)
fun genStruct(registry: PatchedRegistry) {
    genCtx.newFile(FileSpec.builder(VKFFI.vkStructCname))
        .addType(
            TypeSpec.classBuilder(VKFFI.vkStructCname)
                .addModifiers(KModifier.SEALED)
                .superclass(KTFFICodegen.structCname)
                .primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameter("layout", StructLayout::class)
                        .build()
                )
                .addSuperclassConstructorParameter("layout")
                .build()
        )

    fun addStruct(struct: Element.Struct) {
        val structInfo = genCtx.getStructInfo(registry, struct.name)
        val file = FileSpec.builder(VKFFI.structPackageName, struct.name)
        file.addFunctions(structInfo.topLevelFunctions)
        file.addProperties(structInfo.topLevelProperties)

        val structClass = TypeSpec.objectBuilder(structInfo.cname)
        structClass.tryAddKdoc(struct)
        structClass.superclass(VKFFI.vkStructCname)
        structClass.addSuperclassConstructorParameter(
            CodeBlock.builder()
                .add("\n")
                .indent()
                .addStatement("%M(", MemoryLayout::class.member("structLayout"))
                .add(structInfo.memoryLayoutInitializer.build())
                .add(")\n")
                .unindent()
                .build()
        )
        structClass.addProperties(structInfo.properties)

        file.addType(structClass.build())
        genCtx.newFile(file)
    }

    val aliasesFile = genCtx.newFile(FileSpec.builder(VKFFI.structPackageName, "StructAliases"))

    registry.structTypes.forEach { (name, struct) ->
        val aliasType = registry.aliasTypes[name]
        if (aliasType != null) {
            aliasesFile.addTypeAlias(
                TypeAliasSpec.builder(name, ClassName(VKFFI.structPackageName, aliasType.name))
                    .build()
            )
        } else {
            addStruct(struct)
        }
    }
}