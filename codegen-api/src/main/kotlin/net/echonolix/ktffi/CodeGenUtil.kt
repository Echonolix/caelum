package net.echonolix.ktffi

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

public fun TypeSpec.Builder.addMethodHandleFields(): TypeSpec.Builder {
    addProperty(
        PropertySpec.builder("fromNativeDataMH", KTFFICodegenHelper.methodHandleCname)
            .addModifiers(KModifier.OVERRIDE)
            .initializer(
                CodeBlock.builder()
                    .add(
                        "%T.lookup().unreflect(::fromNativeData.%M)",
                        KTFFICodegenHelper.methodHandlesCname,
                        KTFFICodegenHelper.javaMethodMemberName
                    )
                    .build()
            )
            .build()
    )
    addProperty(
        PropertySpec.builder("toNativeDataMH", KTFFICodegenHelper.methodHandleCname)
            .addModifiers(KModifier.OVERRIDE)
            .initializer(
                CodeBlock.builder()
                    .add(
                        "%T.lookup().unreflect(::toNativeData.%M)",
                        KTFFICodegenHelper.methodHandlesCname,
                        KTFFICodegenHelper.javaMethodMemberName
                    )
                    .build()
            )
            .build()
    )
    return this
}