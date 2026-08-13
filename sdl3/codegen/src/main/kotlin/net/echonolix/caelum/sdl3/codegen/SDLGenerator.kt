package net.echonolix.caelum.sdl3.codegen

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

internal object SDLGenerator {
    private const val PACKAGE_NAME = "net.echonolix.caelum.sdl3"
    private const val FUNCTIONS_PER_FILE = 80

    fun generate(registry: SDLRegistry, outputDir: Path): Set<Path> {
        require(registry.functions.size >= 1_200) {
            "SDL parser only found ${registry.functions.size} supported functions; expected at least 1,200"
        }
        val packageDir = outputDir.resolve(PACKAGE_NAME.replace('.', '/')).createDirectories()
        val written = linkedSetOf<Path>()
        written.add(write(packageDir, "SDLFunctions.kt", inventoryFile(registry)))
        registry.functions.chunked(FUNCTIONS_PER_FILE).forEachIndexed { index, functions ->
            written.add(write(packageDir, "SDLBindings${index.toString().padStart(2, '0')}.kt", bindingsFile(functions, registry)))
        }
        return written
    }

    private fun typesFile(registry: SDLRegistry): String = buildString {
        appendLine(FILE_HEADER)
        appendLine("package $PACKAGE_NAME")
        appendLine()
        appendLine("import java.lang.foreign.MemoryLayout")
        appendLine("import java.lang.foreign.ValueLayout")
        appendLine("import net.echonolix.caelum.NInt")
        appendLine("import net.echonolix.caelum.NStruct")
        appendLine("import net.echonolix.caelum.NUnion")
        appendLine()
        appendLine("public object SDL_Rect : NStruct.Impl<SDL_Rect>(")
        appendLine("    NInt.layout.withName(\"x\"),")
        appendLine("    NInt.layout.withName(\"y\"),")
        appendLine("    NInt.layout.withName(\"w\"),")
        appendLine("    NInt.layout.withName(\"h\"),")
        appendLine(")")
        appendLine()
        appendLine("public object SDL_Event : NUnion.Impl<SDL_Event>(")
        appendLine("    MemoryLayout.sequenceLayout(128, ValueLayout.JAVA_BYTE)")
        appendLine("        .withByteAlignment(8)")
        appendLine("        .withName(\"padding\"),")
        appendLine(")")
        appendLine()
        val pointerPointees = registry.functions.asSequence()
            .flatMap { sequenceOf(it.returnType) + it.parameters.asSequence().map(SDLParameter::type) }
            .filterIsInstance<SDLType.Pointer>()
            .mapNotNull(SDLType.Pointer::pointee)
            .filter { it.startsWith("SDL_") }
            .filterNot { it in setOf("SDL_Rect", "SDL_Event") }
            .filterNot { registry.namedTypes[it]?.kind == SDLNamedKind.FUNCTION_POINTER }
            .toSortedSet()
        pointerPointees.forEach { name ->
            appendLine("public object $name : NStruct.Impl<$name>()")
        }
    }

    private fun constantsFile(registry: SDLRegistry): String = buildString {
        appendLine(FILE_HEADER)
        appendLine("package $PACKAGE_NAME")
        appendLine()
        registry.constants.forEach { constant ->
            appendLine("public const val ${constant.name}: ${constant.kind.kotlinType} = ${constant.literal()}")
        }
    }

    private fun inventoryFile(registry: SDLRegistry): String = buildString {
        appendLine(FILE_HEADER)
        appendLine("package $PACKAGE_NAME")
        appendLine()
        appendLine("import java.lang.foreign.FunctionDescriptor")
        appendLine("import java.lang.invoke.MethodHandle")
        appendLine("import java.util.concurrent.atomic.AtomicReferenceArray")
        appendLine("import net.echonolix.caelum.APIHelper.functionDescriptorOf")
        appendLine("import net.echonolix.caelum.NBool")
        appendLine("import net.echonolix.caelum.NDouble")
        appendLine("import net.echonolix.caelum.NFloat")
        appendLine("import net.echonolix.caelum.NInt")
        appendLine("import net.echonolix.caelum.NInt8")
        appendLine("import net.echonolix.caelum.NInt16")
        appendLine("import net.echonolix.caelum.NInt64")
        appendLine("import net.echonolix.caelum.NPointer")
        appendLine("import net.echonolix.caelum.NUInt8")
        appendLine("import net.echonolix.caelum.NUInt16")
        appendLine("import net.echonolix.caelum.NUInt32")
        appendLine("import net.echonolix.caelum.NUInt64")
        appendLine()
        appendLine("public data class SDLSkippedFunction(public val name: String, public val reason: String)")
        appendLine()
        appendLine("public object SDLFunctions {")
        appendLine("    @JvmField")
        appendLine("    public val names: List<String> = listOf(")
        registry.functions.forEach { appendLine("        \"${it.name}\",") }
        appendLine("    )")
        appendLine()
        appendLine("    @JvmField")
        appendLine("    public val descriptors: List<FunctionDescriptor> = listOf(")
        registry.functions.forEach { appendLine("        ${descriptor(it)},") }
        appendLine("    )")
        appendLine()
        appendLine("    @JvmField")
        appendLine("    public val skipped: List<SDLSkippedFunction> = listOf(")
        registry.skipped.forEach {
            appendLine("        SDLSkippedFunction(\"${it.name}\", \"${escape(it.reason)}\"),")
        }
        appendLine("    )")
        appendLine("}")
        appendLine()
        appendLine("internal object SDLFunctionHandles {")
        appendLine("    private val handles: AtomicReferenceArray<MethodHandle?> = AtomicReferenceArray(SDLFunctions.names.size)")
        appendLine()
        appendLine("    fun get(index: Int): MethodHandle {")
        appendLine("        handles.get(index)?.let { return it }")
        appendLine("        val created = SDL.downcallHandle(SDLFunctions.names[index], SDLFunctions.descriptors[index])")
        appendLine("        if (handles.compareAndSet(index, null, created)) return created")
        appendLine("        return handles.get(index)!!")
        appendLine("    }")
        appendLine("}")
    }

    private fun bindingsFile(functions: List<SDLFunction>, registry: SDLRegistry): String = buildString {
        appendLine(FILE_HEADER)
        appendLine("package $PACKAGE_NAME")
        appendLine()
        appendLine("import java.lang.foreign.MemorySegment")
        appendLine("import java.lang.foreign.SegmentAllocator")
        appendLine("import net.echonolix.caelum.*")
        appendLine("import net.echonolix.caelum.sdl3.functions.*")
        appendLine()
        functions.forEach { function ->
            append(function(function))
            appendLine()
            if (function.callbackParameters(registry).isNotEmpty()) {
                append(callbackOverload(function, registry))
                appendLine()
            }
        }
    }

    private fun callbackOverload(function: SDLFunction, registry: SDLRegistry): String = buildString {
        val callbackParameters = function.callbackParameters(registry).toSet()
        val allocatorParameter = if (function.returnType is SDLType.Aggregate) {
            listOf("allocator: SegmentAllocator")
        } else {
            emptyList()
        }
        val renderedParameters = allocatorParameter + function.parameters.map { parameter ->
            "${escapeIdentifier(parameter.name)}: ${overloadType(parameter, callbackParameters)}"
        }
        append("public fun ${function.name}(")
        if (renderedParameters.size <= 3) {
            append(renderedParameters.joinToString())
        } else {
            appendLine()
            renderedParameters.forEach { appendLine("    $it,") }
        }
        appendLine("): ${function.returnType.kotlinType()} = ${function.name}(")
        if (function.returnType is SDLType.Aggregate) appendLine("    allocator,")
        function.parameters.forEach { parameter ->
            val name = escapeIdentifier(parameter.name)
            val argument = if (parameter in callbackParameters) {
                val callbackName = (parameter.type as SDLType.Pointer).pointee
                "$callbackName.toPointer($name)"
            } else {
                name
            }
            appendLine("    $argument,")
        }
        appendLine(")")
    }

    private fun overloadType(parameter: SDLParameter, callbackParameters: Set<SDLParameter>): String =
        if (parameter in callbackParameters) (parameter.type as SDLType.Pointer).pointee!! else parameter.type.kotlinType()

    private fun SDLFunction.callbackParameters(registry: SDLRegistry): List<SDLParameter> = parameters.filter { parameter ->
        val type = parameter.type as? SDLType.Pointer ?: return@filter false
        type.depth == 1 && registry.namedTypes[type.pointee]?.kind == SDLNamedKind.FUNCTION_POINTER
    }

    private fun function(function: SDLFunction): String = buildString {
        val indexExpression = "SDLFunctions.names.binarySearch(\"${function.name}\")"
        val allocatorParameter = if (function.returnType is SDLType.Aggregate) {
            listOf("allocator: SegmentAllocator")
        } else {
            emptyList()
        }
        val renderedParameters = allocatorParameter + function.parameters.map {
            "${escapeIdentifier(it.name)}: ${it.type.kotlinType()}"
        }
        append("public fun ${function.name}(")
        if (renderedParameters.size <= 3) {
            append(renderedParameters.joinToString())
        } else {
            appendLine()
            renderedParameters.forEach { appendLine("    $it,") }
        }
        append("): ${function.returnType.kotlinType()} {")
        appendLine()
        append("    val result = SDLFunctionHandles.get($indexExpression).invokeExact(")
        if (function.returnType is SDLType.Aggregate || function.parameters.isNotEmpty()) {
            appendLine()
            if (function.returnType is SDLType.Aggregate) appendLine("        allocator,")
            function.parameters.forEach { parameter ->
                appendLine("        ${toCarrier(parameter)},")
            }
            append("    ")
        }
        append(")")
        when (val returnType = function.returnType) {
            SDLType.Void -> appendLine(" as Unit")
            is SDLType.Scalar -> appendLine(" as ${returnType.kind.carrierType}")
            is SDLType.Pointer -> appendLine(" as Long")
            is SDLType.Aggregate -> appendLine(" as MemorySegment")
        }
        when (val returnType = function.returnType) {
            SDLType.Void -> appendLine("    return result")
            is SDLType.Scalar -> appendLine("    return ${returnType.kind.fromCarrier("result")}")
            is SDLType.Pointer -> {
                val constructorType = if (returnType.kotlinType().contains("*")) "NPointer<NChar>" else returnType.kotlinType()
                appendLine("    return $constructorType(result)")
            }
            is SDLType.Aggregate -> appendLine("    return NValue(result.address())")
        }
        appendLine("}")
    }

    private fun toCarrier(parameter: SDLParameter): String = when (val type = parameter.type) {
        SDLType.Void -> error("Void parameter: ${parameter.name}")
        is SDLType.Scalar -> type.kind.toCarrier(escapeIdentifier(parameter.name))
        is SDLType.Pointer -> "NPointer.toNativeData(${escapeIdentifier(parameter.name)})"
        is SDLType.Aggregate -> {
            val name = escapeIdentifier(parameter.name)
            "MemorySegment.ofAddress($name._address).reinterpret(${type.name}.layout.byteSize())"
        }
    }

    private fun descriptor(function: SDLFunction): String {
        val types = buildList {
            add(descriptor(function.returnType))
            function.parameters.forEach { add(descriptor(it.type)) }
        }
        return "functionDescriptorOf(${types.joinToString()})"
    }

    private fun descriptor(type: SDLType): String = when (type) {
        SDLType.Void -> "null"
        is SDLType.Scalar -> type.kind.descriptor
        is SDLType.Pointer -> "NPointer"
        is SDLType.Aggregate -> type.name
    }

    private fun SDLConstant.literal(): String = when (kind) {
        SDLConstantKind.INT -> value.toInt().toString()
        SDLConstantKind.UINT -> "${value.toUInt()}u"
        SDLConstantKind.ULONG -> "${value}uL"
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
