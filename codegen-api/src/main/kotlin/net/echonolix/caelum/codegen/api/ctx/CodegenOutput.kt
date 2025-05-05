package net.echonolix.caelum.codegen.api.ctx

import com.squareup.kotlinpoet.FileSpec
import net.echonolix.caelum.codegen.api.CElement
import net.echonolix.caelum.codegen.api.CTopLevelConst
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.addSuppress
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue

public interface CodegenOutput {
    public val basePackageName: String
    public val outputFiles: Set<Path>

    public fun resolvePackageName(element: CElement): String

    public fun writeOutput(path: Path, fileSpec: FileSpec.Builder)

    public fun writeOutput(fileSpec: FileSpec.Builder)

    public open class Base(public val outputDir: Path, override val basePackageName: String) : CodegenOutput {
        public override val outputFiles: Set<Path>
            get() = outputFiles0.toSet()

        private val outputFiles0 = ConcurrentLinkedQueue<Path>()

        override fun resolvePackageName(element: CElement): String {
            return when (element) {
                is CType.Enum -> "${basePackageName}.enums"
                is CType.Struct -> "${basePackageName}.structs"
                is CType.Union -> "${basePackageName}.unions"
                is CType.Function -> "${basePackageName}.functions"
                is CTopLevelConst -> "${basePackageName}.consts"
                else -> throw IllegalArgumentException("Unsupported element type: ${element::class}")
            }
        }

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
}