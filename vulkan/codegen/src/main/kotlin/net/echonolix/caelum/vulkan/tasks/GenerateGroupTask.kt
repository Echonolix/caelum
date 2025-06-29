package net.echonolix.caelum.vulkan.tasks

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.codegen.api.*
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.filterTypeStream
import net.echonolix.caelum.codegen.api.ctx.resolveTypedElement
import net.echonolix.caelum.codegen.api.generator.GroupGenerator
import net.echonolix.caelum.codegen.api.task.CodegenTask
import net.echonolix.caelum.codegen.api.task.GenTypeAliasTask
import net.echonolix.caelum.vulkan.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import java.util.stream.Collectors
import kotlin.io.path.Path

class GenerateGroupTask(ctx: CodegenContext) : CodegenTask<Unit>(ctx) {
    private val skippedStructs = setOf(
        "VkBaseInStructure",
        "VkBaseOutStructure"
    )
    private val vkStructureTypeCName = with(ctx) {
        ctx.resolveTypedElement<CType.Enum>("VkStructureType").ktApiType()
    }

    override fun CodegenContext.compute() {
        val groupTypes = ctx.filterTypeStream<CType.Group>()
            .filter { (name, type) -> name !in skippedStructs && type.name !in skippedStructs }
            .toList()

        val typeAlias = GenTypeAliasTask(this, groupTypes).fork()

        val groupTypesNoAliases = groupTypes.parallelStream()
            .filter { (name, type) -> name == type.name }
            .map { (_, type) -> type }
            .toList()

        val hasFunctionPointer: MutableSet<CType.Group> = groupTypesNoAliases.parallelStream()
            .filter { type -> type.members.any { it.type.deepReferenceResolve() is CType.Function } }
            .collect(Collectors.toCollection(::mutableSetOf))

        val typeMemberAdjLists: Map<CType.Group, Set<CType.Group>> = groupTypesNoAliases.parallelStream()
            .collect(Collectors.toMap(Function.identity()) { type ->
                type.members.asSequence()
                    .map { it.type.deepReferenceResolve() }
                    .filterIsInstance<CType.Group>()
                    .filter { it != type }
                    .toSet()
            })

        val twoWayAdjLists: Map<CType.Group, Set<CType.Group>> = typeMemberAdjLists.entries.parallelStream()
            .flatMap { (type, neighbors) -> neighbors.stream().map { type to it } }
            .collect(
                Collectors.groupingByConcurrent(
                    Pair<CType.Group, CType.Group>::second,
                    {
                        ConcurrentHashMap(typeMemberAdjLists).apply {
                            replaceAll { _, neighbors -> neighbors.toMutableSet() }
                        }
                    },
                    Collectors.mapping(Pair<CType.Group, CType.Group>::first, Collectors.toSet())
                )
            )

        val clusters = mutableSetOf<MutableSet<CType.Group>>()
        val visitedType = mutableMapOf<CType.Group, MutableSet<CType.Group>>()

        val comparator = compareBy<CType> { it.name }

        fun dfs(type: CType.Group, cluster: MutableSet<CType.Group>) {
            if (type in visitedType) return
            visitedType[type] = cluster
            cluster.add(type)
            twoWayAdjLists[type]?.sortedWith(comparator)?.forEach { dfs(it, cluster) }
        }

        groupTypesNoAliases.sortedWith(comparator).forEach { type ->
            if (type in visitedType) return@forEach
            val cluster = mutableSetOf<CType.Group>()
            dfs(type, cluster)
            if (cluster.any { it in hasFunctionPointer }) {
                hasFunctionPointer.addAll(cluster)
            } else {
                clusters.add(cluster)
            }
        }

        val targetClusterSize = (groupTypesNoAliases.size + 7) / 8
        val mergedClusters = mutableListOf<MutableSet<CType.Group>>()


        clusters.forEach { cluster ->
            mergedClusters.find { it.size + cluster.size < targetClusterSize }?.addAll(cluster)
                ?: mergedClusters.add(cluster.toMutableSet())
        }
        mergedClusters.sortBy { it.size }
        while (mergedClusters.size > 8) {
            val second = mergedClusters.removeAt(1)
            mergedClusters.first().addAll(second)
        }

        check(mergedClusters.size <= 8)
        mergedClusters.forEach { cluster ->
            println(cluster.size)
        }

        mergedClusters.forEachIndexed { index, cluster ->
            val clusterPath = Path("groups$index")
            cluster.parallelStream()
                .map { type -> genGroupType(type) }
                .forEach { ctx.writeOutput(clusterPath, it) }
        }

        val groupsPath = Path("groups")
        hasFunctionPointer.parallelStream()
            .map { type -> genGroupType(type) }
            .forEach { ctx.writeOutput(groupsPath, it) }

        typeAlias.joinAndWriteOutput(groupsPath, VulkanCodegen.structPackageName)
    }

    context(ctx: CodegenContext)
    private fun genGroupType(groupType: CType.Group): FileSpec.Builder {
        val generator = object : GroupGenerator(ctx, groupType) {
            context(ctx: CodegenContext)
            override fun groupBaseCName(): ClassName {
                return when (groupType) {
                    is CType.Struct -> VulkanCodegen.vkStructCName
                    is CType.Union -> VulkanCodegen.vkUnionCName
                }
            }

            context(ctx: CodegenContext)
            override fun buildTypeObjectType() {
                super.buildTypeObjectType()

                if (groupType is CType.Struct) {
                    val initializerCodeBlock = groupType.members
                        .firstNotNullOfOrNull { it.tags.getOrNull<StructTypeTag>() }
                        ?.let { tag ->
                            CodeBlock.of(
                                "return %T.%N",
                                vkStructureTypeCName,
                                tag.structType.tags.getOrNull<EnumEntryFixedName>()!!.name
                            )
                        } ?: CodeBlock.of("return null")

                    typeObjectType.addProperty(
                        PropertySpec.builder("structType", vkStructureTypeCName.copy(nullable = true))
                            .addModifiers(KModifier.OVERRIDE)
                            .getter(
                                FunSpec.getterBuilder()
                                    .addCode(initializerCodeBlock)
                                    .build()
                            )
                            .build()
                    )
                }
            }

            context(ctx: CodegenContext)
            override fun memberKtApiType(member: CType.Group.Member): TypeName {
                if (member.name == "pNext") {
                    val vkStructStar = VulkanCodegen.vkStructCName.parameterizedBy(CaelumCodegenHelper.starWildcard)
                    val outVkStruct = WildcardTypeName.producerOf(vkStructStar)
                    return CaelumCodegenHelper.pointerCName.parameterizedBy(outVkStruct)
                }
                return super.memberKtApiType(member)
            }

            private fun String.pluralize(): String {
                return when (this.last()) {
                    's', 'h', 'o', 'x' -> this + "es"
                    'y' -> this.dropLast(1) + "ies"
                    else -> this + "s"
                }
            }

            context(ctx: CodegenContext)
            override fun addMemberAccessor(member: CType.Group.Member, unsafe: Boolean) {
                val memberType = member.type.deepResolve()
                if (memberType is CType.EnumBase) {
                    return commonAccess(member, member.name != "sType", unsafe)
                }
                val counted = member.tags.has<CountedTag>()
                val countTag = member.tags.getOrNull<CountTag>()
                if (countTag != null) {
                    check(memberType is CType.BasicType)
                    var funcName = member.name.removeSuffix("Count")
                    val firstName = countTag.v.first().name
                    if (funcName.length == member.name.length) {
                        check(countTag.v.size == 1)
                        funcName = firstName
                    } else {
                        funcName = funcName.pluralize()
                    }
                    val func = FunSpec.builder(funcName)
                    func.addOptIns(CaelumCodegenHelper.unsafeAPICName)
                    func.receiver(pointerCNameP)
                    val initCheckCode = CodeBlock.builder()
                    initCheckCode.addStatement(
                        "assert(this.%N == 0%L) { %S }",
                        member.name,
                        memberType.baseType.literalSuffix,
                        "${member.name} of ${element.name} already changed"
                    )

                    val nullable = member.tags.has<OptionalTag>()

                    countTag.v.forEachIndexed { i, it ->
                        val pType = it.type
                        val elementType = when (pType) {
                            is CType.Pointer -> pType.elementType
                            is CType.Array -> pType.elementType
                            else -> error("Unexpected type: $pType")
                        }
                        if (pType is CType.Pointer) {
                            initCheckCode.addStatement(
                                "assert(this.%N._address == 0L) { %S }",
                                it.name,
                                "${it.name} of ${element.name} already changed"
                            )
                        }
                        var elementTypeInArray = elementType.typeName()
                        if (elementType is CType.Pointer) {
                            elementTypeInArray = elementType.ktApiType()
                        }
                        func.addParameter(
                            it.name,
                            CaelumCodegenHelper.arrayCName
                                .parameterizedBy(elementTypeInArray)
                        )
                    }

                    countTag.v.forEachIndexed { i, it ->
                        if (it.type is CType.Pointer) {
                            initCheckCode.addStatement(
                                "assert(this.%N._address == 0L) { %S }",
                                it.name,
                                "${it.name} of ${element.name} already changed"
                            )
                        }
                    }

                    val nullOverloadFunc = FunSpec.builder(funcName)
                    nullOverloadFunc.addOptIns(CaelumCodegenHelper.unsafeAPICName)
                    nullOverloadFunc.receiver(pointerCNameP)
                    nullOverloadFunc.addCode(initCheckCode.build())
                    if (!nullable) {
                        nullOverloadFunc.addModifiers(KModifier.PRIVATE)
                    }
                    file.addFunction(nullOverloadFunc.build())
                    file.addFunction(
                        FunSpec.builder(funcName)
                            .receiver(valueCNameP)
                            .addStatement("ptr().%N()", funcName)
                            .build()
                    )

                    val code = CodeBlock.builder()
                    code.addStatement("this.%N()", funcName)

                    countTag.v.forEachIndexed { i, it ->
                        val pType = it.type
                        val elementType = when (pType) {
                            is CType.Pointer -> pType.elementType
                            is CType.Array -> pType.elementType
                            else -> error("Unexpected type: $pType")
                        }
                        var elementTypeName = elementType.typeName()
                        if (elementTypeName == CaelumCodegenHelper.starWildcard) {
                            elementTypeName = CBasicType.uint8_t.caelumCoreTypeName
                        } else if (elementType is CType.Pointer) {
                            elementTypeName = CaelumCodegenHelper.pointerCName
                        }
                        if (i == 0) {
                            code.addStatement("val count = %N.count", it.name)
                        } else {
                            code.addStatement(
                                "assert(count == %N.count) { %S }",
                                it.name,
                                "${it.name} has a different count"
                            )
                        }
                    }
                    val format = if (memberType.ktApiType() != LONG) {
                        "this.%N = count.to${memberType.baseType.kotlinType.simpleName}()"
                    } else {
                        "this.%N = count"
                    }
                    code.addStatement(format, member.name)

                    countTag.v.forEachIndexed { i, it ->
                        code.addStatement("this.%N = %N.ptr()", it.name, it.name)
                    }
                    func.addCode(code.build())
                    file.addFunction(func.build())
                    val valueOverloadCodeBlock = CodeBlock.builder()
                    valueOverloadCodeBlock.add("ptr().%N(", funcName)
                    valueOverloadCodeBlock.add(func.parameters.joinToCode { CodeBlock.of("%N", it.name) })
                    valueOverloadCodeBlock.add(")")
                    file.addFunction(
                        FunSpec.builder(funcName)
                            .receiver(valueCNameP)
                            .addParameters(func.parameters)
                            .addCode(valueOverloadCodeBlock.build())
                            .build()
                    )
                }
                super.addMemberAccessor(member, unsafe || counted || countTag != null)
            }
        }
        return generator.generate()
    }
}