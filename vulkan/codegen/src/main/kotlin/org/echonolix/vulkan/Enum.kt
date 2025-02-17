package org.echonolix.vulkan

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.echonolix.ktffi.CBasicType
import org.echonolix.vulkan.schema.Element
import org.echonolix.vulkan.schema.PatchedRegistry
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.collections.get

enum class EnumKind(val cname: ClassName, val dataType: CBasicType) {
    ENUM(VKFFI.vkEnumsCname, CBasicType.int32_t),
    FLAG32(VKFFI.vkFlags32CNAME, CBasicType.int32_t),
    FLAG64(VKFFI.vkFlags64CNAME, CBasicType.int64_t)
}

context(genCtx: FFIGenContext)
fun genEnums(registry: PatchedRegistry) {
    genCtx.newFile(
        FileSpec.builder(VKFFI.vkEnumsCname)
            .addType(
                TypeSpec.interfaceBuilder(VKFFI.vkEnumBaseCname)
                    .addTypeVariable(TypeVariableName("T"))
                    .addProperty(
                        PropertySpec.builder("value", TypeVariableName("T"))
                            .build()
                    )
                    .addModifiers(KModifier.SEALED)
                    .build()
            )
            .addType(
                TypeSpec.interfaceBuilder(VKFFI.vkEnumsCname)
                    .addModifiers(KModifier.SEALED)
                    .addSuperinterface(VKFFI.vkEnumBaseCname.parameterizedBy(Int::class.asTypeName()))
                    .addProperty(
                        PropertySpec.builder("value", Int::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .build()
                    )
                    .build()
            )
            .addType(
                TypeSpec.interfaceBuilder(VKFFI.vkFlags32CNAME)
                    .addModifiers(KModifier.SEALED)
                    .addSuperinterface(VKFFI.vkEnumBaseCname.parameterizedBy(Int::class.asTypeName()))
                    .addProperty(
                        PropertySpec.builder("value", Int::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .build()
                    )
                    .build()
            )
            .addType(
                TypeSpec.interfaceBuilder(VKFFI.vkFlags64CNAME)
                    .addModifiers(KModifier.SEALED)
                    .addSuperinterface(VKFFI.vkEnumBaseCname.parameterizedBy(Long::class.asTypeName()))
                    .addProperty(
                        PropertySpec.builder("value", Long::class)
                            .addModifiers(KModifier.OVERRIDE)
                            .build()
                    )
                    .build()
            )
    )

    genCtx.newFile(
        FileSpec.builder(VKFFI.enumPackageName, "Constants")
            .apply {
                registry.constantElements.values.forEach {
                    addProperty(
                        PropertySpec.builder(it.name, it.type.typeName)
                            .tryAddKdoc(it)
                            .apply {
                                if (!it.value.toString().contains(".inv()")) {
                                    addModifiers(KModifier.CONST)
                                }
                            }
                            .initializer(it.value)
                            .build()
                    )
                }
            }
    )

    val skipped = setOf("VkFormat")

    registry.enumTypes.values.asSequence()
        .filter { it.name !in skipped }
        .forEach { enumType ->
            genCtx.newFile(FileSpec.builder(VKFFI.enumPackageName, enumType.name))
                .genEnumType(enumType)
        }


    registry.flagTypes.values.asSequence()
        .filter { it.name !in skipped }
        .forEach { flagType ->
            val flagBitType = registry.flagBitTypes[flagType.bitType]
            val thisCname = ClassName(VKFFI.enumPackageName, flagType.name)
            val enumKind = if (flagBitType?.type == CBasicType.uint64_t) EnumKind.FLAG64 else EnumKind.FLAG32
            genCtx.newFile(
                FileSpec.builder(thisCname)
                    .addType(
                        TypeSpec.classBuilder(thisCname)
                            .tryAddKdoc(flagType)
                            .apply {
                                if (flagBitType != null) {
                                    tryAddKdoc(flagBitType)
                                    addVkEnum(enumKind)
                                } else {
                                    addVkEnum(enumKind)
                                }
                            }
                            .addAnnotation(JvmInline::class)
                            .addModifiers(KModifier.VALUE)
                            .addFunction(
                                FunSpec.builder("plus")
                                    .addModifiers(KModifier.OPERATOR)
                                    .addParameter("other", thisCname)
                                    .returns(thisCname)
                                    .addStatement("return %T(value or other.value)", thisCname)
                                    .build()
                            )
                            .addFunction(
                                FunSpec.builder("minus")
                                    .addModifiers(KModifier.OPERATOR)
                                    .addParameter("other", thisCname)
                                    .returns(thisCname)
                                    .addStatement("return %T(value and other.value.inv())", thisCname)
                                    .build()
                            )
                            .addFunction(
                                FunSpec.builder("contains")
                                    .addParameter("other", thisCname)
                                    .returns(Boolean::class)
                                    .addStatement("return (value and other.value == other.value)")
                                    .build()
                            )
                            .addType(
                                TypeSpec.companionObjectBuilder()
                                    .apply {
                                        flagBitType?.entries?.values?.let { flagBitTypes ->
                                            flagBitTypes.forEach {
                                                addProperty(
                                                    PropertySpec.builder(it.fixedName, thisCname)
                                                        .initializer(
                                                            "%T(%L)",
                                                            thisCname,
                                                            it.valueCode.toString().replace("U", "")
                                                        )
                                                        .tryAddKdoc(it)
                                                        .build()
                                                )
                                            }
                                            if (flagBitTypes.none { it.valueCode.toString().startsWith("0") }) {
                                                addProperty(
                                                    PropertySpec.builder("EMPTY", thisCname)
                                                        .initializer("%T(0)", thisCname)
                                                        .build()
                                                )
                                            }
                                        }
                                    }
                                    .addFunction(
                                        FunSpec.builder("toInt")
                                            .addAnnotation(JvmStatic::class)
                                            .addParameter("value", thisCname)
                                            .returns(enumKind.dataType.kotlinType)
                                            .addStatement("return value.value")
                                            .build()
                                    )
                                    .addFunction(
                                        FunSpec.builder("fromInt")
                                            .addAnnotation(JvmStatic::class)
                                            .addParameter("value", enumKind.dataType.kotlinType)
                                            .returns(thisCname)
                                            .addStatement("return %T(value)", thisCname)
                                            .build()
                                    )
                                    .addMethodHandleFields(thisCname, enumKind)
                                    .build()
                            )
                            .build()
                    )
            )
        }
}

private fun TypeSpec.Builder.addVkEnum(kind: EnumKind): TypeSpec.Builder {
    addSuperinterface(kind.cname)

    primaryConstructor(
        FunSpec.constructorBuilder()
            .addParameter("value", kind.dataType.typeName)
            .build()
    )
    addProperty(
        PropertySpec.builder("value", kind.dataType.typeName)
            .addModifiers(KModifier.OVERRIDE)
            .initializer("value")
            .build()
    )
    return this
}

private fun TypeSpec.Builder.addMethodHandleFields(thisCname: ClassName, enumKind: EnumKind): TypeSpec.Builder {
    val type = enumKind.dataType.kotlinType
    addProperty(
        PropertySpec.builder("\$fromIntMH", MethodHandle::class)
            .addModifiers(KModifier.PRIVATE)
            .initializer(
                CodeBlock.builder()
                    .add(
                        "%T.lookup().findStatic(%T::class.java, %S, %T.methodType(%T::class.java, %T::class.javaPrimitiveType))",
                        VKFFI.methodHandlesCname,
                        thisCname,
                        "fromInt",
                        VKFFI.methodTypeCname,
                        thisCname,
                        type
                    )
                    .build()
            )
            .build()
    )
    addProperty(
        PropertySpec.builder("\$toIntMH", MethodHandle::class)
            .addModifiers(KModifier.PRIVATE)
            .initializer(
                CodeBlock.builder()
                    .add(
                        "%T.lookup().findStatic(%T::class.java, %S, %T.methodType(%T::class.javaPrimitiveType, %T::class.java))",
                        VKFFI.methodHandlesCname,
                        thisCname,
                        "toInt",
                        VKFFI.methodTypeCname,
                        type,
                        thisCname
                    )
                    .build()
            )
            .build()
    )
    return this
}

private fun FileSpec.Builder.genEnumType(enumType: Element.EnumType) {
    val thisCname = ClassName(VKFFI.enumPackageName, enumType.name)
            addType(
                TypeSpec.enumBuilder(thisCname)
                    .tryAddKdoc(enumType)
                    .addVkEnum(EnumKind.ENUM)
                    .apply {
                        enumType.entries.values.forEach { entry ->
                            addEnumConstant(
                                entry.fixedName,
                                TypeSpec.anonymousClassBuilder()
                                    .tryAddKdoc(entry)
                                    .superclass(ClassName(VKFFI.enumPackageName, enumType.name))
                                    .addSuperclassConstructorParameter(entry.valueCode)
                                    .build()
                            )
                        }
                    }
                    .addType(
                        TypeSpec.companionObjectBuilder()
                            .addFunction(
                                FunSpec.builder("fromInt")
                                    .addAnnotation(JvmStatic::class)
                                    .addParameter(
                                        "value",
                                        EnumKind.ENUM.dataType.kotlinType
                                    )
                                    .returns(thisCname)
                                    .apply {
                                        addCode(
                                            CodeBlock.builder()
                                                .beginControlFlow("return when (value)")
                                                .apply {
                                                    enumType.entries.values.forEach { entry ->
                                                        addStatement(
                                                            "%L -> %T.%N",
                                                            entry.valueNum,
                                                            thisCname,
                                                            entry.fixedName
                                                        )
                                                    }
                                                }
                                                .addStatement("else -> throw IllegalArgumentException(\"Unknown value: \$value\")")
                                                .endControlFlow()
                                                .build()
                                        )
                                    }
                                    .build()
                            )
                            .addFunction(
                                FunSpec.builder("toInt")
                                    .addAnnotation(JvmStatic::class)
                                    .addParameter("value", thisCname)
                                    .returns(EnumKind.ENUM.dataType.kotlinType)
                                    .addStatement("return value.value")
                                    .build()
                            )
                            .addMethodHandleFields(thisCname, EnumKind.ENUM)
                            .build()
                    )
                    .build()
            )
}