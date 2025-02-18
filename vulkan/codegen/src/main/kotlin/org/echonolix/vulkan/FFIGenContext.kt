package org.echonolix.vulkan

import com.squareup.kotlinpoet.FileSpec
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue

class FFIGenContext() {
    val fileSpecs = ConcurrentLinkedQueue<FileSpec.Builder>()

    fun newFile(fileSpec: FileSpec.Builder): FileSpec.Builder {
        fileSpec.addSuppress()
        fileSpec.indent("    ")
        fileSpecs.add(fileSpec)
        return fileSpec
    }

    fun writeOutput(dir: Path) {
        fileSpecs.toList().parallelStream().forEach {
            it.build().writeTo(dir)
        }
    }
}