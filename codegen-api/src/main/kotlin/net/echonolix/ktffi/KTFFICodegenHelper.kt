package net.echonolix.ktffi

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.asClassName
import java.lang.foreign.MemoryLayout

object KTFFICodegenHelper {
    const val packageName = "net.echonolix.ktffi"
    val typeCname = ClassName(packageName, "NativeType")
    val structCname = ClassName(packageName, "NativeStruct")
    val unionCname = ClassName(packageName, "NativeUnion")
    val arrayCname = ClassName(packageName, "NativeArray")
    val valueCname = ClassName(packageName, "NativeValue")
    val pointerCname = ClassName(packageName, "NativePointer")

    val helper = ClassName(packageName, "APIHelper")
    val omniSegment = helper.member("_\$OMNI_SEGMENT\$_")
    val linker = helper.member("linker")
    val loaderLookup = helper.member("loaderLookup")
    val pointerLayoutMember = helper.member("pointerLayout")
    val symbolLookup = helper.member("symbolLookup")
    val findSymbol = helper.member("findSymbol")

    val memoryLayoutCname = MemoryLayout::class.asClassName()
    val structLayout = memoryLayoutCname.member("structLayout")
    val unionLayout = memoryLayoutCname.member("unionLayout")
    val sequenceLayout = memoryLayoutCname.member("sequenceLayout")
    val paddingLayout = memoryLayoutCname.member("paddingLayout")
}