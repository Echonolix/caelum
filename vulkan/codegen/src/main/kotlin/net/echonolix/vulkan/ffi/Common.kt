package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeAliasSpec
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.className
import net.echonolix.vulkan.schema.Element
import java.util.concurrent.RecursiveTask

class GenTypeAliasTask(private val ctx: VKFFICodeGenContext, private val inputs: Map<String, CType.Handle>) :
    RecursiveTask<List<TypeAliasSpec>>() {
    override fun compute(): List<TypeAliasSpec> {
        with (ctx) {
            return inputs.entries.parallelStream()
                .filter { it.key != it.value.name }
                .map { (name, dstType) ->
                    TypeAliasSpec.builder(name, dstType.className()).build()
                }
                .toList()
        }
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