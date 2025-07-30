package net.echonolix.caelum.vulkan.ctx

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.WildcardTypeName
import net.echonolix.caelum.codegen.api.*
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.ElementResolver
import net.echonolix.caelum.codegen.api.ctx.resolveTypedElement
import net.echonolix.caelum.vulkan.*
import net.echonolix.caelum.vulkan.schema.*

class VulkanElementResolver(val registry: FilteredRegistry) : ElementResolver.Base() {
    private fun resolveTypeDef(xmlTypeDefType: Registry.Types.Type): CType.TypeDef {
        xmlTypeDefType.name!!
        val typeDefStr = xmlTypeDefType.inner.toXmlTagFreeString()
        val matchResult =
            CSyntax.typeDefRegex.matchEntire(typeDefStr)
                ?: throw IllegalStateException("Cannot resolve typedef: $typeDefStr")
        val (dstTypeStr, srcTypeStr) = matchResult.destructured
        assert(srcTypeStr == xmlTypeDefType.name)
        val dstType = resolveTypedElement<CType>(dstTypeStr)
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
        val returnType = resolveTypedElement<CType>(returnTypeStr)
        val parameters = funcStrLines.asSequence()
            .drop(1)
            .flatMap { it.split(CSyntax.funcPointerParamSplitRegex) }
            .filter { it.isNotBlank() }
            .map {
                CSyntax.funcPointerParameterRegex.matchEntire(it)
                    ?: throw IllegalStateException("Cannot resolve func pointer parameter for: ${xmlTypeDefType.name}")
            }
            .map { it.groupValues }
            .map {
                CType.Function.Parameter(it[2], resolveTypedElement<CType>(it[1]))
            }.toList()
        val func = CType.Function("VkFuncPtr${xmlTypeDefType.name.removePrefix("PFN_vk")}", returnType, parameters)
        func.tags.set(OriginalNameTag(xmlTypeDefType.name))
        addToCache(func.name, func)
        val funcPointer = CType.FunctionPointer(func)
        addToCache(funcPointer.name, func)
        return CType.TypeDef(xmlTypeDefType.name, funcPointer)
    }

    private val bitSuffixRegex =
        """(${CSyntax.nameRegex.pattern})_BIT(|_${VulkanCodegen.VENDOR_TAGS.joinToString("|_")})""".toRegex()
    private val flagNameRegex =
        """Vk(${CSyntax.nameRegex.pattern})Flags(\d*)(${CSyntax.nameRegex.pattern})?""".toRegex()
    private val enumTypeNameRegex =
        """(${CSyntax.nameRegex.pattern}?)(|${VulkanCodegen.VENDOR_TAGS.joinToString("|")})""".toRegex()

    private fun CType.EnumBase.fixEntryName(entryName: String): String {
        return when (this) {
            is CType.Bitmask -> {
                val typeNameMatchResult = flagNameRegex.matchEntire(this.name)
                    ?: throw IllegalStateException("Unexpected flag name: ${this.name}")
                val (typeName, num, _) = typeNameMatchResult.destructured
                val entryPrefix = buildString {
                    append("VK_")
                    append(typeName.pascalCaseToAllCaps())
                    if (num.isNotEmpty()) {
                        append('_')
                        append(num)
                    }
                    append('_')
                }
                entryName.removePrefix(entryPrefix).replace(bitSuffixRegex) {
                    "${it.groupValues[1]}${it.groupValues[2]}"
                }
            }
            is CType.Enum -> {
                val typeNameMatchResult = enumTypeNameRegex.matchEntire(this.name)
                    ?: throw IllegalStateException("Unexpected enum name: ${this.name}")
                val entryPrefix = "${typeNameMatchResult.groupValues[1].pascalCaseToAllCaps()}_"
                entryName.removePrefix(entryPrefix)
            }
            else -> throw IllegalStateException("Entry name fixing is not supported for ${this::class.simpleName}")
        }
    }

    private fun CType.EnumBase.addEntry(xmlEnum: Registry.Enums.Enum): CType.EnumBase.Entry {
        val type = entryType.baseType
        val entry = if (xmlEnum.alias != null) {
            val dstEntry = entries[xmlEnum.alias] ?: resolveElement(xmlEnum.alias) as CType.EnumBase.Entry
            val expression = CExpression.Reference(dstEntry)
            val entry = this.Entry(xmlEnum.name, expression)
            entry.tags.set<AliasedTag>(AliasedTag(dstEntry))
            entry
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
                    valueNum =
                        sign * ((extNumber - 1) * VulkanCodegen.VK_EXT_ENUM_BLOCKSIZE + offset + VulkanCodegen.VK_EXT_ENUM_BASE)
                    valueCode = CodeBlock.of(
                        "%L$literalSuffix",
                        valueNum
                    )
                }
            }
            val expression = CExpression.Const(type, valueCode)
            val entry = this.Entry(xmlEnum.name, expression)
            entry
        }

        entry.tags.set(EnumEntryFixedName(fixEntryName(xmlEnum.name)))
        addToCache(xmlEnum.name, entry)
        xmlEnum.comment?.let {
            entry.tags.set(ElementCommentTag(it))
        }
        entries[xmlEnum.name] = entry
        return entry
    }

    private fun resolveEnum(xmlEnums: Registry.Enums, enumName: String): CType.EnumBase {
        val enumBase = when (xmlEnums.type) {
            Registry.Enums.Type.enum -> CType.Enum(enumName, CBasicType.int32_t.cType)
            Registry.Enums.Type.bitmask -> {
                val entryType = if (xmlEnums.bitwidth == 64) CBasicType.int64_t.cType else CBasicType.int32_t.cType
                CType.Bitmask(enumName, entryType)
            }
            else -> throw IllegalStateException("Unsupported enum type: ${xmlEnums.type}")
        }
        xmlEnums.enums.asSequence()
            .filter { it.deprecated.isNullOrBlank() }
            .forEach { enumBase.addEntry(it) }
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
                resolveTypedElement<CType>(typeStr),
            )
            if (bits != -1) {
                member.tags.set(BitWidthTag(bits))
            }
            (xmlMember.len)?.let { len ->
                val altlen = xmlMember.altlen
                if (altlen == null && len != "null-terminated") {
                    val lenFirst = len.substringBefore(",")
                    val countMember =
                        members.find { it.name == lenFirst } ?: error("Cannot find count member $lenFirst")
                    check(countMember.name == lenFirst)
                    val countTag = CountTag((countMember.tags.getOrNull<CountTag>()?.v ?: emptyList()) + member)
                    countMember.tags.set(countTag)
                    check(member.type is CType.Pointer || member.type is CType.Array)
                    check(countMember.name.endsWith("Count") || countTag.v.size == 1)
                    member.tags.set(CountedTag(lenFirst))
                }
            }
            xmlMember.comment?.let {
                member.tags.set(ElementCommentTag(it))
            }
            lineComment?.let {
                member.tags.set(LineCommentTag(it))
            }
            if (xmlMember.optional == "true" || xmlMember.optional?.split(",")?.first() == "true") {
                member.tags.set(OptionalTag)
            }
            if (xmlMember.name == "sType" && xmlMember.values != null) {
                val structType = resolveElement(xmlMember.values) as CType.EnumBase.Entry
                member.tags.set(StructTypeTag(structType))
            }
            lineComment = null
            members.add(member)
        }
        return members
    }

    private fun resolveStruct(xmlStructType: Registry.Types.Type): CType.Struct {
        val members = resolveGroupMembers(xmlStructType)
        val struct = if (xmlStructType.name == "VkBaseOutStructure") {
            object : CType.Struct(xmlStructType.name, members) {
                context(ctx: CodegenContext)
                override fun ktApiType(): TypeName {
                    return WildcardTypeName.producerOf(VulkanCodegen.vkStructCName.parameterizedBy(STAR))
                }
            }
        } else {
            CType.Struct(xmlStructType.name!!, members)
        }
        return struct
    }

    private fun resolveUnion(xmlUnionType: Registry.Types.Type): CType.Union {
        val union = CType.Union(xmlUnionType.name!!, resolveGroupMembers(xmlUnionType))
        return union
    }

    private fun resolveHandle(handle: Registry.Types.Type): CType.Handle {
        handle.name ?: error("Handle name is null")
        val xmlType = handle.inner[0].contentString.toXMLTagFreeString()
        val parent = handle.parent?.let {
            resolveElement(it) as? CType.Handle ?: error("Parent $it is not a handle")
        }
        val objectEnum = resolveElement(handle.objtypeenum!!) as? CType.EnumBase.Entry
            ?: error("Cannot find object type enum for handle ${handle.name}")
        val objectType = (resolveElement("VkObjectType") as CType.Enum)
        check(objectEnum.parent === objectType)
        check(objectEnum.name in objectType.entries)
        val dispatchable = when (xmlType) {
            "VK_DEFINE_HANDLE" -> true
            "VK_DEFINE_NON_DISPATCHABLE_HANDLE" -> false
            else -> error("Unexpected handle type $xmlType for ${handle.name}")
        }

        val handleType = CType.Handle(handle.name)
        handleType.tags.set(VkHandleTag(parent, objectEnum, dispatchable))
        handleType.tags.set(TypeNameRename(handle.name + "Handle"))
        return handleType
    }

    private fun resolveConst(constType: Registry.Enums.Enum): CTopLevelConst {
        val valueStr = constType.value!!
        val expression = constType.type?.let { CExpression.Const(it, it.codeBlock(valueStr)) }
            ?: resolveExpression(valueStr)
        val const = CTopLevelConst(constType.name, expression)
        addToCache(constType.name, const)
        return const
    }

    private fun makeApiVersion(variant: UInt, major: UInt, minor: UInt, patch: UInt): UInt {
        return (variant shl 29) or (major shl 22) or (minor shl 12) or patch
    }

    private fun resolveCommand(xmlCommand: Registry.Commands.Command): CType.Function {
        xmlCommand.proto!!
        val cmdName = xmlCommand.proto.name
        val funcName = "VkFunc${cmdName.removePrefix("vk")}"
        val returnTypeStr = xmlCommand.proto.type
        val returnType = resolveTypedElement<CType>(returnTypeStr)
        val parameters = xmlCommand.params.asSequence()
            .filter { it.name != null }
            .filter { it.api == null || it.api == API.vulkan }
            .map {
                it.name!!
                val innerStr = it.inner.toXmlTagFreeString()
                val matchEntire = CSyntax.typeRegex.matchEntire(innerStr)
                    ?: throw IllegalStateException("Cannot resolve function parameter for: $cmdName")
                val (typeStr) = matchEntire.destructured
                CType.Function.Parameter(it.name, resolveTypedElement<CType>(typeStr)).apply {
                    if (it.optional == "true" || it.optional?.split(",")?.first() == "true") {
                        tags.set(OptionalTag)
                    }
                }
            }
            .toList()
        val function = CType.Function(funcName, returnType, parameters)
        function.tags.set(OriginalNameTag(cmdName))
        xmlCommand.comment?.let {
            function.tags.set(ElementCommentTag(it))
        }
        fun parseResultCodes(str: String?): List<CType.EnumBase.Entry> {
            if (str == null) return emptyList()
            return str.split(",")
                .mapNotNull { runCatching { resolveElement(it) as CType.EnumBase.Entry }.getOrNull() }
        }

        val successCodes = parseResultCodes(xmlCommand.successcodes)
        val errorCodes = parseResultCodes(xmlCommand.errorcodes)
        function.tags.set(ResultCodeTag(successCodes, errorCodes))
        return function
    }

    private fun resolveExtEnum(xmlEnum: Registry.Enums.Enum): CElement {
        return when {
            xmlEnum.extends != null -> {
                val enumTypeStr = xmlEnum.extends
                val enumType = resolveTypedElement<CType>(enumTypeStr) as CType.EnumBase
                enumType.addEntry(xmlEnum)
            }
            xmlEnum.alias != null -> {
                CTopLevelConst(xmlEnum.name, resolveExpression(xmlEnum.alias))
            }
            xmlEnum.value != null -> {
                resolveConst(xmlEnum)
            }
            else -> {
                throw IllegalStateException("Cannot resolve ext enum: ${xmlEnum.name}")
            }
        }
    }

    private fun resolveElementImpl0(cElementStr: String): CElement {
        registry.registryTypes[cElementStr]?.alias?.let {
            return resolveTypedElement<CType>(it)
        }

        registry.typeDefTypes[cElementStr]?.let {
            return resolveTypeDef(it)
        }

        registry.funcPointerTypes[cElementStr]?.let {
            return resolveFuncPointerType(it)
        }

        val bitsRemoved = cElementStr.replace("Bits", "s")
        registry.bitmaskTypes[cElementStr.replace("Bits", "s")]?.let { bitmaskType ->
            if (bitsRemoved.length < cElementStr.length) {
                return resolveTypedElement<CType>(bitsRemoved)
            } else {
                val bitEnumTypeName = bitmaskType.bitvalues ?: bitmaskType.requires ?: return CType.Bitmask(
                    bitmaskType.name!!,
                    CBasicType.int32_t.cType
                )
                val bitEnumXml = registry.enums[bitEnumTypeName]
                    ?: throw IllegalStateException("Cannot find bit enum type: $bitEnumTypeName")
                return resolveEnum(bitEnumXml, bitmaskType.name!!)
            }
        }

        registry.enumTypes[cElementStr]?.let {
            val xmlEnumType =
                registry.enums[it.name] ?: throw IllegalStateException("Cannot find enum type: ${it.name}")
            return resolveEnum(xmlEnumType, it.name!!)
        }

        registry.enumsValueTypeName[cElementStr]?.let { xmlEnumType ->
            return (resolveElement(xmlEnumType.name) as CType.EnumBase).entries[cElementStr]!!
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
                return resolveTypedElement<CType>(it)
            }
            return resolveCommand(commandType)
        }

        registry.extEnums[cElementStr]?.let { extEnum ->
            return resolveExtEnum(extEnum)
        }

        @OptIn(ExperimentalStdlibApi::class)
        VulkanCodegen.vkVersionConstRegex.matchEntire(cElementStr)?.let {
            val (major, minor) = it.destructured
            val apiVersionBits = makeApiVersion(0u, major.toUInt(), minor.toUInt(), 0u)
            val expression =
                CExpression.Const(
                    CBasicType.uint32_t,
                    CodeBlock.of("${apiVersionBits.toLiteralHexString()}U")
                )
            return CTopLevelConst(it.value, expression)
        }

        if (CSyntax.intLiteralRegex.matches(cElementStr)) {
            return CExpression.Const(
                CBasicType.int32_t,
                CodeBlock.of(cElementStr)
            )
        }

        throw IllegalStateException("Cannot resolve type: $cElementStr")
    }

    override fun resolveElementImpl(input: String): CElement {
        return resolveElementImpl0(input).also {
            registry.stuffRequiredBy[input]?.let { requiredBy ->
                it.tags.set(RequiredByTag(requiredBy))
            }
        }
    }
}
