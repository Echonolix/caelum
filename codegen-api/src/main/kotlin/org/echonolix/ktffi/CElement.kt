package org.echonolix.ktffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlin.properties.Delegates

interface ICElement {
    val ctx: KTFFICodegenContext
    val name: String
    val kDoc: KDoc
    val annotations: List<AnnotationSpec>

    fun packageName(): String
    fun className(): ClassName

    fun addAnnotationTo(builder: Annotatable.Builder<*>) {
        builder.addAnnotations(annotations)
    }

    fun addKdocTo(builder: Documentable.Builder<*>) {
        kDoc.toString().takeIf { it.isNotBlank() }?.let {
            builder.addKdoc(it)
        }
    }
}

interface ITopLevelDeclaration : ICElement {
    fun generateImpl(builder: FileSpec.Builder)
}

interface ITopLevelType : ITopLevelDeclaration {
    fun generate() {
        val builder = FileSpec.builder(packageName(), name)
        generateImpl(builder)
        ctx.writeOutput(builder)
    }
}

sealed class CElement : ICElement {
    override lateinit var ctx: KTFFICodegenContext
    override lateinit var name: String
    override val kDoc: KDoc = KDoc()

    override fun packageName(): String {
        return ctx.getPackageName(this)
    }

    override fun className(): ClassName {
        return ClassName(packageName(), name)
    }

    override val annotations = mutableListOf<AnnotationSpec>()
}

sealed class CType : CElement() {
    abstract fun nativeType(): TypeName
    abstract fun ktApiType(): TypeName
    abstract fun memoryLayout(): CodeBlock

    open fun typeObject(builder: TypeSpec.Builder) {
        addAnnotationTo(builder)
        addKdocTo(builder)
    }

    sealed class ValueType(val baseType: CBasicType) : CType(), ITopLevelType {
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
        override fun typeObject(builder: TypeSpec.Builder) {
            super.typeObject(builder)
            builder.superclass(ClassName(KTFFICodegenHelper.packageName, "NativeTypeImpl"))
        }

        override fun nativeType(): TypeName {
            return baseType.nativeTypeName
        }

        override fun ktApiType(): TypeName {
            return baseType.kotlinTypeName
        }

        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of("%M", baseType.valueLayoutMember)
        }
    }

    sealed class CompositeType : CType()

    sealed class EnumBase(val entryType: BasicType) : ValueType(entryType.baseType) {
        val entries = mutableListOf<CConst>()
        val aliases = mutableMapOf<String, String>()

        override fun nativeType(): TypeName {
            return entryType.nativeType()
        }

        override fun ktApiType(): TypeName {
            return className()
        }

        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of("%T.layout", entryType.className())
        }

        override fun typeObject(builder: TypeSpec.Builder) {
            super.typeObject(builder)
            builder.addSuperinterface(KTFFICodegenHelper.typeCname, CodeBlock.of("%T", entryType.className()))
        }
    }

    open class Enum(entryType: BasicType) : EnumBase(entryType)
    open class Bitmask(entryType: BasicType) : EnumBase(entryType)

    class CFunction : CompositeType(), ITopLevelType {
        lateinit var returnType: CType
        val parameters = mutableListOf<CDeclaration>()

        override fun nativeType(): TypeName {
            throw UnsupportedOperationException()
        }

        override fun ktApiType(): TypeName {
            return className()
        }

        override fun memoryLayout(): CodeBlock {
            throw UnsupportedOperationException()
        }

        override fun generateImpl(builder: FileSpec.Builder) {
            TODO("Not yet implemented")
        }
    }

    open class Array : CompositeType() {
        var length by Delegates.notNull<Int>()
        lateinit var elementType: CType

        override fun nativeType(): TypeName {
            return elementType.nativeType()
        }

        override fun ktApiType(): TypeName {
            return KTFFICodegenHelper.pointerCname.parameterizedBy(elementType.ktApiType())
        }

        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of(
                "%M(%T.layout)",
                KTFFICodegenHelper.sequenceLayout,
                elementType.className()
            )
        }
    }

    open class Pointer : CompositeType() {
        open lateinit var elementType: CType

        override fun nativeType(): TypeName {
            return LONG
        }

        override fun ktApiType(): TypeName {
            return KTFFICodegenHelper.pointerCname.parameterizedBy(elementType.ktApiType())
        }

        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of(
                "%M(%L)",
                KTFFICodegenHelper.pointerLayoutMember,
                elementType.memoryLayout()
            )
        }
    }

    class FunctionPointer : Pointer() {
        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of("%M", KTFFICodegenHelper.pointerLayoutMember)
        }
    }

    sealed class Group : CompositeType(), ITopLevelType {
        val members = mutableListOf<CDeclaration>()

        final override fun nativeType(): TypeName {
            throw UnsupportedOperationException()
        }

        final override fun ktApiType(): TypeName {
            return KTFFICodegenHelper.pointerCname.parameterizedBy(className())
        }

        override fun memoryLayout(): CodeBlock {
            val builder = CodeBlock.builder()
            members.forEach {
                builder.addStatement("%T.layout.withName(%S),", it.className(), it.name)
            }
            return builder.build()
        }
    }

    class Struct : Group() {
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

        override fun generateImpl(builder: FileSpec.Builder) {
            TODO("Not yet implemented")
        }
    }

    class Union : Group() {
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

        override fun generateImpl(builder: FileSpec.Builder) {
            TODO("Not yet implemented")
        }
    }
}

open class CDeclaration : CElement() {
    lateinit var type: CType
}

open class CConst : CDeclaration() {
    lateinit var valueInitializer: CodeBlock
}