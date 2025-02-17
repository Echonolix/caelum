package org.echonolix.vkffi

import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.serialization.InputKind
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.structure.XmlDescriptor
import org.echonolix.ktgen.KtgenProcessor
import org.echonolix.vkffi.schema.PatchedRegistry
import org.echonolix.vkffi.schema.Registry
import java.nio.file.Path

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

        val gc = FFIGenContext("org.echonolix.vulkan")

        with(gc) {
            val patchedRegistry = PatchedRegistry(registry)
            genEnums(patchedRegistry)
            genStruct(patchedRegistry)
        }

        gc.writeOutput(outputDir)
    }
}