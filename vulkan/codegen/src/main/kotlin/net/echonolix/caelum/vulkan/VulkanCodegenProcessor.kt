package net.echonolix.caelum.vulkan

import kotlinx.serialization.decodeFromString
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.filterType
import net.echonolix.caelum.codegen.api.deepReferenceResolve
import net.echonolix.caelum.vulkan.schema.API
import net.echonolix.caelum.vulkan.schema.FilteredRegistry
import net.echonolix.caelum.vulkan.schema.Registry
import net.echonolix.caelum.vulkan.tasks.GenerateEnumTask
import net.echonolix.caelum.vulkan.tasks.GenerateFunctionOverloadTask
import net.echonolix.caelum.vulkan.tasks.GenerateFunctionTask
import net.echonolix.caelum.vulkan.tasks.GenerateGroupTask
import net.echonolix.caelum.vulkan.tasks.GenerateHandleTask
import net.echonolix.caelum.vulkan.tasks.GenerateTypeDefTask
import net.echonolix.ktgen.KtgenProcessor
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.serialization.InputKind
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.structure.XmlDescriptor
import java.nio.file.Path
import java.util.concurrent.RecursiveAction
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.deleteRecursively
import kotlin.io.path.walk

fun countDepth(group: CType.Group, currDepth: Int = 1): Int {
    return group.members.maxOf {
        val memberType = it.type.deepReferenceResolve()
        if (memberType is CType.Group && memberType != group) {
            countDepth(memberType, currDepth + 1)
        } else {
            currDepth
        }
    }
}

class VulkanCodegenProcessor : KtgenProcessor {
    override fun process(inputs: Set<Path>, outputDir: Path): Set<Path> { 
        val registryText = javaClass.getResource("/vk.xml")!!.readText()
        val ignored = setOf("spirvextensions", "spirvcapabilities", "sync", "videocodecs")
        val xml = XML {
            indentString = "    "
            defaultToGenericParser = true
            defaultPolicy {
                autoPolymorphic = true
                typeDiscriminatorName = QName("category")
                @OptIn(ExperimentalXmlUtilApi::class)
                unknownChildHandler = object : UnknownChildHandler {
                    override fun handleUnknownChildRecovering(
                        input: XmlReader,
                        inputKind: InputKind,
                        descriptor: XmlDescriptor,
                        name: QName?,
                        candidates: Collection<Any>
                    ): List<XML.ParsedData<*>> {
                        if (ignored.contains(name?.toString())) {
                            return emptyList()
                        }
                        throw IllegalStateException("Unknown child $name")
                    }
                }
            }
        }
        val registry = xml.decodeFromString<Registry>(registryText)
        val filteredRegistry = FilteredRegistry(registry)
        val skipped = setOf("Header boilerplate", "API version macros")
        val ctx = CodegenContext(
            VulkanCodegenOutput( outputDir),
            VulkanElementResolver(filteredRegistry)
        )
        fun processRequire(requires: List<Registry.Feature.Require>) {
            requires.asSequence()
                .filter { it.comment !in skipped }
                .forEach { require ->
                    require.types.forEach {
                        ctx.resolveElement(it.name)
                    }
                    require.commands.forEach {
                        ctx.resolveElement(it.name)
                    }
                    require.enums.asSequence()
                        .filter { it.api == null || it.api == API.vulkan }
                        .forEach {
                            ctx.resolveElement(it.name)
                        }
                }
        }

        filteredRegistry.registryFeatures.forEach { processRequire(it.require) }
        filteredRegistry.registryExtensions.forEach { processRequire(it.require) }

//        val includedVKVersion = setOf(
//            "VK_VERSION_1_0",
//            "VK_VERSION_1_1",
//            "VK_VERSION_1_2",
//            "VK_VERSION_1_3"
//        )
//        val includedExtension = setOf(
//            "VK_KHR_surface",
//            "VK_KHR_swapchain",
//            "VK_EXT_debug_utils",
//            "VK_EXT_present_mode_fifo_latest_ready"
//        )
//        filteredRegistry.registryFeatures.asSequence()
//            .filter { it.name in includedVKVersion }
//            .forEach { processRequire(it.require) }
//        filteredRegistry.registryExtensions.asSequence()
//            .filter { it.name in includedExtension }
//            .forEach { processRequire(it.require) }

        val list = ctx.filterType<CType.Group>()
        val nestedCount = list.groupingBy { countDepth(it.second) }
            .eachCount()
        val handleCount = list.count { s ->
            s.second.members.any {
                it.type is CType.Handle
            }
        }
        val structInFuncPtrCount = ctx.filterType<CType.FunctionPointer>().count { funcPtr ->
            funcPtr.second.elementType.parameters.any {
                it.type.deepReferenceResolve() is CType.Group
            }
        }
        println(nestedCount)
        println(handleCount)
        println(structInFuncPtrCount)
        println(list.size)
        println()
        System.out.flush()
//        return emptySet()

        object : RecursiveAction() {
            override fun compute() {
                val handle = GenerateHandleTask(ctx).fork()
                val enum = GenerateEnumTask(ctx, filteredRegistry).fork()
                val group = GenerateGroupTask(ctx).fork()
                val typeDef = GenerateTypeDefTask(ctx).fork()
                val function = GenerateFunctionTask(ctx).fork()
                val overload = GenerateFunctionOverloadTask(ctx).fork()
                handle.join()
                enum.join()
                group.join()
                typeDef.join()
                function.join()
                overload.join()
            }
        }.fork().join()

        return ctx.outputFiles
    }
}

tailrec fun addParentUpTo(curr: Path?, end: Path, output: MutableCollection<Path>) {
    if (curr == null) return
    if (curr == end) return
    output.add(curr)
    return addParentUpTo(curr.parent, end, output)
}

@OptIn(ExperimentalPathApi::class)
fun main() {
    val time = System.nanoTime()
    val outputDir = Path("vulkan/build/generated/ktgen")
    val updatedFiles = VulkanCodegenProcessor().process(emptySet(), outputDir).toMutableSet()
    updatedFiles.toList()
        .forEach {
            addParentUpTo(it.parent, outputDir, updatedFiles)
        }
    outputDir.walk(PathWalkOption.INCLUDE_DIRECTORIES, PathWalkOption.BREADTH_FIRST, PathWalkOption.FOLLOW_LINKS)
        .filter { it != outputDir }
        .filter { it !in updatedFiles }
        .forEach {
            it.deleteRecursively()
        }
    println("Time: %.2fs".format((System.nanoTime() - time) / 1_000_000.0 / 1000.0))
}