package net.echonolix.caelum.codegen.api.generator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import net.echonolix.caelum.codegen.api.CElement
import net.echonolix.caelum.codegen.api.ctx.CodegenContext

public abstract class Generator<T : CElement.TopLevel>(
    protected val ctx: CodegenContext,
    protected val element: T
) {
    protected val thisCName: ClassName = with(ctx) { element.className() }

    public abstract fun generate(): FileSpec.Builder
}