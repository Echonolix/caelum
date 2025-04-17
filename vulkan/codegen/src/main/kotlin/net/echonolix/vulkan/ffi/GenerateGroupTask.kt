package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.ktffi.CBasicType
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.CTypeName
import net.echonolix.ktffi.KTFFICodegenHelper
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

        struct.join()
        union.join()
    }

    private inner class StructTask : VKFFITask<Unit>(ctx) {
        private val skippedStructs = setOf(
            "VkBaseInStructure",
            "VkBaseOutStructure"
        )

        override fun VKFFICodeGenContext.compute() {
            val structTypes = ctx.filterTypeStream<CType.Struct>()
                .filter { (name, type) -> name !in skippedStructs && type.name !in skippedStructs }
                .toList()
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
        if (groupType is CType.Struct) {
            val vkStructureType = ctx.resolveElement("VkStructureType") as CType.Enum
            val vkStructureTypeCname = vkStructureType.className()
            groupType.members.firstNotNullOfOrNull { it.tags.get<StructTypeTag>() }?.let {
                typeObject.addProperty(
                    PropertySpec.builder("structType", vkStructureType.typeName())
                        .addModifiers(KModifier.OVERRIDE)
                        .getter(
                            FunSpec.getterBuilder()
                                .addStatement("return %T.%N", vkStructureTypeCname, it.structType.name)
                                .build()
                        )
                        .build()
                )
            }
        }

        groupType.members.forEach { member ->
            when (member.type) {
                is CType.Array, is CType.Group -> {
                    // do nothing
                }
                else -> {
                    typeObject.addProperty(
                        PropertySpec.builder("${member.name}_valueVarHandle", VarHandle::class.asClassName())
                            .addModifiers(KModifier.INTERNAL)
                            .addAnnotation(JvmField::class)
                            .initializer("layout.varHandle(%M(%S))", KTFFICodegenHelper.groupElementMember, member.name)
                            .build()
                    )
//                typeObject.addProperty(
//                    PropertySpec.builder("${member.name}_arrayVarHandle", VarHandle::class.asClassName())
//                        .addAnnotation(JvmField::class)
//                        .initializer(
//                            "layout.arrayElementVarHandle(%M(%S))",
//                            KTFFICodegenHelper.groupElementMember
//                            member.name
//                        )
//                        .build()
//                )
                }
            }
            typeObject.addProperty(
                PropertySpec.builder("${member.name}_offsetHandle", MethodHandle::class)
                    .addModifiers(KModifier.INTERNAL)
                    .addAnnotation(JvmField::class)
                    .initializer("layout.byteOffsetHandle(%M(%S))", KTFFICodegenHelper.groupElementMember, member.name)
                    .build()
            )
            typeObject.addProperty(
                PropertySpec.builder("${member.name}_byteSize", Long::class)
                    .addAnnotation(JvmField::class)
                    .initializer("layout.select(%M(%S)).byteSize()", KTFFICodegenHelper.groupElementMember, member.name)
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
                    .addModifiers(KModifier.OPERATOR)
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
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", LONG)
                    .addParameter("value", valueCnameP)
                    .addStatement("set(index, value.ptr())")
                    .build()
            )
            file.addFunction(
                FunSpec.builder("set")
                    .receiver(arrayCnameP)
                    .addModifiers(KModifier.OPERATOR)
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
                    .addModifiers(KModifier.OPERATOR)
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
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", LONG)
                    .addParameter("value", valueCnameP)
                    .addStatement("set(index, value.ptr())")
                    .build()
            )
            file.addFunction(
                FunSpec.builder("set")
                    .receiver(pointerCnameP)
                    .addModifiers(KModifier.OPERATOR)
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
                PropertySpec.builder(member.name, cBasicType.ktApiTypeTypeName)
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
                            .addStatement("return ptr().${member.name}")
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addParameter("value", cBasicType.ktApiTypeTypeName)
                            .addStatement("ptr().${member.name} = value")
                            .build()
                    )
                    .build()
            )
            file.addProperty(
                PropertySpec.builder(member.name, cBasicType.ktApiTypeTypeName)
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
                            .addStatement(
                                "return (%T.%N.get(%M, _address) as %T)${cBasicType.fromBase}",
                                thisCname,
                                "${member.name}_valueVarHandle",
                                KTFFICodegenHelper.omniSegment,
                                cBasicType.nativeDataType.asTypeName()
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addParameter("value", cBasicType.ktApiTypeTypeName)
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
                    "%T.%N.invokeExact(_segment.address()) as Long",
                    thisCname,
                    "${this.name}_offsetHandle"
                )
                .build()
        }

        fun CType.Group.Member.pointerMemberOffset(): CodeBlock {
            return CodeBlock.builder()
                .addStatement(
                    "%T.%N.invokeExact(%M) as Long",
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
                            .addCode(
                                CodeBlock.builder()
                                    .add("return %T(", KTFFICodegenHelper.pointerCname)
                                    .add(valueMemberOffset)
                                    .add(")")
                                    .build()
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
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
        fun commonAccess(member: CType.Group.Member, mutable: Boolean = true) {
            val memberType = member.type
            val descType = memberType.typeDescriptorTypeName()!!
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
                    fromIntTypeParamBlock = CodeBlock.of("<%T>", CBasicType.char.ktffiTypeName)
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
                .addStatement("return ptr().${member.name}")
                .build()
            valueSetter = FunSpec.setterBuilder()
                .addParameter("value", ktApiType)
                .addStatement("ptr().${member.name} = value")
                .build()
            pointerGetter = FunSpec.getterBuilder()
                .addStatement(
                    "return %T.fromNativeData%L((%T.%N.get(%M, _address) as %T))",
                    descType,
                    fromIntTypeParamBlock,
                    thisCname,
                    "${member.name}_valueVarHandle",
                    KTFFICodegenHelper.omniSegment,
                    nativeType
                )
                .build()
            pointerSetter = FunSpec.setterBuilder()
                .addParameter("value", ktApiType)
                .addStatement(
                    "%T.%N.set(%M, _address, %T.toNativeData(value))",
                    thisCname,
                    "${member.name}_valueVarHandle",
                    KTFFICodegenHelper.omniSegment,
                    descType
                )
                .build()

            val valueProperty = PropertySpec.builder(member.name, returnType)
            valueProperty.addAnnotation(cTypeNameAnnotation)
            valueProperty.tryAddKdoc(member)
            valueProperty.receiver(valueCnameP)
            valueProperty.getter(valueGetter)
            if (mutable) {
                valueProperty.mutable()
                valueProperty.setter(valueSetter)
            }

            val pointerProperty = PropertySpec.builder(member.name, returnType)
            pointerProperty.addAnnotation(cTypeNameAnnotation)
            pointerProperty.tryAddKdoc(member)
            pointerProperty.receiver(pointerCnameP)
            pointerProperty.getter(pointerGetter)
            if (mutable) {
                pointerProperty.mutable()
                pointerProperty.setter(pointerSetter)
            }
            file.addProperty(valueProperty.build())
            file.addProperty(pointerProperty.build())
        }

        fun groupAccess(member: CType.Group.Member, memberType: CType.Group) {
            val groupCname = memberType.typeName()
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
                file.addFunction(
                    FunSpec.builder(member.name)
                        .receiver(valueCnameP)
                        .addParameter("value", STRING)
                        .addCode("ptr().${member.name}(value)")
                        .build()
                )
                file.addFunction(
                    FunSpec.builder(member.name)
                        .receiver(pointerCnameP)
                        .addParameter("value", STRING)
                        .addCode(
                            CodeBlock.builder()
                                .add(checkCodeBlock)
                                .add("%M.setString(", KTFFICodegenHelper.omniSegment)
                                .add(member.pointerMemberOffset())
                                .add(", value)")
                                .build()
                        )
                        .build()
                )
            }
        }

        fun funcPointerOverload(member: CType.Group.Member, functionPtrType: CType.FunctionPointer) {
            val funcPtrCname = functionPtrType.elementType.className()
            file.addFunction(
                FunSpec.builder(member.name)
                    .receiver(valueCnameP)
                    .addParameter("func", funcPtrCname)
                    .addStatement("ptr().${member.name}(func)")
                    .build()
            )
            file.addFunction(
                FunSpec.builder(member.name)
                    .receiver(pointerCnameP)
                    .addParameter("func", funcPtrCname)
                    .addStatement("%N = %T.toNativeData(func)", member.name, funcPtrCname)
                    .build()
            )
        }

        groupType.members.asSequence().forEach { member ->
            var memberType = member.type
            while (memberType is CType.TypeDef) {
                memberType = memberType.dstType
            }

            when (memberType) {
                is CType.BasicType -> {
                    basicTypeAccess(member, memberType.baseType, memberType.baseType.cTypeNameStr)
                }
                is CType.Handle -> {
                    commonAccess(member)
                }
                is CType.Pointer -> {
                    commonAccess(member)
                    if (memberType is CType.FunctionPointer) {
                        funcPointerOverload(member, memberType)
                    }
                }
                is CType.EnumBase -> {
                    commonAccess(member, member.name != "sType")
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