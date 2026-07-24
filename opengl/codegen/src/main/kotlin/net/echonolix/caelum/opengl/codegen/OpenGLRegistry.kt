package net.echonolix.caelum.opengl.codegen

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.math.BigInteger
import java.util.SortedMap
import java.util.TreeMap
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal data class GlRegistry(
    val commands: SortedMap<String, GlCommand>,
    val enums: SortedMap<String, GlEnum>,
)

internal data class GlCommand(
    val name: String,
    val returnCarrier: GlCarrier?,
    val parameters: List<GlParameter>,
)

internal data class GlParameter(
    val name: String,
    val carrier: GlCarrier,
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
        val (commandNames, enumNames) = selectCore33Surface()
        val commandResolver = CommandResolver(rawCommands)
        val enumResolver = EnumResolver(rawEnums)

        return GlRegistry(
            commands = commandNames.associateTo(TreeMap()) { name ->
                name to commandResolver.resolve(name)
            },
            enums = enumNames.associateTo(TreeMap()) { name ->
                name to enumResolver.resolve(name)
            },
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

    private fun selectCore33Surface(): Pair<Set<String>, Set<String>> {
        val commands = linkedSetOf<String>()
        val enums = linkedSetOf<String>()
        val features = root.children("feature")
            .filter { it.attributeOrNull("api") == "gl" }
            .map { it to Version.parse(it.requiredAttribute("number")) }
            .filter { (_, version) -> version <= Version(3, 3) }
            .sortedBy { (_, version) -> version }

        features.forEach { (feature, _) ->
            feature.children("require")
                .filter(Element::appliesToDesktopGl)
                .forEach { requirement ->
                    requirement.children("command")
                        .filter(Element::appliesToDesktopGl)
                        .mapTo(commands) { it.requiredAttribute("name") }
                    requirement.children("enum")
                        .filter(Element::appliesToDesktopGl)
                        .mapTo(enums) { it.requiredAttribute("name") }
                }
            feature.children("remove")
                .filter(Element::appliesToDesktopGl)
                .forEach { removal ->
                    removal.children("command")
                        .filter(Element::appliesToDesktopGl)
                        .forEach { commands.remove(it.requiredAttribute("name")) }
                    removal.children("enum")
                        .filter(Element::appliesToDesktopGl)
                        .forEach { enums.remove(it.requiredAttribute("name")) }
                }
        }
        return commands to enums
    }
}

private class CommandResolver(private val commands: Map<String, RawCommand>) {
    private val cache = mutableMapOf<String, ResolvedSignature>()
    private val resolving = linkedSetOf<String>()

    fun resolve(name: String): GlCommand {
        val signature = resolveSignature(name)
        return GlCommand(name, signature.returnCarrier, signature.parameters)
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
            if (own != null && inherited != null) {
                require(own.carriers == inherited.carriers) {
                    "Command ${command.name} conflicts with alias ${command.alias}"
                }
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
        returnCarrier = returnType.toReturnCarrier("return type of $commandName"),
        parameters = parameters.map { parameter ->
            GlParameter(
                name = parameter.name,
                carrier = parameter.type.toCarrier("parameter ${parameter.name} of $commandName"),
            )
        },
    )
}

private data class RawParameter(val name: String, val type: RawType)

private data class RawType(val name: String, val pointer: Boolean) {
    fun toReturnCarrier(context: String): GlCarrier? =
        if (!pointer && name == "void") null else toCarrier(context)

    fun toCarrier(context: String): GlCarrier {
        if (pointer || name == "GLsync") return GlCarrier.ADDRESS
        return when (name) {
            "GLboolean" -> GlCarrier.BOOLEAN
            "GLbyte", "GLubyte", "GLchar" -> GlCarrier.BYTE
            "GLshort", "GLushort" -> GlCarrier.SHORT
            "GLint", "GLuint", "GLenum", "GLbitfield", "GLsizei" -> GlCarrier.INT
            "GLint64", "GLuint64", "GLintptr", "GLsizeiptr" -> GlCarrier.LONG
            "GLfloat" -> GlCarrier.FLOAT
            "GLdouble" -> GlCarrier.DOUBLE
            else -> throw IllegalArgumentException("Unknown C type '$name' in $context")
        }
    }
}

private data class ResolvedSignature(
    val returnCarrier: GlCarrier?,
    val parameters: List<GlParameter>,
) {
    val carriers: List<GlCarrier?>
        get() = listOf(returnCarrier) + parameters.map(GlParameter::carrier)
}

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
    return RawType(typeName, textContent.substringBefore(nameElement.textContent).contains('*'))
}

private fun Element.appliesToDesktopGl(): Boolean =
    attributeOrNull("api").let { it == null || it == "gl" } &&
        attributeOrNull("profile").let { it == null || it == "core" }

private fun Element.requiredAttribute(name: String): String =
    attributeOrNull(name)
        ?: throw IllegalArgumentException("<$tagName> is missing required attribute '$name'")

private fun Element.attributeOrNull(name: String): String? =
    getAttribute(name).takeIf(String::isNotBlank)

private fun Element.child(tagName: String): Element? = children(tagName).singleOrNull()

private fun Element.children(tagName: String): List<Element> = buildList {
    val nodes = childNodes
    for (index in 0 until nodes.length) {
        val node = nodes.item(index)
        if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == tagName) {
            add(node as Element)
        }
    }
}

private fun <T> putUnique(target: MutableMap<String, T>, name: String, value: T, kind: String) {
    val previous = target.putIfAbsent(name, value)
    require(previous == null || previous == value) { "Conflicting $kind definition for $name" }
}
