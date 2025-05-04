package net.echonolix.caelum.codegen.api.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import net.echonolix.caelum.codegen.api.CElement
import net.echonolix.caelum.codegen.api.ctx.CodegenContext

public abstract class Generator<T : CElement.TopLevel>(
    public val ctx: CodegenContext,
    public val element: T
) {
    public val thisCname: ClassName = with(ctx) { element.className() }

    public abstract fun build(): FileSpec.Builder
}