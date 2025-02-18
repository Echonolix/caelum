package org.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeAliasSpec
import org.echonolix.vulkan.schema.Element
import java.util.concurrent.RecursiveTask

class GenTypeAliasTask(private val genCtx: FFIGenContext, private val inputs: List<Pair<String, Element.Type>>) :
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