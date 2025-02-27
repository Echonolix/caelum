package org.echonolix.ktffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlin.properties.Delegates

interface ICElement {
    val ctx: KTFFICodegenContext
    val name: String
    val docs: String
    val annotations: List<AnnotationSpec>
    val aliases: List<String>

    fun packageName(): String
    fun className(): ClassName

    fun addAnnotationTo(builder: Annotatable.Builder<*>) {
        builder.addAnnotations(annotations)
    }

    fun addKdocTo(builder: Documentable.Builder<*>) {
        if (docs.isNotBlank()) builder.addKdoc(docs)
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
    override var docs = ""

    override fun packageName(): String {
        return ctx.getPackageName(this)
    }

    override fun className(): ClassName {
        return ClassName(packageName(), name)
    }

    override val aliases = mutableListOf<String>()
    override val annotations = mutableListOf<AnnotationSpec>()
}

sealed class CType : CElement() {
    abstract fun byteSize(): Long
    abstract fun nativeType(): TypeName
    abstract fun ktApiType(): TypeName
    abstract fun memoryLayout(): CodeBlock

    open fun typeObject(builder: TypeSpec.Builder) {
        addAnnotationTo(builder)
        addKdocTo(builder)
    }

    sealed class ValueType : CType(), ITopLevelType {
        lateinit var baseType: CBasicType

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

    class BasicType : ValueType() {
        override fun typeObject(builder: TypeSpec.Builder) {
            super.typeObject(builder)
            builder.superclass(ClassName(KTFFICodegenHelper.packageName, "NativeTypeImpl"))
        }

        override fun byteSize(): Long {
            return baseType.valueLayout.byteSize()
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

    sealed class EnumBase : ValueType() {
        lateinit var entryType: BasicType
        val entries = mutableListOf<Entry>()

        override fun byteSize(): Long {
            return entryType.byteSize()
        }

        override fun nativeType(): TypeName {
            return entryType.nativeType()
        }

        override fun ktApiType(): TypeName {
            return className()
        }

        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of("%T.layout", entryType.className())
        }

        inner class Entry : CDeclaration() {
            lateinit var valueInitializer: CodeBlock
        }

        override fun typeObject(builder: TypeSpec.Builder) {
            super.typeObject(builder)
            builder.addSuperinterface(KTFFICodegenHelper.typeCname, CodeBlock.of("%T", entryType.className()))
        }
    }

    open class Enum : EnumBase() {

    }

    open class Bitmask : EnumBase()

    open class Array : CompositeType() {
        var length by Delegates.notNull<Int>()
        lateinit var elementType: CType

        override fun byteSize(): Long {
            return length * elementType.byteSize()
        }

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

    class Pointer : CompositeType() {
        lateinit var elementType: CType

        override fun byteSize(): Long {
            return 8L
        }

        override fun nativeType(): TypeName {
            return LONG
        }

        override fun ktApiType(): TypeName {
            return KTFFICodegenHelper.pointerCname.parameterizedBy(elementType.ktApiType())
        }

        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of(
                "%M(%L)",
                KTFFICodegenHelper.pointerLayout,
                elementType.memoryLayout()
            )
        }
    }

    sealed class Group : CompositeType(), ITopLevelType {
        val members = mutableListOf<CDeclaration>()

        abstract fun resolveMembers(): List<ResolvedMember>

        final override fun nativeType(): TypeName {
            throw UnsupportedOperationException()
        }

        final override fun ktApiType(): TypeName {
            return KTFFICodegenHelper.pointerCname.parameterizedBy(className())
        }

        override fun memoryLayout(): CodeBlock {
            val builder = CodeBlock.builder()
            resolveMembers().forEach {
                builder.addStatement("%T.layout.withName(%S),", it.decl.className(), it.decl.name)
                if (it.afterPadding > 0) builder.addStatement("%M(%L),", it.afterPadding)
            }
            return builder.build()
        }

        class ResolvedMember(val decl: CDeclaration) {
            var afterPadding = 0L
        }
    }

    class Struct : Group() {
        override fun resolveMembers(): List<ResolvedMember> {
            val resolved = mutableListOf<ResolvedMember>()
            var offset = 0L
            var maxElement = 0L
            members.forEach { member ->
                val memberSize = member.type.byteSize()
                resolved.lastOrNull()?.let {
                    it.afterPadding = (memberSize - (offset % memberSize))
                    offset += it.afterPadding
                }
                resolved.add(ResolvedMember(member))
                maxElement = maxOf(maxElement, offset + memberSize)
                offset += memberSize
            }
            resolved.lastOrNull()?.afterPadding = (maxElement - (offset % maxElement))
            return resolved
        }

        override fun byteSize(): Long {
            return resolveMembers().sumOf { it.decl.type.byteSize() + it.afterPadding }
        }

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
    }

    class Union : Group() {
        override fun resolveMembers(): List<ResolvedMember> {
            return members.map { ResolvedMember(it) }
        }

        override fun byteSize(): Long {
            return resolveMembers().maxOf { it.decl.type.byteSize() }
        }

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
    }

    class FunctionPointer : NonBasicType() {
        lateinit var proto: CFunctionProto

        override fun byteSize(): Long {
            throw UnsupportedOperationException()
        }

        override fun nativeType(): TypeName {
            throw UnsupportedOperationException()
        }

        override fun ktApiType(): TypeName {
            return className
        }

        override fun memoryLayout(): CodeBlock {
            throw UnsupportedOperationException()
        }
    }

    class Opaque : NonBasicType() {
        lateinit var baseType: CType

        override fun byteSize(): Long {
            return baseType.byteSize()
        }

        override fun nativeType(): TypeName {
            return baseType.nativeType()
        }

        override fun ktApiType(): TypeName {
            return className
        }

        override fun memoryLayout(): CodeBlock {
            return baseType.memoryLayout()
        }
    }
}

open class CDeclaration : CElement() {
    lateinit var type: CType
}

class CParameter : CDeclaration()

class CFunctionProto : CElement() {
    lateinit var returnType: CType
    val parameters = mutableListOf<CParameter>()
}

class CFunction : CElement() {
    lateinit var proto: CFunctionProto
}