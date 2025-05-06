package net.echonolix.caelum.codegen.api.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.codegen.api.*
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.addKdoc
import java.lang.invoke.MethodHandle
import java.lang.invoke.VarHandle

public open class GroupGenerator(
    ctx: CodegenContext,
    element: CType.Group
) : Generator<CType.Group>(ctx, element) {
    protected val arrayCNameP: TypeName = CaelumCodegenHelper.arrayCName.parameterizedBy(thisCName)
    protected val pointerCNameP: TypeName = CaelumCodegenHelper.pointerCName.parameterizedBy(thisCName)
    protected val valueCNameP: TypeName = CaelumCodegenHelper.valueCName.parameterizedBy(thisCName)

    protected val typeObjectType: TypeSpec.Builder = TypeSpec.objectBuilder(thisCName)
    protected val file: FileSpec.Builder = FileSpec.builder(thisCName)

    context(ctx: CodegenContext)
    protected open fun groupBaseCName(): ClassName {
        return when (element) {
            is CType.Struct -> CaelumCodegenHelper.structCName
            is CType.Union -> CaelumCodegenHelper.unionCName
        }
    }

    context(ctx: CodegenContext)
    protected open fun memberKtApiType(member: CType.Group.Member): TypeName {
        return member.type.ktApiType()
    }

    context(ctx: CodegenContext)
    protected open fun addMemberAccessor(member: CType.Group.Member) {
        when (val memberType = member.type.deepResolve()) {
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

    context(ctx: CodegenContext)
    protected open fun buildTypeObjectType() {
        typeObjectType.addAnnotation(CaelumCoreAnnotation.cTypeName(element.toSimpleString()))
        typeObjectType.superclass(groupBaseCName().parameterizedBy(thisCName))
        typeObjectType.addSuperclassConstructorParameter(
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
                    typeObjectType.addProperty(
                        PropertySpec.builder("${member.name}_byteSize", Long::class)
                            .addModifiers(KModifier.INTERNAL)
                            .addAnnotation(JvmField::class)
                            .initializer(
                                "layout.select(%M(%S)).byteSize()",
                                CaelumCodegenHelper.groupElementMember,
                                member.name
                            )
                            .build()
                    )
                    typeObjectType.addProperty(
                        PropertySpec.builder("${member.name}_offsetHandle", MethodHandle::class)
                            .addModifiers(KModifier.INTERNAL)
                            .addAnnotation(JvmField::class)
                            .initializer(
                                "layout.byteOffsetHandle(%M(%S))",
                                CaelumCodegenHelper.groupElementMember,
                                member.name
                            )
                            .build()
                    )
                }
                else -> {
                    typeObjectType.addProperty(
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
//                typeObjectType.addProperty(
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
        }
    }

    override fun generate(): FileSpec.Builder {
        with(ctx) {
            buildTypeObjectType()
            file.addType(typeObjectType.build())
            run {
                file.addFunction(
                    FunSpec.builder("elementAddress")
                        .receiver(arrayCNameP)
                        .addParameter("index", LONG)
                        .returns(LONG)
                        .addStatement(
                            "return %T.arrayByteOffsetHandle.invokeExact(_segment.address(), index) as Long",
                            thisCName
                        )
                        .build()
                )
                file.addFunction(
                    FunSpec.builder("get")
                        .receiver(arrayCNameP)
                        .addModifiers(KModifier.OPERATOR)
                        .addParameter("index", LONG)
                        .returns(pointerCNameP)
                        .addStatement(
                            "return %T(elementAddress(index))",
                            CaelumCodegenHelper.pointerCName
                        )
                        .build()
                )
                file.addFunction(
                    FunSpec.builder("set")
                        .receiver(arrayCNameP)
                        .addModifiers(KModifier.OPERATOR)
                        .addParameter("index", LONG)
                        .addParameter("value", valueCNameP)
                        .addStatement("set(index, value.ptr())")
                        .build()
                )
                file.addFunction(
                    FunSpec.builder("set")
                        .receiver(arrayCNameP)
                        .addModifiers(KModifier.OPERATOR)
                        .addParameter("index", LONG)
                        .addParameter("value", pointerCNameP)
                        .addStatement(
                            "%M(%M, value._address, _segment, elementAddress(index), %T.layout.byteSize())",
                            CaelumCodegenHelper.copyMember,
                            CaelumCodegenHelper.omniSegment,
                            thisCName
                        )
                        .build()
                )
                file.addFunction(
                    FunSpec.builder("elementAddress")
                        .receiver(pointerCNameP)
                        .addParameter("index", LONG)
                        .returns(LONG)
                        .addStatement(
                            "return %T.arrayByteOffsetHandle.invokeExact(_address, index) as Long",
                            thisCName
                        )
                        .build()
                )
                file.addFunction(
                    FunSpec.builder("get")
                        .receiver(pointerCNameP)
                        .addModifiers(KModifier.OPERATOR)
                        .addParameter("index", LONG)
                        .returns(pointerCNameP)
                        .addStatement(
                            "return %T(elementAddress(index))",
                            CaelumCodegenHelper.pointerCName
                        )
                        .build()
                )
                file.addFunction(
                    FunSpec.builder("set")
                        .receiver(pointerCNameP)
                        .addModifiers(KModifier.OPERATOR)
                        .addParameter("index", LONG)
                        .addParameter("value", valueCNameP)
                        .addStatement("set(index, value.ptr())")
                        .build()
                )
                file.addFunction(
                    FunSpec.builder("set")
                        .receiver(pointerCNameP)
                        .addModifiers(KModifier.OPERATOR)
                        .addParameter("index", LONG)
                        .addParameter("value", pointerCNameP)
                        .addStatement(
                            "%M(%M, value._address, %M, elementAddress(index), %T.layout.byteSize())",
                            CaelumCodegenHelper.copyMember,
                            CaelumCodegenHelper.omniSegment,
                            CaelumCodegenHelper.omniSegment,
                            thisCName
                        )
                        .build()
                )
            }
            element.members.forEach {
                addMemberAccessor(it)
            }
            return file
        }
    }

    context(ctx: CodegenContext)
    protected fun basicTypeAccess(member: CType.Group.Member, cBasicType: CBasicType<*>, cTypeName: String) {
        file.addProperty(
            PropertySpec.builder(member.name, cBasicType.ktApiTypeTypeName)
                .addAnnotation(CaelumCoreAnnotation.cTypeName(cTypeName))
                .addKdoc(member)
                .mutable()
                .receiver(valueCNameP)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return ptr().%N", member.name)
                        .build()
                )
                .setter(
                    FunSpec.setterBuilder()
                        .addParameter("value", cBasicType.ktApiTypeTypeName)
                        .addStatement("ptr().%N = value", member.name)
                        .build()
                )
                .build()
        )
        file.addProperty(
            PropertySpec.builder(member.name, cBasicType.ktApiTypeTypeName)
                .addAnnotation(CaelumCoreAnnotation.cTypeName(cTypeName))
                .addKdoc(member)
                .mutable()
                .receiver(pointerCNameP)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement(
                            "return (%T.%N.get(%M, _address) as %T)${cBasicType.fromBase}",
                            thisCName,
                            "${member.name}_valueVarHandle",
                            CaelumCodegenHelper.omniSegment,
                            cBasicType.nativeDataType.asTypeName()
                        )
                        .build()
                )
                .setter(
                    FunSpec.setterBuilder()
                        .addParameter("value", cBasicType.ktApiTypeTypeName)
                        .addStatement(
                            "%T.%N.set(%M, _address, value${cBasicType.toBase})",
                            thisCName,
                            "${member.name}_valueVarHandle",
                            CaelumCodegenHelper.omniSegment
                        )
                        .build()
                )
                .build()
        )
    }

    context(ctx: CodegenContext)
    protected fun CType.Group.Member.valueMemberOffset(): CodeBlock {
        return CodeBlock.builder()
            .addStatement("%T.${this.name}_offsetHandle.invokeExact(_segment.address()) as Long", thisCName)
            .build()
    }

    context(ctx: CodegenContext)
    protected fun CType.Group.Member.pointerMemberOffset(): CodeBlock {
        return CodeBlock.builder()
            .addStatement("%T.${this.name}_offsetHandle.invokeExact(_address) as Long", thisCName)
            .build()
    }

    context(ctx: CodegenContext)
    protected fun nestedAccess(
        member: CType.Group.Member,
        memberPointerCNameP: TypeName,
        cTypeNameAnnotation: AnnotationSpec
    ) {
        val valueMemberOffset = member.valueMemberOffset()
        val pointerMemberOffset = member.pointerMemberOffset()
        file.addProperty(
            PropertySpec.builder(member.name, memberPointerCNameP)
                .addAnnotation(cTypeNameAnnotation)
                .addKdoc(member)
                .mutable()
                .receiver(valueCNameP)
                .getter(
                    FunSpec.getterBuilder()
                        .addCode(
                            CodeBlock.builder()
                                .add("return %T(", CaelumCodegenHelper.pointerCName)
                                .add(valueMemberOffset)
                                .add(")")
                                .build()
                        )
                        .build()
                )
                .setter(
                    FunSpec.setterBuilder()
                        .addParameter("value", memberPointerCNameP)
                        .addCode(
                            CodeBlock.builder()
                                .add(
                                    "%M(%M, value._address, %M, ",
                                    CaelumCodegenHelper.copyMember,
                                    CaelumCodegenHelper.omniSegment,
                                    CaelumCodegenHelper.omniSegment,
                                )
                                .add(valueMemberOffset)
                                .add(
                                    ", %T.%N)",
                                    thisCName,
                                    "${member.name}_byteSize"
                                )
                                .build()
                        )
                        .build()
                )
                .build()
        )
        file.addProperty(
            PropertySpec.builder(member.name, memberPointerCNameP)
                .addAnnotation(cTypeNameAnnotation)
                .addKdoc(member)
                .mutable()
                .receiver(pointerCNameP)
                .getter(
                    FunSpec.getterBuilder()
                        .addCode(
                            CodeBlock.builder()
                                .add(
                                    "return %T(",
                                    CaelumCodegenHelper.pointerCName,
                                )
                                .add(pointerMemberOffset)
                                .add(")")
                                .build()
                        )
                        .build()
                )
                .setter(
                    FunSpec.setterBuilder()
                        .addParameter("value", memberPointerCNameP)
                        .addCode(
                            CodeBlock.builder()
                                .add(
                                    "%M(%M, value._address, %M, ",
                                    CaelumCodegenHelper.copyMember,
                                    CaelumCodegenHelper.omniSegment,
                                    CaelumCodegenHelper.omniSegment,
                                )
                                .add(pointerMemberOffset)
                                .add(
                                    ", %T.%N)",
                                    thisCName,
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
                .receiver(valueCNameP)
                .addParameter("block", LambdaTypeName.get(receiver = memberPointerCNameP, returnType = UNIT))
                .addStatement("%N.block()", member.name)
                .build()
        )
        file.addFunction(
            FunSpec.builder(member.name)
                .addModifiers(KModifier.INLINE)
                .receiver(pointerCNameP)
                .addParameter("block", LambdaTypeName.get(receiver = memberPointerCNameP, returnType = UNIT))
                .addStatement("%N.block()", member.name)
                .build()
        )
    }

    context(ctx: CodegenContext)
    @Suppress("JoinDeclarationAndAssignment")
    protected fun commonAccess(member: CType.Group.Member, mutable: Boolean = true) {
        val memberType = member.type
        val descType = memberType.typeDescriptorTypeName()!!
        val ktApiType = when (memberType) {
            is CType.Pointer -> {
                CaelumCodegenHelper.pointerCName
            }
            else -> {
                memberType.ktApiType()
            }
        }
        val returnType = memberKtApiType(member)
        var fromIntTypeParamBlock = CodeBlock.of("")

        if (returnType is ParameterizedTypeName
            && returnType.rawType == CaelumCodegenHelper.pointerCName
            && returnType.typeArguments.first() == CaelumCodegenHelper.starWildcard
        ) {
            fromIntTypeParamBlock = CodeBlock.of("<%T>", CBasicType.char.caelumCoreTypeName)
        }

        val nativeType = memberType.nativeType()
        val cTypeNameAnnotation = CaelumCoreAnnotation.cTypeName(memberType.toSimpleString())

        val valueGetter: FunSpec
        val valueSetter: FunSpec
        val pointerGetter: FunSpec
        val pointerSetter: FunSpec

        valueGetter = FunSpec.getterBuilder()
            .addStatement("return ptr().%N", member.name)
            .build()
        valueSetter = FunSpec.setterBuilder()
            .addParameter("value", ktApiType)
            .addStatement("ptr().%N = value", member.name)
            .build()
        pointerGetter = FunSpec.getterBuilder()
            .addStatement(
                "return %T.fromNativeData%L((%T.%N.get(%M, _address) as %T))",
                descType,
                fromIntTypeParamBlock,
                thisCName,
                "${member.name}_valueVarHandle",
                CaelumCodegenHelper.omniSegment,
                nativeType
            )
            .build()
        pointerSetter = FunSpec.setterBuilder()
            .addParameter("value", ktApiType)
            .addStatement(
                "%T.%N.set(%M, _address, %T.toNativeData(value))",
                thisCName,
                "${member.name}_valueVarHandle",
                CaelumCodegenHelper.omniSegment,
                descType
            )
            .build()

        val valueProperty = PropertySpec.builder(member.name, returnType)
        valueProperty.addAnnotation(cTypeNameAnnotation)
        valueProperty.addKdoc(member)
        valueProperty.receiver(valueCNameP)
        valueProperty.getter(valueGetter)
        if (mutable) {
            valueProperty.mutable()
            valueProperty.setter(valueSetter)
        }

        val pointerProperty = PropertySpec.builder(member.name, returnType)
        pointerProperty.addAnnotation(cTypeNameAnnotation)
        pointerProperty.addKdoc(member)
        pointerProperty.receiver(pointerCNameP)
        pointerProperty.getter(pointerGetter)
        if (mutable) {
            pointerProperty.mutable()
            pointerProperty.setter(pointerSetter)
        }
        file.addProperty(valueProperty.build())
        file.addProperty(pointerProperty.build())
    }

    context(ctx: CodegenContext)
    protected fun groupAccess(member: CType.Group.Member, memberType: CType.Group) {
        val groupCName = memberType.typeName()
        val memberPointerCNameP = CaelumCodegenHelper.pointerCName.parameterizedBy(groupCName)
        val cTypeNameAnnotation = CaelumCoreAnnotation.cTypeName(memberType.toSimpleString())
        nestedAccess(member, memberPointerCNameP, cTypeNameAnnotation)
    }

    context(ctx: CodegenContext)
    protected fun arrayAccess(member: CType.Group.Member, memberType: CType.Array) {
        val eType = memberType.elementType
        val memberPointerCNameP = memberType.ktApiType()
        val cTypeNameAnnotation = CaelumCoreAnnotation.cTypeName(memberType.toSimpleString())
        nestedAccess(member, memberPointerCNameP, cTypeNameAnnotation)
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
                    .receiver(valueCNameP)
                    .addParameter("value", STRING)
                    .addCode("ptr().%N(value)", member.name)
                    .build()
            )
            file.addFunction(
                FunSpec.builder(member.name)
                    .receiver(pointerCNameP)
                    .addParameter("value", STRING)
                    .addCode(
                        CodeBlock.builder()
                            .add(checkCodeBlock)
                            .add("%M.setString(", CaelumCodegenHelper.omniSegment)
                            .add(member.pointerMemberOffset())
                            .add(", value)")
                            .build()
                    )
                    .build()
            )
        }
    }

    context(ctx: CodegenContext)
    protected fun funcPointerOverload(member: CType.Group.Member, functionPtrType: CType.FunctionPointer) {
        val funcPtrCName = functionPtrType.elementType.className()
        file.addFunction(
            FunSpec.builder(member.name)
                .receiver(valueCNameP)
                .addParameter("func", funcPtrCName)
                .addStatement("ptr().%N(func)", member.name)
                .build()
        )
        file.addFunction(
            FunSpec.builder(member.name)
                .receiver(pointerCNameP)
                .addParameter("func", funcPtrCName)
                .addStatement("%N = %T.toNativeData(func)", member.name, funcPtrCName)
                .build()
        )
    }
}