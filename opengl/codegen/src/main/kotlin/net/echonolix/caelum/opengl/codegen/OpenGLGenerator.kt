package net.echonolix.caelum.opengl.codegen

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

internal object OpenGLGenerator {
    private const val PACKAGE_NAME = "net.echonolix.caelum.opengl"
    private const val BINDINGS_FILE_NAME = "GLBindings.kt"
    private const val INITIALIZER_CHUNK_SIZE = 64
    private val LEGACY_GENERATED_FILE_NAMES = setOf("GL33Bindings.kt")
    private val glClass = ClassName(PACKAGE_NAME, "GL")
    private val capabilitiesClass = ClassName(PACKAGE_NAME, "GLCapabilities")
    private val functionProviderClass = ClassName(PACKAGE_NAME, "GLFunctionProvider")
    private val apiHelperClass = ClassName("net.echonolix.caelum", "APIHelper")
    private val functionDescriptorClass = FunctionDescriptor::class.asClassName()
    private val valueLayoutClass = ValueLayout::class.asClassName()

    fun generate(registry: GlRegistry, outputDir: Path): Set<Path> {
        val commands = registry.commands.values.sortedBy(GlCommand::name)
        val commandIndices = commands.mapIndexed { index, command -> command.name to index }.toMap()
        val ownerFiles = registry.owners.map { owner ->
            require(owner.fileName == openGlOwnerFileName(owner.name)) {
                "Invalid OpenGL owner filename ${owner.fileName} for ${owner.name}"
            }
            owner.fileName
        }
        val generatedFileNames = ownerFiles + BINDINGS_FILE_NAME
        require(generatedFileNames.size == generatedFileNames.map { it.lowercase(Locale.ROOT) }.toSet().size) {
            "Duplicate generated OpenGL relative path"
        }

        val written = linkedSetOf<Path>()
        registry.owners.forEach { owner ->
            written.add(ownerFile(owner, registry, commandIndices).writeTo(outputDir))
        }
        written.add(bindingsFile(commands).writeTo(outputDir))
        deleteStaleGeneratedFiles(outputDir)
        return written
    }

    private fun ownerFile(
        owner: GlOwner,
        registry: GlRegistry,
        commandIndices: Map<String, Int>,
    ): FileSpec =
        FileSpec.builder(PACKAGE_NAME, owner.fileName.removeSuffix(".kt"))
            .indent("    ")
            .addFileComment("OpenGL declarations owned by %L.", owner.name)
            .apply {
                owner.declarationEnumNames.forEach { name ->
                    addProperty(registry.enums.getValue(name).property())
                }
                owner.declarationCommandNames.forEach { name ->
                    addFunction(registry.commands.getValue(name).function(commandIndices.getValue(name)))
                }
            }
            .build()

    private fun bindingsFile(commands: List<GlCommand>): FileSpec =
        FileSpec.builder(PACKAGE_NAME, BINDINGS_FILE_NAME.removeSuffix(".kt"))
            .indent("    ")
            .addFileComment(
                "Shared OpenGL binding metadata. In FUNCTION_GL_HANDLE_ARB_MASKS, bit 0 marks the return type and bit n + 1 marks parameter n.",
            )
            .apply {
                addChunkedArray(
                    propertyName = "FUNCTION_NAMES",
                    elementType = STRING,
                    values = commands,
                    transform = { CodeBlock.of("%S", it.name) },
                )
                addChunkedArray(
                    propertyName = "FUNCTION_DESCRIPTORS",
                    elementType = functionDescriptorClass,
                    values = commands,
                    transform = { it.descriptor() },
                )
                addChunkedLongArray(
                    propertyName = "FUNCTION_GL_HANDLE_ARB_MASKS",
                    values = commands.map { it.glHandleArbMask() },
                )
            }
            .addFunction(createCapabilitiesFunction())
            .build()

    private fun GlEnum.property(): PropertySpec =
        PropertySpec.builder(name, if (bitWidth == 64) LONG else INT)
            .addModifiers(KModifier.PUBLIC, KModifier.CONST)
            .initializer(if (bitWidth == 64) CodeBlock.of("%LL", value) else CodeBlock.of("%L", value.toInt()))
            .build()

    private fun GlCommand.function(index: Int): FunSpec {
        val parameters = parameters.map { parameter ->
            ParameterSpec.builder(parameter.name, parameter.carrier.kotlinType).build()
        }
        val returnType = returnCarrier?.kotlinType ?: UNIT
        return FunSpec.builder(name)
            .addModifiers(KModifier.PUBLIC)
            .addParameters(parameters)
            .returns(returnType)
            .addStatement(
                "val function = %T.getCapabilities().functions[%L] ?: throw %T(%S)",
                glClass,
                index,
                UnsupportedOperationException::class,
                "OpenGL function $name is unavailable",
            )
            .addStatement("return function.invokeExact(%L) as %T", invocationArguments(parameters), returnType)
            .build()
    }

    private fun invocationArguments(parameters: List<ParameterSpec>): CodeBlock = CodeBlock.builder()
        .apply {
            parameters.forEachIndexed { index, parameter ->
                if (index > 0) add(", ")
                add("%N", parameter)
            }
        }
        .build()

    private fun GlCommand.descriptor(): CodeBlock = CodeBlock.builder()
        .apply {
            if (returnCarrier == null) {
                add("%T.ofVoid(", functionDescriptorClass)
            } else {
                add("%T.of(%T.%L", functionDescriptorClass, valueLayoutClass, returnCarrier.layoutName)
                if (parameters.isNotEmpty()) add(", ")
            }
            parameters.forEachIndexed { index, parameter ->
                if (index > 0) add(", ")
                add("%T.%L", valueLayoutClass, parameter.carrier.layoutName)
            }
            add(")")
        }
        .build()

    private fun createCapabilitiesFunction(): FunSpec = FunSpec.builder("createGLCapabilities")
        .addModifiers(KModifier.INTERNAL)
        .addParameter("provider", functionProviderClass)
        .returns(capabilitiesClass)
        .addStatement("val macOs = isMacOs()")
        .beginControlFlow("val addresses = %T(FUNCTION_NAMES.size) { index ->", LongArray::class)
        .addStatement("provider.getFunctionAddress(FUNCTION_NAMES[index])")
        .endControlFlow()
        .addStatement("val requiredIndex = FUNCTION_NAMES.binarySearch(%S)", "glGetString")
        .addStatement("check(requiredIndex >= 0) { %S }", "Generated OpenGL metadata is missing glGetString")
        .addStatement(
            "require(addresses[requiredIndex] !in -1L..3L) { %S }",
            "OpenGL function glGetString is unavailable; an OpenGL context must be current",
        )
        .beginControlFlow("val functions = %T(FUNCTION_NAMES.size) { index ->", Array::class)
        .addStatement("val address = addresses[index]")
        .beginControlFlow("if (address in -1L..3L)")
        .addStatement("null")
        .nextControlFlow("else")
        .addStatement("val mask = FUNCTION_GL_HANDLE_ARB_MASKS[index]")
        .addStatement(
            "val descriptor = glHandleArbFunctionDescriptor(FUNCTION_DESCRIPTORS[index], mask, macOs)",
        )
        .addStatement(
            "val function = %T.downcallHandleOf(%T.ofAddress(address), descriptor)!!",
            apiHelperClass,
            MemorySegment::class,
        )
        .addStatement("adaptGlHandleArbFunction(function, mask, macOs)")
        .endControlFlow()
        .endControlFlow()
        .addStatement("return %T(functions)", capabilitiesClass)
        .build()

    private fun FileSpec.Builder.addChunkedArray(
        propertyName: String,
        elementType: TypeName,
        values: List<GlCommand>,
        transform: (GlCommand) -> CodeBlock,
    ) {
        val helperNames = values.chunked(INITIALIZER_CHUNK_SIZE).mapIndexed { index, chunk ->
            "${propertyName.lowercase()}Chunk$index".also { helperName ->
                addFunction(
                    FunSpec.builder(helperName)
                        .addModifiers(KModifier.PRIVATE)
                        .returns(Array::class.asClassName().parameterizedBy(elementType))
                        .addStatement("return %L", arrayInitializer(chunk, transform))
                        .build(),
                )
            }
        }
        addProperty(
            PropertySpec.builder(
                propertyName,
                Array::class.asClassName().parameterizedBy(elementType),
                KModifier.PRIVATE,
            ).initializer(chunkedArrayInitializer(helperNames)).build(),
        )
    }

    private fun FileSpec.Builder.addChunkedLongArray(propertyName: String, values: List<Long>) {
        val helperNames = values.chunked(INITIALIZER_CHUNK_SIZE).mapIndexed { index, chunk ->
            "${propertyName.lowercase()}Chunk$index".also { helperName ->
                addFunction(
                    FunSpec.builder(helperName)
                        .addModifiers(KModifier.PRIVATE)
                        .returns(LongArray::class)
                        .addStatement(
                            "return longArrayOf(%L)",
                            chunk.joinToCode { CodeBlock.of("%LL", it) },
                        )
                        .build(),
                )
            }
        }
        addProperty(
            PropertySpec.builder(propertyName, LongArray::class, KModifier.PRIVATE)
                .initializer("longArrayOf(%L)", helperNames.joinToCode { CodeBlock.of("*%N()", it) })
                .build(),
        )
    }

    private fun chunkedArrayInitializer(helperNames: List<String>): CodeBlock =
        CodeBlock.of("arrayOf(%L)", helperNames.joinToCode { CodeBlock.of("*%N()", it) })

    private fun <T> List<T>.joinToCode(transform: (T) -> CodeBlock): CodeBlock = CodeBlock.builder()
        .apply {
            forEachIndexed { index, value ->
                if (index > 0) add(", ")
                add("%L", transform(value))
            }
        }
        .build()

    private fun <T> arrayInitializer(values: List<T>, transform: (T) -> CodeBlock): CodeBlock =
        CodeBlock.builder()
            .add("arrayOf(\n")
            .indent()
            .apply {
                values.forEach { value -> add("%L,\n", transform(value)) }
            }
            .unindent()
            .add(")")
            .build()

    private fun GlCommand.glHandleArbMask(): Long {
        require(parameters.size < Long.SIZE_BITS - 1) { "Too many parameters in $name" }
        var mask = if (returnAbi == GlAbi.GL_HANDLE_ARB) 1L else 0L
        parameters.forEachIndexed { index, parameter ->
            if (parameter.abi == GlAbi.GL_HANDLE_ARB) mask = mask or (1L shl (index + 1))
        }
        return mask
    }

    private fun deleteStaleGeneratedFiles(outputDir: Path) {
        val packageDir = outputDir.resolve(PACKAGE_NAME.replace('.', '/'))
        if (!Files.isDirectory(packageDir)) return
        LEGACY_GENERATED_FILE_NAMES.forEach { Files.deleteIfExists(packageDir.resolve(it)) }
    }

    private val GlCarrier.kotlinType: TypeName
        get() = when (this) {
            GlCarrier.BOOLEAN -> BOOLEAN
            GlCarrier.BYTE -> BYTE
            GlCarrier.SHORT -> SHORT
            GlCarrier.INT -> INT
            GlCarrier.LONG, GlCarrier.ADDRESS -> LONG
            GlCarrier.FLOAT -> FLOAT
            GlCarrier.DOUBLE -> DOUBLE
        }

    private val GlCarrier.layoutName: String
        get() = when (this) {
            GlCarrier.BOOLEAN -> "JAVA_BOOLEAN"
            GlCarrier.BYTE -> "JAVA_BYTE"
            GlCarrier.SHORT -> "JAVA_SHORT"
            GlCarrier.INT -> "JAVA_INT"
            GlCarrier.LONG, GlCarrier.ADDRESS -> "JAVA_LONG"
            GlCarrier.FLOAT -> "JAVA_FLOAT"
            GlCarrier.DOUBLE -> "JAVA_DOUBLE"
        }
}
