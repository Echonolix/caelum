package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.CodeBlock
import net.echonolix.ktffi.*
import net.echonolix.vulkan.schema.*
import java.nio.file.Path

class VKFFICodeGenContext(basePkgName: String, outputDir: Path, val registry: FilteredRegistry) :
    KTFFICodegenContext(basePkgName, outputDir) {
    override fun resolvePackageName(element: CElement): String {
        return when (element) {
            is CType.FunctionPointer, is CType.Function -> VKFFI.functionPackageName
            is CType.Enum -> VKFFI.enumPackageName
            is CType.Bitmask -> VKFFI.flagPackageName
            is CType.Struct -> VKFFI.structPackageName
            is CType.Union -> VKFFI.unionPackageName
            is CType.Handle -> VKFFI.handlePackageName
            is CType.EnumBase.Entry -> throw IllegalStateException("Entry should not be resolved")
            is CType.TypeDef -> basePkgName
            is CTopLevelConst -> basePkgName
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
            }
            .map { it.groupValues }
            .map {
                CType.Function.Parameter(it[2], resolveType(it[1]))
            }.toList()
        val func = CType.Function("VkFuncPtr${xmlTypeDefType.name.removePrefix("PFN_vk")}", returnType, parameters)
        addToCache(func.name, func)
        val funcPointer = CType.FunctionPointer(func)
        addToCache(funcPointer.name, func)
        return CType.TypeDef(xmlTypeDefType.name, funcPointer)
    }

    private val bitSuffixRegex =
        """(${CSyntax.nameRegex.pattern})_BIT(|_${VKFFI.VENDOR_TAGS.joinToString("|_")})""".toRegex()
    private val flagNameRegex =
        """Vk(${CSyntax.nameRegex.pattern})Flags(\d*)(${CSyntax.nameRegex.pattern})?""".toRegex()
    private val enumTypeNameRegex =
        """(${CSyntax.nameRegex.pattern}?)(|${VKFFI.VENDOR_TAGS.joinToString("|")})""".toRegex()

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
                    valueNum = sign * ((extNumber - 1) * VKFFI.VK_EXT_ENUM_BLOCKSIZE + offset + VKFFI.VK_EXT_ENUM_BASE)
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
        val objectType = (resolveElement("VkObjectType") as CType.Enum)
        check(objectEnum.parent === objectType)
        check(objectEnum.name in objectType.entries)
        return when (xmlType) {
            "VK_DEFINE_HANDLE" -> VkDispatchableHandle(handle.name, parent, objectEnum)
            "VK_DEFINE_NON_DISPATCHABLE_HANDLE" -> VkHandle(handle.name, parent, objectEnum)
            else -> throw IllegalStateException("Unexpected handle type $xmlType for ${handle.name}")
        }
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
        val funcName = "VkCmd${cmdName.removePrefix("vk")}"
        val returnTypeStr = xmlCommand.proto.type
        val returnType = resolveType(returnTypeStr)
        val parameters = xmlCommand.params.asSequence()
            .filter { it.name != null }
            .filter { it.api == null || it.api == API.vulkan }
            .map {
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

    override fun resolveElementImpl(cElementStr: String): CElement {
        registry.registryTypes[cElementStr]?.alias?.let {
            return resolveType(it)
        }

        registry.typeDefTypes[cElementStr]?.let {
            return resolveTypeDef(it)
        }

        registry.funcPointerTypes[cElementStr]?.let {
            return resolveFuncPointerType(it)
        }

        registry.bitmaskTypes[cElementStr.replace("Bits", "s")]?.let { bitmaskType ->
            val bitEnumTypeName = bitmaskType.bitvalues ?: bitmaskType.requires ?: return CType.Bitmask(
                bitmaskType.name!!,
                CBasicType.int32_t.cType
            )
            val bitEnumXml = registry.enums[bitEnumTypeName]
                ?: throw IllegalStateException("Cannot find bit enum type: $bitEnumTypeName")
            return resolveEnum(bitEnumXml, bitmaskType.name!!)
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
                return resolveType(it)
            }
            return resolveCommand(commandType)
        }

        registry.extEnums[cElementStr]?.let { extEnum ->
            return resolveExtEnum(extEnum)
        }

        @OptIn(ExperimentalStdlibApi::class)
        VKFFI.vkVersionConstRegex.matchEntire(cElementStr)?.let {
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
}
