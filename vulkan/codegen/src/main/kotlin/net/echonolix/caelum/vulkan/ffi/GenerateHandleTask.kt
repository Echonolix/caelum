package net.echonolix.caelum.vulkan.ffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.codegen.api.CBasicType
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.CaelumCodegenHelper
import net.echonolix.caelum.codegen.api.decap
import kotlin.io.path.Path

class GenerateHandleTask(ctx: VulkanCodeGenContext) : CaelumVulkanCodegenTask<Unit>(ctx) {
    private fun CType.Handle.variableName(): String {
        return name.removePrefix("Vk").decap()
    }

    private val objTypeCname = with(ctx) { resolveElement<CType>("VkObjectType").typeName() }
    val vkTypeDescriptorCname = CaelumVulkanCodegen.vkHandleCname.nestedClass("TypeDescriptor")

    override fun VulkanCodeGenContext.compute() {
        val handles = ctx.filterType<CType.Handle>()
        val typeAlias = GenTypeAliasTask(this, handles).fork()

        val functions = ctx.filterVkFunction()
        handles.parallelStream()
            .filter { (name, dstType) -> name == dstType.name }
            .forEach { (_, handleType) ->
                genObjectHandle(handleType)
                genObjectBase(functions, handleType)
            }

        typeAlias.joinAndWriteOutput(Path("handles"), CaelumVulkanCodegen.handlePackageName)
    }

    private enum class ContainerType(val getFuncMemberName: MemberName) {
        Instance(CaelumVulkanCodegen.getInstanceFuncMember) {
            override fun filterFunc(funcType: CType.Function): Boolean {
                return !isDeviceFunc(funcType)
            }
        },
        Device(CaelumVulkanCodegen.getDeviceFuncMember) {
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

    private fun VulkanCodeGenContext.genObjectHandle(
        handleType: CType.Handle
    ) {
        val thisCname = handleType.className()
        val thisTypeDescriptor = CaelumCodegenHelper.typeDescriptorCname.parameterizedBy(thisCname)
        val vkHandleTag = handleType.tags.get<VkHandleTag>() ?: error("$handleType is missing VkHandleTag")

        val interfaceType = TypeSpec.interfaceBuilder(thisCname)
        interfaceType.addSuperinterface(CaelumVulkanCodegen.vkHandleCname)
        interfaceType.addProperty(
            PropertySpec.builder("objectType", objTypeCname)
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement(
                            "return %T.%N",
                            objTypeCname,
                            vkHandleTag.objectTypeEnum.tags.get<EnumEntryFixedName>()!!.name
                        )
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
        interfaceType.addType(implType.build())

        val companion = TypeSpec.companionObjectBuilder()
        companion.superclass(vkTypeDescriptorCname.parameterizedBy(thisCname))
        companion.addFunction(
            FunSpec.builder("fromNativeData")
                .addParameter("value", CBasicType.int64_t.ktApiTypeTypeName)
                .returns(thisCname)
                .addAnnotation(JvmStatic::class)
                .addStatement("return Impl(value)")
                .build()
        )
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

        ctx.writeOutput(Path("objectHandles"), file)
    }

    private fun VulkanCodeGenContext.genObjectBase(
        functions: List<CType.Function>,
        handleType: CType.Handle
    ) {
        val thisObjectHandleCname = handleType.className()
        val thisCname = handleType.objectBaseCName()
        val vkHandleTag = handleType.tags.get<VkHandleTag>() ?: error("$handleType is missing VkHandleTag")
        val parent = vkHandleTag.parent

        val containerCname = ClassName(CaelumVulkanCodegen.handlePackageName, "${handleType.name}Container")
        val containerType = TypeSpec.interfaceBuilder(containerCname)
        parent?.let {
            containerType.addSuperinterface(ClassName(CaelumVulkanCodegen.handlePackageName, "${parent.name}Container"))
        }
        containerType.addProperty(handleType.variableName(), thisCname)

        val interfaceType = TypeSpec.interfaceBuilder(thisCname)
        interfaceType.addSuperinterface(thisObjectHandleCname)
        interfaceType.addSuperinterface(containerCname)
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


        val implType = TypeSpec.classBuilder("Impl")
        implType.addModifiers(KModifier.PRIVATE)
        implType.addSuperinterface(thisCname)
        implType.addProperty(
            PropertySpec.builder("handle", CBasicType.int64_t.ktApiTypeTypeName)
                .addModifiers(KModifier.OVERRIDE)
                .initializer(CodeBlock.of("handle"))
                .build()
        )
        if (parent != null) {
            implType.primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("parent", parent.objectBaseCName())
                    .addParameter("handle", CBasicType.int64_t.ktApiTypeTypeName)
                    .build()
            )
            implType.addSuperinterface(
                ClassName(CaelumVulkanCodegen.handlePackageName, "${parent.name}Container"),
                CodeBlock.of("parent")
            )
        } else {
            implType.addSuperclassConstructorParameter(
                CodeBlock.of("%M", CBasicType.int64_t.valueLayoutMember)
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
                        property.initializer("%T.$funcName", CaelumVulkanCodegen.vkCname)
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
        if (parent != null) {
            companion.addFunction(
                FunSpec.builder("fromNativeData")
                    .addAnnotation(JvmStatic::class)
                    .addParameter("parent", parent.objectBaseCName())
                    .addParameter("value", CBasicType.int64_t.ktApiTypeTypeName)
                    .returns(thisCname)
                    .addStatement("return Impl(parent, value)")
                    .build()
            )
        } else {
            companion.addFunction(
                FunSpec.builder("fromNativeData")
                    .addParameter("value", CBasicType.int64_t.ktApiTypeTypeName)
                    .returns(thisCname)
                    . addAnnotation(JvmStatic::class)
                    . addStatement("return Impl(value)")
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
        file.addType(containerType.build())

        ctx.writeOutput(Path("objectBase"), file)
    }
}