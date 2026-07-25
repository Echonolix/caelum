package net.echonolix.caelum.opengl.codegen

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.math.BigInteger
import java.util.Locale
import java.util.SortedMap
import java.util.TreeMap
import java.util.TreeSet
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal data class GlRegistry(
    val commands: SortedMap<String, GlCommand>,
    val enums: SortedMap<String, GlEnum>,
    val owners: List<GlOwner>,
)

internal data class GlOwner(
    val name: String,
    val fileName: String,
    val commandNames: List<String>,
    val enumNames: List<String>,
    val declarationCommandNames: List<String>,
    val declarationEnumNames: List<String>,
)

internal data class GlCommand(
    val name: String,
    val returnCarrier: GlCarrier?,
    val parameters: List<GlParameter>,
    val returnAbi: GlAbi = GlAbi.DIRECT,
)

internal data class GlParameter(
    val name: String,
    val carrier: GlCarrier,
    val abi: GlAbi = GlAbi.DIRECT,
)

internal data class GlEnum(
    val name: String,
    val alias: String?,
    val value: Long,
    val bitWidth: Int,
)

internal enum class GlCarrier {
    BOOLEAN,
    BYTE,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    ADDRESS,
}

internal enum class GlAbi {
    DIRECT,
    GL_HANDLE_ARB,
}

internal fun openGlOwnerFileName(owner: String): String {
    val version = GL_VERSION_NAME.matchEntire(owner)
    if (version != null) return "GL${version.groupValues[1]}${version.groupValues[2]}.kt"

    require(owner.startsWith("GL_")) { "Invalid OpenGL owner name '$owner'" }
    val parts = owner.removePrefix("GL_").split('_').filter(String::isNotEmpty)
    require(parts.isNotEmpty()) { "Invalid OpenGL owner name '$owner'" }
    val stem = parts.joinToString("", prefix = "GL") { part ->
        if (part.all { it.isUpperCase() || it.isDigit() }) part else part.replaceFirstChar(Char::uppercase)
    }
    require(KOTLIN_FILE_STEM.matches(stem)) {
        "OpenGL owner '$owner' does not map to a valid Kotlin filename"
    }
    return "$stem.kt"
}

internal fun parseOpenGLRegistry(input: InputStream): GlRegistry = try {
    val builder = secureDocumentBuilderFactory().newDocumentBuilder().apply {
        setEntityResolver { _, _ -> throw SAXException("External entities are disabled") }
        setErrorHandler(DefaultHandler())
    }
    val root = builder.parse(input).documentElement
    require(root.tagName == "registry") { "Expected <registry>, found <${root.tagName}>" }
    RegistryParser(root).parse()
} catch (error: IllegalArgumentException) {
    throw error
} catch (error: Exception) {
    throw IllegalArgumentException("Invalid OpenGL registry", error)
}

private fun secureDocumentBuilderFactory(): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        isXIncludeAware = false
        isExpandEntityReferences = false
    }

private class RegistryParser(private val root: Element) {
    private val rawCommands = linkedMapOf<String, RawCommand>()
    private val rawEnums = linkedMapOf<String, RawEnum>()

    fun parse(): GlRegistry {
        readCommands()
        readEnums()
        val core = selectCoreSurface()
        val extensions = selectExtensions()
        val commandNames = TreeSet(core.commandOwners.keys)
        val enumNames = TreeSet(core.enumOwners.keys)
        extensions.forEach { extension ->
            commandNames += extension.commandNames
            enumNames += extension.enumNames
        }
        val commandOwners = canonicalOwners(core.commandOwners, extensions, OwnerMembers::commandNames)
        val enumOwners = canonicalOwners(core.enumOwners, extensions, OwnerMembers::enumNames)
        val owners = buildOwners(core, extensions, commandOwners, enumOwners)
        val commandResolver = CommandResolver(rawCommands)
        val enumResolver = EnumResolver(rawEnums)

        return GlRegistry(
            commands = commandNames.associateTo(TreeMap()) { name ->
                name to commandResolver.resolve(name)
            },
            enums = enumNames.associateTo(TreeMap()) { name ->
                name to enumResolver.resolve(name)
            },
            owners = owners,
        )
    }

    private fun readCommands() {
        root.children("commands").flatMap { it.children("command") }.forEach { element ->
            val proto = element.child("proto")
            val name = element.attributeOrNull("name")
                ?: proto?.child("name")?.textContent?.trim()
                ?: throw IllegalArgumentException("Command definition is missing a name")
            val aliases = buildList {
                element.attributeOrNull("alias")?.let(::add)
                element.children("alias").mapNotNullTo(this) { it.attributeOrNull("name") }
            }.distinct()
            require(aliases.size <= 1) { "Command $name has conflicting aliases: $aliases" }

            val signature = proto?.let {
                RawSignature(
                    returnType = it.rawType(),
                    parameters = element.children("param")
                        .filter(Element::appliesToDesktopGl)
                        .map { parameter ->
                            val parameterName = parameter.child("name")?.textContent?.trim()
                                ?: throw IllegalArgumentException("Command $name has an unnamed parameter")
                            RawParameter(parameterName, parameter.rawType())
                        },
                )
            }
            putUnique(rawCommands, name, RawCommand(name, aliases.singleOrNull(), signature), "command")
        }
    }

    private fun readEnums() {
        root.children("enums").forEach { group ->
            group.children("enum")
                .filter(Element::appliesToDesktopGl)
                .forEach { element ->
                    val name = element.attributeOrNull("name")
                        ?: throw IllegalArgumentException("Enum definition is missing a name")
                    val rawEnum = RawEnum(
                        name = name,
                        alias = element.attributeOrNull("alias"),
                        literal = element.attributeOrNull("value"),
                        typeHint = element.attributeOrNull("type")
                            ?: group.attributeOrNull("bitwidth"),
                    )
                    putUnique(rawEnums, name, rawEnum, "enum")
                }
        }
    }

    private fun selectCoreSurface(): CoreSelection {
        val features = root.children("feature")
            .filter { it.attributeOrNull("api") == "gl" }
            .map { feature ->
                Feature(
                    element = feature,
                    name = feature.requiredAttribute("name"),
                    version = Version.parse(feature.requiredAttribute("number")),
                )
            }
            .filter { it.version <= Version(4, 6) }
            .sortedBy(Feature::version)
        val commandOwners = linkedMapOf<String, String>()
        val enumOwners = linkedMapOf<String, String>()

        features.forEach { feature ->
            feature.element.elementChildren()
                .filter { it.tagName == "require" || it.tagName == "remove" }
                .filter(Element::appliesToCoreGl)
                .forEach { block ->
                    val adding = block.tagName == "require"
                    block.children("command")
                        .filter(Element::appliesToCoreGl)
                        .forEach { item ->
                            updateActiveOwner(commandOwners, item.requiredAttribute("name"), feature.name, adding)
                        }
                    block.children("enum")
                        .filter(Element::appliesToCoreGl)
                        .forEach { item ->
                            updateActiveOwner(enumOwners, item.requiredAttribute("name"), feature.name, adding)
                        }
                }
        }
        return CoreSelection(
            featureNames = features.map(Feature::name),
            commandOwners = commandOwners,
            enumOwners = enumOwners,
        )
    }

    private fun selectExtensions(): List<OwnerMembers> =
        root.children("extensions")
            .flatMap { it.children("extension") }
            .filter { extension ->
                val supported = extension.requiredAttribute("supported").split('|').toSet()
                "disabled" !in supported && ("gl" in supported || "glcore" in supported)
            }
            .sortedBy { it.requiredAttribute("name") }
            .map { extension ->
                val commandNames = TreeSet<String>()
                val enumNames = TreeSet<String>()
                extension.children("require")
                    .filter(Element::appliesToExtensionGl)
                    .forEach { requirement ->
                        requirement.children("command")
                            .filter(Element::appliesToExtensionGl)
                            .mapTo(commandNames) { it.requiredAttribute("name") }
                        requirement.children("enum")
                            .filter(Element::appliesToExtensionGl)
                            .mapTo(enumNames) { it.requiredAttribute("name") }
                    }
                OwnerMembers(
                    name = extension.requiredAttribute("name"),
                    commandNames = commandNames,
                    enumNames = enumNames,
                )
            }

    private fun canonicalOwners(
        coreOwners: Map<String, String>,
        extensions: List<OwnerMembers>,
        names: (OwnerMembers) -> Set<String>,
    ): Map<String, String> = buildMap {
        putAll(coreOwners)
        extensions.forEach { extension ->
            names(extension).forEach { name -> putIfAbsent(name, extension.name) }
        }
    }

    private fun buildOwners(
        core: CoreSelection,
        extensions: List<OwnerMembers>,
        commandOwners: Map<String, String>,
        enumOwners: Map<String, String>,
    ): List<GlOwner> {
        val members = buildList {
            core.featureNames.forEach { featureName ->
                add(
                    OwnerMembers(
                        name = featureName,
                        commandNames = core.commandOwners.filterValues { it == featureName }.keys,
                        enumNames = core.enumOwners.filterValues { it == featureName }.keys,
                    ),
                )
            }
            addAll(extensions)
        }
        require(members.map(OwnerMembers::name).toSet().size == members.size) {
            "Duplicate OpenGL owner name"
        }
        val filenames = members.associate { member -> member.name to openGlOwnerFileName(member.name) }
        val collisions = filenames.entries.groupBy { it.value.lowercase(Locale.ROOT) }
            .filterValues { it.size > 1 }
        require(collisions.isEmpty()) {
            "OpenGL owner filename collision: " +
                collisions.entries.joinToString { (file, entries) ->
                    "$file <- ${entries.joinToString { it.key }}"
                }
        }
        return members.map { member ->
            GlOwner(
                name = member.name,
                fileName = filenames.getValue(member.name),
                commandNames = member.commandNames.sorted(),
                enumNames = member.enumNames.sorted(),
                declarationCommandNames = member.commandNames.filter { commandOwners[it] == member.name }.sorted(),
                declarationEnumNames = member.enumNames.filter { enumOwners[it] == member.name }.sorted(),
            )
        }
    }
}

private data class Feature(val element: Element, val name: String, val version: Version)

private data class CoreSelection(
    val featureNames: List<String>,
    val commandOwners: Map<String, String>,
    val enumOwners: Map<String, String>,
)

private data class OwnerMembers(
    val name: String,
    val commandNames: Set<String>,
    val enumNames: Set<String>,
)

private fun updateActiveOwner(
    owners: MutableMap<String, String>,
    name: String,
    currentOwner: String,
    adding: Boolean,
) {
    if (adding) owners.putIfAbsent(name, currentOwner) else owners.remove(name)
}

private class CommandResolver(private val commands: Map<String, RawCommand>) {
    private val cache = mutableMapOf<String, ResolvedSignature>()
    private val resolving = linkedSetOf<String>()

    fun resolve(name: String): GlCommand {
        val signature = resolveSignature(name)
        return GlCommand(name, signature.returnCarrier, signature.parameters, signature.returnAbi)
    }

    private fun resolveSignature(name: String): ResolvedSignature {
        cache[name]?.let { return it }
        require(resolving.add(name)) { "Command alias cycle: ${(resolving + name).joinToString(" -> ")}" }
        try {
            val command = commands[name]
                ?: throw IllegalArgumentException("Missing command definition for $name")
            val own = command.signature?.resolve(command.name)
            val inherited = command.alias?.let { alias ->
                require(alias in commands) { "Command ${command.name} aliases missing command $alias" }
                resolveSignature(alias)
            }
            require(own != null || inherited != null) {
                "Command ${command.name} has neither a signature nor an alias"
            }
            return requireNotNull(own ?: inherited).also { cache[name] = it }
        } finally {
            resolving.remove(name)
        }
    }
}

private class EnumResolver(private val enums: Map<String, RawEnum>) {
    private val cache = mutableMapOf<String, ResolvedEnum>()
    private val resolving = linkedSetOf<String>()

    fun resolve(name: String): GlEnum {
        val raw = enums[name] ?: throw IllegalArgumentException("Missing enum definition for $name")
        val resolved = resolveValue(name)
        return GlEnum(name, raw.alias, resolved.value, resolved.bitWidth)
    }

    private fun resolveValue(name: String): ResolvedEnum {
        cache[name]?.let { return it }
        require(resolving.add(name)) { "Enum alias cycle: ${(resolving + name).joinToString(" -> ")}" }
        try {
            val raw = enums[name] ?: throw IllegalArgumentException("Missing enum definition for $name")
            val own = raw.literal?.let { parseIntegerLiteral(it, raw.typeHint, name) }
            val inherited = raw.alias?.let { alias ->
                require(alias in enums) { "Enum $name aliases missing enum $alias" }
                resolveValue(alias)
            }
            require(own != null || inherited != null) { "Enum $name has no value" }
            if (own != null && inherited != null) {
                require(own == inherited) { "Enum $name conflicts with alias ${raw.alias}" }
            }
            return requireNotNull(own ?: inherited).also { cache[name] = it }
        } finally {
            resolving.remove(name)
        }
    }
}

private data class RawCommand(
    val name: String,
    val alias: String?,
    val signature: RawSignature?,
)

private data class RawSignature(
    val returnType: RawType,
    val parameters: List<RawParameter>,
) {
    fun resolve(commandName: String): ResolvedSignature = ResolvedSignature(
        returnType = returnType.toReturnType("return type of $commandName"),
        parameters = parameters.map { parameter ->
            val type = parameter.type.toType("parameter ${parameter.name} of $commandName")
            GlParameter(
                name = parameter.name,
                carrier = type.carrier,
                abi = type.abi,
            )
        },
    )
}

private data class RawParameter(val name: String, val type: RawType)

private data class RawType(val name: String, val pointer: Boolean) {
    fun toReturnType(context: String): ResolvedType? =
        if (!pointer && name == "void") null else toType(context)

    fun toType(context: String): ResolvedType {
        if (pointer) return ResolvedType(GlCarrier.ADDRESS)
        val carrier = when (name) {
            "GLboolean" -> GlCarrier.BOOLEAN
            "GLbyte", "GLubyte", "GLchar", "GLcharARB" -> GlCarrier.BYTE
            "GLshort", "GLushort", "GLhalf", "GLhalfARB", "GLhalfNV" -> GlCarrier.SHORT
            "GLenum", "GLbitfield", "GLint", "GLuint", "GLsizei", "GLclampx", "GLfixed" -> GlCarrier.INT
            "GLintptr", "GLintptrARB", "GLsizeiptr", "GLsizeiptrARB",
            "GLint64", "GLint64EXT", "GLuint64", "GLuint64EXT", "GLvdpauSurfaceNV",
            -> GlCarrier.LONG
            "GLfloat", "GLclampf" -> GlCarrier.FLOAT
            "GLdouble", "GLclampd" -> GlCarrier.DOUBLE
            "GLsync", "GLeglClientBufferEXT", "GLeglImageOES",
            "GLDEBUGPROC", "GLDEBUGPROCARB", "GLDEBUGPROCKHR", "GLDEBUGPROCAMD", "GLVULKANPROCNV",
            -> GlCarrier.ADDRESS
            "GLhandleARB" -> GlCarrier.LONG
            else -> throw IllegalArgumentException("Unknown C type '$name' in $context")
        }
        return ResolvedType(
            carrier = carrier,
            abi = if (name == "GLhandleARB") GlAbi.GL_HANDLE_ARB else GlAbi.DIRECT,
        )
    }
}

private data class ResolvedSignature(
    val returnType: ResolvedType?,
    val parameters: List<GlParameter>,
) {
    val returnCarrier: GlCarrier?
        get() = returnType?.carrier
    val returnAbi: GlAbi
        get() = returnType?.abi ?: GlAbi.DIRECT
}

private data class ResolvedType(val carrier: GlCarrier, val abi: GlAbi = GlAbi.DIRECT)

private data class RawEnum(
    val name: String,
    val alias: String?,
    val literal: String?,
    val typeHint: String?,
)

private data class ResolvedEnum(val value: Long, val bitWidth: Int)

private data class Version(val major: Int, val minor: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, Version::major, Version::minor)

    companion object {
        fun parse(value: String): Version {
            val parts = value.split('.')
            require(parts.size == 2) { "Invalid OpenGL feature version '$value'" }
            return Version(
                parts[0].toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid OpenGL feature version '$value'"),
                parts[1].toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid OpenGL feature version '$value'"),
            )
        }
    }
}

private fun parseIntegerLiteral(literal: String, typeHint: String?, enumName: String): ResolvedEnum {
    var valueText = literal.trim()
    while (valueText.length >= 2 && valueText.first() == '(' && valueText.last() == ')') {
        valueText = valueText.substring(1, valueText.lastIndex).trim()
    }
    val suffix = INTEGER_SUFFIX.find(valueText)?.value?.lowercase().orEmpty()
    if (suffix.isNotEmpty()) valueText = valueText.dropLast(suffix.length)
    val negative = valueText.startsWith('-')
    val unsignedText = if (negative || valueText.startsWith('+')) valueText.drop(1) else valueText
    val radix = if (unsignedText.startsWith("0x", ignoreCase = true)) 16 else 10
    val digits = if (radix == 16) unsignedText.drop(2) else unsignedText
    require(digits.isNotEmpty()) { "Invalid value '$literal' for enum $enumName" }
    val magnitude = try {
        BigInteger(digits, radix)
    } catch (error: NumberFormatException) {
        throw IllegalArgumentException("Invalid value '$literal' for enum $enumName", error)
    }
    val mathematicalValue = if (negative) magnitude.negate() else magnitude
    val hint = typeHint?.lowercase().orEmpty()
    val bitWidth = if (
        hint == "64" || hint.contains("ll") || suffix.contains('l') ||
        (!negative && magnitude.bitLength() > 32)
    ) {
        64
    } else {
        32
    }
    val modulus = BigInteger.ONE.shiftLeft(bitWidth)
    val minimum = BigInteger.ONE.shiftLeft(bitWidth - 1).negate()
    require(mathematicalValue >= minimum && mathematicalValue < modulus) {
        "Value '$literal' for enum $enumName does not fit in $bitWidth bits"
    }
    val signed = if (mathematicalValue.signum() >= 0 && mathematicalValue.testBit(bitWidth - 1)) {
        mathematicalValue.subtract(modulus)
    } else {
        mathematicalValue
    }
    return ResolvedEnum(signed.toLong(), bitWidth)
}

private val INTEGER_SUFFIX = Regex("(?i)(?:ull|llu|ul|lu|ll|u|l)$")

private fun Element.rawType(): RawType {
    val nameElement = child("name")
        ?: throw IllegalArgumentException("<$tagName> is missing <name>")
    val typeName = child("ptype")?.textContent?.trim()
        ?: textContent.substringBefore(nameElement.textContent)
            .replace("*", " ")
            .replace(Regex("\\bconst\\b"), " ")
            .trim()
            .split(Regex("\\s+"))
            .lastOrNull()
        ?: throw IllegalArgumentException("<$tagName> for ${nameElement.textContent} is missing a C type")
    return RawType(typeName, textContent.contains('*'))
}

private fun Element.appliesToDesktopGl(): Boolean =
    attributeOrNull("api").let { it == null || it == "gl" } &&
        attributeOrNull("profile").let { it == null || it == "core" || it == "compatibility" }

private fun Element.appliesToCoreGl(): Boolean =
    attributeOrNull("api").let { it == null || it == "gl" } &&
        attributeOrNull("profile").let { it == null || it == "core" }

private fun Element.appliesToExtensionGl(): Boolean =
    attributeOrNull("api").let { it == null || it == "gl" } &&
        attributeOrNull("profile").let { it == null || it == "core" || it == "compatibility" }

private fun Element.requiredAttribute(name: String): String =
    attributeOrNull(name)
        ?: throw IllegalArgumentException("<$tagName> is missing required attribute '$name'")

private fun Element.attributeOrNull(name: String): String? =
    getAttribute(name).takeIf(String::isNotBlank)

private fun Element.child(tagName: String): Element? = children(tagName).singleOrNull()

private fun Element.elementChildren(): List<Element> = buildList {
    val nodes = childNodes
    for (index in 0 until nodes.length) {
        val node = nodes.item(index)
        if (node.nodeType == Node.ELEMENT_NODE) add(node as Element)
    }
}

private fun Element.children(tagName: String): List<Element> = buildList {
    val nodes = childNodes
    for (index in 0 until nodes.length) {
        val node = nodes.item(index)
        if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == tagName) {
            add(node as Element)
        }
    }
}

private val GL_VERSION_NAME = Regex("""GL_VERSION_(\d+)_(\d+)""")
private val KOTLIN_FILE_STEM = Regex("""[A-Za-z_][A-Za-z0-9_]*""")

private fun <T> putUnique(target: MutableMap<String, T>, name: String, value: T, kind: String) {
    val previous = target.putIfAbsent(name, value)
    require(previous == null || previous == value) { "Conflicting $kind definition for $name" }
}
