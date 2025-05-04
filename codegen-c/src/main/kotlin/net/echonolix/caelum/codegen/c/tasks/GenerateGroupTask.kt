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
        val types = ctx.filterType<CType.Group>()
        val typeAlias = GenTypeAliasTask(this, types).fork()

        types.parallelStream()
            .filter { (name, type) -> name == type.name }
            .map { (_, type) -> genGroupType(type) }
            .forEach(ctx::writeOutput)

        typeAlias.joinAndWriteOutput(ctx.basePackageName)
    }

    context(ctx: CodegenContext)
    private fun genGroupType(groupType: CType.Group): FileSpec.Builder {
        val generator = GroupGenerator(ctx, groupType)
        return generator.generate()
    }
}