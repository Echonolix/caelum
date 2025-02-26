package org.echonolix.ktffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlin.properties.Delegates

sealed class CElement {
    lateinit var name: String
    lateinit var packageName: String
    var docs = ""

    val className get() = ClassName(packageName, name)

    val aliases = mutableListOf<String>()
    val annotations = mutableListOf<AnnotationSpec>()
}

sealed class CType : CElement() {
    abstract fun byteSize(): Long
    abstract fun nativeType(): TypeName
    abstract fun ktApiType(): TypeName
    abstract fun memoryLayout(): CodeBlock

    open fun typeObject(builder: TypeSpec.Builder) {
        builder.addAnnotations(annotations)
        if (docs.isNotBlank()) builder.addKdoc(docs)
        builder.addSuperclassConstructorParameter(memoryLayout())
    }

    class BasicType(val cBasicType: CBasicType) : CType() {
        override fun typeObject(builder: TypeSpec.Builder) {
            super.typeObject(builder)
            builder.superclass(ClassName(KTFFICodegenHelper.packageName, "NativeTypeImpl"))
        }

        override fun byteSize(): Long {
            return cBasicType.valueLayout.byteSize()
        }

        override fun nativeType(): TypeName {
            return cBasicType.nativeTypeName
        }

        override fun ktApiType(): TypeName {
            return cBasicType.kotlinTypeName
        }

        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of("%M", cBasicType.valueLayoutMember)
        }
    }

    sealed class NonBasicType : CType()
    sealed class CompositeType : NonBasicType()

    open class Enum : NonBasicType() {
        lateinit var entryType: CType
        val entries = mutableListOf<Entry>()

        override fun byteSize(): Long {
            return entryType.byteSize()
        }

        override fun nativeType(): TypeName {
            return entryType.nativeType()
        }

        override fun ktApiType(): TypeName {
            return className
        }

        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of("%T.layout", entryType.className)
        }

        inner class Entry : CDeclaration() {
            lateinit var valueInitializer: CodeBlock
        }
    }

    open class Bitmask : Enum()

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
            return CodeBlock.builder()
                .add("%M(", KTFFICodegenHelper.sequenceLayout)
                .add("%T.layout", elementType.className)
                .add(")")
                .build()
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
            return CodeBlock.builder()
                .add("%M(", KTFFICodegenHelper.pointerLayout)
                .add(elementType.memoryLayout())
                .add(")")
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
            throw UnsupportedOperationException()
        }

        override fun memoryLayout(): CodeBlock {
            throw UnsupportedOperationException()
        }
    }

    class Opaque : NonBasicType() {
        override fun byteSize(): Long {
            throw UnsupportedOperationException()
        }

        override fun nativeType(): TypeName {
            throw UnsupportedOperationException()
        }

        override fun ktApiType(): TypeName {
            throw UnsupportedOperationException()
        }

        override fun memoryLayout(): CodeBlock {
            throw UnsupportedOperationException()
        }
    }

    sealed class Group : CompositeType() {
        val members = mutableListOf<CDeclaration>()

        abstract fun resolveMembers(): List<ResolvedMember>

        final override fun nativeType(): TypeName {
            throw UnsupportedOperationException()
        }

        final override fun ktApiType(): TypeName {
            throw UnsupportedOperationException()
        }

        override fun memoryLayout(): CodeBlock {
            val builder = CodeBlock.builder()
            resolveMembers().forEach {
                builder.addStatement("%T.layout.withName(%S),", it.decl.className, it.decl.name)
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