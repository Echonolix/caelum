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
import net.echonolix.caelum.vulkan.StructTypeTag
import net.echonolix.caelum.vulkan.VulkanCodegen
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

        check(mergedClusters.size == 8)
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
            override fun buildTypeObjectType(): TypeSpec.Builder {
                val type = super.buildTypeObjectType()

                if (groupType is CType.Struct) {
                    val initializerCodeBlock = groupType.members
                        .firstNotNullOfOrNull { it.tags.get<StructTypeTag>() }
                        ?.let { tag ->
                            CodeBlock.of(
                                "return %T.%N",
                                vkStructureTypeCName,
                                tag.structType.tags.get<EnumEntryFixedName>()!!.name
                            )
                        } ?: CodeBlock.of("return null")

                    type.addProperty(
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

                return type
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

            context(ctx: CodegenContext)
            override fun addMemberAccessor(member: CType.Group.Member) {
                val memberType = member.type.deepResolve()
                if (memberType is CType.EnumBase) {
                    return commonAccess(member, member.name != "sType")
                }
                super.addMemberAccessor(member)
            }
        }
        return generator.generate()
    }
}