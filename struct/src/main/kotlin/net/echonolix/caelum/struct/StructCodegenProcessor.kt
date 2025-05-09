package net.echonolix.caelum.struct

import net.echonolix.caelum.NStruct
import net.echonolix.caelum.NUnion
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.ElementDocumenter
import net.echonolix.caelum.codegen.api.ctx.resolveTypedElement
import net.echonolix.caelum.codegen.api.generator.GroupGenerator
import net.echonolix.ktgen.KtgenProcessor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Path
import kotlin.reflect.full.superclasses
import kotlin.streams.asStream

class StructCodegenProcessor : KtgenProcessor {
    override fun process(
        inputs: Set<Path>,
        outputDir: Path
    ): Set<Path> {
        val ctx = CodegenContext(
            StructCodegenOutput(outputDir),
            StructElementResolver(),
            ElementDocumenter.Base()
        )

        val acceptedSuperInterfaces = setOf(
            NStruct::class,
            NUnion::class
        )

        inputs.parallelStream()
            .flatMap { it.toFile().walk().asStream() }
            .filter { it.extension == "class" }
            .map {
                ClassNode().apply {
                    ClassReader(it.readBytes()).accept(this, 0)
                }
            }.map {
                Class.forName(it.name.replace('/', '.')).kotlin
            }.filter {
                it.java.isInterface
            }.filter {
                it.superclasses.size == 2 && it.superclasses.first() in acceptedSuperInterfaces
            }.map {
                ctx.resolveTypedElement<CType.Group>(it!!.qualifiedName!!)
            }.map {
                GroupGenerator(ctx, it).generate()
            }.forEach {
                ctx.writeOutput(it)
            }

        return ctx.outputFiles
    }
}