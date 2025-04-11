package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.CodeBlock
import net.echonolix.ktffi.*
import net.echonolix.vulkan.schema.FilteredRegistry
import net.echonolix.vulkan.schema.Registry
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

    private val cTypeNameRegex = """[a-zA-Z_][a-zA-Z0-9_]*""".toRegex()
    private val cTypeRegex = """(?:const\s+)?(${cTypeNameRegex.pattern}(?:\s*(?:\*|\[.*?])\s*)*)""".toRegex()
    private val typeDefRegex = """\s*typedef\s+${cTypeRegex.pattern}\s+(${cTypeNameRegex.pattern})\s*;""".toRegex()

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
    private val funcPointerParamSplitRegex = """\s*(?:,|\);)\s*""".toRegex()
    private val funcPointerParameterRegex =
        """\s*${cTypeRegex.pattern}\s+(${cTypeNameRegex.pattern})\s*""".toRegex()

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
            .flatMap { it.split(funcPointerParamSplitRegex) }
            .filter { it.isNotBlank() }
            .map {
                funcPointerParameterRegex.matchEntire(it)
                    ?: throw IllegalStateException("Cannot resolve func pointer parameter for: ${typeDefType.name}")
            }.map { it.groupValues }.map {
                CDeclaration(it[2], resolveType(it[1]))
            }.toList()
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
        // TODO: add enum values
        return enumBase
    }

    private fun resolveStruct(struct: Registry.Types.Type): CType.Struct {
        val struct = CType.Struct(struct.name!!, mutableListOf())
        // TODO: add struct members
        return struct
    }

    private fun resolveHandle(handle: Registry.Types.Type): CType.Handle {
        return object : CType.Handle(handle.name!!, CBasicType.size_t.cType) {
            context(ctx: KTFFICodegenContext) override fun memoryLayout(): CodeBlock {
                TODO("Not yet implemented")
            }
        }
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

        registry.structTypes[cTypeStr]?.let {
            return resolveStruct(it)
        }

        registry.handleTypes[cTypeStr]?.let {
            return resolveHandle(it)
        }

        throw kotlin.IllegalStateException("Cannot resolve type: $cTypeStr")
    }
}
