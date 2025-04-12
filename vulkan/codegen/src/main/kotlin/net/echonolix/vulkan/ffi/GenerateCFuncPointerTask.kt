package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.*
import net.echonolix.ktffi.CFunctionPointer
import net.echonolix.vulkan.schema.Element
import net.echonolix.vulkan.schema.PatchedRegistry
import java.util.concurrent.RecursiveAction

class GenerateCFuncPointerTask(private val genCtx: FFIGenContext, private val registry: PatchedRegistry) :
    RecursiveAction() {
    override fun compute() {
        val funcPtrList = registry.funcpointerTypes.asSequence()
//            .filter { (name, _) -> name !in skipped }
            .filter { (_, type) -> genCtx.filter(type) }
            .map { it.toPair() }
            .toList()
        val genTypeAliasTask = GenTypeAliasTaskOld(genCtx, funcPtrList).fork()

        funcPtrList.parallelStream()
            .filter { (name, type) -> name == type.name }
            .map { (_, type) -> genFuncPtr(type) }
            .forEach { genCtx.writeOutput(it) }


        val typeAliasesFile = FileSpec.builder(genCtx.funcPointerPackageName, "TypeAliases")
        genTypeAliasTask.join().forEach {
            typeAliasesFile.addTypeAlias(it)
        }
        funcPtrList.forEach { (_, type) ->
            val fixedName = type.name.replaceFirst("PFN_v", "V")
            typeAliasesFile.addTypeAlias(
                TypeAliasSpec.builder(fixedName, ClassName(genCtx.funcPointerPackageName, type.name))
                    .build()
            )
        }
        genCtx.writeOutput(typeAliasesFile)
    }

    private fun genFuncPtr(type: Element.FuncpointerType): FileSpec.Builder {
        val thisCname = ClassName(genCtx.funcPointerPackageName, type.name)
        val file = FileSpec.builder(thisCname)
        val type = TypeSpec.funInterfaceBuilder(thisCname)
        type.addFunction(
            FunSpec.builder("invoke")
                .addAnnotation(CFunctionPointer::class)
                .addModifiers(KModifier.ABSTRACT)
                .build()
        )

        return file.addType(type.build())
    }
}