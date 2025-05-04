package net.echonolix.caelum.codegen.api.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.codegen.api.CBasicType
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.CaelumCodegenHelper
import net.echonolix.caelum.codegen.api.CaelumCoreAnnotation
import java.lang.invoke.MethodHandle
import java.lang.invoke.VarHandle

public open class GroupGenerator(
    ctx: CodegenContext,
    element: CType.Group
) : Generator<CType.Group>(ctx, element) {
    context(ctx: CodegenContext)
    protected open fun groupBaseCName(): ClassName {
        return when (element) {
            is CType.Struct -> CaelumCodegenHelper.structCname
            is CType.Union -> CaelumCodegenHelper.unionCname
        }
    }

    context(ctx: CodegenContext)
    protected open fun buildTypeObjectType(): TypeSpec.Builder {
        val typeObject = TypeSpec.objectBuilder(thisCname)
        typeObject.superclass(groupBaseCName().parameterizedBy(thisCname))
        typeObject.addSuperclassConstructorParameter(
            CodeBlock.builder()
                .add("\n")
                .indent()
                .add(element.memoryLayoutDeep())
                .unindent()
                .build()
        )

        element.members.forEach { member ->
            when (member.type) {
                is CType.Array, is CType.Group -> {
                    // do nothing
                }
                else -> {
                    typeObject.addProperty(
                        PropertySpec.builder("${member.name}_valueVarHandle", VarHandle::class.asClassName())
                            .addModifiers(KModifier.INTERNAL)
                            .addAnnotation(JvmField::class)
                            .initializer(
                                "layout.varHandle(%M(%S))",
                                CaelumCodegenHelper.groupElementMember,
                                member.name
                            )
                            .build()
                    )
//                typeObject.addProperty(
//                    PropertySpec.builder("${member.name}_arrayVarHandle", VarHandle::class.asClassName())
//                        .addAnnotation(JvmField::class)
//                        .initializer(
//                            "layout.arrayElementVarHandle(%M(%S))",
//                            CaelumCodegenHelper.groupElementMember
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
                    .initializer("layout.byteOffsetHandle(%M(%S))", CaelumCodegenHelper.groupElementMember, member.name)
                    .build()
            )
            typeObject.addProperty(
                PropertySpec.builder("${member.name}_byteSize", Long::class)
                    .addAnnotation(JvmField::class)
                    .initializer(
                        "layout.select(%M(%S)).byteSize()",
                        CaelumCodegenHelper.groupElementMember,
                        member.name
                    )
                    .build()
            )
        }

        return typeObject
    }

    override fun build(): FileSpec.Builder {
        with (ctx) {
            val typeObjectType = buildTypeObjectType()
            val file = FileSpec.builder(thisCname)
            file.addType(typeObjectType.build())
            return file
        }
    }

//    public open class AccessGenerator<CodegenContext : CodegenContext>() {
//
//        context(ctx: CodegenContext, gg: GroupGenerator<CodegenContext>, file: FileSpec.Builder)
//        public fun basicTypeAccess(member: CType.Group.Member, cBasicType: CBasicType<*>, cTypeName: String) {
//            file.addProperty(
//                PropertySpec.builder(member.name, cBasicType.ktApiTypeTypeName)
//                    .addAnnotation(CaelumCoreAnnotation.cTypeName(cTypeName))
//                    .tryAddKdoc(member)
//                    .mutable()
//                    .receiver(valueCnameP)
//                    .getter(
//                        FunSpec.getterBuilder()
//                            .addStatement("return ptr().%N", member.name)
//                            .build()
//                    )
//                    .setter(
//                        FunSpec.setterBuilder()
//                            .addParameter("value", cBasicType.ktApiTypeTypeName)
//                            .addStatement("ptr().%N = value", member.name)
//                            .build()
//                    )
//                    .build()
//            )
//            file.addProperty(
//                PropertySpec.builder(member.name, cBasicType.ktApiTypeTypeName)
//                    .addAnnotation(
//                        AnnotationSpec.builder(CTypeName::class)
//                            .addMember("%S", cTypeName)
//                            .build()
//                    )
//                    .tryAddKdoc(member)
//                    .mutable()
//                    .receiver(pointerCnameP)
//                    .getter(
//                        FunSpec.getterBuilder()
//                            .addStatement(
//                                "return (%T.%N.get(%M, _address) as %T)${cBasicType.fromBase}",
//                                thisCname,
//                                "${member.name}_valueVarHandle",
//                                CaelumCodegenHelper.omniSegment,
//                                cBasicType.nativeDataType.asTypeName()
//                            )
//                            .build()
//                    )
//                    .setter(
//                        FunSpec.setterBuilder()
//                            .addParameter("value", cBasicType.ktApiTypeTypeName)
//                            .addStatement(
//                                "%T.%N.set(%M, _address, value${cBasicType.toBase})",
//                                thisCname,
//                                "${member.name}_valueVarHandle",
//                                CaelumCodegenHelper.omniSegment
//                            )
//                            .build()
//                    )
//                    .build()
//            )
//        }
//
//        context(ctx: CodegenContext, gg: GroupGenerator<CodegenContext>, file: FileSpec.Builder)
//        fun CType.Group.Member.valueMemberOffset(): CodeBlock {
//            return CodeBlock.builder()
//                .addStatement("%T.${this.name}_offsetHandle.invokeExact(_segment.address()) as Long", gg.thisCname)
//                .build()
//        }
//
//        context(ctx: CodegenContext, gg: GroupGenerator<CodegenContext>, file: FileSpec.Builder)
//        fun CType.Group.Member.pointerMemberOffset(): CodeBlock {
//            return CodeBlock.builder()
//                .addStatement("%T.${this.name}_offsetHandle.invokeExact(_address) as Long", gg.thisCname)
//                .build()
//        }
//
//        context(ctx: CodegenContext, gg: GroupGenerator<CodegenContext>, file: FileSpec.Builder)
//        fun nestedAccess(
//            member: CType.Group.Member,
//            memberPointerCnameP: TypeName,
//            cTypeNameAnnotation: AnnotationSpec
//        ) {
//            val valueMemberOffset = member.valueMemberOffset()
//            val pointerMemberOffset = member.pointerMemberOffset()
//            file.addProperty(
//                PropertySpec.builder(member.name, memberPointerCnameP)
//                    .addAnnotation(cTypeNameAnnotation)
//                    .tryAddKdoc(member)
//                    .mutable()
//                    .receiver(valueCnameP)
//                    .getter(
//                        FunSpec.getterBuilder()
//                            .addCode(
//                                CodeBlock.builder()
//                                    .add("return %T(", CaelumCodegenHelper.pointerCname)
//                                    .add(valueMemberOffset)
//                                    .add(")")
//                                    .build()
//                            )
//                            .build()
//                    )
//                    .setter(
//                        FunSpec.setterBuilder()
//                            .addParameter("value", memberPointerCnameP)
//                            .addCode(
//                                CodeBlock.builder()
//                                    .add(
//                                        "%M(%M, value._address, %M, ",
//                                        CaelumCodegenHelper.copyMember,
//                                        CaelumCodegenHelper.omniSegment,
//                                        CaelumCodegenHelper.omniSegment,
//                                    )
//                                    .add(valueMemberOffset)
//                                    .add(
//                                        ", %T.%N)",
//                                        thisCname,
//                                        "${member.name}_byteSize"
//                                    )
//                                    .build()
//                            )
//                            .build()
//                    )
//                    .build()
//            )
//            file.addProperty(
//                PropertySpec.builder(member.name, memberPointerCnameP)
//                    .addAnnotation(cTypeNameAnnotation)
//                    .tryAddKdoc(member)
//                    .mutable()
//                    .receiver(pointerCnameP)
//                    .getter(
//                        FunSpec.getterBuilder()
//                            .addCode(
//                                CodeBlock.builder()
//                                    .add(
//                                        "return %T(",
//                                        CaelumCodegenHelper.pointerCname,
//                                    )
//                                    .add(pointerMemberOffset)
//                                    .add(")")
//                                    .build()
//                            )
//                            .build()
//                    )
//                    .setter(
//                        FunSpec.setterBuilder()
//                            .addParameter("value", memberPointerCnameP)
//                            .addCode(
//                                CodeBlock.builder()
//                                    .add(
//                                        "%M(%M, value._address, %M, ",
//                                        CaelumCodegenHelper.copyMember,
//                                        CaelumCodegenHelper.omniSegment,
//                                        CaelumCodegenHelper.omniSegment,
//                                    )
//                                    .add(pointerMemberOffset)
//                                    .add(
//                                        ", %T.%N)",
//                                        thisCname,
//                                        "${member.name}_byteSize"
//                                    )
//                                    .build()
//                            )
//                            .build()
//                    )
//                    .build()
//            )
//            file.addFunction(
//                FunSpec.builder(member.name)
//                    .addModifiers(KModifier.INLINE)
//                    .receiver(valueCnameP)
//                    .addParameter("block", LambdaTypeName.get(receiver = memberPointerCnameP, returnType = UNIT))
//                    .addStatement("%N.block()", member.name)
//                    .build()
//            )
//            file.addFunction(
//                FunSpec.builder(member.name)
//                    .addModifiers(KModifier.INLINE)
//                    .receiver(pointerCnameP)
//                    .addParameter("block", LambdaTypeName.get(receiver = memberPointerCnameP, returnType = UNIT))
//                    .addStatement("%N.block()", member.name)
//                    .build()
//            )
//        }
//
//        context(ctx: CodegenContext, gg: GroupGenerator<CodegenContext>, file: FileSpec.Builder)
//        @Suppress("JoinDeclarationAndAssignment")
//        fun commonAccess(member: CType.Group.Member, mutable: Boolean = true) {
//            val memberType = member.type
//            val descType = memberType.typeDescriptorTypeName()!!
//            val ktApiType = when (memberType) {
//                is CType.Pointer -> {
//                    CaelumCodegenHelper.pointerCname
//                }
//                else -> {
//                    memberType.ktApiType()
//                }
//            }
//            var returnType = memberType.ktApiType()
//            var fromIntTypeParamBlock = CodeBlock.of("")
//            if (member.name == "pNext") {
//                val vkStructStar = VulkanCodegen.vkStructCname.parameterizedBy(CaelumCodegenHelper.starWildcard)
//                val outVkStruct = WildcardTypeName.producerOf(vkStructStar)
//                returnType = CaelumCodegenHelper.pointerCname.parameterizedBy(outVkStruct)
//            } else if (memberType is CType.Pointer) {
//                val eType = memberType.elementType
//                if (eType is CType.BasicType && eType.baseType == CBasicType.void) {
//                    fromIntTypeParamBlock = CodeBlock.of("<%T>", CBasicType.char.caelumCoreTypeName)
//                }
//            }
//
//            val nativeType = memberType.nativeType()
//            val cTypeNameAnnotation = AnnotationSpec.builder(CTypeName::class)
//                .addMember("%S", memberType.name)
//                .build()
//
//            val valueGetter: FunSpec
//            val valueSetter: FunSpec
//            val pointerGetter: FunSpec
//            val pointerSetter: FunSpec
//
//            valueGetter = FunSpec.getterBuilder()
//                .addStatement("return ptr().%N", member.name)
//                .build()
//            valueSetter = FunSpec.setterBuilder()
//                .addParameter("value", ktApiType)
//                .addStatement("ptr().%N = value", member.name)
//                .build()
//            pointerGetter = FunSpec.getterBuilder()
//                .addStatement(
//                    "return %T.fromNativeData%L((%T.%N.get(%M, _address) as %T))",
//                    descType,
//                    fromIntTypeParamBlock,
//                    thisCname,
//                    "${member.name}_valueVarHandle",
//                    CaelumCodegenHelper.omniSegment,
//                    nativeType
//                )
//                .build()
//            pointerSetter = FunSpec.setterBuilder()
//                .addParameter("value", ktApiType)
//                .addStatement(
//                    "%T.%N.set(%M, _address, %T.toNativeData(value))",
//                    thisCname,
//                    "${member.name}_valueVarHandle",
//                    CaelumCodegenHelper.omniSegment,
//                    descType
//                )
//                .build()
//
//            val valueProperty = PropertySpec.builder(member.name, returnType)
//            valueProperty.addAnnotation(cTypeNameAnnotation)
//            valueProperty.tryAddKdoc(member)
//            valueProperty.receiver(valueCnameP)
//            valueProperty.getter(valueGetter)
//            if (mutable) {
//                valueProperty.mutable()
//                valueProperty.setter(valueSetter)
//            }
//
//            val pointerProperty = PropertySpec.builder(member.name, returnType)
//            pointerProperty.addAnnotation(cTypeNameAnnotation)
//            pointerProperty.tryAddKdoc(member)
//            pointerProperty.receiver(pointerCnameP)
//            pointerProperty.getter(pointerGetter)
//            if (mutable) {
//                pointerProperty.mutable()
//                pointerProperty.setter(pointerSetter)
//            }
//            file.addProperty(valueProperty.build())
//            file.addProperty(pointerProperty.build())
//        }
//
//        context(ctx: CodegenContext, gg: GroupGenerator<CodegenContext>, file: FileSpec.Builder)
//        fun groupAccess(member: CType.Group.Member, memberType: CType.Group) {
//            val groupCname = memberType.typeName()
//            val memberPointerCnameP = CaelumCodegenHelper.pointerCname.parameterizedBy(groupCname)
//            val cTypeNameAnnotation = AnnotationSpec.builder(CTypeName::class)
//                .addMember("%S", memberType.toSimpleString())
//                .build()
//            nestedAccess(member, memberPointerCnameP, cTypeNameAnnotation)
//        }
//
//        context(ctx: CodegenContext, gg: GroupGenerator<CodegenContext>, file: FileSpec.Builder)
//        fun arrayAccess(member: CType.Group.Member, memberType: CType.Array) {
//            val eType = memberType.elementType
//            val memberPointerCnameP = memberType.ktApiType()
//            val cTypeNameAnnotation = AnnotationSpec.builder(CTypeName::class)
//                .addMember("%S", memberType.toSimpleString())
//                .build()
//            nestedAccess(member, memberPointerCnameP, cTypeNameAnnotation)
//            if (eType is CType.BasicType && eType.baseType == CBasicType.char) {
//                val checkCodeBlock = CodeBlock.builder()
//                    .apply {
//                        if (memberType is CType.Array.Sized) {
//                            val lengthCodeBlock = memberType.length.codeBlock()
//                            val lengthSimpleStr = memberType.length.toSimpleString()
//                            addStatement(
//                                "require(value.length <= %L.toInt()) { %P }",
//                                lengthCodeBlock,
//                                "String length exceeds $lengthSimpleStr(\${$lengthSimpleStr}) characters"
//                            )
//                        }
//                    }
//                    .build()
//                file.addFunction(
//                    FunSpec.builder(member.name)
//                        .receiver(valueCnameP)
//                        .addParameter("value", STRING)
//                        .addCode("ptr().%N(value)", member.name)
//                        .build()
//                )
//                file.addFunction(
//                    FunSpec.builder(member.name)
//                        .receiver(pointerCnameP)
//                        .addParameter("value", STRING)
//                        .addCode(
//                            CodeBlock.builder()
//                                .add(checkCodeBlock)
//                                .add("%M.setString(", CaelumCodegenHelper.omniSegment)
//                                .add(member.pointerMemberOffset())
//                                .add(", value)")
//                                .build()
//                        )
//                        .build()
//                )
//            }
//        }
//
//        context(ctx: CodegenContext, gg: GroupGenerator<CodegenContext>, file: FileSpec.Builder)
//        fun funcPointerOverload(member: CType.Group.Member, functionPtrType: CType.FunctionPointer) {
//            val funcPtrCname = functionPtrType.elementType.className()
//            file.addFunction(
//                FunSpec.builder(member.name)
//                    .receiver(valueCnameP)
//                    .addParameter("func", funcPtrCname)
//                    .addStatement("ptr().%N(func)", member.name)
//                    .build()
//            )
//            file.addFunction(
//                FunSpec.builder(member.name)
//                    .receiver(pointerCnameP)
//                    .addParameter("func", funcPtrCname)
//                    .addStatement("%N = %T.toNativeData(func)", member.name, funcPtrCname)
//                    .build()
//            )
//        }
//    }
}