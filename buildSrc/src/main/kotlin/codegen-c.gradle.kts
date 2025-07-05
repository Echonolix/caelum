package buildsrc.convention

import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

plugins {
    id("buildsrc.convention.published-module")
    id("net.echonolix.ktgen")
}

val codegenCExtension = extensions.create("codegenC", CodegenCExtension::class.java)

dependencies {
    ktgen(project(":codegen-c"))
    implementation(project(":caelum-core"))
}

kotlin {
    explicitApi()
}

tasks.ktgen {
    extra.set("AAA", codegenCExtension.elementMapper)
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("codegenc.packageName", codegenCExtension.packageName.get())
    systemProperty("codegenc.functionBaseTypeName", codegenCExtension.functionBaseTypeName.get())
    systemProperty(
        "codegenc.preprocessDefines",
        codegenCExtension.preprocessDefines.get().entries
            .filter { it.key.isNotBlank() }
            .joinToString(",") { (key, value) ->
                if (value.isBlank()) {
                    key
                } else {
                    "$key=$value"
                }
            }
    )
    systemProperty("codegenc.excludedIncludes", codegenCExtension.excludedIncludes.get().joinToString(","))
    @Suppress("UNCHECKED_CAST")
    val elementMapper = extra.get("AAA") as (ElementType, String) -> String?
    execWrapper = {
        val outputToInputPipe = PipedInputStream()
        val outputPipe = PipedOutputStream(outputToInputPipe)

        val inputToOutputPipe = PipedOutputStream()
        val inputPipe = PipedInputStream(inputToOutputPipe)

        setStandardOutput(outputPipe)
        setStandardInput(inputPipe)

        val incomingLines = ArrayBlockingQueue<String>(100)
        var alive = AtomicBoolean(true)
        Thread {
            outputToInputPipe.bufferedReader().forEachLine {
                incomingLines.put(it)
            }
            incomingLines.put("---EOF---")
        }.start()
        Thread {
            inputToOutputPipe.writer().use { stdinWriter ->
                var line = incomingLines.take()
                while (line != "---EOF---") {
                    val (typeStr, name) = line.split(" ")
                    val type = ElementType.valueOf(typeStr)
                    val newName = elementMapper(type, name) ?: "null"
                    stdinWriter.write("$newName\n")
                    stdinWriter.flush()
                    line = incomingLines.take()
                }
            }
        }.start()
        callExec()
    }
}