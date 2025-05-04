package net.echonolix.caelum.codegen.api.task

import com.squareup.kotlinpoet.TypeAliasSpec
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.CaelumCodegenContext

public class GenTypeAliasTask(ctx: CaelumCodegenContext, private val inputs: List<Pair<String, CType>>) :
    CaelumCodegenTask<List<TypeAliasSpec>>(ctx) {
    override fun CaelumCodegenContext.compute(): List<TypeAliasSpec> {
        return inputs.parallelStream()
            .filter { (name, dstType) -> name != dstType.name }
            .map { (name, dstType) ->
                TypeAliasSpec.Companion.builder(name, dstType.typeName())
                    .build()
            }
            .toList()
    }
}