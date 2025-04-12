package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.CodeBlock
import net.echonolix.ktffi.*
import net.echonolix.vulkan.schema.Element
import net.echonolix.vulkan.schema.FilteredRegistry
import net.echonolix.vulkan.schema.Registry
import net.echonolix.vulkan.schema.XMLComment
import net.echonolix.vulkan.schema.XMLMember
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

    private fun resolveTypeDef(xmlTypeDefType: Registry.Types.Type): CType.TypeDef {
        xmlTypeDefType.name!!
        val typeDefStr = xmlTypeDefType.inner.toXmlTagFreeString()
        val matchResult =
            CSyntax.typeDefRegex.matchEntire(typeDefStr)
                ?: throw IllegalStateException("Cannot resolve typedef: $typeDefStr")
        val (dstTypeStr, srcTypeStr) = matchResult.destructured
        assert(srcTypeStr == xmlTypeDefType.name)
        val dstType = resolveType(dstTypeStr)
        return CType.TypeDef(xmlTypeDefType.name, dstType)
    }

    private fun resolveFuncPointerType(xmlTypeDefType: Registry.Types.Type): CType.TypeDef {
        assert(xmlTypeDefType.category == Registry.Types.Type.Category.funcpointer)
        xmlTypeDefType.name!!
        val funcStrLines = xmlTypeDefType.inner.toXmlTagFreeString().lines()
        val headerMatchResult =
            CSyntax.funcPointerHeaderRegex.matchEntire(funcStrLines.first()) ?: throw IllegalStateException(
                "Cannot resolve func pointer header for: ${xmlTypeDefType.name}"
            )
        assert(xmlTypeDefType.name == headerMatchResult.groupValues[2])
        val returnTypeStr = headerMatchResult.groupValues[1]
        val returnType = resolveType(returnTypeStr)
        val parameters = funcStrLines.asSequence()
            .drop(1)
            .flatMap { it.split(CSyntax.funcPointerParamSplitRegex) }
            .filter { it.isNotBlank() }
            .map {
                CSyntax.funcPointerParameterRegex.matchEntire(it)
                    ?: throw IllegalStateException("Cannot resolve func pointer parameter for: ${xmlTypeDefType.name}")
            }.map { it.groupValues }.map {
                CType.Function.Parameter(it[2], resolveType(it[1]))
            }.toList()
        val func = CType.Function("VkFunc${xmlTypeDefType.name.removePrefix("PFN_vk")}", returnType, parameters)
        addToCache(func)
        val funcPointer = CType.FunctionPointer(func)
        addToCache(func)
        return CType.TypeDef(xmlTypeDefType.name, funcPointer)
    }

    private fun resolveEnum(xmlEnums: Registry.Enums): CType.EnumBase {
        val enumBase = when (xmlEnums.type) {
            Registry.Enums.Type.enum -> CType.Enum(xmlEnums.name, CBasicType.uint32_t.cType)
            Registry.Enums.Type.bitmask -> {
                val entryType = if (xmlEnums.bitwidth == 64) CBasicType.uint64_t.cType else CBasicType.uint32_t.cType
                CType.Enum(xmlEnums.name, entryType)
            }
            else -> throw IllegalStateException("Unsupported enum type: ${xmlEnums.type}")
        }
        // TODO: add enum values
        return enumBase
    }

    private val xmlTagRegex = """<[^>]+>(.+)</[^>]+>""".toRegex()
    private val intBitRegex = """:(\d+)""".toRegex()

    private fun resolveGroupMembers(xmlGroupType: Registry.Types.Type): List<CType.Group.Member> {
        var lineComment: String? = null
        val members = mutableListOf<CType.Group.Member>()
        xmlGroupType.inner.forEach { xmlMember ->
            val xmlComment = xmlMember.tryParseXML<XMLComment>()
            if (xmlComment != null) {
                check(lineComment == null)
                lineComment = xmlComment.value
                return@forEach
            }
            val xmlMember = xmlMember.tryParseXML<XMLMember>()!!
            if (xmlMember.api != null && !xmlMember.api.split(",").contains("vulkan")) return@forEach
            var bits = -1
            var typeStr = xmlMember.inner.toXmlTagFreeString()
            intBitRegex.find(typeStr)?.let {
                bits = it.groupValues[1].toInt()
                typeStr = typeStr.replaceRange(it.range, "")
            }
            typeStr = CSyntax.typeRegex.matchEntire(typeStr)?.groupValues?.get(1)
                ?: throw IllegalStateException("Cannot resolve struct member type: $typeStr")
            val member = CType.Group.Member(
                xmlMember.name,
                resolveType(typeStr),
            )
            if (bits != -1) {
                member.tags[BitWidthTag] = BitWidthTag(bits)
            }
            (xmlMember.altlen ?: xmlMember.len)?.let {
                member.tags[LenTag] = LenTag(it)
            }
            xmlMember.comment?.let {
                member.tags[ElementCommentTag] = ElementCommentTag(it)
            }
            lineComment?.let {
                member.tags[LineCommentTag] = LineCommentTag(it)
            }
            lineComment = null
            members.add(member)
        }
        return members
    }

    private fun resolveStruct(xmlStructType: Registry.Types.Type): CType.Struct {
        val struct = CType.Struct(xmlStructType.name!!, resolveGroupMembers(xmlStructType))
        return struct
    }

    private fun resolveUnion(xmlUnionType: Registry.Types.Type): CType.Union {
        val union = CType.Union(xmlUnionType.name!!, resolveGroupMembers(xmlUnionType))
        return union
    }

    private fun resolveHandle(handle: Registry.Types.Type): CType.Handle {
        return object : CType.Handle(handle.name!!, CBasicType.size_t.cType) {
            context(ctx: KTFFICodegenContext) override fun memoryLayout(): CodeBlock {
                TODO("Not yet implemented")
            }
        }
    }

    private val vkVersionConstRegex = """VK_API_VERSION_(\d+)_(\d+)""".toRegex()
    private fun makeApiVersion(variant: UInt, major: UInt, minor: UInt, patch: UInt): UInt {
        return (variant shl 29) or (major shl 22) or (minor shl 12) or patch
    }

    override fun resolveElementImpl(cElementStr: String): CElement {
        registry.typeDefTypes[cElementStr]?.let {
            return resolveTypeDef(it)
        }

        registry.funcPointerTypes[cElementStr]?.let {
            return resolveFuncPointerType(it)
        }

        registry.enums[cElementStr]?.let {
            return resolveEnum(it)
        }

        registry.structTypes[cElementStr]?.let {
            return resolveStruct(it)
        }

        registry.unionTypes[cElementStr]?.let {
            return resolveUnion(it)
        }

        registry.handleTypes[cElementStr]?.let {
            return resolveHandle(it)
        }

        vkVersionConstRegex.matchEntire(cElementStr)?.let {
            val (major, minor) = it.destructured
            val apiVersionBits = makeApiVersion(0u, major.toUInt(), minor.toUInt(), 0u)
            val expression = CExpression.Const(CBasicType.uint32_t, apiVersionBits)
            return CTopLevelConst(it.value, expression)
        }

        throw kotlin.IllegalStateException("Cannot resolve type: $cElementStr")
    }
}
