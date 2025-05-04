package net.echonolix.caelum.codegen.api.ctx

public class CodegenContext(
    codegenOutput: CodegenOutput,
    elementResolver: ElementResolver
) : CodegenOutput by codegenOutput, ElementResolver by elementResolver