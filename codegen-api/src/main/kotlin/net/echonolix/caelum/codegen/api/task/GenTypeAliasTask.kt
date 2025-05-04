package net.echonolix.caelum.codegen.api.task

import com.squareup.kotlinpoet.TypeAliasSpec
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.ctx.CodegenContext

public class GenTypeAliasTask(
    ctx: CodegenContext,
    private val inputs: List<Pair<String, CType>>
) : CodegenTask<List<TypeAliasSpec>>(ctx) {
    override fun CodegenContext.compute(): List<TypeAliasSpec> {
        return inputs.parallelStream()
            .filter { (name, dstType) -> name != dstType.name }
            .map { (name, dstType) ->
                TypeAliasSpec.Companion.builder(name, dstType.typeName())
                    .build()
            }
            .toList()
    }
}