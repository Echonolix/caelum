package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.ktffi.CBasicType
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.CTypeName
import net.echonolix.ktffi.KTFFICodegenHelper
import java.lang.foreign.MemoryLayout
import java.lang.invoke.MethodHandle
import java.lang.invoke.VarHandle

class GenerateGroupTask(ctx: VKFFICodeGenContext) : VKFFITask<Unit>(ctx) {
    override fun VKFFICodeGenContext.compute() {
        val struct = StructTask().fork()
        val union = UnionTask().fork()

        val unionTypeVar = TypeVariableName("T", VKFFI.vkUnionCname.parameterizedBy(TypeVariableName("T")))
        val vkUnionFile = FileSpec.builder(VKFFI.vkUnionCname)
            .addType(
                TypeSpec.classBuilder(VKFFI.vkUnionCname)
                    .addTypeVariable(unionTypeVar)
                    .addModifiers(KModifier.SEALED)
                    .superclass(KTFFICodegenHelper.unionCname.parameterizedBy(unionTypeVar))
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter("members", KTFFICodegenHelper.memoryLayoutCname, KModifier.VARARG)
                            .build()
                    )
                    .addSuperclassConstructorParameter("*members")
                    .build()
            )
        ctx.writeOutput(vkUnionFile)

        val structTypeVar = TypeVariableName("T", VKFFI.vkStructCname.parameterizedBy(TypeVariableName("T")))
        val vkStructFile = FileSpec.builder(VKFFI.vkStructCname)
            .addType(
                TypeSpec.classBuilder(VKFFI.vkStructCname)
                    .addTypeVariable(structTypeVar)
                    .addModifiers(KModifier.SEALED)
                    .superclass(KTFFICodegenHelper.structCname.parameterizedBy(structTypeVar))
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter("members", KTFFICodegenHelper.memoryLayoutCname, KModifier.VARARG)
                            .build()
                    )
                    .addSuperclassConstructorParameter("*members")
                    .build()
            )
        ctx.writeOutput(vkStructFile)

        struct.join()
        union.join()
    }

    private inner class StructTask : VKFFITask<Unit>(ctx) {
        override fun VKFFICodeGenContext.compute() {
            val structTypes = ctx.filterType<CType.Struct>()
            val typeAlias = GenTypeAliasTask(this, structTypes).fork()

            structTypes.parallelStream()
                .filter { (name, type) -> name == type.name }
                .map { (_, structType) -> genGroupType(structType) }
                .forEach(ctx::writeOutput)

            typeAlias.joinAndWriteOutput(VKFFI.structPackageName)
        }
    }

    private inner class UnionTask : VKFFITask<Unit>(ctx) {
        override fun VKFFICodeGenContext.compute() {
            val unionTypes = ctx.filterType<CType.Union>()
            val typeAlias = GenTypeAliasTask(this, unionTypes).fork()

            unionTypes.parallelStream()
                .filter { (name, type) -> name == type.name }
                .map { (_, enumType) -> genGroupType(enumType) }
                .forEach(ctx::writeOutput)

            typeAlias.joinAndWriteOutput(VKFFI.unionPackageName)
        }
    }

    context(ctx: VKFFICodeGenContext)
    private fun genGroupType(groupType: CType.Group): FileSpec.Builder {
        val thisCname = groupType.className()
        val file = FileSpec.builder(thisCname)

        val typeObject = TypeSpec.objectBuilder(thisCname)
        typeObject.tryAddKdoc(groupType)
        val superCname = when (groupType) {
            is CType.Struct -> VKFFI.vkStructCname
            is CType.Union -> VKFFI.vkUnionCname
        }
        typeObject.superclass(superCname.parameterizedBy(thisCname))
        typeObject.addSuperclassConstructorParameter(
            CodeBlock.builder()
                .add("\n")
                .indent()
                .add(groupType.memoryLayoutDeep())
                .unindent()
                .build()
        )

        groupType.members.forEach { member ->
            typeObject.addProperty(
                PropertySpec.builder("${member.name}_valueVarHandle", VarHandle::class.asClassName())
                    .addAnnotation(JvmField::class)
                    .initializer(
                        "layout.varHandle(%M(%S))",
                        MemoryLayout.PathElement::class.member("groupElement"),
                        member.name
                    )
                    .build()
            )
            typeObject.addProperty(
                PropertySpec.builder("${member.name}_arrayVarHandle", VarHandle::class.asClassName())
                    .addAnnotation(JvmField::class)
                    .initializer(
                        "layout.arrayElementVarHandle(%M(%S))",
                        MemoryLayout.PathElement::class.member("groupElement"),
                        member.name
                    )
                    .build()
            )
            typeObject.addProperty(
                PropertySpec.builder("${member.name}_offsetHandle", MethodHandle::class)
                    .addAnnotation(JvmField::class)
                    .initializer(
                        "layout.byteOffsetHandle(%M(%S))",
                        MemoryLayout.PathElement::class.member("groupElement"),
                        member.name
                    )
                    .build()
            )
            typeObject.addProperty(
                PropertySpec.builder("${member.name}_layout", MemoryLayout::class)
                    .addAnnotation(JvmField::class)
                    .initializer(
                        "layout.select(%M(%S))",
                        MemoryLayout.PathElement::class.member("groupElement"),
                        member.name
                    )
                    .build()
            )
            typeObject.addProperty(
                PropertySpec.builder("${member.name}_byteSize", Long::class)
                    .addAnnotation(JvmField::class)
                    .initializer("%N.byteSize()", "${member.name}_layout")
                    .build()
            )
        }

        file.addType(typeObject.build())

        val arrayCnameP = KTFFICodegenHelper.arrayCname.parameterizedBy(thisCname)
        val pointerCnameP = KTFFICodegenHelper.pointerCname.parameterizedBy(thisCname)
        val valueCnameP = KTFFICodegenHelper.valueCname.parameterizedBy(thisCname)

        run {
            file.addFunction(
                FunSpec.builder("elementAddress")
                    .receiver(arrayCnameP)
                    .addModifiers(KModifier.INLINE)
                    .addParameter("index", LONG)
                    .returns(LONG)
                    .addStatement(
                        "return %T.arrayByteOffsetHandle.invokeExact(_segment.address(), index) as Long",
                        thisCname
                    )
                    .build()
            )
            file.addFunction(
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
            file.addFunction(
                FunSpec.builder("set")
                    .receiver(arrayCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .addParameter("value", valueCnameP)
                    .addStatement(
                        "%M(value._segment, 0L, _segment, elementAddress(index), %T.layout.byteSize())",
                        KTFFICodegenHelper.copyMember,
                        thisCname
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("set")
                    .receiver(arrayCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .addParameter("value", pointerCnameP)
                    .addStatement(
                        "%M(%M, value._address, _segment, elementAddress(index), %T.layout.byteSize())",
                        KTFFICodegenHelper.copyMember,
                        KTFFICodegenHelper.omniSegment,
                        thisCname
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("elementAddress")
                    .receiver(pointerCnameP)
                    .addModifiers(KModifier.INLINE)
                    .addParameter("index", LONG)
                    .returns(LONG)
                    .addStatement(
                        "return %T.arrayByteOffsetHandle.invokeExact(_address, index) as Long",
                        thisCname
                    )
                    .build()
            )
            file.addFunction(
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
            file.addFunction(
                FunSpec.builder("set")
                    .receiver(pointerCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .addParameter("value", valueCnameP)
                    .addStatement(
                        "%M(value._segment, 0L, %M, elementAddress(index), %T.layout.byteSize())",
                        KTFFICodegenHelper.copyMember,
                        KTFFICodegenHelper.omniSegment,
                        thisCname
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("set")
                    .receiver(pointerCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .addParameter("value", pointerCnameP)
                    .addStatement(
                        "%M(%M, value._address, %M, elementAddress(index), %T.layout.byteSize())",
                        KTFFICodegenHelper.copyMember,
                        KTFFICodegenHelper.omniSegment,
                        KTFFICodegenHelper.omniSegment,
                        thisCname
                    )
                    .build()
            )
        }

        fun basicTypeAccess(member: CType.Group.Member, cBasicType: CBasicType<*>, cTypeName: String) {
            file.addProperty(
                PropertySpec.builder(member.name, cBasicType.kotlinTypeName)
                    .addAnnotation(
                        AnnotationSpec.builder(CTypeName::class)
                            .addMember("%S", cTypeName)
                            .build()
                    )
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(valueCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return (%T.%N.get(_segment, 0L) as %T)${cBasicType.fromBase}",
                                thisCname,
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
                                thisCname,
                                "${member.name}_valueVarHandle",
                            )
                            .build()
                    )
                    .build()
            )
            file.addProperty(
                PropertySpec.builder(member.name, cBasicType.kotlinTypeName)
                    .addAnnotation(
                        AnnotationSpec.builder(CTypeName::class)
                            .addMember("%S", cTypeName)
                            .build()
                    )
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(pointerCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return (%T.%N.get(%M, _address) as %T)${cBasicType.fromBase}",
                                thisCname,
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
                                thisCname,
                                "${member.name}_valueVarHandle",
                                KTFFICodegenHelper.omniSegment
                            )
                            .build()
                    )
                    .build()
            )
        }

        fun CType.Group.Member.valueMemberOffset(): CodeBlock {
            return CodeBlock.builder()
                .addStatement(
                    "%T.%N.invokeExact(_segment.address(), 0L) as Long",
                    thisCname,
                    "${this.name}_offsetHandle"
                )
                .build()
        }

        fun CType.Group.Member.pointerMemberOffset(): CodeBlock {
            return CodeBlock.builder()
                .addStatement(
                    "%T.%N.invokeExact(%M, _address) as Long",
                    thisCname,
                    "${this.name}_offsetHandle",
                    KTFFICodegenHelper.omniSegment
                )
                .build()
        }

        fun nestedAccess(
            member: CType.Group.Member,
            memberPointerCnameP: TypeName,
            cTypeNameAnnotation: AnnotationSpec
        ) {
            val valueMemberOffset = member.valueMemberOffset()
            val pointerMemberOffset = member.pointerMemberOffset()
            file.addProperty(
                PropertySpec.builder(member.name, memberPointerCnameP)
                    .addAnnotation(cTypeNameAnnotation)
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(valueCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addCode(
                                CodeBlock.builder()
                                    .add(
                                        "return %T(",
                                        KTFFICodegenHelper.pointerCname,
                                    )
                                    .add(valueMemberOffset)
                                    .add(")")
                                    .build()
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", memberPointerCnameP)
                            .addCode(
                                CodeBlock.builder()
                                    .add(
                                        "%M(%M, value._address, %M, ",
                                        KTFFICodegenHelper.copyMember,
                                        KTFFICodegenHelper.omniSegment,
                                        KTFFICodegenHelper.omniSegment,
                                    )
                                    .add(valueMemberOffset)
                                    .add(
                                        ", %T.%N)",
                                        thisCname,
                                        "${member.name}_byteSize"
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            file.addProperty(
                PropertySpec.builder(member.name, memberPointerCnameP)
                    .addAnnotation(cTypeNameAnnotation)
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(pointerCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addCode(
                                CodeBlock.builder()
                                    .add(
                                        "return %T(",
                                        KTFFICodegenHelper.pointerCname,
                                    )
                                    .add(pointerMemberOffset)
                                    .add(")")
                                    .build()
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", memberPointerCnameP)
                            .addCode(
                                CodeBlock.builder()
                                    .add(
                                        "%M(%M, value._address, %M, ",
                                        KTFFICodegenHelper.copyMember,
                                        KTFFICodegenHelper.omniSegment,
                                        KTFFICodegenHelper.omniSegment,
                                    )
                                    .add(pointerMemberOffset)
                                    .add(
                                        ", %T.%N)",
                                        thisCname,
                                        "${member.name}_byteSize"
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder(member.name)
                    .addModifiers(KModifier.INLINE)
                    .receiver(valueCnameP)
                    .addParameter("block", LambdaTypeName.get(receiver = memberPointerCnameP, returnType = UNIT))
                    .addStatement("%N.block()", member.name)
                    .build()
            )
            file.addFunction(
                FunSpec.builder(member.name)
                    .addModifiers(KModifier.INLINE)
                    .receiver(pointerCnameP)
                    .addParameter("block", LambdaTypeName.get(receiver = memberPointerCnameP, returnType = UNIT))
                    .addStatement("%N.block()", member.name)
                    .build()
            )
        }

        @Suppress("JoinDeclarationAndAssignment")
        fun commonAccess(member: CType.Group.Member) {
            val memberType = member.type
            val ktApiType = when (memberType) {
                is CType.Pointer -> {
                    KTFFICodegenHelper.pointerCname
                }
                else -> {
                    memberType.ktApiType()
                }
            }
            var returnType = memberType.ktApiType()
            var fromIntTypeParamBlock = CodeBlock.of("")
            if (member.name == "pNext") {
                val vkStructStar = VKFFI.vkStructCname.parameterizedBy(KTFFICodegenHelper.starWildcard)
                val outVkStruct = WildcardTypeName.producerOf(vkStructStar)
                returnType = KTFFICodegenHelper.pointerCname.parameterizedBy(outVkStruct)
            } else if (memberType is CType.Pointer) {
                val eType = memberType.elementType
                if (eType is CType.BasicType && eType.baseType == CBasicType.void) {
                    fromIntTypeParamBlock = CodeBlock.of("<%T>", CBasicType.char.ktffiTypeTName)
                }
            }

            val nativeType = memberType.nativeType()
            val cTypeNameAnnotation = AnnotationSpec.builder(CTypeName::class)
                .addMember("%S", memberType.name)
                .build()

            val valueGetter: FunSpec
            val valueSetter: FunSpec
            val pointerGetter: FunSpec
            val pointerSetter: FunSpec

            valueGetter = FunSpec.getterBuilder()
                .addModifiers(KModifier.INLINE)
                .addStatement(
                    "return %T.fromNativeData%L((%T.%N.get(_segment, 0L) as %T))",
                    ktApiType,
                    fromIntTypeParamBlock,
                    thisCname,
                    "${member.name}_valueVarHandle",
                    nativeType
                )
                .build()
            valueSetter = FunSpec.setterBuilder()
                .addModifiers(KModifier.INLINE)
                .addParameter("value", ktApiType)
                .addStatement(
                    "%T.%N.set(_segment, 0L, %T.toNativeData(value))",
                    thisCname,
                    "${member.name}_valueVarHandle",
                    ktApiType
                )
                .build()
            pointerGetter = FunSpec.getterBuilder()
                .addModifiers(KModifier.INLINE)
                .addStatement(
                    "return %T.fromNativeData%L((%T.%N.get(%M, _address) as %T))",
                    ktApiType,
                    fromIntTypeParamBlock,
                    thisCname,
                    "${member.name}_valueVarHandle",
                    KTFFICodegenHelper.omniSegment,
                    nativeType
                )
                .build()
            pointerSetter = FunSpec.setterBuilder()
                .addModifiers(KModifier.INLINE)
                .addParameter("value", ktApiType)
                .addStatement(
                    "%T.%N.set(%M, _address, %T.toNativeData(value))",
                    thisCname,
                    "${member.name}_valueVarHandle",
                    KTFFICodegenHelper.omniSegment,
                    ktApiType
                )
                .build()

            file.addProperty(
                PropertySpec.builder(member.name, returnType)
                    .addAnnotation(cTypeNameAnnotation)
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(valueCnameP)
                    .getter(valueGetter)
                    .setter(valueSetter)
                    .build()
            )
            file.addProperty(
                PropertySpec.builder(member.name, returnType)
                    .addAnnotation(cTypeNameAnnotation)
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(pointerCnameP)
                    .getter(pointerGetter)
                    .setter(pointerSetter)
                    .build()
            )
        }

        fun groupAccess(member: CType.Group.Member, memberType: CType.Group) {
            val groupCname = memberType.className()
            val memberPointerCnameP = KTFFICodegenHelper.pointerCname.parameterizedBy(groupCname)
            val cTypeNameAnnotation = AnnotationSpec.builder(CTypeName::class)
                .addMember("%S", memberType.toSimpleString())
                .build()
            nestedAccess(member, memberPointerCnameP, cTypeNameAnnotation)
        }

        fun arrayAccess(member: CType.Group.Member, memberType: CType.Array) {
            val eType = memberType.elementType
            val memberPointerCnameP = memberType.ktApiType()
            val cTypeNameAnnotation = AnnotationSpec.builder(CTypeName::class)
                .addMember("%S", memberType.toSimpleString())
                .build()
            nestedAccess(member, memberPointerCnameP, cTypeNameAnnotation)
            if (eType is CType.BasicType && eType.baseType == CBasicType.char) {
                val checkCodeBlock = CodeBlock.builder()
                    .apply {
                        if (memberType is CType.Array.Sized) {
                            val lengthCodeBlock = memberType.length.codeBlock()
                            val lengthSimpleStr = memberType.length.toSimpleString()
                            addStatement(
                                "require(value.length <= %L.toInt()) { %P }",
                                lengthCodeBlock,
                                "String length exceeds $lengthSimpleStr(\${$lengthSimpleStr}) characters"
                            )
                        }
                    }
                    .build()
                val valueMemberOffset = member.valueMemberOffset()
                val pointerMemberOffset = member.pointerMemberOffset()
                file.addFunction(
                    FunSpec.builder(member.name)
                        .addModifiers(KModifier.INLINE)
                        .receiver(valueCnameP)
                        .addParameter("value", STRING)
                        .addCode(
                            CodeBlock.builder()
                                .add(checkCodeBlock)
                                .add("_segment.setString(")
                                .add(valueMemberOffset)
                                .add(", value)")
                                .build()
                        )
                        .build()
                )
                file.addFunction(
                    FunSpec.builder(member.name)
                        .addModifiers(KModifier.INLINE)
                        .receiver(pointerCnameP)
                        .addParameter("value", STRING)
                        .addCode(
                            CodeBlock.builder()
                                .add(checkCodeBlock)
                                .add("%M.setString(", KTFFICodegenHelper.omniSegment)
                                .add(pointerMemberOffset)
                                .add(", value)")
                                .build()
                        )
                        .build()
                )
            }
        }

        groupType.members.forEach { member ->
            var memberType = member.type
            while (memberType is CType.TypeDef) {
                memberType = memberType.dstType
            }

            when (memberType) {
                is CType.BasicType -> {
                    basicTypeAccess(member, memberType.baseType, memberType.baseType.cTypeNameStr)
                }
                is CType.Handle, is CType.EnumBase, is CType.Pointer -> {
                    commonAccess(member)
                }
                is CType.Group -> {
                    groupAccess(member, memberType)
                }
                is CType.Array -> {
                    arrayAccess(member, memberType)
                }
                else -> throw IllegalStateException("Unsupported member type: ${memberType.toSimpleString()}")
            }
        }

        return file
    }
}