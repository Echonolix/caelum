package net.echonolix.caelum

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName

public object CaelumCoreAnnotation {
    public val cTypeNameCName: ClassName = ClassName(CaelumCodegenHelper.basePkgName, "CTypeName")

    public fun cTypeName(name: String): AnnotationSpec {
        return AnnotationSpec.builder(cTypeNameCName)
            .addMember("%S", name)
            .build()
    }
}