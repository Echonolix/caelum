package net.echonolix.caelum.vulkan.tasks

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.codegen.api.*
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.filterType
import net.echonolix.caelum.codegen.api.ctx.resolveTypedElement
import net.echonolix.caelum.codegen.api.task.CodegenTask
import net.echonolix.caelum.codegen.api.task.GenTypeAliasTask
import net.echonolix.caelum.vulkan.*
import kotlin.io.path.Path

class GenerateHandleTask(ctx: CodegenContext) : CodegenTask<Unit>(ctx) {
    private fun CType.Handle.variableName(): String {
        return name.removePrefix("Vk").decap()
    }

    private val objTypeCName = with(ctx) { resolveTypedElement<CType>("VkObjectType").typeName() }

    override fun CodegenContext.compute() {
        val handles = ctx.filterType<CType.Handle>()
        val typeAlias = GenTypeAliasTask(this, handles).fork()

        val functions = ctx.filterVkFunction()
        handles.parallelStream()
            .filter { (name, dstType) -> name == dstType.name }
            .forEach { (_, handleType) ->
                genObjectHandle(handleType)
                genObjectBase(functions, handleType)
            }

        typeAlias.joinAndWriteOutput(Path("handles"), VulkanCodegen.handlePackageName)
    }

    private enum class ContainerType(val getFuncMemberName: MemberName) {
        Instance(VulkanCodegen.getInstanceFuncMember) {
            override fun filterFunc(funcType: CType.Function): Boolean {
                return !isDeviceFunc(funcType)
            }
        },
        Device(VulkanCodegen.getDeviceFuncMember) {
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

    private fun CodegenContext.genObjectHandle(handleType: CType.Handle) {
        val thisCName = handleType.className()
        val thisTypeDescriptor = CaelumCodegenHelper.NEnum.descriptorCName.parameterizedBy(thisCName, LONG)
        val vkHandleTag = handleType.tags.get<VkHandleTag>() ?: error("$handleType is missing VkHandleTag")

        val interfaceType = TypeSpec.interfaceBuilder(thisCName)
        interfaceType.addSuperinterface(VulkanCodegen.vkHandleCName.parameterizedBy(thisCName))
        interfaceType.addProperty(
            PropertySpec.builder("objectType", objTypeCName)
                .addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement(
                            "return %T.%N",
                            objTypeCName,
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
        implType.addSuperinterface(thisCName)
        implType.addProperty(
            PropertySpec.builder("value", CBasicType.int64_t.ktApiTypeTypeName)
                .addModifiers(KModifier.OVERRIDE)
                .initializer(CodeBlock.of("value"))
                .build()
        )
        implType.primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter("value", CBasicType.int64_t.ktApiTypeTypeName)
                .build()
        )
        interfaceType.addType(implType.build())

        val companion = TypeSpec.companionObjectBuilder()
        companion.addSuperinterface(thisTypeDescriptor)
        companion.addSuperinterface(
            handleType.baseType.nNativeDataType.nNativeDataCName.parameterizedBy(thisCName, thisCName),
            CodeBlock.of("%T.implOf()", handleType.baseType.nNativeDataType.nNativeDataCName)
        )
        companion.addFunction(
            FunSpec.builder("fromNativeData")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("value", CBasicType.int64_t.ktApiTypeTypeName)
                .returns(thisCName)
                .addStatement("return Impl(value)")
                .build()
        )
        companion.addFunction(
            FunSpec.builder("toNativeData")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("value", thisCName)
                .returns(CBasicType.int64_t.ktApiTypeTypeName)
                .addStatement("return value.value")
                .build()
        )
        interfaceType.addType(companion.build())

        val file = FileSpec.builder(thisCName)
        file.addType(interfaceType.build())

        ctx.writeOutput(Path("objectHandles"), file)
    }

    private fun CodegenContext.genObjectBase(
        functions: List<CType.Function>,
        handleType: CType.Handle
    ) {
        val thisObjectHandleCName = handleType.className()
        val thisCName = handleType.objectBaseCName()
        val vkHandleTag = handleType.tags.get<VkHandleTag>() ?: error("$handleType is missing VkHandleTag")
        val parent = vkHandleTag.parent

        val containerCName = ClassName(VulkanCodegen.handlePackageName, "${handleType.name}Container")
        val containerType = TypeSpec.interfaceBuilder(containerCName)
        parent?.let {
            containerType.addSuperinterface(ClassName(VulkanCodegen.handlePackageName, "${parent.name}Container"))
        }
        containerType.addProperty(handleType.variableName(), thisCName)

        val interfaceType = TypeSpec.interfaceBuilder(thisCName)
//        interfaceType.addSuperinterface(VulkanCodegen.vkHandleCName.parameterizedBy(thisCName))
        interfaceType.addSuperinterface(thisObjectHandleCName)
        interfaceType.addSuperinterface(containerCName)
        interfaceType.addProperty(
            PropertySpec.builder(handleType.variableName(), thisCName)
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
        implType.addSuperinterface(thisCName)
        implType.addProperty(
            PropertySpec.builder("value", CBasicType.int64_t.ktApiTypeTypeName)
                .addModifiers(KModifier.OVERRIDE)
                .initializer(CodeBlock.of("value"))
                .build()
        )
        if (parent != null) {
            implType.primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("parent", parent.objectBaseCName())
                    .addParameter("value", CBasicType.int64_t.ktApiTypeTypeName)
                    .build()
            )
            implType.addSuperinterface(
                ClassName(VulkanCodegen.handlePackageName, "${parent.name}Container"),
                CodeBlock.of("parent")
            )
        } else {
            implType.addSuperclassConstructorParameter(
                CodeBlock.of("%M", CBasicType.int64_t.valueLayoutMember)
            )
            implType.primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("value", CBasicType.int64_t.ktApiTypeTypeName)
                    .build()
            )
        }
        ContainerType[handleType.name]?.let { containerType ->
            fun CType.Function.funcName() = tags.get<OriginalNameTag>()!!.name

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
                        property.initializer("%T.$funcName", VulkanCodegen.vkCName)
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
        val thisTypeDescriptor = CaelumCodegenHelper.NEnum.descriptorCName.parameterizedBy(
            thisObjectHandleCName,
            LONG
        )
        companion.addSuperinterface(
            thisTypeDescriptor,
            CodeBlock.of("%T.Companion", thisObjectHandleCName)
        )
        companion.addSuperinterface(CaelumCodegenHelper.allocOverloadCName.parameterizedBy(thisCName))
        companion.addAllocOverload(thisCName)
        if (parent != null) {
            companion.addFunction(
                FunSpec.builder("fromNativeData")
                    .addParameter("parent", parent.objectBaseCName())
                    .addParameter("value", CBasicType.int64_t.ktApiTypeTypeName)
                    .returns(thisCName)
                    .addStatement("return Impl(parent, value)")
                    .build()
            )
            companion.addFunction(
                FunSpec.builder("fromNativeData")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("value", CBasicType.int64_t.ktApiTypeTypeName)
                    .returns(thisCName)
                    .addStatement(
                        "throw %T(\"Cannot create %L from native data without parent\")",
                        UnsupportedOperationException::class,
                        thisCName.simpleName
                    )
                    .build()
            )
        } else {
            companion.addFunction(
                FunSpec.builder("fromNativeData")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter("value", CBasicType.int64_t.ktApiTypeTypeName)
                    .returns(thisCName)
                    .addStatement("return Impl(value)")
                    .build()
            )
        }
        interfaceType.addType(companion.build())

        val file = FileSpec.builder(thisCName)
        file.addType(interfaceType.build())
        file.addType(containerType.build())

        ctx.writeOutput(Path("objectBase"), file)
    }
}