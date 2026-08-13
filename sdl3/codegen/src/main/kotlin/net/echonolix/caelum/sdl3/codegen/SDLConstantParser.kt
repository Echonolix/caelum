package net.echonolix.caelum.sdl3.codegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readText

internal object SDLConstantParser {
    private val publicName = Regex("""SDL(?:K|_[A-Za-z0-9])[A-Za-z0-9_]*""")
    private val enumBlock = Regex(
        """\b(?:typedef\s+)?enum(?:\s+[A-Za-z_][A-Za-z0-9_]*)?\s*\{(.*?)\}\s*(?:[A-Za-z_][A-Za-z0-9_]*)?\s*;""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val comments = Regex("""/\*.*?\*/|//[^\r\n]*""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val preprocessorLine = Regex("""(?m)^\s*#.*$""")

    fun parse(includeDir: Path): SDLConstantRegistry {
        require(Files.isDirectory(includeDir)) { "SDL include directory does not exist: $includeDir" }
        val headers = Files.walk(includeDir).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.extension == "h" }
                .filter { it.name != "SDL_oldnames.h" }
                .sorted()
                .map { it.readText() }
                .toList()
        }
        val enumDeclarations = parseEnumDeclarations(headers)
        val macroDeclarations = parseMacroDeclarations()
        val values = platformSymbols.toMutableMap()
        val entries = linkedMapOf<String, SDLGeneratedConstant>()
        val skipped = mutableListOf<SDLSkippedConstant>()

        evaluateEnums(enumDeclarations, values, entries, skipped)
        evaluateMacros(macroDeclarations, values, entries, skipped)

        require(entries.values.count { it.source == SDLConstantSource.ENUM } >= 1_100) {
            "SDL constant parser only generated ${entries.values.count { it.source == SDLConstantSource.ENUM }} enum entries"
        }
        verifyMacroCoverage(entries, skipped)
        return SDLConstantRegistry(entries.values.sortedBy(SDLGeneratedConstant::name), skipped.sortedWith(
            compareBy(SDLSkippedConstant::source, SDLSkippedConstant::name, SDLSkippedConstant::expression),
        ))
    }

    private fun parseEnumDeclarations(headers: List<String>): List<Declaration> {
        val declarations = mutableListOf<Declaration>()
        headers.forEach { header ->
            val text = selectTargetBranches(comments.replace(header, " "))
            enumBlock.findAll(text).forEach { block ->
                var previousName: String? = null
                splitTopLevel(preprocessorLine.replace(block.groupValues[1], " "), ',').forEach { item ->
                    val match = Regex("""^\s*(SDL(?:K|_[A-Za-z0-9])[A-Za-z0-9_]*)\b(?:\s*=\s*(.+))?\s*$""", RegexOption.DOT_MATCHES_ALL)
                        .matchEntire(item) ?: return@forEach
                    val name = match.groupValues[1]
                    val expression = match.groupValues[2].trim().ifEmpty {
                        previousName?.let { "$it + 1" } ?: "0"
                    }
                    declarations += Declaration(name, expression, SDLConstantSource.ENUM)
                    previousName = name
                }
            }
        }
        return declarations
    }

    private fun parseMacroDeclarations(): List<Declaration> = SDLActiveMacroManifest.publicCandidates.map { definition ->
        Declaration(definition.name, definition.expression, SDLConstantSource.MACRO)
    }

    private fun verifyMacroCoverage(
        entries: Map<String, SDLGeneratedConstant>,
        skipped: List<SDLSkippedConstant>,
    ) {
        val candidates = SDLActiveMacroManifest.publicCandidates.map(SDLActiveMacroDefinition::name).toSet()
        val classifications = buildList {
            entries.keys.filterTo(this) { it in candidates }
            skipped.filterTo(mutableListOf()) { it.source == SDLConstantSource.MACRO }
                .mapTo(this, SDLSkippedConstant::name)
        }
        require(classifications.size == classifications.toSet().size) {
            "Active SDL macro candidates were classified more than once"
        }
        require(classifications.toSet() == candidates) {
            val missing = candidates - classifications.toSet()
            val unexpected = classifications.toSet() - candidates
            "Active SDL macro classification mismatch; missing=$missing, unexpected=$unexpected"
        }
    }

    private fun evaluateEnums(
        declarations: List<Declaration>,
        values: MutableMap<String, SDLConstantValue>,
        entries: MutableMap<String, SDLGeneratedConstant>,
        skipped: MutableList<SDLSkippedConstant>,
    ) {
        declarations.forEach { declaration ->
            val evaluated = runCatching { ExpressionParser(declaration.expression, values).parse() }
                .getOrElse {
                    skipped += SDLSkippedConstant(
                        declaration.name,
                        SDLConstantSource.ENUM,
                        declaration.expression,
                        "unsupported constant expression: ${it.message ?: it::class.simpleName}",
                    )
                    return@forEach
                }
            val value = normalizeEnumValue(evaluated)
            val previous = entries[declaration.name]
            if (previous != null && previous.value != value) {
                entries.remove(declaration.name)
                values.remove(declaration.name)
                skipped += SDLSkippedConstant(
                    declaration.name,
                    SDLConstantSource.ENUM,
                    declaration.expression,
                    "target branches produce conflicting values",
                )
            } else if (previous == null) {
                values[declaration.name] = value
                entries[declaration.name] = SDLGeneratedConstant(declaration.name, value, SDLConstantSource.ENUM)
            }
        }
    }

    private fun evaluateMacros(
        declarations: List<Declaration>,
        values: MutableMap<String, SDLConstantValue>,
        entries: MutableMap<String, SDLGeneratedConstant>,
        skipped: MutableList<SDLSkippedConstant>,
    ) {
        val candidates = declarations.groupBy(Declaration::name).toSortedMap()
        val pending = candidates.toMutableMap()
        var changed: Boolean
        do {
            changed = false
            pending.toMap().forEach { (name, definitions) ->
                if (name in entries) {
                    pending.remove(name)
                    return@forEach
                }
                val evaluated = definitions.mapNotNull { declaration ->
                    runCatching { parseMacroValue(declaration.expression, values) }.getOrNull()
                }.distinct()
                if (evaluated.size == 1) {
                    val value = evaluated.single()
                    values[name] = value
                    entries[name] = SDLGeneratedConstant(name, value, SDLConstantSource.MACRO)
                    pending.remove(name)
                    changed = true
                } else if (evaluated.size > 1) {
                    skipped += SDLSkippedConstant(
                        name, SDLConstantSource.MACRO, definitions.joinToString(" | ", transform = Declaration::expression),
                        "target branches produce conflicting values",
                    )
                    pending.remove(name)
                }
            }
        } while (changed)

        pending.forEach { (name, definitions) ->
            skipped += SDLSkippedConstant(
                name, SDLConstantSource.MACRO, definitions.joinToString(" | ", transform = Declaration::expression),
                "unsupported expression or unresolved dependency",
            )
        }
    }

    private fun parseMacroValue(expression: String, values: Map<String, SDLConstantValue>): SDLConstantValue {
        parseCString(expression, values)?.let { return SDLConstantValue.StringValue(it) }
        return ExpressionParser(expression, values).parse()
    }

    private fun parseCString(expression: String, values: Map<String, SDLConstantValue>): String? {
        var input = expression.trim()
        val result = StringBuilder()
        var consumed = false
        while (input.isNotEmpty()) {
            val identifier = publicName.matchAt(input, 0)
            if (identifier != null) {
                val value = values[identifier.value] as? SDLConstantValue.StringValue ?: return null
                result.append(value.value)
                input = input.substring(identifier.range.last + 1).trimStart()
                consumed = true
                continue
            }
            if (!input.startsWith('"')) return null
            val end = findStringEnd(input)
            if (end < 0) return null
            result.append(unescapeCString(input.substring(1, end)))
            input = input.substring(end + 1).trimStart()
            consumed = true
        }
        return result.toString().takeIf { consumed }
    }

    private fun findStringEnd(value: String): Int {
        var escaped = false
        for (index in 1 until value.length) {
            val char = value[index]
            if (char == '"' && !escaped) return index
            escaped = char == '\\' && !escaped
            if (char != '\\') escaped = false
        }
        return -1
    }

    private fun unescapeCString(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            if (value[index] != '\\' || index == value.lastIndex) {
                append(value[index++])
                continue
            }
            val escaped = value[++index]
            append(when (escaped) {
                'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; 'b' -> '\b'; 'f' -> '\u000c'
                '\\' -> '\\'; '"' -> '"'; '\'' -> '\''; '0' -> '\u0000'
                else -> escaped
            })
            index++
        }
    }

    private fun normalizeEnumValue(value: SDLConstantValue): SDLConstantValue = when (value) {
        is SDLConstantValue.SignedInteger -> SDLConstantValue.SignedInteger(value.value.toInt().toLong(), 32)
        is SDLConstantValue.UnsignedInteger -> SDLConstantValue.SignedInteger(value.value.toUInt().toInt().toLong(), 32)
        else -> value
    }

    // SDL's public enum conditionals in the vendored headers only select native byte order.
    private fun selectTargetBranches(text: String): String {
        var result = text.replace("\r\n", "\n")
        val conditional = Regex(
            """(?ms)^\s*#\s*if\s+SDL_BYTEORDER\s*==\s*SDL_(BIG|LIL)_ENDIAN\s*\n(.*?)^\s*#\s*else\s*\n(.*?)^\s*#\s*endif[^\n]*$""",
        )
        while (true) {
            val match = conditional.find(result) ?: break
            val selected = if (match.groupValues[1] == "LIL") match.groupValues[2] else match.groupValues[3]
            result = result.replaceRange(match.range, selected)
        }
        return result
    }

    private fun splitTopLevel(value: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = 0
        var quote: Char? = null
        var escaped = false
        value.forEachIndexed { index, char ->
            if (quote != null) {
                if (char == quote && !escaped) quote = null
                escaped = char == '\\' && !escaped
                if (char != '\\') escaped = false
                return@forEachIndexed
            }
            if (char == '\'' || char == '"') quote = char
            else when (char) {
                '(' -> depth++
                ')' -> depth--
                delimiter -> if (depth == 0) {
                    result += value.substring(start, index)
                    start = index + 1
                }
            }
        }
        result += value.substring(start)
        return result
    }

    private data class Declaration(val name: String, val expression: String, val source: SDLConstantSource)

    private val platformSymbols = mapOf(
        "PRId32" to SDLConstantValue.StringValue("d"),
        "PRIu32" to SDLConstantValue.StringValue("u"),
        "PRIx32" to SDLConstantValue.StringValue("x"),
        "PRIX32" to SDLConstantValue.StringValue("X"),
        "SIZE_MAX" to SDLConstantValue.UnsignedInteger(ULong.MAX_VALUE, 64),
    )
}

private class ExpressionParser(
    expression: String,
    private val symbols: Map<String, SDLConstantValue>,
) {
    private val input = stripCasts(expression.trim())
    private var offset = 0

    fun parse(): SDLConstantValue {
        parseFloatingLiteral()?.let { return it }
        val value = parseOr()
        skipWhitespace()
        require(offset == input.length) { "unexpected input '${input.substring(offset)}'" }
        return value
    }

    private fun parseOr(): SDLConstantValue = binary(::parseXor, "|") { a, b -> a or b }
    private fun parseXor(): SDLConstantValue = binary(::parseAnd, "^") { a, b -> a xor b }
    private fun parseAnd(): SDLConstantValue = binary(::parseShift, "&") { a, b -> a and b }

    private fun parseShift(): SDLConstantValue {
        var value = parseAdd()
        while (true) {
            value = when {
                consume("<<") -> combine(value, parseAdd()) { a, b -> a shl b.toInt() }
                consume(">>") -> combine(value, parseAdd()) { a, b -> a shr b.toInt() }
                else -> return value
            }
        }
    }

    private fun parseAdd(): SDLConstantValue {
        var value = parseMultiply()
        while (true) {
            value = when {
                consume("+") -> combine(value, parseMultiply()) { a, b -> a + b }
                consume("-") -> combine(value, parseMultiply()) { a, b -> a - b }
                else -> return value
            }
        }
    }

    private fun parseMultiply(): SDLConstantValue {
        var value = parseUnary()
        while (true) {
            value = when {
                consume("*") -> combine(value, parseUnary()) { a, b -> a * b }
                consume("/") -> combine(value, parseUnary()) { a, b -> a / b }
                consume("%") -> combine(value, parseUnary()) { a, b -> a % b }
                else -> return value
            }
        }
    }

    private fun parseUnary(): SDLConstantValue = when {
        consume("+") -> parseUnary()
        consume("-") -> {
            val operand = parseUnary()
            signed(-integer(operand).toLong(), width(operand))
        }
        consume("~") -> {
            val operand = parseUnary()
            integerValue(operand).inv().let { fromRaw(it, operand) }
        }
        else -> parsePrimary()
    }

    private fun parsePrimary(): SDLConstantValue {
        if (consume("(")) return parseOr().also { require(consume(")")) }
        skipWhitespace()
        parseCharacter()?.let { return SDLConstantValue.SignedInteger(it.toLong(), 32) }
        val number = NUMBER.matchAt(input, offset)
        if (number != null) {
            offset = number.range.last + 1
            return integerLiteral(number.value)
        }
        val identifier = IDENTIFIER.matchAt(input, offset) ?: error("expected constant at '${input.substring(offset)}'")
        offset = identifier.range.last + 1
        val name = identifier.value
        if (consume("(")) {
            val arguments = mutableListOf<SDLConstantValue>()
            if (!consume(")")) {
                do {
                    arguments += parseOr()
                } while (consume(","))
                require(consume(")"))
            }
            return when (name) {
                "SDL_UINT64_C", "UINT64_C" -> unsigned(integerValue(arguments.single()), 64)
                "SDL_SINT64_C", "INT64_C" -> signed(integerValue(arguments.single()).toLong(), 64)
                "SDL_BUTTON_MASK" -> {
                    val button = integerValue(arguments.single()).toInt()
                    require(button in 1..32) { "SDL button index out of range: $button" }
                    unsigned(1uL shl (button - 1), 32)
                }
                "SDL_VERSIONNUM" -> {
                    require(arguments.size == 3)
                    val major = integerValue(arguments[0])
                    val minor = integerValue(arguments[1])
                    val patch = integerValue(arguments[2])
                    signed((major * 1_000_000uL + minor * 1_000uL + patch).toLong(), 32)
                }
                "SDL_WINDOWPOS_CENTERED_DISPLAY" -> combine(
                    unsigned(0x2FFF0000uL, 32), arguments.single(), ULong::or,
                )
                "SDL_WINDOWPOS_UNDEFINED_DISPLAY" -> combine(
                    unsigned(0x1FFF0000uL, 32), arguments.single(), ULong::or,
                )
                else -> error("unsupported macro call $name")
            }
        }
        return symbols[name] ?: error("unresolved symbol $name")
    }

    private fun parseFloatingLiteral(): SDLConstantValue.FloatingPoint? {
        val match = FLOAT.matchEntire(input) ?: return null
        val suffix = match.groupValues[1]
        val literal = input.dropLast(suffix.length)
        return SDLConstantValue.FloatingPoint(literal.toDouble(), suffix.equals("f", true))
    }

    private fun parseCharacter(): Int? {
        if (offset >= input.length || input[offset] != '\'') return null
        val end = input.indexOf('\'', offset + 1)
        require(end > offset + 1) { "invalid character literal" }
        val body = input.substring(offset + 1, end)
        offset = end + 1
        return when {
            body.length == 1 -> body[0].code
            body.startsWith("\\x") -> body.substring(2).toInt(16)
            body == "\\n" -> '\n'.code
            body == "\\r" -> '\r'.code
            body == "\\t" -> '\t'.code
            body == "\\0" -> 0
            body == "\\\\" -> '\\'.code
            body == "\\\'" -> '\''.code
            else -> error("unsupported character literal '$body'")
        }
    }

    private fun binary(next: () -> SDLConstantValue, operator: String, operation: (ULong, ULong) -> ULong): SDLConstantValue {
        var value = next()
        while (consume(operator) && !peek(operator)) value = combine(value, next(), operation)
        return value
    }

    private fun combine(
        left: SDLConstantValue,
        right: SDLConstantValue,
        operation: (ULong, ULong) -> ULong,
    ): SDLConstantValue {
        val bits = maxOf(width(left), width(right))
        val unsigned = left is SDLConstantValue.UnsignedInteger || right is SDLConstantValue.UnsignedInteger
        val raw = operation(integerValue(left), integerValue(right))
        return if (unsigned) unsigned(raw, bits) else signed(raw.toLong(), bits)
    }

    private fun integer(value: SDLConstantValue): ULong = integerValue(value)
    private fun width(value: SDLConstantValue): Int = when (value) {
        is SDLConstantValue.SignedInteger -> value.bits
        is SDLConstantValue.UnsignedInteger -> value.bits
        else -> error("not an integer")
    }

    private fun integerLiteral(literal: String): SDLConstantValue {
        val suffix = literal.takeLastWhile { it in "uUlL" }
        val digits = literal.dropLast(suffix.length)
        val value = if (digits.startsWith("0x", true)) digits.substring(2).toULong(16) else digits.toULong()
        val bits = if (suffix.count { it.equals('l', true) } >= 2 || value > UInt.MAX_VALUE.toULong()) 64 else 32
        return if (suffix.any { it.equals('u', true) }) unsigned(value, bits) else signed(value.toLong(), bits)
    }

    private fun consume(token: String): Boolean {
        skipWhitespace()
        if (!input.startsWith(token, offset)) return false
        offset += token.length
        return true
    }

    private fun peek(token: String): Boolean = input.startsWith(token, offset)
    private fun skipWhitespace() { while (offset < input.length && input[offset].isWhitespace()) offset++ }

    private fun stripCasts(value: String): String {
        var result = value
        val cast = Regex(
            """\(\s*(?:const\s+)?(?:u?int(?:8|16|32|64)_t|size_t|(?:S|U)int(?:8|16|32|64)|SDL_[A-Za-z0-9_]*[a-z][A-Za-z0-9_]*)\s*\)""",
        )
        do {
            val previous = result
            result = cast.replace(result, "")
        } while (result != previous)
        return result.trim()
    }

    private fun integerValue(value: SDLConstantValue): ULong = when (value) {
        is SDLConstantValue.SignedInteger -> value.value.toULong()
        is SDLConstantValue.UnsignedInteger -> value.value
        else -> error("not an integer")
    }

    private fun fromRaw(raw: ULong, prototype: SDLConstantValue): SDLConstantValue = when (prototype) {
        is SDLConstantValue.UnsignedInteger -> unsigned(raw, prototype.bits)
        is SDLConstantValue.SignedInteger -> signed(raw.toLong(), prototype.bits)
        else -> error("not an integer")
    }

    private fun signed(value: Long, bits: Int) = SDLConstantValue.SignedInteger(
        if (bits == 32) value.toInt().toLong() else value,
        bits,
    )

    private fun unsigned(value: ULong, bits: Int) = SDLConstantValue.UnsignedInteger(
        if (bits == 32) value.toUInt().toULong() else value,
        bits,
    )

    companion object {
        private val IDENTIFIER = Regex("""[A-Za-z_][A-Za-z0-9_]*""")
        private val NUMBER = Regex("""(?:0[xX][0-9A-Fa-f]+|[0-9]+)[uUlL]*""")
        private val FLOAT = Regex("""(?:[0-9]+\.[0-9]*|[0-9]*\.[0-9]+)(?:[eE][+-]?[0-9]+)?([fF]?)""")
    }
}
