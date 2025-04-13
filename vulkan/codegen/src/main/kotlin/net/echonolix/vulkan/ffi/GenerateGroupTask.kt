package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.KTFFICodegenHelper

class GenerateGroupTask(ctx: VKFFICodeGenContext) : VKFFITask<Unit>(ctx) {
    override fun VKFFICodeGenContext.compute() {
        val struct = StructTask().fork()
        val union = UnionTask().fork()

        val unionTypeVar = TypeVariableName("T", VKFFI.vkUnionCname.parameterizedBy(TypeVariableName("T")))
        val vkUnionFile = FileSpec.builder(VKFFI.vkUnionCname)
            .addType(
                TypeSpec.classBuilder(VKFFI.vkUnionCname)
                    .addTypeVariable(unionTypeVar)
                    .addModifiers(KModifier.SEALED)
                    .superclass(KTFFICodegenHelper.unionCname.parameterizedBy(unionTypeVar))
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter("members", KTFFICodegenHelper.memoryLayoutCname, KModifier.VARARG)
                            .build()
                    )
                    .addSuperclassConstructorParameter("*members")
                    .build()
            )
        ctx.writeOutput(vkUnionFile)

        val structTypeVar = TypeVariableName("T", VKFFI.vkStructCname.parameterizedBy(TypeVariableName("T")))
        val vkStructFile = FileSpec.builder(VKFFI.vkStructCname)
            .addType(
                TypeSpec.classBuilder(VKFFI.vkStructCname)
                    .addTypeVariable(structTypeVar)
                    .addModifiers(KModifier.SEALED)
                    .superclass(KTFFICodegenHelper.structCname.parameterizedBy(structTypeVar))
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter("members", KTFFICodegenHelper.memoryLayoutCname, KModifier.VARARG)
                            .build()
                    )
                    .addSuperclassConstructorParameter("*members")
                    .build()
            )
        ctx.writeOutput(vkStructFile)

        struct.join()
        union.join()
    }

    private inner class StructTask : VKFFITask<Unit>(ctx) {
        override fun VKFFICodeGenContext.compute() {
            val structTypes = ctx.filterType<CType.Struct>()
            val typeAlias = GenTypeAliasTask(this, structTypes).fork()

            structTypes.parallelStream()
                .filter { (name, type) -> name == type.name }
                .map { (_, structType) ->
                    genStructType(structType)
                }
                .forEach(ctx::writeOutput)

            typeAlias.joinAndWriteOutput(VKFFI.structPackageName)
        }
    }

    private inner class UnionTask : VKFFITask<Unit>(ctx) {
        override fun VKFFICodeGenContext.compute() {
            val unionTypes = ctx.filterType<CType.Union>()
            val typeAlias = GenTypeAliasTask(this, unionTypes).fork()

            unionTypes.parallelStream()
                .filter { (name, type) -> name == type.name }
                .map { (_, enumType) ->
                    genUnionType(enumType)
                }
                .forEach(ctx::writeOutput)

            typeAlias.joinAndWriteOutput(VKFFI.unionPackageName)
        }
    }

    private fun VKFFICodeGenContext.makeTypeObject(groupType: CType.Group): TypeSpec.Builder {
        val thisCname = groupType.className()
        val typeObject = TypeSpec.objectBuilder(thisCname)
        typeObject.tryAddKdoc(groupType)
        val superCname = when (groupType) {
            is CType.Struct -> VKFFI.vkStructCname
            is CType.Union -> VKFFI.vkUnionCname
        }
        typeObject.superclass(superCname.parameterizedBy(thisCname))
        typeObject.addSuperclassConstructorParameter(
            CodeBlock.builder()
                .add("\n")
                .indent()
                .add(groupType.memoryLayoutDeep())
                .unindent()
                .build()
        )
        return typeObject
    }

    private fun VKFFICodeGenContext.genStructType(structType: CType.Struct): FileSpec.Builder {
        val thisCname = structType.className()
        val typeObject = makeTypeObject(structType)

        val file = FileSpec.builder(thisCname)
        file.addType(typeObject.build())
        return file
    }

    private fun VKFFICodeGenContext.genUnionType(unionType: CType.Union): FileSpec.Builder {
        val thisCname = unionType.className()
        val typeObject = makeTypeObject(unionType)

        val file = FileSpec.builder(thisCname)
        file.addType(typeObject.build())
        return file
    }
}