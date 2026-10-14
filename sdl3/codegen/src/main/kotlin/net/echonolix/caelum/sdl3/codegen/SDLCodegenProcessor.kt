package net.echonolix.caelum.sdl3.codegen

import net.echonolix.ktgen.KtgenProcessor
import java.nio.file.Files
import java.nio.file.Path

public class SDLCodegenProcessor : KtgenProcessor {
    override fun process(inputs: Set<Path>, outputDir: Path): Set<Path> {
        val includeDir = inputs.singleOrNull { Files.isDirectory(it) }
            ?: error("SDL codegen requires exactly one SDL3 include directory, got: ${inputs.sorted()}")
        val registry = SDLHeaderParser.parse(includeDir)
        val callbacks = SDLCallbackParser.parse(includeDir, registry.namedTypes)
        val constants = SDLConstantParser.parse(includeDir)
        return SDLTypeGenerator.generate(registry, outputDir) +
            SDLGenerator.generate(registry, outputDir) +
            SDLCallbackGenerator.generate(callbacks, outputDir) +
            SDLConstantGenerator.generate(constants, outputDir)
    }
}
