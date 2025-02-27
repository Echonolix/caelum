package org.echonolix.ktffi

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeName
import java.nio.file.Path

abstract class KTFFICodegenContext {
    abstract val outputDir : Path

    abstract fun filter(type: CElement): Boolean
    abstract fun getPackageName(type: CElement): String
    abstract fun getCustomBaseType(type: CElement): TypeName?

    fun writeOutput(fileSpec: FileSpec.Builder) {
        fileSpec.addSuppress()
        fileSpec.indent("    ")
        fileSpec.build().writeTo(outputDir)
    }
}