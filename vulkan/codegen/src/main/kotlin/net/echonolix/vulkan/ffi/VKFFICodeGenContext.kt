package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.CodeBlock
import net.echonolix.ktffi.*
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

    private val cTypeNameRegex = """[a-zA-Z_][a-zA-Z0-9_]*""".toRegex()
    private val cTypeRegex = """(?:(?:const|struct|union)\s+)?(${cTypeNameRegex.pattern}(?:\s*(?:\*|\[.*?])\s*)*)""".toRegex()
    private val typeDefRegex = """\s*typedef\s+${cTypeRegex.pattern}\s+(${cTypeNameRegex.pattern})\s*;""".toRegex()

    private fun resolveTypeDef(xmlTypeDefType: Registry.Types.Type): CType.TypeDef {
        xmlTypeDefType.name!!
        val typeDefStr = xmlTypeDefType.inner.toXmlTagFreeString()
        val matchResult =
            typeDefRegex.matchEntire(typeDefStr) ?: throw IllegalStateException("Cannot resolve typedef: $typeDefStr")
        val (dstTypeStr, srcTypeStr) = matchResult.destructured
        assert(srcTypeStr == xmlTypeDefType.name)
        val dstType = resolveType(dstTypeStr)
        return CType.TypeDef(xmlTypeDefType.name, dstType)
    }

    private val funcPointerHeaderRegex =
        """\s*typedef\s+${cTypeRegex.pattern}\s+\(VKAPI_PTR\s*\*\s+(${cTypeNameRegex.pattern})\s*\)\((?:void\);)?""".toRegex()
    private val funcPointerParamSplitRegex = """\s*(?:,|\);)\s*""".toRegex()
    private val funcPointerParameterRegex =
        """\s*${cTypeRegex.pattern}\s+(${cTypeNameRegex.pattern})\s*""".toRegex()

    private fun resolveFuncPointerType(xmlTypeDefType: Registry.Types.Type): CType.TypeDef {
        assert(xmlTypeDefType.category == Registry.Types.Type.Category.funcpointer)
        xmlTypeDefType.name!!
        val funcStrLines = xmlTypeDefType.inner.toXmlTagFreeString().lines()
        val headerMatchResult = funcPointerHeaderRegex.matchEntire(funcStrLines.first()) ?: throw IllegalStateException(
            "Cannot resolve func pointer header for: ${xmlTypeDefType.name}"
        )
        assert(xmlTypeDefType.name == headerMatchResult.groupValues[2])
        val returnTypeStr = headerMatchResult.groupValues[1]
        val returnType = resolveType(returnTypeStr)
        val parameters = funcStrLines.asSequence()
            .drop(1)
            .flatMap { it.split(funcPointerParamSplitRegex) }
            .filter { it.isNotBlank() }
            .map {
                funcPointerParameterRegex.matchEntire(it)
                    ?: throw IllegalStateException("Cannot resolve func pointer parameter for: ${xmlTypeDefType.name}")
            }.map { it.groupValues }.map {
                CType.Function.Parameter(it[2], resolveType(it[1]))
            }.toList()
        val func = CType.Function("VkFunc${xmlTypeDefType.name.removePrefix("PFN_vk")}", returnType, parameters)
        addToCache(func)
        val funcPointer = CType.Pointer(func)
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

    private fun resolveStruct(xmlStruct: Registry.Types.Type): CType.Struct {
        var comment: String? = null
        val members = mutableListOf<CDeclaration>()
        xmlStruct.inner.forEach { xmlMember ->
            val xmlComment = xmlMember.tryParseXML<XMLComment>()
            if (xmlComment != null) {
                check(comment == null)
                comment = xmlComment.value
                return@forEach
            }
            val xmlMember = xmlMember.tryParseXML<XMLMember>()!!
            if (xmlMember.api != null && !xmlMember.api.split(",").contains("vulkan")) return@forEach
            val innerText = xmlMember.inner.map { it.contentString }
            var arrayLen: String? = null
            var bits = -1
            val typeStr = xmlMember.inner.toXmlTagFreeString()
            println(typeStr)
            val member = CType.Function.Parameter(
                xmlMember.name,
                resolveType("void"),
            )
//            member.docs = comment
            comment = null
            members.add(member)
        }
        val struct = CType.Struct(xmlStruct.name!!, members)
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
