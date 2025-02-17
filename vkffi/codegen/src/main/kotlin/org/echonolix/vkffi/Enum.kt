package org.echonolix.vkffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.echonolix.vkffi.schema.PatchedRegistry
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.collections.get

enum class EnumKind(val cname: ClassName, val dataType: CBasicType) {
    ENUM(VKFFI.vkEnumsCName, CBasicType.int32_t),
    FLAG32(VKFFI.vkFlags32CNAME, CBasicType.int32_t),
    FLAG64(VKFFI.vkFlags64CNAME, CBasicType.int64_t)
}

context(genCtx: FFIGenContext)
fun genEnums(registry: PatchedRegistry) {
    genCtx.newFile(
        FileSpec.builder(VKFFI.vkEnumsCName)
            .addType(
                TypeSpec.interfaceBuilder(VKFFI.vkEnumBaseCName)
                    .addTypeVariable(TypeVariableName("T"))
                    .addProperty(
                        PropertySpec.builder("value", TypeVariableName("T"))
                            .build()
                    )
                    .addModifiers(KModifier.SEALED)
                    .build()
            )
            .addType(
                TypeSpec.interfaceBuilder(VKFFI.vkEnumsCName)
                    .addModifiers(KModifier.SEALED)
                    .addSuperinterface(VKFFI.vkEnumBaseCName.parameterizedBy(Int::class.asTypeName()))
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
                    .addSuperinterface(VKFFI.vkEnumBaseCName.parameterizedBy(Int::class.asTypeName()))
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
                    .addSuperinterface(VKFFI.vkEnumBaseCName.parameterizedBy(Long::class.asTypeName()))
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
    fun TypeSpec.Builder.addVkEnum(kind: EnumKind): TypeSpec.Builder {
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

    val skipped = setOf("VkFormat")
    val methodHandlesCName = MethodHandles::class.asClassName()
    val methodTypeCName = MethodType::class.asClassName()

    fun TypeSpec.Builder.addMethodHandleFields(thisCName: ClassName, enumKind: EnumKind): TypeSpec.Builder {
        val type = enumKind.dataType.kotlinType
        addProperty(
            PropertySpec.builder("\$fromIntMH", MethodHandle::class)
                .addModifiers(KModifier.PRIVATE)
                .initializer(
                    CodeBlock.builder()
                        .add(
                            "%T.lookup().findStatic(%T::class.java, %S, %T.methodType(%T::class.java, %T::class.javaPrimitiveType))",
                            methodHandlesCName,
                            thisCName,
                            "fromInt",
                            methodTypeCName,
                            thisCName,
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
                            methodHandlesCName,
                            thisCName,
                            "toInt",
                            methodTypeCName,
                            type,
                            thisCName
                        )
                        .build()
                )
                .build()
        )
        return this
    }

    registry.enumTypes.values.asSequence()
        .filter { it.name !in skipped }
        .forEach { enumType ->
            val thisCName = ClassName(VKFFI.enumPackageName, enumType.name)
            genCtx.newFile(
                FileSpec.builder(thisCName)
                    .addType(
                        TypeSpec.enumBuilder(thisCName)
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
                                            .returns(thisCName)
                                            .apply {
                                                addCode(
                                                    CodeBlock.builder()
                                                        .beginControlFlow("return when (value)")
                                                        .apply {
                                                            enumType.entries.values.forEach { entry ->
                                                                addStatement(
                                                                    "%L -> %T.%N",
                                                                    entry.valueNum,
                                                                    thisCName,
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
                                            .addParameter("value", thisCName)
                                            .returns(EnumKind.ENUM.dataType.kotlinType)
                                            .addStatement("return value.value")
                                            .build()
                                    )
                                    .addMethodHandleFields(thisCName, EnumKind.ENUM)
                                    .build()
                            )
                            .build()
                    )
            )
        }


    registry.flagTypes.values.asSequence()
        .filter { it.name !in skipped }
        .forEach { flagType ->
            val flagBitType = registry.flagBitTypes[flagType.bitType]
            val thisCName = ClassName(VKFFI.enumPackageName, flagType.name)
            val enumKind = if (flagBitType?.type == CBasicType.uint64_t) EnumKind.FLAG64 else EnumKind.FLAG32
            genCtx.newFile(
                FileSpec.builder(thisCName)
                    .addType(
                        TypeSpec.classBuilder(thisCName)
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
                                    .addParameter("other", thisCName)
                                    .returns(thisCName)
                                    .addStatement("return %T(value or other.value)", thisCName)
                                    .build()
                            )
                            .addFunction(
                                FunSpec.builder("minus")
                                    .addModifiers(KModifier.OPERATOR)
                                    .addParameter("other", thisCName)
                                    .returns(thisCName)
                                    .addStatement("return %T(value and other.value.inv())", thisCName)
                                    .build()
                            )
                            .addFunction(
                                FunSpec.builder("contains")
                                    .addParameter("other", thisCName)
                                    .returns(Boolean::class)
                                    .addStatement("return (value and other.value == other.value)")
                                    .build()
                            )
                            .addType(
                                TypeSpec.companionObjectBuilder()
                                    .apply {
                                        flagBitType?.entries?.values?.forEach {
                                            addProperty(
                                                PropertySpec.builder(it.fixedName, thisCName)
                                                    .initializer(
                                                        "%T(%L)",
                                                        thisCName,
                                                        it.valueCode.toString().replace("U", "")
                                                    )
                                                    .tryAddKdoc(it)
                                                    .build()
                                            )
                                        }
                                        addProperty(
                                            PropertySpec.builder("EMPTY", thisCName)
                                                .initializer("%T(0)", thisCName)
                                                .build()
                                        )
                                    }
                                    .addFunction(
                                        FunSpec.builder("toInt")
                                            .addAnnotation(JvmStatic::class)
                                            .addParameter("value", thisCName)
                                            .returns(enumKind.dataType.kotlinType)
                                            .addStatement("return value.value")
                                            .build()
                                    )
                                    .addFunction(
                                        FunSpec.builder("fromInt")
                                            .addAnnotation(JvmStatic::class)
                                            .addParameter("value", enumKind.dataType.kotlinType)
                                            .returns(thisCName)
                                            .addStatement("return %T(value)", thisCName)
                                            .build()
                                    )
                                    .addMethodHandleFields(thisCName, enumKind)
                                    .build()
                            )
                            .build()
                    )
            )
        }
}