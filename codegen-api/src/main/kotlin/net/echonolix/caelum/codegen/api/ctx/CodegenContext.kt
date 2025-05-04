package net.echonolix.caelum.codegen.api.ctx

public class CodegenContext(
    codegenOutput: CodegenOutput,
    elementResolver: ElementResolver,
    elementDocumenter: ElementDocumenter
) : CodegenOutput by codegenOutput,
    ElementResolver by elementResolver,
    ElementDocumenter by elementDocumenter
