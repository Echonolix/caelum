package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.FileSpec
import net.echonolix.ktffi.addSuppress
import net.echonolix.vulkan.schema.Element
import java.nio.file.Path

class FFIGenContext(val packageName: String, val outputDir: Path, val filter: (Element.Type) -> Boolean) {
    val enumPackageName = "${packageName}.enums"
    val structPackageName = "${VKFFI.basePkgName}.structs"
    val unionPackageName = "${VKFFI.basePkgName}.unions"
    val funcPointerPackageName = "${VKFFI.basePkgName}.funcptrs"
    val handlePackageName = "${VKFFI.basePkgName}.handles"

    fun getPackageName(type: Element.Type): String {
        return when (type) {
            is Element.CEnum -> enumPackageName
            is Element.FlagType -> enumPackageName
            is Element.Struct -> structPackageName
            is Element.Union -> unionPackageName
            is Element.HandleType -> handlePackageName
            else -> throw IllegalArgumentException("Unsupported type: $type")
        }
    }

    fun writeOutput(fileSpec: FileSpec.Builder) {
        fileSpec.addSuppress()
        fileSpec.indent("    ")
        fileSpec.build().writeTo(outputDir)
    }
}