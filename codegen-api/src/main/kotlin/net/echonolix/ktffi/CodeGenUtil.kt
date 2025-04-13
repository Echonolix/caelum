package net.echonolix.ktffi

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import java.lang.invoke.MethodHandle

public fun TypeSpec.Builder.addMethodHandleFields(): TypeSpec.Builder {
    addProperty(
        PropertySpec.builder("\$fromNativeDataMH", MethodHandle::class)
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
        PropertySpec.builder("\$toNativeDataMH", MethodHandle::class)
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