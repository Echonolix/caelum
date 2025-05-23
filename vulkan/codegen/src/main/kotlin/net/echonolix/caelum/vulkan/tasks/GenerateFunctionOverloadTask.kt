package net.echonolix.caelum.vulkan.tasks

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.CaelumCodegenHelper
import net.echonolix.caelum.codegen.api.OriginalNameTag
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.decap
import net.echonolix.caelum.codegen.api.task.CodegenTask
import net.echonolix.caelum.vulkan.*
import kotlin.io.path.Path

class GenerateFunctionOverloadTask(ctx: CodegenContext) : CodegenTask<Unit>(ctx) {
    override fun CodegenContext.compute() {
        val skippedNames = setOf("vkGetInstanceProcAddr", "vkGetDeviceProcAddr")
        ctx.filterVkFunction().asSequence()
            .filter { it.tags.getOrNull<OriginalNameTag>()!!.name !in skippedNames }
            .groupBy { it.parameters.first().type }
            .map { Task(it.key as CType.Handle, it.value) }
            .onEach(Task::fork)
            .forEach(Task::join)
    }

    private inner class Task(
        private val handleType: CType.Handle,
        private val functions: List<CType.Function>
    ) : CodegenTask<Unit>(ctx) {
        private val vkResultCName = with(ctx) { (ctx.resolveElement("VkResult") as CType.Enum).className() }
        private val resultCName = Result::class.asClassName()

        override fun CodegenContext.compute() {
            val file = FileSpec.builder(VulkanCodegen.basePkgName, "${handleType.name}Functions")
            file.addProperty(
                PropertySpec.builder("_UNIT_RESULT_", resultCName.parameterizedBy(UNIT))
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("%T.success(%T)", resultCName, UNIT)
                    .build()
            )
            file.addFunctions(
                functions.parallelStream()
                    .map { vkFuncOverload(it) }
                    .toList()
            )
            ctx.writeOutput(Path("main"), file)
        }

        private fun CodegenContext.vkFuncOverload(funcType: CType.Function): FunSpec {
            val prefixRemoved = funcType.name.removePrefix("VkFunc")
            val funcName = prefixRemoved.decap()
            val resultCodeTag =
                funcType.tags.getOrNull<ResultCodeTag>() ?: error("$funcType is missing result code tag")
            val origName = funcType.tags.getOrNull<OriginalNameTag>()?.name
                ?: error("$funcType is missing original function name tag")

            val dispatcher = if (isDeviceBase(handleType)) "device" else "instance"

            val firstParam = funcType.parameters.first()
            val firstDropped = funcType.parameters.drop(1)
            check(firstParam.type is CType.Handle)

            val func1 = FunSpec.builder(funcName)
            func1.receiver(handleType.objectBaseCName())

            val lastParam = funcType.parameters.last()
            val lastParamType = lastParam.type

            val returnType1: TypeName
            val params1: List<CType.Function.Parameter>

            if (prefixRemoved.startsWith("Create") && (lastParamType as CType.Pointer).elementType is CType.Handle) {
                check(funcType.returnType.name == "VkResult")
                check(resultCodeTag.successCodes.isNotEmpty())
                check(resultCodeTag.errorCodes.isNotEmpty())
                val lastParamElementType = lastParamType.elementType as CType.Handle
                returnType1 = lastParamElementType.objectBaseCName()
                val returnHandleType = lastParamElementType
                params1 = firstDropped.dropLast(1)

                func1.returns(resultCName.parameterizedBy(returnType1))
                func1.addParameters(params1.toKtParamOverloadSpecs(true))
                val funcCode = CodeBlock.builder()
                funcCode.beginControlFlow("return %M", CaelumCodegenHelper.memoryStackMember)
                funcCode.addStatement(
                    "val handle114514 = %T.%M()",
                    lastParamElementType.typeName(),
                    CaelumCodegenHelper.mallocMember
                )
                funcCode.add("when (val result69420 = $dispatcher.$origName(")
                val thisAtFunc = CodeBlock.of("this@%N", funcName)
                val callParams = mutableListOf(thisAtFunc)
                params1.mapTo(callParams) { CodeBlock.of("%N", it.name) }
                callParams.add(CodeBlock.of("handle114514.ptr()"))
                funcCode.add(callParams.joinToCode())
                funcCode.beginControlFlow("))")
                funcCode.add(resultCodeTag.successCodes.joinToCode(",\n") {
                    CodeBlock.of(
                        "%T.%N",
                        vkResultCName,
                        it.name
                    )
                })
                funcCode.add(" -> %T.success(%T.fromNativeData(", resultCName, returnType1)

                val returnHandleParentType = returnHandleType.tags.getOrNull<VkHandleTag>()!!.parent
                if (returnHandleParentType != handleType) {
                    val secondParam = params1.first()
                    val secondParamType = secondParam.type
                    check(secondParamType is CType.Handle)
                    check(secondParamType == returnHandleParentType)
                    funcCode.add(secondParam.name)
                } else {
                    funcCode.add(thisAtFunc)
                }

                funcCode.addStatement(", handle114514.%M))", VulkanCodegen.handleValueMember)
                funcCode.add(resultCodeTag.errorCodes.joinToCode(",\n") {
                    CodeBlock.of(
                        "%T.%N",
                        vkResultCName,
                        it.name
                    )
                })
                funcCode.addStatement(" -> %T.failure(%T(result69420))", resultCName, VulkanCodegen.vkExceptionCName)
                funcCode.addStatement("else -> error(%P)", "Unexpected result from $origName: \$result69420")
                funcCode.endControlFlow()
                funcCode.endControlFlow()
                func1.addCode(funcCode.build())
            } else if (funcType.returnType.name == "VkResult") {
                returnType1 = resultCName.parameterizedBy(UNIT)
                params1 = firstDropped

                func1.returns(returnType1)
                func1.addParameters(params1.toKtParamOverloadSpecs(true))
                val funcCode = CodeBlock.builder()
                funcCode.add("return when (val result69420 = $dispatcher.$origName(")
                val callParams = mutableListOf(CodeBlock.of("this"))
                params1.mapTo(callParams) { CodeBlock.of("%N", it.name) }
                funcCode.add(callParams.joinToCode())
                funcCode.beginControlFlow("))")
                funcCode.add(resultCodeTag.successCodes.joinToCode(",\n") {
                    CodeBlock.of(
                        "%T.%N",
                        vkResultCName,
                        it.name
                    )
                })
                funcCode.addStatement(" -> _UNIT_RESULT_")
                if (resultCodeTag.errorCodes.isNotEmpty()) {
                    funcCode.add(resultCodeTag.errorCodes.joinToCode(",\n") {
                        CodeBlock.of(
                            "%T.%N",
                            vkResultCName,
                            it.name
                        )
                    })
                    funcCode.addStatement(" -> %T.failure(%T(result69420))", resultCName, VulkanCodegen.vkExceptionCName)
                }
                funcCode.addStatement("else -> error(%P)", "Unexpected result from $origName: \$result69420")
                funcCode.endControlFlow()
                func1.addCode(funcCode.build())
            } else {
                returnType1 = funcType.returnType.ktApiType()
                params1 = firstDropped

                func1.returns(returnType1)
                func1.addParameters(params1.toKtParamOverloadSpecs(true))
                val funcCode = CodeBlock.builder()
                funcCode.add("return $dispatcher.$origName(")
                val callParams = mutableListOf(CodeBlock.of("this"))
                params1.mapTo(callParams) { CodeBlock.of("%N", it.name) }
                funcCode.add(callParams.joinToCode())
                funcCode.add(")")
                func1.addCode(funcCode.build())
            }

            return func1.build()
//            val results = mutableListOf(func1.build())
//
//            if (params1.any { it.type is CType.Pointer }) {
//                val funcBase = FunSpec.builder(funcName)
//                funcBase.returns(returnType1)
//                val funcCode = CodeBlock.builder()
//                funcCode.add("return $funcName(")
//                funcCode.add(firstDropped.joinToCode {
//                    if (it.type is CType.Pointer) {
//                        CodeBlock.of("%N.ptr()", it.name)
//                    } else {
//                        CodeBlock.of("%N", it.name)
//                    }
//                })
//                funcCode.add(")")
//                funcBase.addCode(funcCode.build())
//
//                funcBase.addParameters(firstDropped.toParamSpecs(true) {
//                    var pType = it.type.ktApiType()
//                    if (it.type is CType.Pointer) {
//                        val typeArguments = (pType as ParameterizedTypeName).typeArguments
//                        pType = CaelumCodegenHelper.valueCName.parameterizedBy(typeArguments)
//                    }
//                    pType
//                })
//                results.add(funcBase.build())
//                funcBase.parameters.clear()
//
//                funcBase.addParameters(firstDropped.toParamSpecs(true) {
//                    var pType = it.type.ktApiType()
//                    if (it.type is CType.Pointer) {
//                        val typeArguments = (pType as ParameterizedTypeName).typeArguments
//                        pType = CaelumCodegenHelper.arrayCName.parameterizedBy(typeArguments)
//                    }
//                    pType
//                })
//                results.add(funcBase.build())
//            }
//
//            return results
        }
    }
}