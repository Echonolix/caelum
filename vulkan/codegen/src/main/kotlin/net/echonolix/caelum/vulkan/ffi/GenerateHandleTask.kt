package net.echonolix.caelum.vulkan.ffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.CBasicType
import net.echonolix.caelum.CType
import net.echonolix.caelum.CaelumCodegenHelper
import net.echonolix.caelum.decap

class GenerateHandleTask(ctx: VKFFICodeGenContext) : VKFFITask<Unit>(ctx) {
    private fun CType.Handle.variableName(): String {
        return name.removePrefix("Vk").decap()
    }

    private val objTypeCname = with(ctx) { resolveType("VkObjectType").typeName() }
    val vkTypeDescriptorCname = VKFFI.vkHandleCname.nestedClass("TypeDescriptor")

    override fun VKFFICodeGenContext.compute() {
        val handles = ctx.filterType<CType.Handle>()
        val typeAlias = GenTypeAliasTask(this, handles).fork()

        val functions = ctx.filterVkFunction()
        handles.parallelStream()
            .filter { (name, dstType) -> name == dstType.name }
            .map { (_, handleType) -> genHandle(functions, handleType) }
            .forEach(ctx::writeOutput)

        typeAlias.joinAndWriteOutput(VKFFI.handlePackageName)
    }

    private enum class ContainerType(val getFuncMemberName: MemberName) {
        Instance(VKFFI.getInstanceFuncMember) {
            override fun filterFunc(funcType: CType.Function): Boolean {
                return !isDeviceFunc(funcType)
            }
        },
        Device(VKFFI.getDeviceFuncMember) {
            override fun filterFunc(funcType: CType.Function): Boolean {
                return isDeviceFunc(funcType)
            }
        };

        abstract fun filterFunc(funcType: CType.Function): Boolean

        protected fun isDeviceFunc(funcType: CType.Function): Boolean {
            return isDeviceBase(funcType.parameters.first().type as CType.Handle)
        }

        companion object {
            operator fun get(name: String): ContainerType? {
                return when (name) {
                    "VkInstance" -> Instance
                    "VkDevice" -> Device
                    else -> null
                }
            }
        }
    }

    private fun VKFFICodeGenContext.genHandle(
        functions: List<CType.Function>,
        handleType: CType.Handle
    ): FileSpec.Builder {
        val thisCname = handleType.className()
        val thisTypeDescriptor = CaelumCodegenHelper.typeDescriptorCname.parameterizedBy(thisCname)
        val vkHandleTag = handleType.tags.get<VkHandleTag>() ?: error("$handleType is missing VkHandleTag")
        val parent = vkHandleTag.parent

        val baseCname = ClassName(VKFFI.handlePackageName, "${handleType.name}Base")
        val baseType = TypeSpec.interfaceBuilder(baseCname)
        parent?.let {
            baseType.addSuperinterface(ClassName(VKFFI.handlePackageName, "${parent.name}Base"))
        }
        baseType.addProperty(handleType.variableName(), thisCname)

        val interfaceType = TypeSpec.interfaceBuilder(thisCname)
        interfaceType.addSuperinterface(VKFFI.vkHandleCname)
        interfaceType.addSuperinterface(baseCname)
        interfaceType.addProperty(
            PropertySpec.builder(handleType.variableName(), thisCname)
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return this")
                        .build()
                )
                .build()
        )

        interfaceType.addProperty(
            PropertySpec.builder("objectType", objTypeCname)
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return %T.%N", objTypeCname, vkHandleTag.objectTypeEnum.name)
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
        implType.addModifiers(KModifier.PRIVATE)
        implType.addSuperinterface(thisCname)
        if (parent != null) {
            implType.addProperty(
                PropertySpec.builder("handle", CBasicType.int64_t.ktApiTypeTypeName)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(CodeBlock.of("handle"))
                    .build()
            )
            implType.primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("parent", parent.typeName())
                    .addParameter("handle", CBasicType.int64_t.ktApiTypeTypeName)
                    .build()
            )
            implType.addSuperinterface(
                ClassName(VKFFI.handlePackageName, "${parent.name}Base"),
                CodeBlock.of("parent")
            )
        } else {
            implType.addSuperclassConstructorParameter(
                CodeBlock.of("%M", CBasicType.int64_t.valueLayoutMember)
            )
            implType.addProperty(
                PropertySpec.builder("handle", CBasicType.int64_t.ktApiTypeTypeName)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(CodeBlock.of("handle"))
                    .build()
            )
            implType.primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("handle", CBasicType.int64_t.ktApiTypeTypeName)
                    .build()
            )
        }
        ContainerType[handleType.name]?.let { containerType ->
            fun CType.Function.funcName() = tags.get<OriginalFunctionNameTag>()!!.name

            val filteredFunctions = functions.parallelStream()
                .filter(containerType::filterFunc)
                .toList()

            interfaceType.addProperties(filteredFunctions.parallelStream().map {
                PropertySpec.builder(it.funcName(), it.className())
                    .build()
            }.toList())

            implType.addProperties(filteredFunctions.parallelStream().map {
                val funcName = it.funcName()
                val property = PropertySpec.builder(funcName, it.className())
                property.addModifiers(KModifier.OVERRIDE)
                when (funcName) {
                    "vkGetInstanceProcAddr" -> {
                        property.initializer("%T.$funcName", VKFFI.vkCname)
                    }
                    "vkGetDeviceProcAddr" -> {
                        property.initializer(
                            "instance.%M(%T)",
                            ContainerType.Instance.getFuncMemberName,
                            it.className()
                        )
                    }
                    else -> {
                        property.delegate(
                            CodeBlock.builder()
                                .beginControlFlow("lazy")
                                .addStatement("this.%M(%T)", containerType.getFuncMemberName, it.className())
                                .endControlFlow()
                                .build()
                        )
                    }
                }

                property.build()
            }.toList())
        }

        interfaceType.addType(implType.build())


        val companion = TypeSpec.companionObjectBuilder()
        companion.superclass(vkTypeDescriptorCname.parameterizedBy(thisCname))
        companion.addFunction(
            FunSpec.builder("fromNativeData")
                .addParameter("value", CBasicType.int64_t.ktApiTypeTypeName)
                .returns(thisCname)
                .apply {
                    if (parent == null) {
                        addAnnotation(JvmStatic::class)
                        addStatement("return Impl(value)")
                    } else {
                        addModifiers(KModifier.INTERNAL)
                        addStatement(
                            "throw %T()",
                            UnsupportedOperationException::class
                        )
                    }
                }
                .build()
        )
        if (parent != null) {
            companion.addFunction(
                FunSpec.builder("fromNativeData")
                    .addAnnotation(JvmStatic::class)
                    .addParameter("parent", parent.typeName())
                    .addParameter("value", CBasicType.int64_t.ktApiTypeTypeName)
                    .returns(thisCname)
                    .addStatement("return Impl(parent, value)")
                    .build()
            )
        }
        companion.addFunction(
            FunSpec.builder("toNativeData")
                .addAnnotation(JvmStatic::class)
                .addParameter("value", thisCname)
                .returns(CBasicType.int64_t.ktApiTypeTypeName)
                .addStatement("return value.handle")
                .build()
        )
        interfaceType.addType(companion.build())

        val file = FileSpec.builder(thisCname)
        file.addType(interfaceType.build())
        file.addType(baseType.build())
        return file
    }
}