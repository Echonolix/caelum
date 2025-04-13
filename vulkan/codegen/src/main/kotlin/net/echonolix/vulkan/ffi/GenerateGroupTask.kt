package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import net.echonolix.ktffi.CBasicType
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.CTypeName
import net.echonolix.ktffi.KTFFICodegenHelper
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
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
                .map { (_, structType) ->
                    genGroupType(structType)
                }
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
                .map { (_, enumType) ->
                    genGroupType(enumType)
                }
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
                        MemorySegment::class.member("copy"),
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
                        MemorySegment::class.member("copy"),
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
                        MemorySegment::class.member("copy"),
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
                        MemorySegment::class.member("copy"),
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

        fun commonAccess(member: CType.Group.Member) {
            val memberType = member.type
            val ktApiType = when (memberType) {
                is CType.Pointer, is CType.Array -> {
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

            val nativeType = when (memberType) {
                is CType.Array -> {
                    LONG
                }
                else -> {
                    memberType.nativeType()
                }
            }
            file.addProperty(
                PropertySpec.builder(member.name, returnType)
                    .addAnnotation(
                        AnnotationSpec.builder(CTypeName::class)
                            .addMember("%S", memberType.name)
                            .build()
                    )
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(valueCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return %T.fromInt%L((%T.%N.get(_segment, 0L) as %T))",
                                ktApiType,
                                fromIntTypeParamBlock,
                                thisCname,
                                "${member.name}_valueVarHandle",
                                nativeType
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", ktApiType)
                            .addStatement(
                                "%T.%N.set(_segment, 0L, %T.toInt(value))",
                                thisCname,
                                "${member.name}_valueVarHandle",
                                ktApiType
                            )
                            .build()
                    )
                    .build()
            )
            file.addProperty(
                PropertySpec.builder(member.name, returnType)
                    .addAnnotation(
                        AnnotationSpec.builder(CTypeName::class)
                            .addMember("%S", memberType.name)
                            .build()
                    )
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(pointerCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return %T.fromInt%L((%T.%N.get(%M, _address) as %T))",
                                ktApiType,
                                fromIntTypeParamBlock,
                                thisCname,
                                "${member.name}_valueVarHandle",
                                KTFFICodegenHelper.omniSegment,
                                nativeType
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", ktApiType)
                            .addStatement(
                                "%T.%N.set(%M, _address, %T.toInt(value))",
                                thisCname,
                                "${member.name}_valueVarHandle",
                                KTFFICodegenHelper.omniSegment,
                                ktApiType
                            )
                            .build()
                    )
                    .build()
            )
        }

        fun groupAccess(member: CType.Group.Member, memberType: CType.Group) {
            val groupCname = memberType.className()
            val annotations = listOf(
                AnnotationSpec.builder(CTypeName::class)
                    .addMember("%S", memberType.toSimpleString())
                    .build(),
            )
            val memberPointerCnameP = KTFFICodegenHelper.pointerCname.parameterizedBy(groupCname)
            file.addProperty(
                PropertySpec.builder(member.name, memberPointerCnameP)
                    .addAnnotations(annotations)
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(valueCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return %T(%T.%N_offsetHandle.invokeExact(_segment.address(), 0L) as Long)",
                                KTFFICodegenHelper.pointerCname,
                                thisCname,
                                member.name
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", memberPointerCnameP)
                            .addStatement(
                                "%M(%M, value._address, %M, %T.%N_offsetHandle.invokeExact(_segment.address(), 0L) as Long, %T.%N_byteSize)",
                                MemorySegment::class.member("copy"),
                                KTFFICodegenHelper.omniSegment,
                                KTFFICodegenHelper.omniSegment,
                                thisCname,
                                member.name,
                                thisCname,
                                member.name
                            )
                            .build()
                    )
                    .build()
            )
            file.addProperty(
                PropertySpec.builder(member.name, memberPointerCnameP)
                    .addAnnotations(annotations)
                    .tryAddKdoc(member)
                    .mutable()
                    .receiver(pointerCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return %T(%T.%N_offsetHandle.invokeExact(_address, 0L) as Long)",
                                KTFFICodegenHelper.pointerCname,
                                thisCname,
                                member.name
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", memberPointerCnameP)
                            .addStatement(
                                "%M(%M, value._address, %M, %T.%N_offsetHandle.invokeExact(_address, 0L) as Long, %T.%N_byteSize)",
                                MemorySegment::class.member("copy"),
                                KTFFICodegenHelper.omniSegment,
                                KTFFICodegenHelper.omniSegment,
                                thisCname,
                                member.name,
                                thisCname,
                                member.name
                            )
                            .build()
                    )
                    .build()
            )
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
                is CType.Handle, is CType.EnumBase -> {
                    commonAccess(member)
                }
                is CType.Pointer -> {
                    commonAccess(member)
                }
                is CType.Group -> {
                    groupAccess(member, memberType)
                }
                is CType.Array -> {
                    commonAccess(member)
                }
                else -> throw IllegalStateException("Unsupported member type: ${memberType.toSimpleString()}")
            }
        }

        return file
    }
}