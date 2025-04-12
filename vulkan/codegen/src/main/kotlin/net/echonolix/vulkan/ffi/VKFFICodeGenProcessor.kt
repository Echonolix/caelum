package net.echonolix.vulkan.ffi

import kotlinx.serialization.decodeFromString
import net.echonolix.ktffi.CType
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.serialization.InputKind
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.structure.XmlDescriptor
import net.echonolix.ktgen.KtgenProcessor
import net.echonolix.vulkan.schema.FilteredRegistry
import net.echonolix.vulkan.schema.Registry
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

class VKFFICodeGenProcessor : KtgenProcessor {
    override fun process(inputs: List<Path>, outputDir: Path) {
        val registryText = javaClass.getResource("/vk.xml")!!.readText()
        val ignored = setOf("spirvextensions", "spirvcapabilities", "sync", "videocodecs")
        val xml = XML {
            indentString = "    "
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
        val ctx = VKFFICodeGenContext(VKFFI.packageName, outputDir, filteredRegistry)
        ctx.resolveElement("VK_API_VERSION_1_0")
        filteredRegistry.registryFeatures.asSequence()
            .flatMap { it.require }
            .filter { it.comment !in skipped }
            .forEach { require ->
                require.types.forEach {
                    ctx.resolveElement(it.name)
                }
            }
        ctx.allElement.values.asSequence()
            .filterIsInstance<CType.EnumBase>()
            .sorted()
            .forEach {
            println(it)
        }
    }
}

@OptIn(ExperimentalPathApi::class)
fun main() {
    System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "1")
    val time = System.nanoTime()
    val outputDir = Path.of("vulkan/build/generated/ktgen")
    outputDir.deleteRecursively()
    VKFFICodeGenProcessor().process(emptyList(), outputDir)
    println("Time: %.2fs".format((System.nanoTime() - time) / 1_000_000.0 / 1000.0))
}