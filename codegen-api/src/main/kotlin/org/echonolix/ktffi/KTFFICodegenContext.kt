package org.echonolix.ktffi

import com.squareup.kotlinpoet.FileSpec
import java.nio.file.Path

abstract class KTFFICodegenContext(val basePkgName: String, val outputDir: Path) {
    abstract fun resolvePackageName(element: CElement): String
    abstract fun resolveTypeImpl(cTypeStr: String): CType

    fun resolveType(cTypeStr: String): CType {
        if (cTypeStr.last() == '*') {
            return CType.Pointer(resolveType(cTypeStr.dropLast(1)))
        }
        CBasicType.Companion.fromStringOrNull(cTypeStr)?.let {
            return it.cType
        }
        return resolveTypeImpl(cTypeStr)
    }

    fun writeOutput(fileSpec: FileSpec.Builder) {
        fileSpec.addSuppress()
        fileSpec.indent("    ")
        fileSpec.build().writeTo(outputDir)
    }
}