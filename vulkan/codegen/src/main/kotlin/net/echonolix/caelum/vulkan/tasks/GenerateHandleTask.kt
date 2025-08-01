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

        typeAlias.joinAndWriteOutput(Path("objectHandles"), VulkanCodegen.handlePackageName)
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
        val vkHandleTag = handleType.tags.getOrNull<VkHandleTag>() ?: error("$handleType is missing VkHandleTag")

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
                            vkHandleTag.objectTypeEnum.tags.getOrNull<EnumEntryFixedName>()!!.name
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
        implType.superclass(VulkanCodegen.vkHandleImplCName.parameterizedBy(thisCName))
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
        companion.addFunction(
            FunSpec.builder("toNativeData")
                .addParameter("value", thisCName.copy(nullable = true))
                .returns(CBasicType.int64_t.ktApiTypeTypeName)
                .addStatement("return value?.value ?: 0L")
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
        val objectBasePath = Path("objectBase")
        val thisObjectHandleCName = handleType.className()
        val thisCName = handleType.objectBaseCName()
        val vkHandleTag = handleType.tags.getOrNull<VkHandleTag>() ?: error("$handleType is missing VkHandleTag")
        val parent = vkHandleTag.parent

        val containerCName = ClassName(VulkanCodegen.handlePackageName, "${handleType.name}Container")
        val containerType = TypeSpec.interfaceBuilder(containerCName)
        parent?.let {
            containerType.addSuperinterface(ClassName(VulkanCodegen.handlePackageName, "${parent.name}Container"))
        }
        containerType.addProperty(handleType.variableName(), thisCName)

        val interfaceType = TypeSpec.interfaceBuilder(thisCName)
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
        implType.superclass(VulkanCodegen.vkHandleImplCName.parameterizedBy(thisObjectHandleCName))
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
            implType.primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("value", CBasicType.int64_t.ktApiTypeTypeName)
                    .build()
            )
        }

        ContainerType[handleType.name]?.let { containerType ->
            val functionContainerCname = ClassName(VulkanCodegen.handlePackageName, "${handleType.name}FuncContainer")
            val funcContainerType = TypeSpec.classBuilder(functionContainerCname)

            val constructor = FunSpec.constructorBuilder()

            if (containerType != ContainerType.Instance) {
                val vkInstanceType = ctx.resolveTypedElement<CType.Handle>("VkInstance")
                val vkInstanceCName = vkInstanceType.objectBaseCName()
                funcContainerType.addProperty(
                    PropertySpec.builder("instance", vkInstanceCName)
                        .addModifiers(KModifier.INTERNAL)
                        .initializer("instance")
                        .build()
                )
                constructor.addParameter("instance", vkInstanceCName)
            }

            funcContainerType.addProperty(
                PropertySpec.builder("handle", LONG)
                    .addModifiers(KModifier.INTERNAL)
                    .initializer("handle")
                    .build()
            )
            constructor.addParameter("handle", LONG)
            funcContainerType.primaryConstructor(constructor.build())

            fun CType.Function.funcName() = tags.getOrNull<OriginalNameTag>()!!.name

            val filteredFunctions = functions.parallelStream()
                .filter(containerType::filterFunc)
                .toList()

            funcContainerType.addProperties(filteredFunctions.parallelStream().map {
                val funcName = it.funcName()
                val property = PropertySpec.builder(funcName, CaelumCodegenHelper.methodHandleCName)
                val fdMName = MemberName("${basePackageName}.functions", it.funcDescPropertyName())
                when (funcName) {
                    "vkGetInstanceProcAddr" -> {
                        property.initializer("%T.$funcName", VulkanCodegen.vkCName)
                    }
                    "vkGetDeviceProcAddr" -> {
                        property.initializer(
                            "instance.%M(%S, %M)",
                            ContainerType.Instance.getFuncMemberName,
                            it.funcName(),
                            fdMName
                        )
                    }
                    else -> {
                        property.delegate(
                            CodeBlock.builder()
                                .beginControlFlow("lazy")
                                .addStatement(
                                    "this.%M(%S, %M)",
                                    containerType.getFuncMemberName,
                                    it.funcName(),
                                    fdMName
                                )
                                .endControlFlow()
                                .build()
                        )
//                        property.initializer("this.%M(%T)", containerType.getFuncMemberName, it.className())
                    }
                }
                property.build()
            }.toList())

            val funcContainerFile = FileSpec.builder(functionContainerCname)
            funcContainerFile.addType(funcContainerType.build())
            writeOutput(objectBasePath, funcContainerFile)

            interfaceType.addProperty("funcContainer", functionContainerCname)

            val initializerCode = if (parent == null) {
                CodeBlock.of("%T(value)", functionContainerCname)
            } else {
                CodeBlock.of("%T(parent.instance, value)", functionContainerCname)
            }

            implType.addProperty(
                PropertySpec.builder("funcContainer", functionContainerCname)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(initializerCode)
                    .build()
            )
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

        ctx.writeOutput(objectBasePath, file)
    }
}