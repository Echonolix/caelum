package org.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.*
import org.echonolix.ktffi.CBasicType
import org.echonolix.vulkan.schema.PatchedRegistry
import java.util.concurrent.RecursiveAction

class GenerateHandleTask(private val genCtx: FFIGenContext, private val registry: PatchedRegistry) :
    RecursiveAction() {
    override fun compute() {
        val handleList = registry.handleTypes.asSequence()
//            .filter { (name, _) -> name !in skipped }
            .filter { (_, type) -> genCtx.filter(type) }
            .map { it.toPair() }
            .toList()
        val genTypeAliasTask = GenTypeAliasTask(genCtx, handleList).fork()

        val handleFile = FileSpec.builder(genCtx.handlePackageName, "Handles")
        handleList.asSequence()
            .filter { (name, type) -> name == type.name }
            .forEach { (_, type) ->
                handleFile.addTypeAlias(
                    TypeAliasSpec.builder(type.name, CBasicType.uint64_t.nativeTypeName).build()
                )
            }
        genCtx.writeOutput(handleFile)

        val typeAliasesFile = FileSpec.builder(genCtx.handlePackageName, "TypeAliases")
        genTypeAliasTask.join().forEach {
            typeAliasesFile.addTypeAlias(it)
        }
        genCtx.writeOutput(typeAliasesFile)
    }
}