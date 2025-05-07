package net.echonolix.caelum.codegen.api

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle

public object CaelumCodegenHelper {
    public const val basePkgName: String = "net.echonolix.caelum"

    public object NType {
        public val cName: ClassName = ClassName(basePkgName, "NType")
        public val descriptorCName: ClassName = cName.nestedClass("Descriptor")
        public val descriptorImplCName: ClassName = descriptorCName.nestedClass("Impl")
    }

    public object NPrimitive {
        public val cName: ClassName = ClassName(basePkgName, "NPrimitive")

        public val nativeDataCName: ClassName = cName.nestedClass("NativeData")
        public val nativeDataImplCName: ClassName = nativeDataCName.nestedClass("Impl")

        public val kotlinAPICName: ClassName = cName.nestedClass("KotlinAPI")

        public val descriptorCName: ClassName = cName.nestedClass("Descriptor")
        public val typeObjectCName: ClassName = cName.nestedClass("TypeObject")
    }

    public object NComposite {
        public val cName: ClassName = ClassName(basePkgName, "NComposite")
        public val implCName: ClassName = cName.nestedClass("Impl")
        public val descriptorCName: ClassName = cName.nestedClass("Descriptor")
        public val descriptorImplCName: ClassName = descriptorCName.nestedClass("Impl")
    }

    public object NEnum {
        public val cName: ClassName = ClassName(basePkgName, "NEnum")
        public val descriptorCName: ClassName = cName.nestedClass("Descriptor")
        public val typeObjectCName: ClassName = cName.nestedClass("TypeObject")
    }

    public object NFunction {
        public val cName: ClassName = ClassName(basePkgName, "NFunction")
        public val implCName: ClassName = cName.nestedClass("Impl")
        public val typeDescriptorCName: ClassName = cName.nestedClass("Descriptor")
    }

    public val allocOverloadCName: ClassName = ClassName(basePkgName, "AllocateOverload")

    public val enumCName: ClassName = ClassName(basePkgName, "NEnum")
    public val structCName: ClassName = ClassName(basePkgName, "NStruct")
    public val unionCName: ClassName = ClassName(basePkgName, "NUnion")

    public val arrayCName: ClassName = ClassName(basePkgName, "NArray")
    public val valueCName: ClassName = ClassName(basePkgName, "NValue")
    public val pointerCName: ClassName = ClassName(basePkgName, "NPointer")

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

    public val varHandleCName: ClassName = VarHandle::class.asClassName()

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