package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.ktffi.*

class GenerateHandleTask(ctx: VKFFICodeGenContext) : VKFFITask<Unit>(ctx) {
    private fun CType.Handle.variableName(): String {
        return name.removePrefix("Vk").decap()
    }

    private val objTypeCname = with(ctx) { resolveType("VkObjectType").className() }
    private val typeVariable = TypeVariableName("T", VKFFI.vkHandleCname)
    val vkTypeDescriptorCname = VKFFI.vkHandleCname.nestedClass("TypeDescriptor")

    override fun VKFFICodeGenContext.compute() {
        val handles = ctx.filterType<CType.Handle>()
        val typeAlias = GenTypeAliasTask(this, handles).fork()

        val vkHandleFile = FileSpec.builder(VKFFI.vkHandleCname)
            .addType(
                TypeSpec.interfaceBuilder(VKFFI.vkHandleCname)
                    .addSuperinterface(NativeType::class)
                    .addProperty("handle", CBasicType.int64_t.kotlinTypeName)
                    .addProperty("objectType", objTypeCname)
                    .addType(
                        TypeSpec.classBuilder(vkTypeDescriptorCname)
                            .addModifiers(KModifier.ABSTRACT)
                            .addTypeVariable(typeVariable)
                            .superclass(KTFFICodegenHelper.typeDescriptorImplCname.parameterizedBy(typeVariable))
                            .addSuperclassConstructorParameter("%M", CBasicType.int64_t.valueLayoutMember)
                            .build()
                    )
                    .build()
            )

        ctx.writeOutput(vkHandleFile)

        handles.parallelStream()
            .filter { (name, dstType) -> name == dstType.name }
            .map { (_, handleType) -> genHandle(handleType) }
            .forEach(ctx::writeOutput)

        typeAlias.joinAndWriteOutput(VKFFI.handlePackageName)
    }

    private fun VKFFICodeGenContext.genHandle(handleType: CType.Handle): FileSpec.Builder {
        handleType as VkHandle
        val thisCname = handleType.className()
        val thisTypeDescriptor = KTFFICodegenHelper.typeDescriptorCname.parameterizedBy(thisCname)
        val parent = handleType.parent

        val interfaceType = TypeSpec.interfaceBuilder(thisCname)
        interfaceType.addSuperinterface(VKFFI.vkHandleCname)
        parent?.className()?.nestedClass("Child")?.let {
            interfaceType.addSuperinterface(it)
        }

        interfaceType.addProperty(
            PropertySpec.builder("objectType", objTypeCname)
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return %T.%N", objTypeCname, handleType.objectTypeEnum.name)
                        .build()
                )
                .build()
        )
        interfaceType.addProperty(
            PropertySpec.builder("typeDescriptor", thisTypeDescriptor)
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return Companion")
                        .build()
                )
                .build()
        )


        val implType = TypeSpec.classBuilder("Impl")
        implType.addSuperinterface(thisCname)
        if (handleType.parent != null) {
            val parentVariableName = handleType.parent.variableName()
            val parentCname = handleType.parent.className()
            implType.addProperty(
                PropertySpec.builder(parentVariableName, parentCname)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(CodeBlock.of(parentVariableName))
                    .build()
            )
            implType.addProperty(
                PropertySpec.builder("handle", CBasicType.int64_t.kotlinTypeName)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(CodeBlock.of("handle"))
                    .build()
            )
            implType.primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter(parentVariableName, handleType.parent.className())
                    .addParameter("handle", CBasicType.int64_t.kotlinTypeName)
                    .build()
            )
            val grandparent = handleType.parent.parent
            if (grandparent != null) {
                implType.addSuperinterface(
                    grandparent.className().nestedClass("Child"),
                    CodeBlock.of(parentVariableName)
                )
            }
        } else {
            implType.addSuperclassConstructorParameter(
                CodeBlock.of("%M", CBasicType.int64_t.valueLayoutMember)
            )
            implType.addProperty(
                PropertySpec.builder("handle", CBasicType.int64_t.kotlinTypeName)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(CodeBlock.of("handle"))
                    .build()
            )
            implType.primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("handle", CBasicType.int64_t.kotlinTypeName)
                    .build()
            )
        }
        interfaceType.addType(implType.build())


        val childType = TypeSpec.interfaceBuilder("Child")
        handleType.parent?.className()?.nestedClass("Child")?.let {
            childType.addSuperinterface(it)
        }
        childType.addProperty(handleType.variableName(), thisCname)
        interfaceType.addType(childType.build())


        val companion = TypeSpec.companionObjectBuilder()
        companion.superclass(vkTypeDescriptorCname.parameterizedBy(thisCname))
        companion.addFunction(
            FunSpec.builder("toNativeData")
                .addAnnotation(JvmStatic::class)
                .addModifiers(KModifier.INLINE)
                .addParameter("value", thisCname)
                .returns(CBasicType.int64_t.kotlinTypeName)
                .addStatement("return value.handle")
                .build()
        )
        companion.addFunction(
            FunSpec.builder("fromNativeData")
                .addAnnotation(JvmStatic::class)
                .addModifiers(KModifier.INLINE)
                .addParameter("value", CBasicType.int64_t.kotlinTypeName)
                .returns(thisCname)
                .apply {
                    if (handleType.parent == null) {
                        addStatement("return Impl(value)")
                    } else {
                        addStatement(
                            "throw %T()",
                            UnsupportedOperationException::class
                        )
                    }
                }
                .build()
        )
        companion.addMethodHandleFields()
        interfaceType.addType(companion.build())


        val file = FileSpec.builder(thisCname)
        file.addType(interfaceType.build())
        return file
    }
}