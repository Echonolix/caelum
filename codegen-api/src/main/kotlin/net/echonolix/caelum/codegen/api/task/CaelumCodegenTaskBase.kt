package net.echonolix.caelum.codegen.api.task

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeAliasSpec
import net.echonolix.caelum.codegen.api.CaelumCodegenContext
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.RecursiveTask
import java.util.stream.Stream
import kotlin.io.path.Path

public abstract class CaelumCodegenTaskBase<CTX : CaelumCodegenContext, R>(protected val ctx: CTX) :
    RecursiveTask<R>() {
    public final override fun compute(): R {
        return ctx.compute()
    }

    protected fun ForkJoinTask<List<TypeAliasSpec>>.joinAndWriteOutput(packageName: String) {
        val file = FileSpec.Companion.builder(packageName, "TypeAliases")
        this.join().forEach {
            file.addTypeAlias(it)
        }
        ctx.writeOutput(file)
    }

    protected fun ForkJoinTask<List<TypeAliasSpec>>.joinAndWriteOutput(path: Path, packageName: String) {
        val file = FileSpec.Companion.builder(packageName, "TypeAliases")
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

    protected abstract fun CTX.compute(): R
}

public typealias CaelumCodegenTask<R> = CaelumCodegenTaskBase<CaelumCodegenContext, R>