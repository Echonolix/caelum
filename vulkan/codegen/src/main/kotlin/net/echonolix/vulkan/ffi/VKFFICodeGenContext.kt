package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.CodeBlock
import net.echonolix.ktffi.*
import net.echonolix.vulkan.schema.FilteredRegistry
import net.echonolix.vulkan.schema.Registry
import net.echonolix.vulkan.schema.XMLComment
import net.echonolix.vulkan.schema.XMLMember
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
        val func = CType.Function("VkFuncPtr${xmlTypeDefType.name.removePrefix("PFN_vk")}", returnType, parameters)
        addToCache(func)
        val funcPointer = CType.FunctionPointer(func)
        addToCache(func)
        return CType.TypeDef(xmlTypeDefType.name, funcPointer)
    }

    private fun CType.EnumBase.addEntry(xmlEnum: Registry.Enums.Enum): CType.EnumBase.Entry {
        val type = entryType.baseType
        val expression = if (xmlEnum.alias != null) {
            val dstEntry = entries[xmlEnum.alias] ?: resolveElement(xmlEnum.alias) as CType.EnumBase.Entry
            CExpression.Reference(dstEntry)
        } else {
            val literalSuffix = type.literalSuffix
            val valueCode: CodeBlock
            val valueNum: Number
            when {
                xmlEnum.bitpos != null -> {
                    valueCode = CodeBlock.of("1$literalSuffix shl %L", xmlEnum.bitpos)
                    valueNum = if (type == CBasicType.uint32_t) 1 shl xmlEnum.bitpos else 1L shl xmlEnum.bitpos
                }
                xmlEnum.value != null -> {
                    valueCode = CodeBlock.of("%L$literalSuffix", xmlEnum.value)
                    valueNum = xmlEnum.value.decOrHexToInt()
                }
                else -> {
                    val extNumber = xmlEnum.extnumber!!.decOrHexToInt()
                    val sign = if (xmlEnum.dir == "-") -1 else 1
                    val offset = xmlEnum.offset!!.decOrHexToInt()
                    valueNum = sign * ((extNumber - 1) * VKFFI.VK_EXT_ENUM_BLOCKSIZE + offset + VKFFI.VK_EXT_ENUM_BASE)
                    valueCode = CodeBlock.of(
                        "%L$literalSuffix",
                        valueNum
                    )
                }
            }
            CExpression.Const(type, valueCode)
        }
//        fun String.removeVendorTag(): String {
//            VKFFI.VENDOR_TAGS.forEach {
//                val suffix = "_$it"
//                if (endsWith(suffix)) {
//                    return removeSuffix(suffix)
//                }
//            }
//            return this
//        }

//        val allCaps = name.pascalCaseToAllCaps()
//        val split = allCaps.split("_FLAG_BITS".toRegex())
//        val suffix = if (split.size == 2) "_BIT" else ""

//        val prefix = if (split.size == 2) {
//            var temp = split[0] + "_"
//            if (name.contains("FlagBits2")) {
//                temp += "2_"
//            }
//            temp
//        } else {
////            allCaps.removeVendorTag() + "_"
//            ""
//        }

        var fixedEnumName = xmlEnum.name
//        if (this.name != "VkVendorId") {
//            fixedEnumName = fixedEnumName.removeVendorTag()
//        }
//        if (this.name !in enumTypeWhitelist && !fixedEnumName.contains("RESERVED")) {
//            check(fixedEnumName.startsWith(prefix)) {
//                "Expected $fixedEnumName to start with $prefix"
//            }
//        }
//        if (xmlEnum.name !in enumWhitelist) {
//            check(fixedEnumName.endsWith(suffix)) {
//                "Expected $fixedEnumName to end with $suffix"
//            }
//        }
//        fixedEnumName = fixedEnumName.removePrefix(prefix)/*.removeSuffix(suffix)*/
        val entry = this.Entry(fixedEnumName, expression)
        addToCache(entry)
        xmlEnum.comment?.let {
            entry.tags.set(ElementCommentTag(it))
        }
        entries[fixedEnumName] = entry
        return entry
    }

    private fun resolveEnum(xmlEnums: Registry.Enums, enumName: String): CType.EnumBase {
        val enumBase = when (xmlEnums.type) {
            Registry.Enums.Type.enum -> CType.Enum(enumName, CBasicType.uint32_t.cType)
            Registry.Enums.Type.bitmask -> {
                val entryType = if (xmlEnums.bitwidth == 64) CBasicType.int64_t.cType else CBasicType.int32_t.cType
                CType.Enum(enumName, entryType)
            }
            else -> throw IllegalStateException("Unsupported enum type: ${xmlEnums.type}")
        }
        xmlEnums.enums.forEach {
            enumBase.addEntry(it)
        }
        return enumBase
    }

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
            typeStr = CSyntax.typeRegex.matchEntire(typeStr.trim())?.groupValues?.get(1)
                ?: throw IllegalStateException("Cannot resolve struct member type: $typeStr")
            val member = CType.Group.Member(
                xmlMember.name,
                resolveType(typeStr),
            )
            if (bits != -1) {
                member.tags.set(BitWidthTag(bits))
            }
            (xmlMember.altlen ?: xmlMember.len)?.let {
                member.tags.set(LenTag(it))
            }
            xmlMember.comment?.let {
                member.tags.set(ElementCommentTag(it))
            }
            lineComment?.let {
                member.tags.set(LineCommentTag(it))
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

    private fun resolveHandle(handle: Registry.Types.Type): VkHandle {
        handle.name ?: throw IllegalStateException("Handle name is null")
        val xmlType = handle.inner[0].contentString.toXMLTagFreeString()
        val parent = handle.parent?.let {
            resolveElement(it) as? VkHandle ?: throw IllegalStateException("Parent $it is not a handle")
        }
        val objectEnum = resolveElement(handle.objtypeenum!!) as? CType.EnumBase.Entry
            ?: throw IllegalStateException("Cannot find object type enum for handle ${handle.name}")
        return when (xmlType) {
            "VK_DEFINE_HANDLE" -> VkDispatchableHandle(handle.name, parent, objectEnum)
            "VK_DEFINE_NON_DISPATCHABLE_HANDLE" -> VkHandle(handle.name, parent, objectEnum)
            else -> throw IllegalStateException("Unexpected handle type $xmlType for ${handle.name}")
        }
    }

    private val invIntLiteralRegex = """~(${CSyntax.intLiteralRegex})""".toRegex()

    private fun resolveConst(constType: Registry.Enums.Enum): CTopLevelConst {
        var valueStr = constType.value!!
        invIntLiteralRegex.find(valueStr)?.let {
            val (num) = it.destructured
            valueStr = valueStr.replaceRange(it.range, "($num).inv()")
        }
        val type = constType.type ?: CBasicType.int32_t
        val const = CTopLevelConst(constType.name, CExpression.Const(type, CodeBlock.of(valueStr)))
        addToCache(const)
        return const
    }

    private val vkVersionConstRegex = """VK_API_VERSION_(\d+)_(\d+)""".toRegex()
    private fun makeApiVersion(variant: UInt, major: UInt, minor: UInt, patch: UInt): UInt {
        return (variant shl 29) or (major shl 22) or (minor shl 12) or patch
    }

    private fun resolveCommand(xmlCommand: Registry.Commands.Command): CType.Function {
        xmlCommand.proto!!
        val cmdName = xmlCommand.proto.name
        val funcName = "VkCmd${cmdName.removePrefix("vk")}"
        val returnTypeStr = xmlCommand.proto.type
        val returnType = resolveType(returnTypeStr)
        val parameters = xmlCommand.params.asSequence()
            .filter {
                it.name != null
            }.map {
                it.name!!
                val innerStr = it.inner.toXmlTagFreeString()
                val matchEntire = CSyntax.typeRegex.matchEntire(innerStr)
                    ?: throw IllegalStateException("Cannot resolve function parameter for: $cmdName")
                val (typeStr) = matchEntire.destructured
                CType.Function.Parameter(it.name, resolveType(typeStr))
            }
            .toList()
        val function = CType.Function(funcName, returnType, parameters)
        xmlCommand.comment?.let {
            function.tags.set(ElementCommentTag(it))
        }
        return function
    }

    private fun resolveExtEnum(xmlEnum: Registry.Enums.Enum): CElement {
        return when {
            xmlEnum.extends != null -> {
                val enumTypeStr = xmlEnum.extends
                val enumType = resolveType(enumTypeStr) as CType.EnumBase
                enumType.addEntry(xmlEnum)
            }
            xmlEnum.alias != null -> {
                CTopLevelConst(xmlEnum.name, CExpression.Reference(resolveElement(xmlEnum.alias) as CConst))
            }
            xmlEnum.value != null -> {
                resolveConst(xmlEnum)
            }
            else -> {
                throw IllegalStateException("Cannot resolve ext enum: ${xmlEnum.name}")
            }
        }
    }

    override fun resolveElementImpl(cElementStr: String): CElement {
        registry.registryTypes[cElementStr]?.alias?.let {
            return CType.TypeDef(cElementStr, resolveType(it))
        }

        registry.typeDefTypes[cElementStr]?.let {
            return resolveTypeDef(it)
        }

        registry.funcPointerTypes[cElementStr]?.let {
            return resolveFuncPointerType(it)
        }

        registry.bitmaskTypes[cElementStr]?.let { bitmaskType ->
            val bitEnumTypeName = bitmaskType.bitvalues ?: bitmaskType.requires ?: return CType.Enum(
                bitmaskType.name!!,
                CBasicType.uint32_t.cType
            )
            val bitEnumXml = registry.enums[bitEnumTypeName]
                ?: throw IllegalStateException("Cannot find bit enum type: $bitEnumTypeName")
            return resolveEnum(bitEnumXml, bitmaskType.name!!)
        }

        registry.enumTypes[cElementStr]?.let {
            val xmlEnumType = registry.enums[it.name] ?: throw IllegalStateException("Cannot find enum type: ${it.name}")
            return resolveEnum(xmlEnumType, it.name!!)
        }

        registry.enumsValueTypeName[cElementStr]?.let { xmlEnumType ->
            return resolveEnum(xmlEnumType, xmlEnumType.name).entries[cElementStr]!!
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

        registry.constants[cElementStr]?.let {
            return resolveConst(it)
        }

        registry.commands[cElementStr]?.let { commandType ->
            commandType.alias?.let {
                return CType.TypeDef(cElementStr, resolveType(it))
            }
            return resolveCommand(commandType)
        }

        registry.extEnums[cElementStr]?.let { extEnum ->
            return resolveExtEnum(extEnum)
        }

        @OptIn(ExperimentalStdlibApi::class)
        vkVersionConstRegex.matchEntire(cElementStr)?.let {
            val (major, minor) = it.destructured
            val apiVersionBits = makeApiVersion(0u, major.toUInt(), minor.toUInt(), 0u)
            val expression =
                CExpression.Const(CBasicType.uint32_t, CodeBlock.of(apiVersionBits.toHexString(HexFormat.UpperCase)))
            return CTopLevelConst(it.value, expression)
        }

        if (CSyntax.intLiteralRegex.matches(cElementStr)) {
            return CExpression.Const(
                CBasicType.uint32_t,
                CodeBlock.of(cElementStr)
            )
        }

        throw IllegalStateException("Cannot resolve type: $cElementStr")
    }
}
