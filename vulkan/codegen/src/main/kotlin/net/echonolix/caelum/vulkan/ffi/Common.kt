package net.echonolix.caelum.vulkan.ffi

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeAliasSpec
import net.echonolix.caelum.CType
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.RecursiveTask
import java.util.stream.Stream
import kotlin.io.path.Path

abstract class VKFFITask<R>(protected val ctx: VulkanCodeGenContext) : RecursiveTask<R>() {
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

    protected fun ForkJoinTask<List<TypeAliasSpec>>.joinAndWriteOutput(path: Path, packageName: String) {
        val file = FileSpec.builder(packageName, "TypeAliases")
        this.join().forEach {
            file.addTypeAlias(it)
        }
        ctx.writeOutput(path, file)
    }

    protected fun Stream<FileSpec.Builder>.partitionWrite(baseName: String) {
        val list = sorted(compareBy { it.name }).toList()
        list.asSequence()
            .chunked((list.size + 7) / 8)
            .forEachIndexed { index, chunk ->
                val path = Path("$baseName$index")
                chunk.forEach { ctx.writeOutput(path, it) }
            }
    }

    protected abstract fun VulkanCodeGenContext.compute(): R
}

class GenTypeAliasTask(ctx: VulkanCodeGenContext, private val inputs: List<Pair<String, CType>>) :
    VKFFITask<List<TypeAliasSpec>>(ctx) {
    override fun VulkanCodeGenContext.compute(): List<TypeAliasSpec> {
        return inputs.parallelStream()
            .filter { (name, dstType) -> name != dstType.name }
            .map { (name, dstType) ->
                TypeAliasSpec.builder(name, dstType.typeName())
                    .build()
            }
            .toList()
    }
}
