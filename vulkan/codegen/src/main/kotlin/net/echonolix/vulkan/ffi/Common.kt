package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeAliasSpec
import com.squareup.kotlinpoet.TypeSpec
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.KTFFICodegenHelper
import net.echonolix.vulkan.schema.Element
import java.lang.invoke.MethodHandle
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.RecursiveTask

abstract class VKFFITask<R>(protected val ctx: VKFFICodeGenContext) : RecursiveTask<R>() {
    final override fun compute(): R {
        return ctx.compute()
    }

    protected fun ForkJoinTask<List<TypeAliasSpec>>.joinAndWriteOutput(packageName: String) {
        val file = FileSpec.builder(packageName, "TypeAliases")
        this.join().forEach {
            file.addTypeAlias(it)
        }
        ctx.writeOutput(file)
    }

    protected fun TypeSpec.Builder.addMethodHandleFields(): TypeSpec.Builder {
        addProperty(
            PropertySpec.builder("\$fromIntMH", MethodHandle::class)
                .addModifiers(KModifier.PRIVATE)
                .initializer(
                    CodeBlock.builder()
                        .add(
                            "%T.lookup().unreflect(::fromInt.%M)",
                            VKFFI.methodHandlesCname,
                            KTFFICodegenHelper.javaMethodMemberName
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
                            "%T.lookup().unreflect(::toInt.%M)",
                            VKFFI.methodHandlesCname,
                            KTFFICodegenHelper.javaMethodMemberName
                        )
                        .build()
                )
                .build()
        )
        return this
    }

    protected abstract fun VKFFICodeGenContext.compute(): R
}

class GenTypeAliasTask(ctx: VKFFICodeGenContext, private val inputs: List<Pair<String, CType>>) :
    VKFFITask<List<TypeAliasSpec>>(ctx) {
    override fun VKFFICodeGenContext.compute(): List<TypeAliasSpec> {
        return inputs.parallelStream()
            .filter { (name, dstType) -> name != dstType.name }
            .map { (name, dstType) ->
                TypeAliasSpec.builder(name, dstType.className())
                    .build()
            }
            .toList()
    }
}

class GenTypeAliasTaskOld(private val genCtx: FFIGenContext, private val inputs: List<Pair<String, Element.Type>>) :
    RecursiveTask<List<TypeAliasSpec>>() {
    override fun compute(): List<TypeAliasSpec> {
        return inputs.parallelStream()
            .filter { (name, type) -> name != type.name }
            .map { (name, type) ->
                val packageName = genCtx.getPackageName(type)
                TypeAliasSpec.builder(name, ClassName(packageName, type.name)).build()
            }
            .toList()
    }
}