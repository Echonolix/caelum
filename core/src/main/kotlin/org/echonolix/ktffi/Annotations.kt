package org.echonolix.ktffi

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
annotation class CType(val name: String)
@Target(AnnotationTarget.CLASS)
annotation class CHandleType
@Target(AnnotationTarget.PROPERTY)
annotation class CArrayLength(val length: UInt)
@Target(AnnotationTarget.FUNCTION)
annotation class CFunctionPointer