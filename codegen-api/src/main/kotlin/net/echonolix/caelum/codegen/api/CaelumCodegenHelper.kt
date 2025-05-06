package net.echonolix.caelum.codegen.api

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles

public object CaelumCodegenHelper {
    public const val basePkgName: String = "net.echonolix.caelum"

    public val typeCName: ClassName = ClassName(basePkgName, "NativeType")
    public val typeImplCName: ClassName = typeCName.nestedClass("Impl")

    public val typeDescriptorCName: ClassName = ClassName(basePkgName, "TypeDescriptor")
    public val typeDescriptorImplCName: ClassName = typeDescriptorCName.nestedClass("Impl")

    public val enumCName: ClassName = ClassName(basePkgName, "NativeEnum")
    
    public val structCName: ClassName = ClassName(basePkgName, "NativeStruct")
    public val unionCName: ClassName = ClassName(basePkgName, "NativeUnion")
    public val arrayCName: ClassName = ClassName(basePkgName, "NativeArray")
    public val valueCName: ClassName = ClassName(basePkgName, "NativeValue")
    public val pointerCName: ClassName = ClassName(basePkgName, "NativePointer")

    public val functionCName: ClassName = ClassName(basePkgName, "NativeFunction")
    public val functionImplCName: ClassName = functionCName.nestedClass("Impl")
    public val functionTypeDescriptorImplCName: ClassName = functionCName.nestedClass("TypeDescriptorImpl")

    public val helperCName: ClassName = ClassName(basePkgName, "APIHelper")
    public val omniSegment: MemberName = helperCName.member("_\$OMNI_SEGMENT\$_")
    public val linker: MemberName = helperCName.member("linker")
    public val loaderLookup: MemberName = helperCName.member("loaderLookup")
    public val pointerLayoutMember: MemberName = helperCName.member("pointerLayout")
    public val symbolLookup: MemberName = helperCName.member("symbolLookup")
    public val findSymbolMemberName: MemberName = helperCName.member("findSymbol")

    public val memoryLayoutCName: ClassName = MemoryLayout::class.asClassName()
    public val structLayoutMember: MemberName = memoryLayoutCName.member("structLayout")
    public val unionLayoutMember: MemberName = memoryLayoutCName.member("unionLayout")
    public val sequenceLayout: MemberName = memoryLayoutCName.member("sequenceLayout")
    public val paddingLayout: MemberName = memoryLayoutCName.member("paddingLayout")

    public val pathElementCName: ClassName = memoryLayoutCName.nestedClass("PathElement")
    public val groupElementMember: MemberName = pathElementCName.member("groupElement")

    public val valueLayoutCName: ClassName = ValueLayout::class.asClassName()
    public val addressLayoutMember: MemberName = valueLayoutCName.member("ADDRESS")

    public val methodHandleCName: ClassName = MethodHandle::class.asClassName()
    public val methodHandlesCName: ClassName = MethodHandles::class.asClassName()

    public val methodTypeCName: ClassName = ClassName("java.lang.invoke", "MethodType")

    public val memorySegmentCName: ClassName = MemorySegment::class.asClassName()
    public val copyMember: MemberName = memorySegmentCName.member("copy")

    public val cstrMember: MemberName = MemberName("net.echonolix.caelum", "c_str")

    public val starWildcard: WildcardTypeName = WildcardTypeName.producerOf(ANY.copy(nullable = true))

    public val memoryStackMember: MemberName = MemberName("net.echonolix.caelum", "MemoryStack")

    public val mallocMember: MemberName = MemberName("net.echonolix.caelum", "malloc")
}