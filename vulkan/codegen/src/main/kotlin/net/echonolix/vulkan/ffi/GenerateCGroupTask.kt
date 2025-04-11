package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.ktffi.*
import net.echonolix.ktffi.KTFFICodegenHelper
import net.echonolix.vulkan.schema.Element
import net.echonolix.vulkan.schema.PatchedRegistry
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.StructLayout
import java.lang.foreign.UnionLayout
import java.lang.invoke.MethodHandle
import java.lang.invoke.VarHandle
import java.util.concurrent.RecursiveAction
import kotlin.collections.get

class GenerateCGroupTask(private val genCtx: FFIGenContext, private val registry: PatchedRegistry) : RecursiveAction() {
    override fun compute() {
        val groupTypeList = registry.groupTypes.asSequence()
            .filter { (_, type) -> genCtx.filter(type) }
            .map { it.toPair() }
            .toList()

        val unionList = registry.unionTypes.asSequence()
            .filter { (_, type) -> genCtx.filter(type) }
            .map { it.toPair() }
            .toList()
        val genUnionAliasTask = GenTypeAliasTask(genCtx, unionList).fork()

        val structList = registry.flagTypes.asSequence()
            .filter { (_, type) -> genCtx.filter(type) }
            .map { it.toPair() }
            .toList()
        val genStructAliasTask = GenTypeAliasTask(genCtx, structList).fork()

        val vkUnionFile = FileSpec.builder(VKFFI.vkUnionCname)
            .addType(
                TypeSpec.classBuilder(VKFFI.vkUnionCname)
                    .addModifiers(KModifier.SEALED)
                    .superclass(KTFFICodegenHelper.unionCname)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter("layout", UnionLayout::class)
                            .build()
                    )
                    .addSuperclassConstructorParameter("layout")
                    .build()
            )
        genCtx.writeOutput(vkUnionFile)

        val vkStructFile = FileSpec.builder(VKFFI.vkStructCname)
            .addType(
                TypeSpec.classBuilder(VKFFI.vkStructCname)
                    .addModifiers(KModifier.SEALED)
                    .superclass(KTFFICodegenHelper.structCname)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter("layout", StructLayout::class)
                            .build()
                    )
                    .addSuperclassConstructorParameter("layout")
                    .build()
            )
        genCtx.writeOutput(vkStructFile)

        groupTypeList.parallelStream()
            .filter { (name, type) -> name == type.name }
            .map { (_, groupType) -> genGroupType(groupType) }
            .forEach { genCtx.writeOutput(it) }

        val unionAliasesFile = FileSpec.builder(genCtx.unionPackageName, "UnionAliases")
        genUnionAliasTask.join().forEach {
            unionAliasesFile.addTypeAlias(it)
        }
        genCtx.writeOutput(unionAliasesFile)

        val structAliasesFile = FileSpec.builder(genCtx.structPackageName, "StructAliases")
        genStructAliasTask.join().forEach {
            structAliasesFile.addTypeAlias(it)
        }
        genCtx.writeOutput(structAliasesFile)
    }

    private fun genGroupType(groupType: Element.Group): FileSpec.Builder {
        val groupInfo = getGroupInfo(registry, groupType)
        val packageName: String
        val superCname: ClassName
        val memoryLayoutMember: MemberName
        when (groupType) {
            is Element.Struct -> {
                packageName = genCtx.structPackageName
                superCname = VKFFI.vkStructCname
                memoryLayoutMember = MemoryLayout::class.member("structLayout")
            }
            is Element.Union -> {
                packageName = genCtx.unionPackageName
                superCname = VKFFI.vkUnionCname
                memoryLayoutMember = MemoryLayout::class.member("unionLayout")
            }
        }

        val file = FileSpec.builder(packageName, groupType.name)
        file.addFunctions(groupInfo.topLevelFunctions)
        file.addProperties(groupInfo.topLevelProperties)

        val structClass = TypeSpec.objectBuilder(groupInfo.cname)
        structClass.tryAddKdoc(groupType)
        structClass.superclass(superCname)
        structClass.addSuperclassConstructorParameter(
            CodeBlock.builder()
                .add("\n")
                .indent()
                .addStatement("%M(", memoryLayoutMember)
                .add(groupInfo.layoutInitializer.build())
                .add(").withName(%S)\n", groupType.name)
                .unindent()
                .build()
        )
        structClass.addProperties(groupInfo.properties)

        file.addType(structClass.build())
        return file
    }


    sealed class GroupInfo(val type: Element.Type, val cname: ClassName) {
        val arrayCnameP = KTFFICodegenHelper.arrayCname.parameterizedBy(cname)
        val pointerCnameP = KTFFICodegenHelper.pointerCname.parameterizedBy(cname)
        val valueCnameP = KTFFICodegenHelper.valueCname.parameterizedBy(cname)

        val properties = mutableListOf<PropertySpec>()
        val topLevelProperties = mutableListOf<PropertySpec>()
        val topLevelFunctions = mutableListOf<FunSpec>()
        val layoutInitializer = CodeBlock.builder().indent()

        init {
            topLevelFunctions.add(
                FunSpec.builder("elementAddress")
                    .receiver(arrayCnameP)
                    .addModifiers(KModifier.INLINE)
                    .addParameter("index", LONG)
                    .returns(LONG)
                    .addStatement(
                        "return %T.arrayByteOffsetHandle.invokeExact(_segment.address(), index) as Long",
                        cname
                    )
                    .build()
            )
            topLevelFunctions.add(
                FunSpec.builder("get")
                    .receiver(arrayCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .returns(pointerCnameP)
                    .addStatement(
                        "return %T(elementAddress(index))",
                        KTFFICodegenHelper.pointerCname
                    )
                    .build()
            )
            topLevelFunctions.add(
                FunSpec.builder("set")
                    .receiver(arrayCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .addParameter("value", valueCnameP)
                    .addStatement(
                        "%M(value._segment, 0L, _segment, elementAddress(index), %T.arrayLayout.byteSize())",
                        MemorySegment::class.member("copy"),
                        cname
                    )
                    .build()
            )
            topLevelFunctions.add(
                FunSpec.builder("set")
                    .receiver(arrayCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .addParameter("value", pointerCnameP)
                    .addStatement(
                        "%M(%M, value._address, _segment, elementAddress(index), %T.arrayLayout.byteSize())",
                        MemorySegment::class.member("copy"),
                        KTFFICodegenHelper.omniSegment,
                        cname
                    )
                    .build()
            )
            topLevelFunctions.add(
                FunSpec.builder("elementAddress")
                    .receiver(pointerCnameP)
                    .addModifiers(KModifier.INLINE)
                    .addParameter("index", LONG)
                    .returns(LONG)
                    .addStatement(
                        "return %T.arrayByteOffsetHandle.invokeExact(_address, index) as Long",
                        cname
                    )
                    .build()
            )
            topLevelFunctions.add(
                FunSpec.builder("get")
                    .receiver(pointerCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .returns(pointerCnameP)
                    .addStatement(
                        "return %T(elementAddress(index))",
                        KTFFICodegenHelper.pointerCname
                    )
                    .build()
            )
            topLevelFunctions.add(
                FunSpec.builder("set")
                    .receiver(pointerCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .addParameter("value", valueCnameP)
                    .addStatement(
                        "%M(value._segment, 0L, %M, elementAddress(index), %T.arrayLayout.byteSize())",
                        MemorySegment::class.member("copy"),
                        KTFFICodegenHelper.omniSegment,
                        cname
                    )
                    .build()
            )
            topLevelFunctions.add(
                FunSpec.builder("set")
                    .receiver(pointerCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .addParameter("value", pointerCnameP)
                    .addStatement(
                        "%M(%M, value._address, %M, elementAddress(index), %T.arrayLayout.byteSize())",
                        MemorySegment::class.member("copy"),
                        KTFFICodegenHelper.omniSegment,
                        KTFFICodegenHelper.omniSegment,
                        cname
                    )
                    .build()
            )
        }

        abstract fun finish()
    }

    class StructInfo(type: Element.Type, cname: ClassName) : GroupInfo(type, cname) {
        override fun finish() {
            layoutInitializer.unindent()
        }
    }

    class UnionInfo(type: Element.Type, cname: ClassName) : GroupInfo(type, cname) {
        override fun finish() {
            layoutInitializer.unindent()
        }
    }

    inner class DefaultVisitor(private val registry: PatchedRegistry, private val groupInfo: GroupInfo) :
        MemberVisitor {

        private fun common(member: Element.Member) {
            groupInfo.properties.add(
                PropertySpec.builder("${member.name}_valueVarHandle", VarHandle::class.asClassName())
                    .addAnnotation(JvmField::class)
                    .initializer(
                        "layout.varHandle(%M(%S))",
                        MemoryLayout.PathElement::class.member("groupElement"),
                        member.name
                    )
                    .build()
            )
            groupInfo.properties.add(
                PropertySpec.builder("${member.name}_arrayVarHandle", VarHandle::class.asClassName())
                    .addAnnotation(JvmField::class)
                    .initializer(
                        "layout.arrayElementVarHandle(%M(%S))",
                        MemoryLayout.PathElement::class.member("groupElement"),
                        member.name
                    )
                    .build()
            )
            groupInfo.properties += PropertySpec.builder("${member.name}_offsetHandle", MethodHandle::class)
                .addAnnotation(JvmField::class)
                .initializer(
                    "layout.byteOffsetHandle(%M(%S))",
                    MemoryLayout.PathElement::class.member("groupElement"),
                    member.name
                )
                .build()
            groupInfo.properties += PropertySpec.builder("${member.name}_layout", MemoryLayout::class)
                .addAnnotation(JvmField::class)
                .initializer(
                    "layout.select(%M(%S))",
                    MemoryLayout.PathElement::class.member("groupElement"),
                    member.name
                )
                .build()
            groupInfo.properties += PropertySpec.builder("${member.name}_byteSize", Long::class)
                .addAnnotation(JvmField::class)
                .initializer("%N.byteSize()", "${member.name}_layout")
                .build()
        }

        private fun structOrUnion(member: Element.Member, type: Element.Group, info: GroupInfo) {
            val groupCname = ClassName(info.cname.packageName, member.type)
            groupInfo.layoutInitializer.addStatement(
                "%T.%N.withName(%S),",
                groupCname,
                "layout",
                member.name
            )

            val str = if (type is Element.Struct) "struct " else "union "
            val annotations = listOf(
                AnnotationSpec.builder(CTypeName::class)
                    .addMember("%S", str + member.type)
                    .build(),
            )
            val pointerCnameP = KTFFICodegenHelper.pointerCname.parameterizedBy(groupCname)
            groupInfo.topLevelProperties.add(
                PropertySpec.builder(member.name, pointerCnameP)
                    .addAnnotations(annotations)
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(groupInfo.valueCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return %T(%T.%N_offsetHandle.invokeExact(_segment.address(), 0L) as Long)",
                                KTFFICodegenHelper.pointerCname,
                                groupInfo.cname,
                                member.name
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", pointerCnameP)
                            .addStatement(
                                "%M(%M, value._address, %M, %T.%N_offsetHandle.invokeExact(_segment.address(), 0L) as Long, %T.%N_byteSize)",
                                MemorySegment::class.member("copy"),
                                KTFFICodegenHelper.omniSegment,
                                KTFFICodegenHelper.omniSegment,
                                groupInfo.cname,
                                member.name,
                                groupInfo.cname,
                                member.name
                            )
                            .build()
                    )
                    .build()
                )
            groupInfo.topLevelProperties.add(
                PropertySpec.builder(member.name, pointerCnameP)
                    .addAnnotations(annotations)
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(groupInfo.pointerCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return %T(%T.%N_offsetHandle.invokeExact(_address, 0L) as Long)",
                                KTFFICodegenHelper.pointerCname,
                                groupInfo.cname,
                                member.name
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", pointerCnameP)
                            .addStatement(
                                "%M(%M, value._address, %M, %T.%N_offsetHandle.invokeExact(_address, 0L) as Long, %T.%N_byteSize)",
                                MemorySegment::class.member("copy"),
                                KTFFICodegenHelper.omniSegment,
                                KTFFICodegenHelper.omniSegment,
                                groupInfo.cname,
                                member.name,
                                groupInfo.cname,
                                member.name
                            )
                            .build()
                    )
                    .build()
            )
        }

        private fun array(member: Element.Member, type: Element.Type) {
            if (groupInfo.type.name == "VkTransformMatrixKHR") {
                //TODO: 2D array support
                return
            }

            val lengthCodeBlock: CodeBlock
            val lengthCodeBlockAnnotation: CodeBlock

            val lengthAsInt = member.length?.toIntOrNull()
            if (lengthAsInt != null) {
                lengthCodeBlock = CodeBlock.of("%L", lengthAsInt)
                lengthCodeBlockAnnotation = CodeBlock.of("%LU", lengthAsInt)
            } else {
                val memberName = MemberName(genCtx.enumPackageName, member.length!!)
                lengthCodeBlock = CodeBlock.of("%M.toLong()", memberName)
                lengthCodeBlockAnnotation = CodeBlock.of("%M", memberName)
            }

            val elementCname: ClassName
            if (type is Element.BasicType) {
                if (type.value == CBasicType.char) {
                    check(groupInfo.type.name == "VkPhysicalDeviceLayeredApiPropertiesKHR" || member.xml.len == "null-terminated")
                }
                groupInfo.layoutInitializer.add("%M(", MemoryLayout::class.member("sequenceLayout"))
                groupInfo.layoutInitializer.add(lengthCodeBlock)
                groupInfo.layoutInitializer.addStatement(
                    ", %M).withName(%S),",
                    type.value.valueLayoutMember,
                    member.name
                )
                elementCname = ClassName(KTFFICodegenHelper.packageName, type.value.name)
            } else {
                val packageName = genCtx.getPackageName(type)
                elementCname = ClassName(packageName, type.name)
                groupInfo.layoutInitializer.add("%M(", MemoryLayout::class.member("sequenceLayout"))
                groupInfo.layoutInitializer.add(lengthCodeBlock)
                groupInfo.layoutInitializer.addStatement(
                    ", %T.arrayLayout).withName(%S),",
                    elementCname,
                    member.name
                )
            }

            val annotations = listOf(
                AnnotationSpec.builder(CTypeName::class)
                    .addMember("%S", member.type)
                    .build(),
                AnnotationSpec.builder(CArrayType::class)
                    .addMember(lengthCodeBlockAnnotation)
                    .build()
            )

            val pointerElementCname = KTFFICodegenHelper.pointerCname.parameterizedBy(elementCname)
            groupInfo.topLevelProperties.add(
                PropertySpec.builder(member.name, pointerElementCname)
                    .addAnnotations(annotations)
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(groupInfo.valueCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return %T(%T.%N_offsetHandle.invokeExact(_segment.address(), 0L) as Long)",
                                KTFFICodegenHelper.pointerCname,
                                groupInfo.cname,
                                member.name
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", pointerElementCname)
                            .addStatement(
                                "%M(%M, value._address, %M, %T.%N_offsetHandle.invokeExact(_segment.address(), 0L) as Long, %T.%N_byteSize)",
                                MemorySegment::class.member("copy"),
                                KTFFICodegenHelper.omniSegment,
                                KTFFICodegenHelper.omniSegment,
                                groupInfo.cname,
                                member.name,
                                groupInfo.cname,
                                member.name
                            )
                            .build()
                    )
                    .build()
            )
            groupInfo.topLevelProperties.add(
                PropertySpec.builder(member.name, pointerElementCname)
                    .addAnnotations(annotations)
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(groupInfo.pointerCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return %T(%T.%N_offsetHandle.invokeExact(_address, 0L) as Long)",
                                KTFFICodegenHelper.pointerCname,
                                groupInfo.cname,
                                member.name
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", pointerElementCname)
                            .addStatement(
                                "%M(%M, value._address, %M, %T.%N_offsetHandle.invokeExact(_address, 0L) as Long, %T.%N_byteSize)",
                                MemorySegment::class.member("copy"),
                                KTFFICodegenHelper.omniSegment,
                                KTFFICodegenHelper.omniSegment,
                                groupInfo.cname,
                                member.name,
                                groupInfo.cname,
                                member.name
                            )
                            .build()
                    )
                    .build()
            )
        }

        private fun pointer(member: Element.Member, type: Element.Type) {
            val layoutCode: CodeBlock

            val constructorType: TypeName
            val pointerTargetCname: TypeName

            if (type is Element.BasicType) {
                layoutCode = CodeBlock.of("%M", type.value.valueLayoutMember)
                if (type.value == CBasicType.void) {
                    if (member.name == "pNext") {
                        constructorType = KTFFICodegenHelper.pointerCname
                        pointerTargetCname = KTFFICodegenHelper.pointerCname.parameterizedBy(WildcardTypeName.producerOf(VKFFI.vkStructCname))
                    } else {
                        constructorType = KTFFICodegenHelper.pointerCname.parameterizedBy(CBasicType.uint8_t.nativeTypeName)
                        pointerTargetCname = KTFFICodegenHelper.pointerCname.parameterizedBy(type.value.nativeTypeName)
                    }
                } else {
                    constructorType = KTFFICodegenHelper.pointerCname
                    pointerTargetCname = KTFFICodegenHelper.pointerCname.parameterizedBy(type.value.nativeTypeName)
                }
            } else {
                val packageName = genCtx.getPackageName(type)
                val elementCname = ClassName(packageName, type.name)
                layoutCode = CodeBlock.of("%T.arrayLayout", elementCname)
                constructorType = KTFFICodegenHelper.pointerCname
                pointerTargetCname = KTFFICodegenHelper.pointerCname.parameterizedBy(elementCname)
            }

            groupInfo.layoutInitializer.add(
                CodeBlock.builder()
                    .add("%M(", KTFFICodegenHelper.pointerLayoutMember)
                    .add(layoutCode)
                    .addStatement(").withName(%S),", member.name)
                    .build()
            )

            val annotations = listOf(
                AnnotationSpec.builder(CTypeName::class)
                    .addMember("%S", member.type)
                    .build(),
                AnnotationSpec.builder(CPointerType::class)
                    .apply {
                        if (member.xml.len != null) {
                            addMember("lengthVariable = %S", member.xml.altlen ?: member.xml.len)
                        }
                    }
                    .build()
            )

            groupInfo.topLevelProperties.add(
                PropertySpec.builder(member.name, pointerTargetCname)
                    .addAnnotations(annotations)
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(groupInfo.valueCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return %T(%T.%N.get(_segment, 0L) as Long)",
                                constructorType,
                                groupInfo.cname,
                                "${member.name}_valueVarHandle"
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", pointerTargetCname)
                            .addStatement(
                                "%T.%N.set(_segment, 0L, value._address)",
                                groupInfo.cname,
                                "${member.name}_valueVarHandle",
                            )
                            .build()
                    )
                    .build()
            )
            groupInfo.topLevelProperties.add(
                PropertySpec.builder(member.name, pointerTargetCname)
                    .addAnnotations(annotations)
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(groupInfo.pointerCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return %T(%T.%N.get(%M, _address) as Long)",
                                constructorType,
                                groupInfo.cname,
                                "${member.name}_valueVarHandle",
                                    KTFFICodegenHelper.omniSegment
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", pointerTargetCname)
                            .addStatement(
                                "%T.%N.set(%M, _address, value._address)",
                                groupInfo.cname,
                                "${member.name}_valueVarHandle",
                                KTFFICodegenHelper.omniSegment
                            )
                            .build()
                    )
                    .build()
            )
        }

        private fun cbasicTypeAccess(
            member: Element.Member,
            cBasicType: CBasicType<*>,
            block: PropertySpec.Builder.() -> Unit = {}
        ) {
            groupInfo.topLevelProperties.add(
                PropertySpec.builder(member.name, cBasicType.kotlinTypeName)
                    .addAnnotation(
                        AnnotationSpec.builder(CTypeName::class)
                            .addMember("%S", member.type)
                            .build()
                    )
                    .apply(block)
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(groupInfo.valueCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return (%T.%N.get(_segment, 0L) as %T)${cBasicType.fromBase}",
                                groupInfo.cname,
                                "${member.name}_valueVarHandle",
                                cBasicType.baseType.asTypeName()
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", cBasicType.kotlinTypeName)
                            .addStatement(
                                "%T.%N.set(_segment, 0L, value${cBasicType.toBase})",
                                groupInfo.cname,
                                "${member.name}_valueVarHandle",
                            )
                            .build()
                    )
                    .build()
            )
            groupInfo.topLevelProperties.add(
                PropertySpec.builder(member.name, cBasicType.kotlinTypeName)
                    .addAnnotation(
                        AnnotationSpec.builder(CTypeName::class)
                            .addMember("%S", member.type)
                            .build()
                    )
                    .apply(block)
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(groupInfo.pointerCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return (%T.%N.get(%M, _address) as %T)${cBasicType.fromBase}",
                                groupInfo.cname,
                                "${member.name}_valueVarHandle",
                                KTFFICodegenHelper.omniSegment,
                                cBasicType.baseType.asTypeName()
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", cBasicType.kotlinTypeName)
                            .addStatement(
                                "%T.%N.set(%M, _address, value${cBasicType.toBase})",
                                groupInfo.cname,
                                "${member.name}_valueVarHandle",
                                KTFFICodegenHelper.omniSegment
                            )
                            .build()
                    )
                    .build()
            )
        }

        private fun cenumTypeAccess(member: Element.Member, type: Element.Type, cBasicType: CBasicType<*>) {
            val typeCname = ClassName(genCtx.enumPackageName, type.name)
            groupInfo.topLevelProperties.add(
                PropertySpec.builder(member.name, typeCname)
                    .addAnnotation(
                        AnnotationSpec.builder(CTypeName::class)
                            .addMember("%S", member.type)
                            .build()
                    )
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(groupInfo.valueCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return %T.fromInt((%T.%N.get(_segment, 0L) as %T))",
                                typeCname,
                                groupInfo.cname,
                                "${member.name}_valueVarHandle",
                                cBasicType.baseType.asTypeName()
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", cBasicType.kotlinTypeName)
                            .addStatement(
                                "%T.%N.set(_segment, 0L, %T.toInt(value))",
                                groupInfo.cname,
                                "${member.name}_valueVarHandle",
                                typeCname
                            )
                            .build()
                    )
                    .build()
            )
            groupInfo.topLevelProperties.add(
                PropertySpec.builder(member.name, typeCname)
                    .addAnnotation(
                        AnnotationSpec.builder(CTypeName::class)
                            .addMember("%S", member.type)
                            .build()
                    )
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(groupInfo.pointerCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return %T.fromInt((%T.%N.get(%M, _address) as %T))",
                                typeCname,
                                groupInfo.cname,
                                "${member.name}_valueVarHandle",
                                KTFFICodegenHelper.omniSegment,
                                cBasicType.baseType.asTypeName()
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", cBasicType.kotlinTypeName)
                            .addStatement(
                                "%T.%N.set(%M, _address, %T.toInt(value))",
                                groupInfo.cname,
                                "${member.name}_valueVarHandle",
                                KTFFICodegenHelper.omniSegment,
                                typeCname
                            )
                            .build()
                    )
                    .build()
            )
        }

        private fun cbasicType(member: Element.Member, cBasicType: CBasicType<*>) {
            groupInfo.layoutInitializer.addStatement(
                "%M.withName(%S),",
                cBasicType.valueLayoutMember,
                member.name
            )
        }

        override fun visit(member: Element.Member) {
            common(member)
        }

        override fun visitOpaqueType(index: Int, member: Element.Member, name: String) {
            //TODO: Opaque type support
//            println("Unsupported opaque type: <${member.type} ${member.name}>")
        }

        override fun visitBasicType(index: Int, member: Element.Member, type: Element.BasicType) {
            cbasicType(member, type.value)
            cbasicTypeAccess(member, type.value)
        }

        override fun visitHandleType(index: Int, member: Element.Member, type: Element.HandleType) {
            val cBasicType = CBasicType.uint64_t
            cbasicType(member, cBasicType)
            cbasicTypeAccess(member, cBasicType) {
                addAnnotation(CHandleType::class)
            }
        }

        override fun visitEnumType(index: Int, member: Element.Member, type: Element.EnumType) {
            cbasicType(member, CBasicType.int32_t)
            cenumTypeAccess(member, type, CBasicType.int32_t)
        }

        override fun visitFlagType(
            index: Int,
            member: Element.Member,
            type: Element.FlagType,
            flagBitType: Element.FlagBitType?
        ) {
            val cBasicType = registry.flagBitTypes[type.bitType]?.type ?: CBasicType.int32_t
            cbasicType(member, cBasicType)
            cenumTypeAccess(member, type, cBasicType)
        }

        override fun visitFuncpointerType(
            index: Int,
            member: Element.Member,
            type: Element.FuncpointerType
        ) {
//            println(member)
//            println(type)
//            println()
        }

        override fun visitStructType(index: Int, member: Element.Member, type: Element.Struct) {
            val structInfo = getStructInfo(registry, member.type)
            structOrUnion(member, type, structInfo)
        }

        override fun visitUnionType(index: Int, member: Element.Member, type: Element.Union) {
            val unionInfo = getUnionInfo(registry, member.type)
            structOrUnion(member, type, unionInfo)
        }

        override fun visitPointer(index: Int, member: Element.Member, type: Element.Type) {
            pointer(member, type)
        }

        override fun visitArray(index: Int, member: Element.Member, type: Element.Type) {
            array(member, type)
        }
    }

    private fun visitStruct(registry: PatchedRegistry, struct: Element.Group, visitor: MemberVisitor) {
        fun unTypeDef(type: Element.Type?): Element.Type? {
            if (type == null) {
                return null
            }

            var deTypeDef = type
            while (deTypeDef is Element.TypeDef) {
                deTypeDef = deTypeDef.value
            }
            return deTypeDef
        }

        val typeText = struct.javaClass.simpleName.lowercase()

        for (i in struct.members.indices) {
            val member = struct.members[i]
            runCatching {
                var fixedType = member.type.removePrefix("const ")
                fixedType = fixedType.removePrefix("struct ")
                val withoutStar = fixedType.removeSuffix("*")
                if (withoutStar in registry.externalTypes || registry.opaqueTypes.containsKey(withoutStar)) {
                    check(withoutStar.length == fixedType.length - 1)
                    visitor.visitOpaqueType(i, member, withoutStar)
                    return@runCatching
                }

                visitor.visit(member)

                if (withoutStar.length == fixedType.length - 1) {
                    visitor.visitPointer(i, member, unTypeDef(registry.allTypes[withoutStar])!!)
                    return@runCatching
                }

                if (fixedType.endsWith("[]")) {
                    val withoutBrackets = fixedType.removeSuffix("[]")
                    visitor.visitArray(i, member, unTypeDef(registry.allTypes[withoutBrackets])!!)
                    return@runCatching
                }

                val deTypeDef = unTypeDef(registry.allTypes[fixedType])
                val basicType = deTypeDef as? Element.BasicType
                if (basicType != null) {
                    visitor.visitBasicType(i, member, basicType)
                    return@runCatching
                }

                val handleType = deTypeDef as? Element.HandleType
                if (handleType != null) {
                    visitor.visitHandleType(i, member, handleType)
                    return@runCatching
                }

                val enumType = deTypeDef as? Element.EnumType
                if (enumType != null) {
                    visitor.visitEnumType(i, member, enumType)
                    return@runCatching
                }

                val flagType = deTypeDef as? Element.FlagType
                if (flagType != null) {
                    visitor.visitFlagType(i, member, flagType, registry.flagBitTypes[flagType.bitType])
                    return@runCatching
                }

                val flagBitType = deTypeDef as? Element.FlagBitType
                if (flagBitType != null) {
                    visitor.visitFlagType(i, member, flagBitType.bitmaskType!!, flagBitType)
                    return@runCatching
                }

                val funcpointerType = deTypeDef as? Element.FuncpointerType
                if (funcpointerType != null) {
                    visitor.visitFuncpointerType(i, member, funcpointerType)
                    return@runCatching
                }

                val structType = deTypeDef as? Element.Struct
                if (structType != null) {
                    visitor.visitStructType(i, member, structType)
                    return@runCatching
                }

                val unionType = deTypeDef as? Element.Union
                if (unionType != null) {
                    visitor.visitUnionType(i, member, unionType)
                    return@runCatching
                }
                error("Unsupported type: $fixedType($deTypeDef)")
            }.onFailure {
                throw RuntimeException(
                    "Failed to process member <${member.type} ${member.name}> of <$typeText ${struct.name}>",
                    it
                )
            }
        }
    }

    private fun getUnionInfo(registry: PatchedRegistry, name: String): GroupInfo {
        val union = registry.unionTypes[name] ?: error("Struct $name not found")
        return UnionInfo(union, ClassName(genCtx.unionPackageName, name)).apply {
            visitStruct(registry, union, DefaultVisitor(registry, this))
            finish()
        }
    }

    private fun getStructInfo(registry: PatchedRegistry, name: String): GroupInfo {
        val struct = registry.structTypes[name] ?: error("Struct $name not found")
        return StructInfo(struct, ClassName(genCtx.structPackageName, name)).apply {
            visitStruct(registry, struct, DefaultVisitor(registry, this))
            finish()
        }
    }

    private fun getGroupInfo(registry: PatchedRegistry, group: Element.Group): GroupInfo {
        return when (group) {
            is Element.Struct -> getStructInfo(registry, group.name)
            is Element.Union -> getUnionInfo(registry, group.name)
        }
    }
}