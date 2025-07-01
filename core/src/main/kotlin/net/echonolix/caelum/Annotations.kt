package net.echonolix.caelum

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.TYPE, AnnotationTarget.CLASS, AnnotationTarget.VALUE_PARAMETER)
public annotation class CTypeName(val name: String)
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
public annotation class CHandleType
@Target(AnnotationTarget.PROPERTY)
public annotation class CArrayType(val length: UInt)
@Target(AnnotationTarget.FUNCTION)
public annotation class CFunctionPointer
@Target(AnnotationTarget.PROPERTY)
public annotation class CPointerType(val lengthVariable: String = "")

@RequiresOptIn(level = RequiresOptIn.Level.WARNING)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
public annotation class UnsafeAPI

@DslMarker
@Retention(AnnotationRetention.SOURCE)
@Target( AnnotationTarget.TYPE, AnnotationTarget.FUNCTION)
public annotation class StructAccessor