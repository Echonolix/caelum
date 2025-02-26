package org.echonolix.ktffi

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeName
import java.nio.file.Path

abstract class KTFFICodegenContext {
    abstract val outputDir : Path

    abstract fun filter(type: CDeclaration): Boolean
    abstract fun getPackageName(type: CDeclaration): String
    abstract fun getCustomBaseType(type: CDeclaration): TypeName?

    fun writeOutput(fileSpec: FileSpec.Builder) {
        fileSpec.addSuppress()
        fileSpec.indent("    ")
        fileSpec.build().writeTo(outputDir)
    }
}