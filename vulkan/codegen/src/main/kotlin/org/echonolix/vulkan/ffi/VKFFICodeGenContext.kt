package org.echonolix.vulkan.ffi

import org.echonolix.ktffi.CElement
import org.echonolix.ktffi.CType
import org.echonolix.ktffi.KTFFICodegenContext
import org.echonolix.vulkan.schema.NewPatchedRegistry
import java.nio.file.Path

class VKFFICodeGenContext(basePkgName: String, outputDir: Path, val pRegistry: NewPatchedRegistry) :
    KTFFICodegenContext(basePkgName, outputDir) {
    val enumPackageName = "${basePkgName}.enums"
    val structPackageName = "${basePkgName}.structs"
    val unionPackageName = "${basePkgName}.unions"
    val funcPointerPackageName = "${basePkgName}.funcptrs"
    val handlePackageName = "${basePkgName}.handles"

    override fun resolvePackageName(element: CElement): String {
        return when (element) {
            is CType.Enum -> enumPackageName
            is CType.Bitmask -> enumPackageName
            is CType.Struct -> structPackageName
            is CType.Union -> unionPackageName
            is CType.Handle -> handlePackageName
            else -> throw IllegalArgumentException("Unsupported element: $element")
        }
    }

    override fun resolveTypeImpl(cTypeStr: String): CType {
        TODO("Not yet implemented")
    }
}