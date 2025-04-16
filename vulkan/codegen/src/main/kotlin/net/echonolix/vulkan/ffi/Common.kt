package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeAliasSpec
import net.echonolix.ktffi.CType
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.RecursiveTask

abstract class VKFFITask<R>(protected val ctx: VKFFICodeGenContext) : RecursiveTask<R>() {
    public final override fun compute(): R {
        return ctx.compute()
    }

    protected fun ForkJoinTask<List<TypeAliasSpec>>.joinAndWriteOutput(packageName: String) {
        val file = FileSpec.builder(packageName, "TypeAliases")
        this.join().forEach {
            file.addTypeAlias(it)
        }
        ctx.writeOutput(file)
    }

    protected abstract fun VKFFICodeGenContext.compute(): R
}

class GenTypeAliasTask(ctx: VKFFICodeGenContext, private val inputs: List<Pair<String, CType>>) :
    VKFFITask<List<TypeAliasSpec>>(ctx) {
    override fun VKFFICodeGenContext.compute(): List<TypeAliasSpec> {
        return inputs.parallelStream()
            .filter { (name, dstType) -> name != dstType.name }
            .map { (name, dstType) ->
                TypeAliasSpec.builder(name, dstType.typeName())
                    .build()
            }
            .toList()
    }
}
