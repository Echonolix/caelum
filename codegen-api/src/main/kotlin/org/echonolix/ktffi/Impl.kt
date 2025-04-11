package org.echonolix.ktffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlin.properties.Delegates

interface CElement {
    val name: String
    val kDoc: KDoc
    val annotations: List<AnnotationSpec>

    context(ctx: KTFFICodegenContext)
    fun addAnnotationTo(builder: Annotatable.Builder<*>) {
        builder.addAnnotations(annotations)
    }

    context(ctx: KTFFICodegenContext)
    fun addKdocTo(builder: Documentable.Builder<*>) {
        kDoc.toString().takeIf { it.isNotBlank() }?.let {
            builder.addKdoc(it)
        }
    }

    sealed class Impl(override val name: String) : CElement {
        override val kDoc: KDoc = KDoc()

        override val annotations = mutableListOf<AnnotationSpec>()
    }
}

context(ctx: KTFFICodegenContext)
fun CElement.packageName(): String {
    return ctx.resolvePackageName(this)
}

context(ctx: KTFFICodegenContext)
fun CElement.className(): ClassName {
    return ClassName(packageName(), name)
}

interface ITopLevelDeclaration : CElement {
    context(ctx: KTFFICodegenContext)
    fun generateImpl(builder: FileSpec.Builder)
}

interface ITopLevelType : ITopLevelDeclaration {
    context(ctx: KTFFICodegenContext)
    fun generate() {
        val builder = FileSpec.builder(packageName(), name)
        generateImpl(builder)
        ctx.writeOutput(builder)
    }
}

sealed class CType(name: String) : CElement.Impl(name) {
    context(ctx: KTFFICodegenContext)
    abstract fun nativeType(): TypeName
    context(ctx: KTFFICodegenContext)
    abstract fun ktApiType(): TypeName
    context(ctx: KTFFICodegenContext)
    abstract fun memoryLayout(): CodeBlock

    context(ctx: KTFFICodegenContext)
    open fun typeObject(builder: TypeSpec.Builder) {
        addAnnotationTo(builder)
        addKdocTo(builder)
    }

    sealed class ValueType(val baseType: CBasicType) : CType(baseType.name),
        ITopLevelType {
        context(ctx: KTFFICodegenContext)
        override fun generateImpl(builder: FileSpec.Builder) {
            val thisCname = className()
            val pointerCnameP = KTFFICodegenHelper.pointerCname.parameterizedBy(thisCname)
            val arrayCnameP = KTFFICodegenHelper.arrayCname.parameterizedBy(thisCname)
            builder.addFunction(
                FunSpec.builder("get")
                    .receiver(arrayCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .returns(thisCname)
                    .addStatement(
                        "return %T.fromNative(%T.arrayVarHandle.get(_segment, 0L, index) as %T)",
                        thisCname,
                        baseType.nativeTypeName,
                        baseType.kotlinTypeName
                    )
                    .build()
            )
            builder.addFunction(
                FunSpec.builder("set")
                    .receiver(arrayCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .addParameter("value", thisCname)
                    .addStatement(
                        "%T.arrayVarHandle.set(_segment, 0L, index, %T.toNative(value))",
                        baseType.nativeTypeName,
                        thisCname,
                    )
                    .build()
            )
            builder.addFunction(
                FunSpec.builder("get")
                    .receiver(pointerCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .returns(thisCname)
                    .addStatement(
                        "return %T.fromNative(%T.arrayVarHandle.get(%M, _address, index) as %T)",
                        thisCname,
                        baseType.nativeTypeName,
                        KTFFICodegenHelper.omniSegment,
                        baseType.kotlinTypeName
                    )
                    .build()
            )
            builder.addFunction(
                FunSpec.builder("set")
                    .receiver(pointerCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .addParameter("value", thisCname)
                    .addStatement(
                        "%T.arrayVarHandle.set(%M, _address, index, %T.toNative(value))",
                        baseType.nativeTypeName,
                        KTFFICodegenHelper.omniSegment,
                        thisCname
                    )
                    .build()
            )
        }
    }

    class BasicType(baseType: CBasicType) : ValueType(baseType) {
        context(ctx: KTFFICodegenContext)
        override fun typeObject(builder: TypeSpec.Builder) {
            super.typeObject(builder)
            builder.superclass(ClassName(KTFFICodegenHelper.packageName, "NativeTypeImpl"))
        }

        context(ctx: KTFFICodegenContext)
        override fun nativeType(): TypeName {
            return baseType.nativeTypeName
        }

        context(ctx: KTFFICodegenContext)
        override fun ktApiType(): TypeName {
            return baseType.kotlinTypeName
        }

        context(ctx: KTFFICodegenContext)
        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of("%M", baseType.valueLayoutMember)
        }
    }

    sealed class CompositeType(name: String) : CType(name)

    abstract class Handle(name: String, val baseType: BasicType): CompositeType(name) {
        context(ctx: KTFFICodegenContext)
        override fun nativeType(): TypeName {
            return baseType.nativeType()
        }

        context(ctx: KTFFICodegenContext)
        override fun ktApiType(): TypeName {
            return this.className()
        }
    }

    class TypeDef(name: String, dstType: CType): CompositeType(name) {
        context(ctx: KTFFICodegenContext)
        override fun nativeType(): TypeName {
            TODO("Not yet implemented")
        }

        context(ctx: KTFFICodegenContext)
        override fun ktApiType(): TypeName {
            TODO("Not yet implemented")
        }

        context(ctx: KTFFICodegenContext)
        override fun memoryLayout(): CodeBlock {
            TODO("Not yet implemented")
        }
    }

    sealed class EnumBase(val entryType: BasicType) : ValueType(entryType.baseType) {
        val entries = mutableListOf<CConst>()
        val aliases = mutableMapOf<String, String>()

        context(ctx: KTFFICodegenContext)
        override fun nativeType(): TypeName {
            return entryType.nativeType()
        }

        context(ctx: KTFFICodegenContext)
        override fun ktApiType(): TypeName {
            return className()
        }

        context(ctx: KTFFICodegenContext)
        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of("%T.layout", entryType.className())
        }

        context(ctx: KTFFICodegenContext)
        override fun typeObject(builder: TypeSpec.Builder) {
            super.typeObject(builder)
            builder.addSuperinterface(KTFFICodegenHelper.typeCname, CodeBlock.of("%T", entryType.className()))
        }
    }

    open class Enum(entryType: BasicType) : EnumBase(entryType)
    open class Bitmask(entryType: BasicType) : EnumBase(entryType)

    class CFunction(name: String, val returnType: CType, val parameters: List<CDeclaration>) : CompositeType(name), ITopLevelType {
        context(ctx: KTFFICodegenContext)
        override fun nativeType(): TypeName {
            throw UnsupportedOperationException()
        }

        context(ctx: KTFFICodegenContext)
        override fun ktApiType(): TypeName {
            return className()
        }

        context(ctx: KTFFICodegenContext)
        override fun memoryLayout(): CodeBlock {
            throw UnsupportedOperationException()
        }

        context(ctx: KTFFICodegenContext)
        override fun generateImpl(builder: FileSpec.Builder) {
            TODO("Not yet implemented")
        }
    }

    open class Array(name: String) : CompositeType(name) {
        var length by Delegates.notNull<Int>()
        lateinit var elementType: CType

        context(ctx: KTFFICodegenContext)
        override fun nativeType(): TypeName {
            return elementType.nativeType()
        }

        context(ctx: KTFFICodegenContext)
        override fun ktApiType(): TypeName {
            return KTFFICodegenHelper.pointerCname.parameterizedBy(elementType.ktApiType())
        }

        context(ctx: KTFFICodegenContext)
        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of(
                "%M(%T.layout)",
                KTFFICodegenHelper.sequenceLayout,
                elementType.className()
            )
        }
    }

    open class Pointer(open val elementType: CType) : CompositeType(elementType.name) {
        context(ctx: KTFFICodegenContext)
        override fun nativeType(): TypeName {
            return LONG
        }

        context(ctx: KTFFICodegenContext)
        override fun ktApiType(): TypeName {
            return KTFFICodegenHelper.pointerCname.parameterizedBy(elementType.ktApiType())
        }

        context(ctx: KTFFICodegenContext)
        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of(
                "%M(%L)",
                KTFFICodegenHelper.pointerLayoutMember,
                elementType.memoryLayout()
            )
        }
    }

    class FunctionPointer(override val elementType: CFunction) : Pointer(elementType) {
        context(ctx: KTFFICodegenContext)
        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of("%M", KTFFICodegenHelper.pointerLayoutMember)
        }
    }

    sealed class Group(name: String) : CompositeType(name), ITopLevelType {
        val members = mutableListOf<CDeclaration>()

        context(ctx: KTFFICodegenContext)
        final override fun nativeType(): TypeName {
            throw UnsupportedOperationException()
        }

        context(ctx: KTFFICodegenContext)
        final override fun ktApiType(): TypeName {
            return KTFFICodegenHelper.pointerCname.parameterizedBy(className())
        }

        context(ctx: KTFFICodegenContext)
        override fun memoryLayout(): CodeBlock {
            val builder = CodeBlock.builder()
            members.forEach {
                builder.addStatement("%T.layout.withName(%S),", it.className(), it.name)
            }
            return builder.build()
        }
    }

    class Struct(name: String) : Group(name) {
        context(ctx: KTFFICodegenContext)
        override fun memoryLayout(): CodeBlock {
            return CodeBlock.builder()
                .add("\n")
                .indent()
                .addStatement("%M(", KTFFICodegenHelper.structLayout)
                .indent()
                .add(super.memoryLayout())
                .unindent()
                .add(").withName(%S)", name)
                .unindent()
                .build()
        }

        context(ctx: KTFFICodegenContext)
        override fun generateImpl(builder: FileSpec.Builder) {
            TODO("Not yet implemented")
        }
    }

    class Union(name: String) : Group(name) {
        context(ctx: KTFFICodegenContext)
        override fun memoryLayout(): CodeBlock {
            return CodeBlock.builder()
                .add("\n")
                .indent()
                .addStatement("%M(", KTFFICodegenHelper.unionLayout)
                .indent()
                .add(super.memoryLayout())
                .unindent()
                .add(").withName(%S)", name)
                .unindent()
                .build()
        }

        context(ctx: KTFFICodegenContext)
        override fun generateImpl(builder: FileSpec.Builder) {
            TODO("Not yet implemented")
        }
    }
}

open class CDeclaration(name: String) : CElement.Impl(name) {
    lateinit var type: CType
}

open class CConst(name: String) : CDeclaration(name) {
    lateinit var valueInitializer: CodeBlock
}