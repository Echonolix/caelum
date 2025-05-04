package net.echonolix.caelum.codegen.c

import net.echonolix.caelum.CElement
import net.echonolix.caelum.CaelumCodegenContext
import net.echonolix.caelum.codegen.c.adapter.CAstContext
import java.nio.file.Path

class CCodeGenContext(basePkgName: String, outputDir: Path, val cAstContext: CAstContext) : CaelumCodegenContext(basePkgName, outputDir) {
    override fun resolvePackageName(element: CElement): String {
        TODO("Not yet implemented")
    }

    override fun resolveElementImpl(cElementStr: String): CElement {
        TODO("Not yet implemented")
    }
}