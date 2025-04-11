package org.echonolix.vulkan.ffi

import org.echonolix.ktffi.CElement
import org.echonolix.ktffi.CType
import org.echonolix.ktffi.KTFFICodegenContext
import org.echonolix.vulkan.schema.FilteredRegistry
import org.echonolix.vulkan.schema.Registry
import org.echonolix.vulkan.schema.XMLType
import java.lang.IllegalStateException
import java.nio.file.Path

class VKFFICodeGenContext(basePkgName: String, outputDir: Path, val registry: FilteredRegistry) :
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

    private val pointerOrArrayRegex = """(\*|\[.*?])+""".toRegex()

    private fun resolveTypeDef(typeDefType: Registry.Types.Type): CType.TypeDef {
        typeDefType.name!!
        var (index, dstTypeStr) = typeDefType.inner.withIndex().firstNotNullOfOrNull { (index, value) ->
            value.tryParseXML<XMLType>()?.value?.let {
                index to it
            }
        } ?: throw IllegalStateException("Cannot resolve typedef: ${typeDefType.name}")

        typeDefType.inner.getOrNull(index + 1)?.let {
            val trimStr = it.contentString.trim()
            if (trimStr.matches(pointerOrArrayRegex)) {
                dstTypeStr += trimStr
            }
        }

        val dstType = resolveType(dstTypeStr)
        return CType.TypeDef(typeDefType.name, dstType)
    }

    override fun resolveTypeImpl(cTypeStr: String): CType {
        registry.typeDefTypes[cTypeStr]?.let {
            return resolveTypeDef(it)
        }

        throw kotlin.IllegalStateException("Cannot resolve type: $cTypeStr")
    }
}
