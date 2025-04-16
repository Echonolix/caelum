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

            val func = FunSpec.builder(funcName)
            func.receiver(handleType.typeName())
            val firstParam = funcType.parameters.first()
            val firstDropped = funcType.parameters.drop(1)
            check(firstParam.type is CType.Handle)

            if (prefixRemoved.startsWith("Create")) {
                check(funcType.returnType.name == "VkResult")
                check(resultCodeTag.successCodes.isNotEmpty())
                check(resultCodeTag.errorCodes.isNotEmpty())
                val lastParam = funcType.parameters.last()
                val lastParamType = lastParam.type
                check(lastParamType is CType.Pointer)
                if (lastParamType.elementType is CType.Handle) {
                    val returnValueCname = lastParamType.elementType.ktApiType()
                    func.returns(resultCname.parameterizedBy(returnValueCname))
                    val firstAndLastDropped = firstDropped.dropLast(1)
                    func.addParameters(firstAndLastDropped.toKtParamSpecs(true))
                    val funcCode = CodeBlock.builder()
                    funcCode.beginControlFlow("return %M", KTFFICodegenHelper.memoryStackMember)
                    funcCode.addStatement(
                        "val handle114514 = %T.%M()",
                        returnValueCname,
                        KTFFICodegenHelper.mallocMember
                    )
                    funcCode.add("when (val result69420 = $origName(")
                    val funcThis = CodeBlock.of("this@%N", funcName)
                    val callParams = mutableListOf(funcThis)
                    firstAndLastDropped.mapTo(callParams) { CodeBlock.of("%N", it.name) }
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
                    funcCode.add(" -> %T.success(%T.fromNativeData(", resultCname, returnValueCname)
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
                    func.addCode(funcCode.build())
                    return func.build()
                }
            } else if (funcType.returnType.name == "VkResult") {
                func.returns(resultCname.parameterizedBy(UNIT))
                func.addParameters(firstDropped.toKtParamSpecs(true))
                val funcCode = CodeBlock.builder()
                funcCode.add("return when (val result69420 = $origName(")
                val funcThis = CodeBlock.of("this@%N", funcName)
                val callParams = mutableListOf(funcThis)
                firstDropped.mapTo(callParams) { CodeBlock.of("%N", it.name) }
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
                func.addCode(funcCode.build())
                return func.build()
            }

            return func.build()
        }
    }
}