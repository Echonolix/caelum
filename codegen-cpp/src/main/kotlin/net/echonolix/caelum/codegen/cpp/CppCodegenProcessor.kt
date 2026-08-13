package net.echonolix.caelum.codegen.cpp

import net.echonolix.ktgen.KtgenProcessor
import java.nio.file.Path

/** Ktgen service entry point for the C++ header-to-Caelum pipeline. */
public class CppCodegenProcessor : KtgenProcessor {
    override fun process(inputs: Set<Path>, outputDir: Path): Set<Path> {
        require(inputs.size == 1) { "C++ codegen accepts exactly one root header, got ${inputs.size}" }
        val header = inputs.single()
        val moduleName = requiredProperty("codegencpp.moduleName")
        val config = CppCodegenConfig(
            moduleName = moduleName,
            kotlinPackage = requiredProperty("codegencpp.packageName"),
            kotlinObjectName = System.getProperty("codegencpp.objectName")?.takeIf(String::isNotBlank)
                ?: moduleName.toPascalCase() + "Native",
        )
        val generated = CppCodegen.generate(
            header = header,
            outputDirectory = outputDir,
            config = config,
            clangExecutable = System.getProperty("codegencpp.clang")?.takeIf(String::isNotBlank) ?: "clang++",
            compilerArguments = compilerArguments(),
        )
        return setOf(generated.header, generated.source, generated.kotlin, generated.diagnostics)
    }

    private fun requiredProperty(name: String): String =
        requireNotNull(System.getProperty(name)?.takeIf(String::isNotBlank)) { "missing required system property '$name'" }

    private fun compilerArguments(): List<String> {
        val indexed = System.getProperties().stringPropertyNames()
            .mapNotNull { name ->
                val index = name.removePrefix("codegencpp.compilerArg.").takeIf { name.startsWith("codegencpp.compilerArg.") }?.toIntOrNull()
                index?.let { it to System.getProperty(name) }
            }
            .sortedBy(Pair<Int, String>::first)
            .map(Pair<Int, String>::second)
        if (indexed.isNotEmpty()) return indexed
        return System.getProperty("codegencpp.compilerArgs")
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.toList()
            .orEmpty()
    }
}
