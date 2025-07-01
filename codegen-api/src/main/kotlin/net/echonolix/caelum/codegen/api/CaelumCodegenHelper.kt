package net.echonolix.caelum.codegen.api

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import java.lang.foreign.FunctionDescriptor
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

    public object NStruct {
        public val cName: ClassName = ClassName(basePkgName, "NStruct")
        public val implCName: ClassName = cName.nestedClass("Impl")
    }

    public object NUnion {
        public val cName: ClassName = ClassName(basePkgName, "NUnion")
        public val implCName: ClassName = cName.nestedClass("Impl")
    }

    public val enumCName: ClassName = ClassName(basePkgName, "NEnum")

    public val arrayCName: ClassName = ClassName(basePkgName, "NArray")
    public val valueCName: ClassName = ClassName(basePkgName, "NValue")
    public val pointerCName: ClassName = ClassName(basePkgName, "NPointer")

    public val helperCName: ClassName = ClassName(basePkgName, "APIHelper")
    public val omniSegment: MemberName = helperCName.member("_\$OMNI_SEGMENT\$_")
    public val linker: MemberName = helperCName.member("LINKER")
    public val loaderLookup: MemberName = helperCName.member("LOADER_LOOKUP")
    public val pointerLayoutMember: MemberName = helperCName.member("POINTER_LAYOUT")
    public val symbolLookup: MemberName = helperCName.member("SYMBOL_LOOKUP")
    public val findSymbolMemberName: MemberName = helperCName.member("findSymbol")
    public val functionDescriptorOfMemberName: MemberName = helperCName.member("functionDescriptorOf")
    public val downcallHandleOfMemberName: MemberName = helperCName.member("downcallHandleOf")

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
    public val functionDescriptorCName: ClassName = FunctionDescriptor::class.asClassName()

    public val methodTypeCName: ClassName = ClassName("java.lang.invoke", "MethodType")

    public val memorySegmentCName: ClassName = MemorySegment::class.asClassName()
    public val copyMember: MemberName = memorySegmentCName.member("copy")

    public val cstrMember: MemberName = MemberName("net.echonolix.caelum", "c_str")

    public val memoryStackMember: MemberName = MemberName("net.echonolix.caelum", "MemoryStack")

    public val mallocMember: MemberName = MemberName("net.echonolix.caelum", "malloc")

    public val unsafeAPICName: ClassName = ClassName(basePkgName, "UnsafeAPI")
    public val unsafeAPIAnnotation: AnnotationSpec = AnnotationSpec.builder(unsafeAPICName).build()

    public val structAccessorCName: ClassName = ClassName(basePkgName, "StructAccessor")
    public val structAccessorAnnotation: AnnotationSpec = AnnotationSpec.builder(structAccessorCName).build()
}