package org.echonolix.vulkan

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import org.echonolix.ktffi.KTFFICodegen
import org.echonolix.vulkan.schema.Element
import org.echonolix.vulkan.schema.PatchedRegistry
import java.lang.foreign.MemoryLayout
import java.lang.foreign.StructLayout
import java.lang.foreign.UnionLayout

context(genCtx: FFIGenContext)
fun genGroups(registry: PatchedRegistry) {
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

    val structAliases = genCtx.newFile(FileSpec.builder(VKFFI.structPackageName, "StructAliases"))
    val unionAliases = genCtx.newFile(FileSpec.builder(VKFFI.unionPackageName, "UnionAliases"))

    fun addGroup(name: String, groupType: Element.Group) {
        val groupInfo = genCtx.getGroupInfo(registry, groupType)
        val packageName: String
        val superCname: ClassName
        val memoryLayoutMember: MemberName
        when (groupType) {
            is Element.Struct -> {
                packageName = VKFFI.structPackageName
                superCname = VKFFI.vkStructCname
                memoryLayoutMember = MemoryLayout::class.member("structLayout")
            }
            is Element.Union -> {
                packageName = VKFFI.unionPackageName
                superCname = VKFFI.vkUnionCname
                memoryLayoutMember = MemoryLayout::class.member("unionLayout")
            }
        }

        val aliasType = registry.aliasTypes[name]
        if (aliasType != null) {
            val typeAliasSpce = TypeAliasSpec.builder(groupType.name, ClassName(packageName, aliasType.name))
                .build()
            val aliasesFile = when (groupType) {
                is Element.Struct -> structAliases
                is Element.Union -> unionAliases
            }
            aliasesFile.addTypeAlias(typeAliasSpce)
            return
        }

        val file = FileSpec.builder(packageName, groupType.name)
        file.addFunctions(groupInfo.topLevelFunctions)
        file.addProperties(groupInfo.topLevelProperties)

        val structClass = TypeSpec.objectBuilder(groupInfo.cname)
        structClass.tryAddKdoc(groupType)
        structClass.superclass(superCname)
        structClass.addSuperclassConstructorParameter(
            CodeBlock.builder()
                .add("\n")
                .indent()
                .addStatement("%M(", memoryLayoutMember)
                .add(groupInfo.layoutInitializer.build())
                .add(")\n")
                .unindent()
                .build()
        )
        structClass.addProperties(groupInfo.properties)

        file.addType(structClass.build())
        genCtx.newFile(file)
    }

    registry.groupTypes.asSequence()
        .filter { (_, groupType) -> groupType.requiredBy!! == "Vulkan 1.0" }
        .toList()
        .parallelStream()
        .forEach { (name, groupType) ->
            addGroup(name, groupType)
        }
}