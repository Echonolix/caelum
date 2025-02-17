package org.echonolix.ktffi

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

object KTFFICodegen {
    const val packageName = "org.echonolix.ktffi"
    val typeCname = ClassName(packageName, "NativeType")
    val structCname = ClassName(packageName, "NativeStruct")
    val unionCname = ClassName(packageName, "NativeUnion")
    val arrayCname = ClassName(packageName, "NativeArray")
    val valueCname = ClassName(packageName, "NativeValue")
    val pointerCname = ClassName(packageName, "NativePointer")

    val omniSegment = MemberName(packageName, "_\$OMNI_SEGMENT\$_")
}