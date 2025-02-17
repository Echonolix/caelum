package org.echonolix.vulkan

import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.serialization.InputKind
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.structure.XmlDescriptor
import org.echonolix.ktgen.KtgenProcessor
import org.echonolix.vulkan.schema.PatchedRegistry
import org.echonolix.vulkan.schema.Registry
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

class VkFFICodeGenProcessor : KtgenProcessor {
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
        val patchedRegistry = PatchedRegistry(registry)

        val gc = FFIGenContext(VKFFI.packageName, patchedRegistry.externalTypes + patchedRegistry.opaqueTypes.keys)

        with(gc) {
            genEnums(patchedRegistry)
            genUnion(patchedRegistry)
            genStruct(patchedRegistry)
        }

        gc.writeOutput(outputDir)
    }
}

@OptIn(ExperimentalPathApi::class)
fun main() {
    val outputDir = Path.of("vulkan/build/generated/ktgen")
    outputDir.deleteRecursively()
    VkFFICodeGenProcessor().process(emptyList(), outputDir)
}