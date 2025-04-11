package org.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.CodeBlock
import org.echonolix.ktffi.CBasicType
import org.echonolix.ktffi.CConst
import org.echonolix.ktffi.CDeclaration
import org.echonolix.ktffi.CElement
import org.echonolix.ktffi.CType
import org.echonolix.ktffi.KTFFICodegenContext
import org.echonolix.vulkan.schema.Element
import org.echonolix.vulkan.schema.FilteredRegistry
import org.echonolix.vulkan.schema.Registry
import java.nio.file.Path
import kotlin.collections.set

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

    private val cTypeNameRegex = """[a-zA-Z_][a-zA-Z0-9_]*""".toRegex()
    private val cTypeRegex = """(?:const\s+)?(${cTypeNameRegex.pattern}(?:\s*(?:\*|\[.*?])\s*)*)""".toRegex()
    private val typeDefRegex =
        """\s*typedef\s+${cTypeRegex.pattern}\s+(${cTypeNameRegex.pattern})\s*;""".toRegex()

    private fun resolveTypeDef(typeDefType: Registry.Types.Type): CType.TypeDef {
        typeDefType.name!!
        val typeDefStr = typeDefType.inner.toXmlTagFreeString()
        val matchResult =
            typeDefRegex.matchEntire(typeDefStr) ?: throw IllegalStateException("Cannot resolve typedef: $typeDefStr")
        val (dstTypeStr, srcTypeStr) = matchResult.destructured
        assert(srcTypeStr == typeDefType.name)
        val dstType = resolveType(dstTypeStr)
        return CType.TypeDef(typeDefType.name, dstType)
    }

    private val funcPointerHeaderRegex =
        """\s*typedef\s+${cTypeRegex.pattern}\s+\(VKAPI_PTR\s*\*\s+(${cTypeNameRegex.pattern})\s*\)\((?:void\);)?""".toRegex()
    private val funcPointerParameterRegex =
        """\s*${cTypeRegex.pattern}\s+(${cTypeNameRegex.pattern})\s*(?:.|\);)""".toRegex()

    private fun resolveFuncPointerType(typeDefType: Registry.Types.Type): CType.TypeDef {
        assert(typeDefType.category == Registry.Types.Type.Category.funcpointer)
        typeDefType.name!!
        val funcStrLines = typeDefType.inner.toXmlTagFreeString().lines()
        val headerMatchResult = funcPointerHeaderRegex.matchEntire(funcStrLines.first()) ?: throw IllegalStateException(
            "Cannot resolve func pointer header for: ${typeDefType.name}"
        )
        assert(typeDefType.name == headerMatchResult.groupValues[2])
        val returnTypeStr = headerMatchResult.groupValues[1]
        val returnType = resolveType(returnTypeStr)
        val parameters = funcStrLines.asSequence()
            .drop(1)
            .map {
                funcPointerParameterRegex.matchEntire(it)
                    ?: throw IllegalStateException("Cannot resolve func pointer parameter for: ${typeDefType.name}")
            }
            .map { it.groupValues }
            .map {
                CDeclaration(it[2], resolveType(it[1]))
            }
            .toList()
        val func = CType.Function("VkFunc${typeDefType.name.removePrefix("PFN_vk")}", returnType, parameters)
        addToCache(func)
        val funcPointer = CType.Pointer(func)
        addToCache(func)
        return CType.TypeDef(typeDefType.name, funcPointer)
    }

    private fun resolveEnum(enums: Registry.Enums): CType.EnumBase {
        val enumBase = when (enums.type) {
            Registry.Enums.Type.enum -> CType.Enum(enums.name, CBasicType.uint32_t.cType)
            Registry.Enums.Type.bitmask -> {
                val entryType = if (enums.bitwidth == 64) CBasicType.uint64_t.cType else CBasicType.uint32_t.cType
                CType.Enum(enums.name, entryType)
            }
            else -> throw IllegalStateException("Unsupported enum type: ${enums.type}")
        }
        //TODO: add enum values
        return enumBase
    }

    override fun resolveTypeImpl(cTypeStr: String): CType {
        registry.typeDefTypes[cTypeStr]?.let {
            return resolveTypeDef(it)
        }

        registry.funcPointerTypes[cTypeStr]?.let {
            return resolveFuncPointerType(it)
        }

        registry.enums[cTypeStr]?.let {
            return resolveEnum(it)
        }

        throw kotlin.IllegalStateException("Cannot resolve type: $cTypeStr")
    }
}
