package org.echonolix.vulkan.ffi

import kotlinx.serialization.decodeFromString
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.serialization.InputKind
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.structure.XmlDescriptor
import org.echonolix.ktgen.KtgenProcessor
import org.echonolix.vulkan.schema.FilteredRegistry
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
        val filteredRegistry = FilteredRegistry(registry)
        val ctx = VKFFICodeGenContext(VKFFI.packageName, outputDir, filteredRegistry)
        filteredRegistry.funcPointerTypes.values.asSequence()
            .forEach {
                println(ctx.resolveType(it.name!!))
            }
//        ctx.allElement.values.sorted().forEach(::println)

//        val patchedRegistry = PatchedRegistry(registry)
//
//        val gc = FFIGenContext(VKFFI.packageName, outputDir) {
//            true
//        }
//
//        object : RecursiveAction() {
//            override fun compute() {
//                val genEnumTask = GenerateCEnumTask(gc, patchedRegistry).fork()
//                val genGroupTask = GenerateCGroupTask(gc, patchedRegistry).fork()
//                val genFuncPointerTask = GenerateCFuncPointerTask(gc, patchedRegistry).fork()
//                val genHandleTask = GenerateHandleTask(gc, patchedRegistry).fork()
//
//                genEnumTask.join()
//                genGroupTask.join()
//                genFuncPointerTask.join()
//                genHandleTask.join()
//            }
//        }.fork().join()
    }
}

@OptIn(ExperimentalPathApi::class)
fun main() {
    System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "1")
    val time = System.nanoTime()
    val outputDir = Path.of("vulkan/build/generated/ktgen")
    outputDir.deleteRecursively()
    VkFFICodeGenProcessor().process(emptyList(), outputDir)
    println("Time: %.2fs".format((System.nanoTime() - time) / 1_000_000.0 / 1000.0))
}