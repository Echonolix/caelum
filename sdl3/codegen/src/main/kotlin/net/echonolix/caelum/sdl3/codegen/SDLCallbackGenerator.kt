package net.echonolix.caelum.sdl3.codegen

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal object SDLCallbackGenerator {
    private const val PACKAGE_NAME = "net.echonolix.caelum.sdl3"
    private const val FUNCTIONS_PACKAGE = "$PACKAGE_NAME.functions"

    fun generate(registry: SDLCallbackRegistry, outputDir: Path): Set<Path> {
        require(registry.callbacks.size + registry.unsupported.size >= 40) {
            "SDL callback parser classified only ${registry.callbacks.size + registry.unsupported.size} callbacks; expected at least 40"
        }
        val packageDir = outputDir.resolve(PACKAGE_NAME.replace('.', '/')).createDirectories()
        val functionsDir = outputDir.resolve(FUNCTIONS_PACKAGE.replace('.', '/')).createDirectories()
        return buildSet {
            add(write(packageDir, "SDLCallbacks.kt", inventoryFile(registry)))
            registry.callbacks.forEach { callback ->
                add(write(functionsDir, "${callback.name}.kt", callbackFile(callback)))
            }
        }
    }

    private fun inventoryFile(registry: SDLCallbackRegistry): String = buildString {
        appendLine(FILE_HEADER)
        appendLine("package $PACKAGE_NAME")
        appendLine()
        appendLine("public data class SDLUnsupportedCallback(public val name: String, public val reason: String)")
        appendLine()
        appendLine("public data class SDLCallbackPointeeErasure(")
        appendLine("    public val callbackName: String,")
        appendLine("    public val parameterName: String,")
        appendLine("    public val nativeType: String,")
        appendLine("    public val exposedType: String,")
        appendLine("    public val reason: String,")
        appendLine(")")
        appendLine()
        appendLine("public object SDLCallbacks {")
        appendLine("    @JvmField")
        appendLine("    public val names: List<String> = listOf(")
        registry.callbacks.forEach { appendLine("        \"${it.name}\",") }
        appendLine("    )")
        appendLine()
        appendLine("    @JvmField")
        appendLine("    public val unsupported: List<SDLUnsupportedCallback> = listOf(")
        registry.unsupported.forEach {
            appendLine("        SDLUnsupportedCallback(\"${it.name}\", \"${escape(it.reason)}\"),")
        }
        appendLine("    )")
        appendLine()
        appendLine("    @JvmField")
        appendLine("    public val erasures: List<SDLCallbackPointeeErasure> = listOf(")
        registry.erasures.forEach {
            appendLine("        SDLCallbackPointeeErasure(")
            appendLine("            \"${escape(it.callbackName)}\",")
            appendLine("            \"${escape(it.parameterName)}\",")
            appendLine("            \"${escape(it.nativeType)}\",")
            appendLine("            \"${escape(it.exposedType)}\",")
            appendLine("            \"${escape(it.reason)}\",")
            appendLine("        ),")
        }
        appendLine("    )")
        appendLine("}")
    }

    private fun callbackFile(callback: SDLCallback): String = buildString {
        appendLine(FILE_HEADER)
        appendLine("package $FUNCTIONS_PACKAGE")
        appendLine()
        appendLine("import java.lang.foreign.MemorySegment")
        appendLine("import java.lang.invoke.MethodHandle")
        appendLine("import net.echonolix.caelum.*")
        appendLine("import net.echonolix.caelum.sdl3.*")
        appendLine()
        appendLine("public fun interface ${callback.name} : SDLFunction {")
        appendLine("    override val typeDescriptor: SDLFunction.Descriptor<${callback.name}>")
        appendLine("        get() = TypeDescriptor")
        appendLine()
        append("    public operator fun invoke(")
        appendParameters(callback.parameters, apiTypes = true)
        append("): ${callback.returnType.kotlinType()}")
        appendLine()
        appendLine()
        append("    public fun invokeNative(")
        appendParameters(callback.parameters, apiTypes = false)
        appendLine("): ${carrierType(callback.returnType)} {")
        appendLine("        val result = invoke(")
        appendConvertedArguments(callback.parameters, fromCarrier = true, indent = "            ")
        appendLine("        )")
        appendLine("        return ${toCarrierExpression(callback.returnType, "result")}")
        appendLine("    }")
        appendLine()
        appendLine("    public companion object TypeDescriptor : SDLFunction.Descriptor<${callback.name}>(")
        appendLine("        \"${callback.name}\",")
        appendLine("        ${callback.name}::class.java,")
        appendLine("        ${descriptor(callback.returnType)},")
        callback.parameters.forEach { appendLine("        ${descriptor(it.type)},") }
        appendLine("    ) {")
        appendLine("        override fun fromNativeData(value: MemorySegment): ${callback.name} = Impl(downcallHandle(value))")
        appendLine()
        appendLine("        private class Impl(")
        appendLine("            funcHandle: MethodHandle,")
        appendLine("        ) : NFunction.Impl(funcHandle), ${callback.name} {")
        append("            override fun invoke(")
        appendParameters(callback.parameters, apiTypes = true, multilineIndent = "                ")
        appendLine("): ${callback.returnType.kotlinType()} {")
        appendLine("                val result = invokeNative(")
        appendConvertedArguments(callback.parameters, fromCarrier = false, indent = "                    ")
        appendLine("                )")
        appendLine("                return ${fromCarrierExpression(callback.returnType, "result")}")
        appendLine("            }")
        appendLine()
        append("            override fun invokeNative(")
        appendParameters(callback.parameters, apiTypes = false, multilineIndent = "                ")
        append("): ${carrierType(callback.returnType)} = funcHandle.invokeExact(")
        if (callback.parameters.isEmpty()) {
            append(") as ${carrierType(callback.returnType)}")
            appendLine()
        } else {
            appendLine()
            callback.parameters.forEach { appendLine("                ${escapeIdentifier(it.name)},") }
            appendLine("            ) as ${carrierType(callback.returnType)}")
        }
        appendLine("        }")
        appendLine("    }")
        appendLine("}")
    }

    private fun StringBuilder.appendParameters(
        parameters: List<SDLParameter>,
        apiTypes: Boolean,
        multilineIndent: String = "        ",
    ) {
        if (parameters.isEmpty()) return
        appendLine()
        parameters.forEach { parameter ->
            val type = if (apiTypes) parameter.type.kotlinType() else carrierType(parameter.type)
            appendLine("$multilineIndent${escapeIdentifier(parameter.name)}: $type,")
        }
        append(multilineIndent.dropLast(4))
    }

    private fun StringBuilder.appendConvertedArguments(
        parameters: List<SDLParameter>,
        fromCarrier: Boolean,
        indent: String,
    ) {
        parameters.forEach { parameter ->
            val name = escapeIdentifier(parameter.name)
            val expression = if (fromCarrier) fromCarrierExpression(parameter.type, name)
            else toCarrierExpression(parameter.type, name)
            appendLine("$indent$expression,")
        }
    }

    private fun carrierType(type: SDLType): String = when (type) {
        SDLType.Void -> "Unit"
        is SDLType.Scalar -> type.kind.carrierType
        is SDLType.Pointer -> "Long"
        is SDLType.Aggregate -> error("Aggregate callbacks are unsupported: ${type.name}")
    }

    private fun toCarrierExpression(type: SDLType, expression: String): String = when (type) {
        SDLType.Void -> expression
        is SDLType.Scalar -> type.kind.toCarrier(expression)
        is SDLType.Pointer -> "NPointer.toNativeData($expression)"
        is SDLType.Aggregate -> error("Aggregate callbacks are unsupported: ${type.name}")
    }

    private fun fromCarrierExpression(type: SDLType, expression: String): String = when (type) {
        SDLType.Void -> expression
        is SDLType.Scalar -> type.kind.fromCarrier(expression)
        is SDLType.Pointer -> "NPointer.fromNativeData<${pointerElementType(type)}>($expression)"
        is SDLType.Aggregate -> error("Aggregate callbacks are unsupported: ${type.name}")
    }

    private fun pointerElementType(type: SDLType.Pointer): String {
        if (type.depth > 1) return SDLType.Pointer(type.pointee, type.depth - 1).kotlinType()
        return when (type.pointee) {
            null -> "NChar"
            "char" -> "NChar"
            "wchar_t" -> "NUInt16"
            else -> type.pointee
        }
    }

    private fun descriptor(type: SDLType): String = when (type) {
        SDLType.Void -> "null"
        is SDLType.Scalar -> type.kind.descriptor
        is SDLType.Pointer -> "NPointer"
        is SDLType.Aggregate -> type.name
    }

    private fun escapeIdentifier(name: String): String = if (name in KOTLIN_KEYWORDS) "`$name`" else name

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun write(packageDir: Path, name: String, content: String): Path =
        packageDir.resolve(name).also { it.writeText(content) }

    private const val FILE_HEADER = """@file:Suppress("FunctionName", "ObjectPropertyName", "unused")"""
    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
        "interface", "is", "null", "object", "package", "return", "super", "this", "throw", "true",
        "try", "typealias", "typeof", "val", "var", "when", "while",
    )
}
