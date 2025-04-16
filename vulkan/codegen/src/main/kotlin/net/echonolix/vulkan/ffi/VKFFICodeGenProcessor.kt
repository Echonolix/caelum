package net.echonolix.vulkan.ffi

import kotlinx.serialization.decodeFromString
import net.echonolix.ktgen.KtgenProcessor
import net.echonolix.vulkan.schema.API
import net.echonolix.vulkan.schema.FilteredRegistry
import net.echonolix.vulkan.schema.Registry
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
import kotlin.io.path.PathWalkOption
import kotlin.io.path.deleteRecursively
import kotlin.io.path.walk

class VKFFICodeGenProcessor : KtgenProcessor {
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
        val ctx = VKFFICodeGenContext(VKFFI.basePkgName, outputDir, filteredRegistry)
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

//        filteredRegistry.registryFeatures.forEach { processRequire(it.require) }
//        filteredRegistry.registryExtensions.forEach { processRequire(it.require) }

        val includedVKVersion = setOf("VK_VERSION_1_0", "VK_VERSION_1_1")
        val includedExtension = setOf("VK_KHR_surface", "VK_KHR_swapchain", "VK_EXT_debug_utils")
        filteredRegistry.registryFeatures.asSequence()
            .filter { it.name in includedVKVersion }
            .forEach { processRequire(it.require) }
        filteredRegistry.registryExtensions.asSequence()
            .filter { it.name in includedExtension }
            .forEach { processRequire(it.require) }

        object : RecursiveAction() {
            override fun compute() {
                val handle = GenerateHandleTask(ctx).fork()
                val enum = GenerateEnumTask(ctx).fork()
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
    val outputDir = Path.of("vulkan/build/generated/ktgen")
    val updatedFiles = VKFFICodeGenProcessor().process(emptySet(), outputDir).toMutableSet()
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