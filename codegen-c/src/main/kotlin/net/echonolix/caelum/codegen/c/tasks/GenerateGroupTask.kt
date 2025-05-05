package net.echonolix.caelum.codegen.c.tasks

import com.squareup.kotlinpoet.FileSpec
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.filterType
import net.echonolix.caelum.codegen.api.generator.GroupGenerator
import net.echonolix.caelum.codegen.api.task.CodegenTask
import net.echonolix.caelum.codegen.api.task.GenTypeAliasTask

class GenerateGroupTask(ctx: CodegenContext) : CodegenTask<Unit>(ctx) {
    override fun CodegenContext.compute() {
        val structTypes = ctx.filterType<CType.Struct>()
        val unionTypes = ctx.filterType<CType.Union>()

        val structTypeAliases = GenTypeAliasTask(this, structTypes).fork()
        val unionTypeAliases = GenTypeAliasTask(this, unionTypes).fork()

        structTypes.parallelStream()
            .filter { (name, type) -> name == type.name }
            .map { (_, type) -> genGroupType(type) }
            .forEach(ctx::writeOutput)

        unionTypes.parallelStream()
            .filter { (name, type) -> name == type.name }
            .map { (_, type) -> genGroupType(type) }
            .forEach(ctx::writeOutput)

        structTypeAliases.joinAndWriteOutput("${ctx.basePackageName}.structs")
        unionTypeAliases.joinAndWriteOutput("${ctx.basePackageName}.unions")
    }

    context(ctx: CodegenContext)
    private fun genGroupType(groupType: CType.Group): FileSpec.Builder {
        val generator = GroupGenerator(ctx, groupType)
        return generator.generate()
    }
}