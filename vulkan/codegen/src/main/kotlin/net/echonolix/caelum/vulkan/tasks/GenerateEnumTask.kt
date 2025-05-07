package net.echonolix.caelum.vulkan.tasks

import com.squareup.kotlinpoet.*
import net.echonolix.caelum.codegen.api.CBasicType
import net.echonolix.caelum.codegen.api.CExpression
import net.echonolix.caelum.codegen.api.CTopLevelConst
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.filterType
import net.echonolix.caelum.codegen.api.ctx.filterTypeStream
import net.echonolix.caelum.codegen.api.generator.EnumBaseGenerator
import net.echonolix.caelum.codegen.api.generator.FlagGenerator
import net.echonolix.caelum.codegen.api.task.CodegenTask
import net.echonolix.caelum.codegen.api.task.GenTypeAliasTask
import net.echonolix.caelum.vulkan.VulkanCodegen
import net.echonolix.caelum.vulkan.schema.FilteredRegistry
import kotlin.io.path.Path

class GenerateEnumTask(ctx: CodegenContext, val registry: FilteredRegistry) : CodegenTask<Unit>(ctx) {
    override fun CodegenContext.compute() {
        val constants = ConstantsTask().fork()
        val enum = EnumTask().fork()
        val bitmask = BitmaskTask().fork()

        constants.join()
        enum.join()
        bitmask.join()
    }

    private inner class EnumTask : CodegenTask<Unit>(ctx) {
        override fun CodegenContext.compute() {
            val enumTypes = ctx.filterType<CType.Enum>()
            val typeAlias = GenTypeAliasTask(this, enumTypes).fork()

            enumTypes.parallelStream()
                .filter { (name, type) -> name == type.name }
                .map { (_, enumType) -> genEnumType(enumType) }
                .partitionWrite("enums")

            typeAlias.joinAndWriteOutput(Path("enums"), VulkanCodegen.enumPackageName)
        }
    }

    private inner class BitmaskTask : CodegenTask<Unit>(ctx) {
        override fun CodegenContext.compute() {
            val flagTypes = ctx.filterTypeStream<CType.Bitmask>()
                .filter { !it.first.contains("FlagBits") }
                .toList()
            val typeAlias = GenTypeAliasTask(this, flagTypes).fork()

            flagTypes.parallelStream()
                .filter { (name, type) -> name == type.name }
                .map { (_, flagType) -> genFlagType(flagType) }
                .partitionWrite("enums")

            typeAlias.joinAndWriteOutput(Path("enums"), VulkanCodegen.flagPackageName)
        }
    }

    private inner class ConstantsTask : CodegenTask<Unit>(ctx) {
        override fun CodegenContext.compute() {
            val constantsFile = FileSpec.builder(VulkanCodegen.basePkgName, "Constants")
            constantsFile.addProperties(
                ctx.filterTypeStream<CTopLevelConst>()
                    .filter { !VulkanCodegen.vkVersionConstRegex.matches(it.first) }
                    .sorted(
                        compareBy<Pair<String, CTopLevelConst>> { (_, const) ->
                            const.expression is CExpression.StringLiteral
                        }.thenBy { (_, const) ->
                            const.expression is CExpression.Const
                        }.reversed()
                    )
                    .filter { (_, const) -> const.javaClass == CTopLevelConst::class.java }
                    .map { (_, const) ->
                        val initCode = const.expression.codeBlock()
                        val constType = const.type
                        val type = if (constType is CType.Pointer) {
                            STRING
                        } else {
                            constType.ktApiType()
                        }
                        PropertySpec.builder(const.name, type)
                            .addModifiers(KModifier.CONST)
                            .initializer(initCode)
                            .build()
                    }
                    .toList()
            )
            ctx.writeOutput(Path("baseSrc"), constantsFile)
        }
    }

    private fun genFlagType(flagType: CType.Bitmask): FileSpec.Builder {
        val generator = object : FlagGenerator(ctx, flagType) {
            context(ctx: CodegenContext)
            override fun enumBaseCName(): ClassName {
                val baseType = flagType.baseType
                return when (baseType) {
                    CBasicType.int32_t -> VulkanCodegen.vkFlags32CNAME
                    CBasicType.int64_t -> VulkanCodegen.vkFlags64CNAME
                    else -> throw IllegalArgumentException("Unsupported base type: $baseType")
                }
            }
        }
        return generator.generate()
    }

    private fun genEnumType(enumType: CType.Enum): FileSpec.Builder {
        val generator = object : EnumBaseGenerator(ctx, enumType) {
            context(ctx: CodegenContext)
            override fun enumBaseCName(): ClassName {
                return VulkanCodegen.vkEnumCName
            }
        }
        return generator.generate()
    }

}
