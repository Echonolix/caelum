package org.echonolix.vulkan.schema

import com.squareup.kotlinpoet.CodeBlock
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import nl.adaptivity.xmlutil.util.CompactFragment
import org.echonolix.ktffi.CBasicType
import org.echonolix.vulkan.decOrHexToInt
import org.echonolix.vulkan.VKFFI
import org.echonolix.vulkan.pascalCaseToAllCaps
import org.echonolix.vulkan.tryParseXML
import java.lang.foreign.MemoryLayout

private val enumTypeWhitelist = setOf(
    "VkResult",
    "VkVendorId"
)
private val enumWhitelist = setOf(
    "VK_ACCESS_NONE",
    "VK_SHADER_STAGE_ALL_GRAPHICS",
    "VK_SHADER_STAGE_ALL",
    "VK_IMAGE_ASPECT_NONE",
    "VK_PIPELINE_STAGE_NONE",
    "VK_STENCIL_FACE_FRONT_AND_BACK",
    "VK_CULL_MODE_NONE",
    "VK_CULL_MODE_FRONT_AND_BACK",
    "VK_GEOMETRY_INSTANCE_FORCE_OPACITY_MICROMAP_2_STATE_EXT",
    "VK_GEOMETRY_INSTANCE_DISABLE_OPACITY_MICROMAPS_EXT",
    "VK_CLUSTER_ACCELERATION_STRUCTURE_CLUSTER_ALLOW_DISABLE_OPACITY_MICROMAPS_NV",
    "VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_OPACITY_MICROMAP_UPDATE_EXT",
    "VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_DISABLE_OPACITY_MICROMAPS_EXT",
    "VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_OPACITY_MICROMAP_DATA_UPDATE_EXT",
    "VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_DISPLACEMENT_MICROMAP_UPDATE_NV",
    "VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_DATA_ACCESS_KHR",
    "VK_ACCESS_2_NONE",
    "VK_PIPELINE_STAGE_2_NONE",
    "VK_INDIRECT_COMMANDS_INPUT_MODE_VULKAN_INDEX_BUFFER_EXT",
    "VK_INDIRECT_COMMANDS_INPUT_MODE_DXGI_INDEX_BUFFER_EXT",
    "VK_CLUSTER_ACCELERATION_STRUCTURE_INDEX_FORMAT_8BIT_NV",
    "VK_CLUSTER_ACCELERATION_STRUCTURE_INDEX_FORMAT_16BIT_NV",
    "VK_CLUSTER_ACCELERATION_STRUCTURE_INDEX_FORMAT_32BIT_NV",
    "VK_RESOLVE_MODE_NONE",
    "VK_HOST_IMAGE_COPY_MEMCPY",
    "VK_PARTITIONED_ACCELERATION_STRUCTURE_INSTANCE_FLAG_ENABLE_EXPLICIT_BOUNDING_BOX_NV",
    "VK_IMAGE_CONSTRAINTS_INFO_CPU_READ_RARELY_FUCHSIA",
    "VK_IMAGE_CONSTRAINTS_INFO_CPU_READ_OFTEN_FUCHSIA",
    "VK_IMAGE_CONSTRAINTS_INFO_CPU_WRITE_RARELY_FUCHSIA",
    "VK_IMAGE_CONSTRAINTS_INFO_CPU_WRITE_OFTEN_FUCHSIA",
    "VK_IMAGE_CONSTRAINTS_INFO_PROTECTED_OPTIONAL_FUCHSIA",
    "VK_IMAGE_COMPRESSION_DEFAULT_EXT",
    "VK_IMAGE_COMPRESSION_FIXED_RATE_DEFAULT_EXT",
    "VK_IMAGE_COMPRESSION_FIXED_RATE_EXPLICIT_EXT",
    "VK_IMAGE_COMPRESSION_DISABLED_EXT",
    "VK_IMAGE_COMPRESSION_FIXED_RATE_NONE_EXT",
    "VK_OPTICAL_FLOW_GRID_SIZE_UNKNOWN_NV",
    "VK_OPTICAL_FLOW_USAGE_UNKNOWN_NV",
    "VK_PHYSICAL_DEVICE_SCHEDULING_CONTROLS_SHADER_CORE_COUNT_ARM",
    "VK_VIDEO_CODEC_OPERATION_NONE_KHR",
    "VK_VIDEO_DECODE_USAGE_DEFAULT_KHR",
    "VK_VIDEO_DECODE_H264_PICTURE_LAYOUT_PROGRESSIVE_KHR",
    "VK_VIDEO_ENCODE_USAGE_DEFAULT_KHR",
    "VK_VIDEO_ENCODE_CONTENT_DEFAULT_KHR",
    "VK_VIDEO_ENCODE_RATE_CONTROL_MODE_DEFAULT_KHR",
    "VK_VIDEO_CHROMA_SUBSAMPLING_INVALID_KHR",
    "VK_VIDEO_COMPONENT_BIT_DEPTH_INVALID_KHR",
    "VK_ACCESS_3_NONE_KHR",
    "VK_RESOLVE_MODE_NONE",
    "VK_GEOMETRY_INSTANCE_FORCE_OPACITY_MICROMAP_2_STATE_EXT",
    "VK_GEOMETRY_INSTANCE_DISABLE_OPACITY_MICROMAPS_EXT",
    "VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_OPACITY_MICROMAP_UPDATE_EXT",
    "VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_DISABLE_OPACITY_MICROMAPS_EXT",
    "VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_OPACITY_MICROMAP_DATA_UPDATE_EXT",
    "VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_DISPLACEMENT_MICROMAP_UPDATE_NV",
    "VK_BUILD_ACCELERATION_STRUCTURE_ALLOW_DATA_ACCESS_KHR",
    "VK_ACCESS_2_NONE",
    "VK_PIPELINE_STAGE_2_NONE",
    "VK_HOST_IMAGE_COPY_MEMCPY",
)
private val xmlTagRegex = """<[^>]+>(.+)</[^>]+>""".toRegex()

class PatchedRegistry(registry: Registry) {
    val raw = registry
    val registryFeatures = registry.features.asSequence()
        .filter { it.api.contains("vulkan") }
        .toList()
    val registryExtensions = registry.extensions.extensions.asSequence()
        .filter { it.author !in VKFFI.ignoredVendor }
        .filter { it.obsoletedby == null }
        .filter { it.deprecatedby == null }
        .filter { it.supported.contains("vulkan") }
        .toList()
    val enums = registry.enums
    val registryTypes = registry.types.types
        .associate { type ->
            val name = type.name ?: type.inner.firstNotNullOf {
                it.tryParseXML<XMLName>()?.value
            }
            name to type.copy(name = name)
        }
    private val typesWithTypeDef =
        registryTypes.values.filter { it.inner.getOrNull(0)?.contentString?.startsWith("typedef") == true }

    val basicTypes = CBasicType.entries.associateWith { Element.BasicType(it.name, it) }
    val opaqueTypes = mutableMapOf<String, Element.OpaqueType>()
    val externalTypes = registryTypes.values.asSequence()
        .filter { it.requires != null }
        .map { it.name!! }
        .toSet()
    val unusedTypes = mutableSetOf<String>()
    val aliasTypes = mutableMapOf<String, Element.Type>()
    val allTypes = mutableMapOf<String, Element.Type>()
    val allElements = mutableMapOf<String, Element>()

    val typedefs = mutableMapOf<String, Element.TypeDef>()
    val basetypes = mutableMapOf<String, Element.BaseType>()

    init {
        basicTypes.values.associateByTo(allTypes) { it.name }
        allTypes.toMap(allElements)

        typesWithTypeDef.asSequence()
            .filter { it.category == Registry.Types.Type.Category.basetype }
            .forEach { typeDef ->
                typeDef.name!!
                val type = CBasicType.Companion.fromStringOrNull(typeDef.inner.firstNotNullOfOrNull {
                    it.tryParseXML<XMLType>()?.value
                } ?: "") ?: return@forEach
                val basicType = basicTypes[type]!!
                val typeDefType = Element.TypeDef(typeDef.name, basicType)
                val baseType = Element.BaseType(typeDef.name, typeDefType)
                basicType.docs = typeDef.comment
                typeDefType.docs = typeDef.comment
                baseType.docs = typeDef.comment
                basetypes[typeDef.name] = baseType
                typedefs[typeDef.name] = typeDefType
            }

        typedefs.toMap(allTypes)
        allTypes.toMap(allElements)

        registryTypes.values.asSequence()
            .filter { it.category == Registry.Types.Type.Category.basetype }
            .filterNot { it.name in basetypes }
            .forEach {
                it.name!!
                val opaqueType = Element.OpaqueType(it.name)
                val typedefType = Element.TypeDef(it.name, opaqueType)
                val baseType = Element.BaseType(it.name, typedefType)
                opaqueType.docs = it.comment
                baseType.docs = it.comment
                opaqueTypes[it.name] = opaqueType
                basetypes[it.name] = baseType
            }

        opaqueTypes.toMap(allTypes)
        allTypes.toMap(allElements)
        check(
            basetypes.keys.containsAll(
                registryTypes.values.asSequence()
                    .filter { it.category == Registry.Types.Type.Category.basetype }
                    .mapNotNull { it.name }
                    .toList()
            )
        )
    }

    val flagBitTypes = mutableMapOf<String, Element.FlagBitType>()
    val flagTypes = mutableMapOf<String, Element.FlagType>()

    init {
        typesWithTypeDef.asSequence()
            .filter { it.category == Registry.Types.Type.Category.bitmask }
            .forEach { typeDef ->
                typeDef.name!!
                val basetypeName = typeDef.inner.firstNotNullOf {
                    it.tryParseXML<XMLType>()?.value
                }
                val baseType = basetypes[basetypeName]!!
                typedefs[typeDef.name] = Element.TypeDef(typeDef.name, baseType)
            }

        val bitmaskBitsEnum = enums.asSequence()
            .filter { it.type == Registry.Enums.Type.bitmask }
            .associateBy { it.name }

        registryTypes.values.asSequence()
            .filter { it.category == Registry.Types.Type.Category.bitmask }
            .forEach { xml ->
                xml.name!!
                if (xml.alias != null) {
                    val aliasType = flagTypes[xml.alias]!!
                    aliasTypes[xml.name] = aliasType
                    flagTypes[xml.name] = aliasType
                } else {
                    val bitTypeName = xml.bitvalues ?: xml.requires
                    val bitmask = Element.FlagType(xml.name, bitTypeName)
                    bitmask.docs = xml.comment
                    flagTypes[xml.name] = bitmask

                    if (bitTypeName == null) {
                        unusedTypes.add(xml.name)
                    } else {
                        val bitmaskBitsEnum = bitmaskBitsEnum[bitTypeName]!!
                        val bitDataType = if (bitmaskBitsEnum.bitwidth == 64) CBasicType.int64_t else CBasicType.int32_t
                        val bitmaskBits = Element.FlagBitType(bitTypeName, bitDataType, bitmask)
                        flagBitTypes[bitmaskBits.name] = bitmaskBits
                        bitmaskBitsEnum.enums.asSequence()
                            .filter { it.deprecated == null }
                            .forEach { bitEnumEntry ->
                                bitmaskBits.addEntry(bitEnumEntry)
                            }
                    }
                }
            }

        bitmaskBitsEnum.values.asSequence()
            .filterNot { flagBitTypes.containsKey(it.name) }
            .forEach {
                check(it.enums.isEmpty())
                val bitDataType = if (it.bitwidth == 64) CBasicType.int64_t else CBasicType.int32_t
                val bitmaskBits = Element.FlagBitType(it.name, bitDataType, null)
                bitmaskBits.docs = it.comment
                flagBitTypes[bitmaskBits.name] = bitmaskBits
            }

        flagTypes.toMap(allTypes)
        flagBitTypes.toMap(allTypes)
        allTypes.toMap(allElements)

        check(
            flagTypes.keys.containsAll(
                registryTypes.values.asSequence()
                    .filter { it.category == Registry.Types.Type.Category.bitmask }
                    .mapNotNull { it.name }
                    .toList()
            )
        )
        check(flagBitTypes.keys.containsAll(bitmaskBitsEnum.keys))
    }

    val enumTypes = mutableMapOf<String, Element.EnumType>()

    init {
        val enumEnums = enums.asSequence()
            .filter { it.type == Registry.Enums.Type.enum }
            .toList()

        enumEnums.asSequence()
            .forEach { xml ->
                val enum = Element.EnumType(xml.name)
                enumTypes[xml.name] = enum
                xml.enums.asSequence()
                    .forEach { enumEntry ->
                        enum.addEntry(enumEntry)
                    }
            }

        registryTypes.values.asSequence()
            .filter { it.category == Registry.Types.Type.Category.enum }
            .forEach { xml ->
                xml.name!!
                if (xml.alias != null) {
                    val bitmaskAlias = flagBitTypes[xml.alias]
                    if (bitmaskAlias != null) {
                        flagBitTypes[xml.name] = bitmaskAlias
                        aliasTypes[xml.name] = bitmaskAlias
                    } else {
                        val enumAliasType = enumTypes[xml.alias]!!
                        enumTypes[xml.name] = enumAliasType
                        aliasTypes[xml.name] = enumAliasType
                    }
                }
            }

        enumTypes.toMap(allTypes)
        allTypes.toMap(allElements)
        check(enumTypes.keys.containsAll(enumEnums.map { it.name }))
    }

    val constantElements = mutableMapOf<String, Element.Constant>()

    init {
        val constantEnums = enums.asSequence()
            .filter { it.type == Registry.Enums.Type.constants }
            .toList()

        constantEnums.asSequence()
            .forEach { enumType ->
                enumType.enums.asSequence()
                    .forEach { enumEntry ->
                        if (enumEntry.alias != null) {
                            constantElements[enumEntry.name] = constantElements[enumEntry.alias]!!
                        } else {
                            val type = enumEntry.type!!
                            var value = enumEntry.value!!.replace("LL", "L")
                            if (value.contains('~')) {
                                value = value.replace("~", "") + ".inv()"
                            }
                            if (type == CBasicType.uint32_t && !value.contains('U')) {
                                value += "U"
                            }
                            val constant = Element.Constant(enumEntry.name, type, CodeBlock.of(value))
                            constant.docs = enumEntry.comment
                            constantElements[enumEntry.name] = constant
                        }
                    }
            }

        constantElements.toMap(allElements)
        check(constantElements.keys.containsAll(constantEnums.flatMap { it.enums.map { it.name } }))
    }

    val allStuff = enumTypes + flagBitTypes + constantElements
    val allEnum = enumTypes + flagBitTypes

    val funcpointerTypeTypes = mutableMapOf<String, Element.FuncpointerType>()

    init {
        val funcHeaderRegex = """typedef (.+) \(VKAPI_PTR \*<name>.+</name>\)\((?:void\);)?""".toRegex()
        val parameterRegex = """(const |)<type>(.+)</type>(\*?)\s+([a-zA-Z1-9_]+).+""".toRegex()
        typesWithTypeDef.asSequence()
            .filter { it.category == Registry.Types.Type.Category.funcpointer }
            .forEach { typeDef ->
                typeDef.name!!
                val innerText = typeDef.inner.joinToString("") { it.contentString }.lines()
                val headerMatch = funcHeaderRegex.matchEntire(innerText[0])!!
                val returnType = headerMatch.groupValues[1]
                val params = innerText.asSequence()
                    .drop(1)
                    .flatMap { it.split(", ") }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { parameterRegex.matchEntire(it)!! }
                    .associate { it.groupValues[4] to it.groupValues.subList(1, 4).joinToString("") }
                val funcpointerType = Element.FuncpointerType(
                    typeDef.name,
                    returnType,
                    params
                )
                val typeDefType = Element.TypeDef(typeDef.name, funcpointerType)
                funcpointerType.docs = typeDef.comment
                typeDefType.docs = typeDef.comment
                funcpointerTypeTypes[typeDefType.name] = funcpointerType
                typedefs[typeDefType.name] = typeDefType
            }

        funcpointerTypeTypes.toMap(allTypes)
        allTypes.toMap(allElements)
    }

    init {
        val ignored = setOf("IOSurfaceRef")
        check(
            typedefs.keys.containsAll(
                typesWithTypeDef.asSequence()
                    .map { it.name!! }
                    .filterNot { it in ignored }
                    .toSet()
            ))
    }

    val handleTypes = mutableMapOf<String, Element.HandleType>()

    init {
        val registryHandleTypes = registryTypes.values.asSequence()
            .filter { it.category == Registry.Types.Type.Category.handle }
            .toList()

        registryHandleTypes.asSequence()
            .forEach { handleType ->
                if (handleType.alias != null) {
                    handleTypes[handleType.name!!] = handleTypes[handleType.alias]!!
                } else {
                    val handle = Element.HandleType(
                        handleType.name!!,
                        handleType.objtypeenum!!,
                        handleType.parent
                    )
                    handle.docs = handleType.comment
                    handleTypes[handleType.name] = handle
                }
            }

        handleTypes.toMap(allTypes)
        allTypes.toMap(allElements)
        check(handleTypes.keys.containsAll(registryHandleTypes.map { it.name }))
    }

    val defineTypes = mutableMapOf<String, Element.Define>()

    init {
        val registryDefineTypes = registryTypes.values
            .filter { it.category == Registry.Types.Type.Category.define }
            .toList()

        registryDefineTypes
            .forEach { defineType ->
                defineType.name!!
                val value = defineType.inner.joinToString("") { it.contentString }
                val define = Element.Define(defineType.name, value)
                define.docs = defineType.comment
                defineTypes[defineType.name] = define
            }

        defineTypes.toMap(allElements)
        check(defineTypes.keys.containsAll(registryDefineTypes.map { it.name }))
    }

    private fun <T : Element.CEnum> T.addEntry(xmlEnum: Registry.Enums.Enum, extensionNum: Int? = null): Element {
        if (xmlEnum.alias != null) {
            val alias = Element.CEnum.Alias(xmlEnum.name, xmlEnum.alias)
            aliases[xmlEnum.name] = alias
            return alias
        }
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
                val extNumber = xmlEnum.extnumber?.decOrHexToInt() ?: extensionNum!!
                val sign = if (xmlEnum.dir == "-") -1 else 1
                val offset = xmlEnum.offset!!.decOrHexToInt()
                valueNum = sign * ((extNumber - 1) * VKFFI.VK_EXT_ENUM_BLOCKSIZE + offset + VKFFI.VK_EXT_ENUM_BASE)
                valueCode = CodeBlock.of(
                    "%L$literalSuffix",
                    valueNum
                )
            }
        }
        fun String.removeVendorTag(): String {
            VKFFI.VENDOR_TAGS.forEach {
                val suffix = "_$it"
                if (endsWith(suffix)) {
                    return removeSuffix(suffix)
                }
            }
            return this
        }

        val allCaps = name.pascalCaseToAllCaps()
        val split = allCaps.split("_FLAG_BITS".toRegex())
//        val suffix = if (split.size == 2) "_BIT" else ""

        val prefix = if (split.size == 2) {
            var temp = split[0] + "_"
            if (name.contains("FlagBits2")) {
                temp += "2_"
            }
            temp
        } else {
//            allCaps.removeVendorTag() + "_"
            ""
        }

        var fixedEnumName = xmlEnum.name
//        if (this.name != "VkVendorId") {
//            fixedEnumName = fixedEnumName.removeVendorTag()
//        }
        if (this.name !in enumTypeWhitelist) {
            check(fixedEnumName.startsWith(prefix)) {
                "Expected $fixedEnumName to start with $prefix"
            }
        }
//        if (xmlEnum.name !in enumWhitelist) {
//            check(fixedEnumName.endsWith(suffix)) {
//                "Expected $fixedEnumName to end with $suffix"
//            }
//        }
        fixedEnumName = fixedEnumName.removePrefix(prefix)/*.removeSuffix(suffix)*/
        val entry = Element.CEnum.Entry(xmlEnum.name, fixedEnumName, valueCode, valueNum)
        entry.docs = xmlEnum.comment
        entries[xmlEnum.name] = entry
        return entry
    }

    val structTypes = mutableMapOf<String, Element.Struct>()
    val unionTypes = mutableMapOf<String, Element.Union>()

    init {
        registryTypes.values.asSequence()
            .filter { it.category == Registry.Types.Type.Category.struct || it.category == Registry.Types.Type.Category.union }
            .forEach { struct ->
                var comment: String? = null
                val members = mutableListOf<Element.Member>()
                struct.inner.forEach { member ->
                    val xmlComment = member.tryParseXML<XMLComment>()
                    if (xmlComment != null) {
                        check(comment == null)
                        comment = xmlComment.value
                        return@forEach
                    }
                    val xmlMember = member.tryParseXML<XMLMember>()!!
                    val innerText = xmlMember.inner.map { it.contentString }
                    var type = xmlMember.type
                    var arrayLen: String? = null
                    var bits = -1
                    when (innerText.size) {
                        0 -> {}
                        1 -> {
                            val firstText = innerText[0]
                            when(firstText[0]) {
                                '*' -> {
                                    type = "$type*"
                                }
                                '[' -> {
                                    type = "$type[]"
                                    arrayLen = firstText.substring(1, firstText.length - 1)
                                }
                                ':' -> {
                                    bits = firstText.substring(1).toInt()
                                }
                                else -> {
                                    error("Unexpected inner text: $firstText")
                                }
                            }
                        }
                        2 -> {
                            val firstText = innerText[0]
                            val secondText = innerText[1]
                            check(secondText[0] == '*')
                            type = "$firstText$type*"
                        }
                        3 -> {
                            val firstText = innerText[0]
                            val secondText = innerText[1]
                            check(firstText == "[")
                            type = "$type[]"
                            arrayLen = xmlTagRegex.matchEntire(secondText)!!.groupValues[1]
                        }
                    }
                    val member = Element.Member(
                        xmlMember.name,
                        type,
                        arrayLen,
                        bits,
                        xmlMember
                    )
                    member.docs = comment
                    comment = null
                    members.add(member)
                }
                if (struct.category == Registry.Types.Type.Category.union) {
                    val unionType = Element.Union(struct.name!!, members)
                    unionType.docs = struct.comment
                    unionTypes[struct.name] = unionType
                } else {
                    val structType = Element.Struct(struct.name!!, members)
                    structType.docs = struct.comment
                    structTypes[struct.name] = structType
                }
            }

        unionTypes.toMap(allTypes)
        structTypes.toMap(allTypes)
        allTypes.toMap(allElements)
    }

    init {
        val skipped = setOf("vk_platform")
        val skippedRequire = setOf("API constants")
        val skippedCategory = setOf(Registry.Types.Type.Category.struct, Registry.Types.Type.Category.union)
        registryFeatures.forEach { feature ->
            val featureVKVersion = "Vulkan ${feature.number}"
            feature.require.asSequence()
                .filterNot { it.comment in skippedRequire }
                .forEach { require ->
                    require.types.asSequence()
                        .filterNot { it.name in skipped }
                        .filterNot { registryTypes[it.name]!!.category in skippedCategory }
                        .forEach {
                            allElements[it.name]!!.requiredBy = featureVKVersion
                        }
                    require.enums.asSequence()
                        .forEach {
                            if (it.extends == null) {
                                allStuff[it.name]!!.requiredBy = featureVKVersion
                                return@forEach
                            }
                            allEnum[it.extends]!!.addEntry(it).requiredBy = featureVKVersion
                        }
                }
        }

        registryExtensions.forEach { extension ->
            extension.require.forEach { require ->
                require.types.asSequence()
                    .filterNot { it.name in skipped }
                    .filterNot { registryTypes[it.name]!!.category in skippedCategory }
                    .forEach {
                        val type = allElements[it.name] ?: aliasTypes[it.name]
                        type?.requiredBy = extension.name
                    }

                val specVersionEnum = require.enums.find {
                    it.name.endsWith("_SPEC_VERSION")
                }
                val numberEnum = require.enums.find {
                    it.name.endsWith("_NUMBER")
                }
                val extensionNameEnum = require.enums.find {
                    it.name.endsWith("_EXTENSION_NAME")
                }

                require.enums.asSequence()
                    .filter { it.deprecated == null }
                    .filter { it.name != specVersionEnum?.name }
                    .filter { it.name != numberEnum?.name }
                    .filter { it.name != extensionNameEnum?.name }
                    .forEach {
                        if (it.extends == null) {
                            allStuff[it.name]?.requiredBy = extension.name
                            return@forEach
                        }
                        allEnum[it.extends]!!.addEntry(it, extension.number).requiredBy = extension.name
                    }
            }
        }
    }
}

sealed class Element(val name: String) {
    var requiredBy: String? = null
    var docs: String? = null

    override fun toString(): String {
        return "Type($name)"
    }

    sealed class Type(name: String) : Element(name)
    sealed class CEnum(name: String, val type: CBasicType) : Type(name) {
        val entries = mutableMapOf<String, Entry>()
        val aliases = mutableMapOf<String, Alias>()

        class Entry(name: String, val fixedName: String, val valueCode: CodeBlock, val valueNum: Number) : Element(name)
        class Alias(name: String, val value: String) : Element(name)
    }

    class Constant(name: String, val type: CBasicType, val value: CodeBlock) : Element(name)
    class Define(name: String, val value: String) : Element(name)

    class OpaqueType(name: String) : Type(name)
    class BasicType(name: String, val value: CBasicType) : Type(name)
    class BaseType(name: String, val value: TypeDef) : Type(name)
    class TypeDef(name: String, val value: Type) : Type(name)

    class FlagType(name: String, val bitType: String?) : Type(name) {
        val unused get() = bitType == null
    }

    class FlagBitType(name: String, type: CBasicType, val bitmaskType: FlagType?) : CEnum(name, type)
    class EnumType(name: String) : CEnum(name, CBasicType.int32_t)
    class HandleType(name: String, val objectEnum: String, val parent: String?) : Type(name)


    class FuncpointerType(name: String, val returnType: String, val params: Map<String, String>) : Type(name)

    class Member(name: String, val type: String, val maxCharLen: String?, val bits: Int, val xml: XMLMember) : Element(name)

    sealed class StructUnion(name: String, val members: List<Member>) : Type(name)
    class Struct(name: String, members: List<Member>) : StructUnion(name, members)
    class Union(name: String, members: List<Member>) : StructUnion(name, members)

    class VulkanVersion(name: String, val version: String, val required: List<Element>) : Element(name)
    class Extension(name: String, val specVersion: Int, val required: List<Element>) : Element(name)
}

@Serializable
@XmlSerialName("member")
data class XMLMember(
    @XmlElement val name: String,
    @XmlElement val type: String,
    val optional: Boolean = false,
    val noautovalidity: Boolean = false,
    val limittype: String? = null,
    val len: String? = null,
    val values: String? = null,
    val deprecated: String? = null,
    val altlen: String? = null,
    val api: String? = null,
    val objecttype: String? = null,
    val featurelink: String? = null,
    val selector: String? = null,
    val selection: String? = null,
    val externsync: Boolean = false,
    @XmlElement val comment: String? = null,
    @XmlValue val inner: List<CompactFragment> = emptyList()
)

fun main() {
    val a = MemoryLayout.unionLayout()
}