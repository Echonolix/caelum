package org.echonolix.vulkan

import com.squareup.kotlinpoet.FileSpec
import java.nio.file.Path

class FFIGenContext(
    val packageName: String,
) {
    val fileSpecs = mutableListOf<FileSpec.Builder>()

    fun newFile(fileSpec: FileSpec.Builder): FileSpec.Builder {
        fileSpec.addSuppress()
        fileSpec.indent("    ")
        fileSpecs.add(fileSpec)
        return fileSpec
    }

    fun writeOutput(dir: Path) {
        fileSpecs.forEach {
            it.build().writeTo(dir)
        }
    }
}