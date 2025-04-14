package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.*
import net.echonolix.ktffi.CType

class GenerateFunctionTask(ctx: VKFFICodeGenContext) : VKFFITask<Unit>(ctx) {
    override fun VKFFICodeGenContext.compute() {
        ctx.filterTypeStream<CType.Function>()
            .map { (_, funcType) -> genFunc(funcType) }
            .forEach(ctx::writeOutput)
    }

    private fun VKFFICodeGenContext.genFunc(funcType: CType.Function): FileSpec.Builder {
        val thisCname = funcType.className()
        val returnType = funcType.returnType

        val funInterfaceType = TypeSpec.funInterfaceBuilder(thisCname)

        val invokeFunc = FunSpec.builder("invoke")
            .addModifiers(KModifier.OPERATOR, KModifier.ABSTRACT)
            .returns(returnType.ktApiType())
            .addParameters(funcType.parameters.map {
                ParameterSpec.builder(it.name, it.type.ktApiType())
                    .build()
            })
        funInterfaceType.addFunction(invokeFunc.build())

        TypeSpec.companionObjectBuilder()
            .build()

        funInterfaceType.build()


        val file = FileSpec.builder(thisCname)
        file.addType(funInterfaceType.build())
        return file
    }
}