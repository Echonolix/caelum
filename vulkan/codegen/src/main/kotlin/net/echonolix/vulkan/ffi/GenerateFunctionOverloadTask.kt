package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.KTFFICodegenHelper
import net.echonolix.ktffi.decap

class GenerateFunctionOverloadTask(ctx: VKFFICodeGenContext) : VKFFITask<Unit>(ctx) {
    override fun VKFFICodeGenContext.compute() {
        val skippedNames = setOf("vkGetInstanceProcAddr", "vkGetDeviceProcAddr")
        ctx.filterVkFunction().asSequence()
            .filter { it.tags.get<OriginalFunctionNameTag>()!!.name !in skippedNames }
            .groupBy { it.parameters.first().type }
            .map { Task(it.key as CType.Handle, it.value) }
            .onEach(Task::fork)
            .forEach(Task::join)
    }

    private inner class Task(
        private val handleType: CType.Handle,
        private val functions: List<CType.Function>
    ) : VKFFITask<Unit>(ctx) {
        private val vkResultCname = with(ctx) { (ctx.resolveElement("VkResult") as CType.Enum).className() }
        private val resultCname = Result::class.asClassName()

        override fun VKFFICodeGenContext.compute() {
            val file = FileSpec.builder(VKFFI.basePkgName, "${handleType.name}Functions")
            file.addProperty(
                PropertySpec.builder("_UNIT_RESULT_", resultCname.parameterizedBy(UNIT))
                    .addModifiers(KModifier.PRIVATE)
                    .initializer("%T.success(%T)", resultCname, UNIT)
                    .build()
            )
            file.addFunctions(
                functions.parallelStream()
                    .map { vkFuncOverload(it) }
                    .toList()
            )
            ctx.writeOutput(file)
        }

        private fun VKFFICodeGenContext.vkFuncOverload(funcType: CType.Function): FunSpec {
            val prefixRemoved = funcType.name.removePrefix("VkFunc")
            val funcName = prefixRemoved.decap()
            val resultCodeTag = funcType.tags.get<ResultCodeTag>() ?: error("$funcType is missing result code tag")
            val origName = funcType.tags.get<OriginalFunctionNameTag>()?.name
                ?: error("$funcType is missing original function name tag")

            val firstParam = funcType.parameters.first()
            val firstDropped = funcType.parameters.drop(1)
            check(firstParam.type is CType.Handle)

            val func1 = FunSpec.builder(funcName)
            func1.receiver(handleType.typeName())

            val lastParam = funcType.parameters.last()
            val lastParamType = lastParam.type

            val returnType1: TypeName
            val params1: List<CType.Function.Parameter>

            if (prefixRemoved.startsWith("Create") && (lastParamType as CType.Pointer).elementType is CType.Handle) {
                check(funcType.returnType.name == "VkResult")
                check(resultCodeTag.successCodes.isNotEmpty())
                check(resultCodeTag.errorCodes.isNotEmpty())
                returnType1 = lastParamType.elementType.ktApiType()
                params1 = firstDropped.dropLast(1)

                func1.returns(resultCname.parameterizedBy(returnType1))
                func1.addParameters(params1.toKtParamSpecs(true))
                val funcCode = CodeBlock.builder()
                funcCode.beginControlFlow("return %M", KTFFICodegenHelper.memoryStackMember)
                funcCode.addStatement(
                    "val handle114514 = %T.%M()",
                    returnType1,
                    KTFFICodegenHelper.mallocMember
                )
                funcCode.add("when (val result69420 = $origName(")
                val funcThis = CodeBlock.of("this@%N", funcName)
                val callParams = mutableListOf(funcThis)
                params1.mapTo(callParams) { CodeBlock.of("%N", it.name) }
                callParams.add(CodeBlock.of("handle114514.ptr()"))
                funcCode.add(callParams.joinToCode())
                funcCode.beginControlFlow("))")
                funcCode.add(resultCodeTag.successCodes.joinToCode(",\n") {
                    CodeBlock.of(
                        "%T.%N",
                        vkResultCname,
                        it.name
                    )
                })
                funcCode.add(" -> %T.success(%T.fromNativeData(", resultCname, returnType1)
                funcCode.add(funcThis)
                funcCode.addStatement(", handle114514.%M))", VKFFI.handleValueMember)
                funcCode.add(resultCodeTag.errorCodes.joinToCode(",\n") {
                    CodeBlock.of(
                        "%T.%N",
                        vkResultCname,
                        it.name
                    )
                })
                funcCode.addStatement(" -> %T.failure(%T(result69420))", resultCname, VKFFI.vkExceptionCname)
                funcCode.addStatement("else -> error(%P)", "Unexpected result from $origName: \$result69420")
                funcCode.endControlFlow()
                funcCode.endControlFlow()
                func1.addCode(funcCode.build())
            } else if (funcType.returnType.name == "VkResult") {
                returnType1 = resultCname.parameterizedBy(UNIT)
                params1 = firstDropped

                func1.returns(returnType1)
                func1.addParameters(params1.toKtParamSpecs(true))
                val funcCode = CodeBlock.builder()
                funcCode.add("return when (val result69420 = $origName(")
                val callParams = mutableListOf(CodeBlock.of("this"))
                params1.mapTo(callParams) { CodeBlock.of("%N", it.name) }
                funcCode.add(callParams.joinToCode())
                funcCode.beginControlFlow("))")
                funcCode.add(resultCodeTag.successCodes.joinToCode(",\n") {
                    CodeBlock.of(
                        "%T.%N",
                        vkResultCname,
                        it.name
                    )
                })
                funcCode.addStatement(" -> _UNIT_RESULT_")
                if (resultCodeTag.errorCodes.isNotEmpty()) {
                    funcCode.add(resultCodeTag.errorCodes.joinToCode(",\n") {
                        CodeBlock.of(
                            "%T.%N",
                            vkResultCname,
                            it.name
                        )
                    })
                    funcCode.addStatement(" -> %T.failure(%T(result69420))", resultCname, VKFFI.vkExceptionCname)
                }
                funcCode.addStatement("else -> error(%P)", "Unexpected result from $origName: \$result69420")
                funcCode.endControlFlow()
                func1.addCode(funcCode.build())
            } else {
                returnType1 = funcType.returnType.ktApiType()
                params1 = firstDropped

                func1.returns(returnType1)
                func1.addParameters(firstDropped.toKtParamSpecs(true))
                val funcCode = CodeBlock.builder()
                funcCode.add("return $origName(")
                val callParams = mutableListOf(CodeBlock.of("this"))
                firstDropped.mapTo(callParams) { CodeBlock.of("%N", it.name) }
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
//                        pType = KTFFICodegenHelper.valueCname.parameterizedBy(typeArguments)
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
//                        pType = KTFFICodegenHelper.arrayCname.parameterizedBy(typeArguments)
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