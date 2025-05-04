package net.echonolix.caelum.codegen.api.ctx

import com.squareup.kotlinpoet.FileSpec
import net.echonolix.caelum.codegen.api.CElement
import net.echonolix.caelum.codegen.api.addSuppress
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue

public interface CodegenOutput {
    public val outputFiles: Set<Path>

    public fun resolvePackageName(element: CElement): String

    public fun writeOutput(path: Path, fileSpec: FileSpec.Builder)

    public fun writeOutput(fileSpec: FileSpec.Builder)

    public abstract class Base(public val outputDir: Path) : CodegenOutput {
        public override val outputFiles: Set<Path>
            get() = outputFiles0.toSet()

        private val outputFiles0 = ConcurrentLinkedQueue<Path>()

        public override fun writeOutput(path: Path, fileSpec: FileSpec.Builder) {
            fileSpec.addSuppress()
            fileSpec.indent("    ")
            outputFiles0.add(fileSpec.build().writeTo(outputDir.resolve(path)))
        }

        public override fun writeOutput(fileSpec: FileSpec.Builder) {
            fileSpec.addSuppress()
            fileSpec.indent("    ")
            outputFiles0.add(fileSpec.build().writeTo(outputDir))
        }
    }

    public class Default(outputDir: Path, private val packageName: String) : Base(outputDir) {
        override fun resolvePackageName(element: CElement): String {
            return packageName
        }
    }
}