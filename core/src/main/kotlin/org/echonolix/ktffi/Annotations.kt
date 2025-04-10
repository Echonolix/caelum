package org.echonolix.ktffi

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
annotation class CTypeName(val name: String)
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class CHandleType
@Target(AnnotationTarget.PROPERTY)
annotation class CArrayType(val length: UInt)
@Target(AnnotationTarget.FUNCTION)
annotation class CFunctionPointer
@Target(AnnotationTarget.PROPERTY)
annotation class CPointerType(val lengthVariable: String = "")