package net.echonolix.ktffi

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec


fun FileSpec.Builder.addSuppress() {
    addAnnotation(
        AnnotationSpec.builder(Suppress::class)
            .addMember("%S", "RemoveRedundantQualifierName")
            .addMember("%S", "PropertyName")
            .addMember("%S", "RedundantVisibilityModifier")
            .addMember("%S", "unused")
            .addMember("%S", "NOTHING_TO_INLINE")
            .addMember("%S", "RemoveExplicitTypeArguments")
            .build()
    )
}
