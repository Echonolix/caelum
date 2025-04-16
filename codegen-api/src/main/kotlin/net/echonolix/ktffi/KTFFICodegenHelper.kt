package net.echonolix.ktffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

public object KTFFICodegenHelper {
    public const val packageName: String = "net.echonolix.ktffi"

    public val typeCname: ClassName = ClassName(packageName, "NativeType")
    public val typeImplCname: ClassName = typeCname.nestedClass("Impl")

    public val typeDescriptorCname: ClassName = ClassName(packageName, "TypeDescriptor")
    public val typeDescriptorImplCname: ClassName = typeDescriptorCname.nestedClass("Impl")

    public val structCname: ClassName = ClassName(packageName, "NativeStruct")
    public val unionCname: ClassName = ClassName(packageName, "NativeUnion")
    public val arrayCname: ClassName = ClassName(packageName, "NativeArray")
    public val valueCname: ClassName = ClassName(packageName, "NativeValue")
    public val pointerCname: ClassName = ClassName(packageName, "NativePointer")

    public val functionCname: ClassName = ClassName(packageName, "NativeFunction")
    public val functionImplCname: ClassName = functionCname.nestedClass("Impl")
    public val functionTypeDescriptorImplCname: ClassName = functionCname.nestedClass("TypeDescriptorImpl")

    public val helper: ClassName = ClassName(packageName, "APIHelper")
    public val omniSegment: MemberName = helper.member("_\$OMNI_SEGMENT\$_")
    public val linker: MemberName = helper.member("linker")
    public val loaderLookup: MemberName = helper.member("loaderLookup")
    public val pointerLayoutMember: MemberName = helper.member("pointerLayout")
    public val symbolLookup: MemberName = helper.member("symbolLookup")
    public val findSymbol: MemberName = helper.member("findSymbol")

    public val memoryLayoutCname: ClassName = MemoryLayout::class.asClassName()
    public val structLayoutMember: MemberName = memoryLayoutCname.member("structLayout")
    public val unionLayoutMember: MemberName = memoryLayoutCname.member("unionLayout")
    public val sequenceLayout: MemberName = memoryLayoutCname.member("sequenceLayout")
    public val paddingLayout: MemberName = memoryLayoutCname.member("paddingLayout")

    public val pathElementCname: ClassName = memoryLayoutCname.nestedClass("PathElement")
    public val groupElementMember: MemberName = pathElementCname.member("groupElement")

    public val valueLayoutCname: ClassName = ValueLayout::class.asClassName()
    public val addressLayoutMember: MemberName = valueLayoutCname.member("ADDRESS")

    public val methodHandleCname: ClassName = MethodHandle::class.asClassName()
    public val methodHandlesCname: ClassName = MethodHandles::class.asClassName()
    public val javaMethodMemberName: MemberName = MemberName("kotlin.reflect.jvm", "javaMethod")

    public val methodTypeCname: ClassName = ClassName("java.lang.invoke", "MethodType")

    public val memorySegmentCname: ClassName = MemorySegment::class.asClassName()
    public val copyMember: MemberName = memorySegmentCname.member("copy")

    public val cstrMember: MemberName = MemberName("net.echonolix.ktffi", "c_str")

    public val starWildcard: WildcardTypeName = WildcardTypeName.producerOf(ANY.copy(nullable = true))

    public val memoryStackMember: MemberName = MemberName("net.echonolix.ktffi", "MemoryStack")

    public val mallocMember: MemberName = MemberName("net.echonolix.ktffi", "malloc")
}