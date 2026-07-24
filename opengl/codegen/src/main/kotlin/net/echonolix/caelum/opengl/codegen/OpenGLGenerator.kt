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
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.file.Path

internal object OpenGLGenerator {
    private const val PACKAGE_NAME = "net.echonolix.caelum.opengl"
    private val glClass = ClassName(PACKAGE_NAME, "GL")
    private val capabilitiesClass = ClassName(PACKAGE_NAME, "GLCapabilities")
    private val functionProviderClass = ClassName(PACKAGE_NAME, "GLFunctionProvider")
    private val apiHelperClass = ClassName("net.echonolix.caelum", "APIHelper")
    private val functionDescriptorClass = FunctionDescriptor::class.asClassName()
    private val valueLayoutClass = ValueLayout::class.asClassName()

    fun generate(registry: GlRegistry, outputDir: Path): Set<Path> {
        val commands = registry.commands.values.sortedBy(GlCommand::name)
        val enums = registry.enums.values.sortedBy(GlEnum::name)
        return linkedSetOf(
            apiFile(commands, enums).writeTo(outputDir),
            bindingsFile(commands).writeTo(outputDir),
        )
    }

    private fun apiFile(commands: List<GlCommand>, enums: List<GlEnum>): FileSpec =
        FileSpec.builder(PACKAGE_NAME, "GL33")
            .indent("    ")
            .addType(
                TypeSpec.objectBuilder("GL33")
                    .addModifiers(KModifier.PUBLIC)
                    .apply {
                        enums.forEach { enum -> addProperty(enum.property()) }
                        commands.forEachIndexed { index, command -> addFunction(command.function(index)) }
                    }
                    .build(),
            )
            .build()

    private fun bindingsFile(commands: List<GlCommand>): FileSpec =
        FileSpec.builder(PACKAGE_NAME, "GL33Bindings")
            .indent("    ")
            .addProperty(
                PropertySpec.builder(
                    "FUNCTION_NAMES",
                    Array::class.asClassName().parameterizedBy(STRING),
                    KModifier.PRIVATE,
                ).initializer(arrayInitializer(commands) { CodeBlock.of("%S", it.name) }).build(),
            )
            .addProperty(
                PropertySpec.builder(
                    "FUNCTION_DESCRIPTORS",
                    Array::class.asClassName().parameterizedBy(functionDescriptorClass),
                    KModifier.PRIVATE,
                ).initializer(arrayInitializer(commands) { it.descriptor() }).build(),
            )
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
        val invocation = CodeBlock.builder()
            .add("%T.getCapabilities().functions[%L].invokeExact(", glClass, index)
            .apply {
                parameters.forEachIndexed { parameterIndex, parameter ->
                    if (parameterIndex > 0) add(", ")
                    add("%N", parameter)
                }
            }
            .add(")")
            .build()
        return FunSpec.builder(name)
            .addModifiers(KModifier.PUBLIC)
            .addAnnotation(JvmStatic::class)
            .addParameters(parameters)
            .returns(returnType)
            .addStatement("return %L as %T", invocation, returnType)
            .build()
    }

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
        .beginControlFlow("val addresses = %T(FUNCTION_NAMES.size) { index ->", LongArray::class)
        .beginControlFlow("provider.getFunctionAddress(FUNCTION_NAMES[index]).also { address ->")
        .addStatement(
            "require(address !in -1L..3L) { %S + address + %S + FUNCTION_NAMES[index] + %S }",
            "Invalid OpenGL function address ",
            " for ",
            "; an OpenGL context must be current",
        )
        .endControlFlow()
        .endControlFlow()
        .beginControlFlow("val functions = %T(FUNCTION_NAMES.size) { index ->", Array::class)
        .addStatement(
            "%T.downcallHandleOf(%T.ofAddress(addresses[index]), FUNCTION_DESCRIPTORS[index])!!",
            apiHelperClass,
            MemorySegment::class,
        )
        .endControlFlow()
        .addStatement("return %T(functions)", capabilitiesClass)
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
