package net.echonolix.ktffi

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.asClassName
import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

object KTFFICodegenHelper {
    const val packageName = "net.echonolix.ktffi"
    val typeCname = ClassName(packageName, "NativeType")
    val typeImplCname = typeCname.nestedClass("Impl")
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

    val valueLayoutCname = ValueLayout::class.asClassName()
    val addressLayoutMember = valueLayoutCname.member("ADDRESS")

    val methodHandlesCname = MethodHandles::class.asClassName()
    val javaMethodMemberName = MemberName("kotlin.reflect.jvm", "javaMethod")
}