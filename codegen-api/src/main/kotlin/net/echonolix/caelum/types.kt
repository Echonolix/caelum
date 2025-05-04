package net.echonolix.caelum

import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName

context(ctx: CaelumCodegenContext)
public inline fun List<CType.Function.Parameter>.toParamSpecs(
    annotations: Boolean,
    typeMapper: (CType.Function.Parameter) -> TypeName
) = map {
    val builder = ParameterSpec.builder(it.name, typeMapper(it))
    if (annotations) {
        builder.addAnnotation(CaelumCoreAnnotation.cTypeName(it.type.name))
    }
    builder.build()
}