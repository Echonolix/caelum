package net.echonolix.caelum.struct

import net.echonolix.caelum.codegen.api.CElement
import net.echonolix.caelum.codegen.api.ctx.CodegenOutput
import java.nio.file.Path

class StructCodegenOutput(outputDir: Path) : CodegenOutput.Base(outputDir, "") {
    override fun resolvePackageName(element: CElement): String {
        return element.tags.get<PackageNameTag>().packageName
    }
}