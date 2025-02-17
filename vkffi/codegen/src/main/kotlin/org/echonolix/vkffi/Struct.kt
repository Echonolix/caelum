package org.echonolix.vkffi

import com.squareup.kotlinpoet.*
import org.echonolix.vkffi.schema.Element
import org.echonolix.vkffi.schema.PatchedRegistry
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment

context(genCtx: FFIGenContext)
fun genStruct(registry: PatchedRegistry) {
    genCtx.newFile(
        FileSpec.builder(VKFFI.vkStructCName)
            .addType(
                TypeSpec.interfaceBuilder(VKFFI.vkStructCName)
                    .addProperty(
                        PropertySpec.builder("address", Long::class)
                            .build()
                    )
                    .addModifiers(KModifier.SEALED)
                    .addType(
                        TypeSpec.interfaceBuilder(VKFFI.vkStructArrayCName)
                            .addModifiers(KModifier.SEALED)
                            .addProperty(
                                PropertySpec.builder("segment", MemorySegment::class)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
    )

    fun addStruct(struct: Element.Struct) {
        genCtx.newFile(
            FileSpec.builder(VKFFI.structPackage, struct.name)
                .addType(
                    TypeSpec.classBuilder(struct.name)
                        .addAnnotation(JvmInline::class)
                        .addModifiers(KModifier.VALUE)
                        .addSuperinterface(VKFFI.vkStructCName)
                        .addProperty(
                            PropertySpec.builder("address", Long::class)
                                .initializer("address")
                                .addModifiers(KModifier.OVERRIDE)
                                .build()
                        )
                        .primaryConstructor(
                            FunSpec.constructorBuilder()
                                .addParameter("address", Long::class)
                                .build()
                        )
                        .addType(
                            TypeSpec.classBuilder("Array")
                                .addAnnotation(JvmInline::class)
                                .addModifiers(KModifier.VALUE)
                                .addSuperinterface(VKFFI.vkStructArrayCName)
                                .addProperty(
                                    PropertySpec.builder("segment", MemorySegment::class)
                                        .initializer("segment")
                                        .addModifiers(KModifier.OVERRIDE)
                                        .build()
                                )
                                .primaryConstructor(
                                    FunSpec.constructorBuilder()
                                        .addParameter("segment", MemorySegment::class)
                                        .build()
                                )
                                .build()
                        )
                        .addType(
                            TypeSpec.companionObjectBuilder()
                                .addProperty(
                                    PropertySpec.builder("layout", MemoryLayout::class)
                                        .initializer(
                                            CodeBlock.builder()
                                                .addStatement("MemoryLayout.structLayout(")
                                                .apply {
                                                    struct.members.forEach { field ->
                                                        var fixedTypeName = field.type
                                                        fixedTypeName = fixedTypeName.removePrefix("const ")
                                                        fixedTypeName = fixedTypeName.removePrefix("struct ")
                                                        if (fixedTypeName.endsWith("*")) {
//                                                         println(fixedTypeName)
                                                        }
                                                    }
                                                }
                                                .addStatement(")")
                                                .build()
                                        )
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
        )
    }

    registry.structTypes.values.forEach { struct ->
        addStruct(struct)
    }
}