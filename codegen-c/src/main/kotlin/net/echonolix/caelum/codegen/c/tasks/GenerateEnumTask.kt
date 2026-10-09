package net.echonolix.caelum.codegen.c.tasks

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import net.echonolix.caelum.codegen.api.CaelumCodegenHelper
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.filterType
import net.echonolix.caelum.codegen.api.generator.EnumBaseGenerator
import net.echonolix.caelum.codegen.api.task.CodegenTask
import net.echonolix.caelum.codegen.api.task.GenTypeAliasTask

class GenerateEnumTask(ctx: CodegenContext) : CodegenTask<Unit>(ctx) {
    override fun CodegenContext.compute() {
        val enumTypes = ctx.filterType<CType.Enum>()
        val typeAlias = GenTypeAliasTask(this, enumTypes).fork()

        enumTypes.parallelStream()
            .filter { (name, type) -> name == type.name }
            .map { (_, enumType) -> genEnumType(enumType) }
            .forEach(ctx::writeOutput)

        typeAlias.joinAndWriteOutput("${ctx.basePackageName}.enums")
    }

    private fun genEnumType(enumType: CType.Enum): FileSpec.Builder {
        // C enums are int-backed; NIntEnum supplies the representation that the
        // two-parameter NEnum needs.
        val generator = object : EnumBaseGenerator(ctx, enumType) {
            context(ctx: CodegenContext)
            override fun enumBaseCName(): ClassName = ClassName(CaelumCodegenHelper.basePkgName, "NIntEnum")
        }
        return generator.generate()
    }
}