package net.echonolix.ktffi

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.util.*
import java.util.concurrent.ConcurrentHashMap

interface Tag

class TagStorage {
    private val backingMap = mutableMapOf<Class<out Tag>, Tag>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Tag> get(clazz: Class<T>): T? {
        return backingMap[clazz] as? T
    }

    fun <T : Tag> set(clazz: Class<T>, value: T) {
        backingMap[clazz] = value
    }

    inline fun <reified T : Tag> get() = get(T::class.java)
    inline fun <reified T : Tag> set(value: T) = set(T::class.java, value)
}

interface CElement : Comparable<CElement> {
    val name: String

    //    val annotations: List<AnnotationSpec>
    val tags: TagStorage

//    context(ctx: KTFFICodegenContext)
//    fun addAnnotationTo(builder: Annotatable.Builder<*>) {
//        builder.addAnnotations(annotations)
//    }
//
//    context(ctx: KTFFICodegenContext)
//    fun addKdocTo(builder: Documentable.Builder<*>) {
//        kDoc.toString().takeIf { it.isNotBlank() }?.let {
//            builder.addKdoc(it)
//        }
//    }

    fun toSimpleString(): String {
        return toString()
    }

    fun compositeName(parentStr: String): String {
        return "$name$parentStr"
    }

    sealed class Impl(override val name: String) : CElement {
        override val tags: TagStorage = TagStorage()

        override fun compareTo(other: CElement): Int {
            return this.javaClass.simpleName.compareTo(other.javaClass.simpleName)
        }
    }

    interface TopLevel : CElement
}

sealed class CExpression<T : Any>(val type: CType, val value: T) : CElement.Impl(value.toString()) {
    context(ctx: KTFFICodegenContext)
    abstract fun codeBlock(): CodeBlock

    class Const(type: CBasicType<*>, value: CodeBlock) : CExpression<CodeBlock>(type.cType, value) {
        context(ctx: KTFFICodegenContext)
        override fun codeBlock(): CodeBlock {
            return value
        }

        override fun toString(): String {
            return value.toString()
        }
    }

    class StringLiteral(value: String) : CExpression<String>(CType.Pointer(CBasicType.char::cType), value) {
        context(ctx: KTFFICodegenContext)
        override fun codeBlock(): CodeBlock {
            return CodeBlock.of("%S", value)
        }

        override fun toString(): String {
            return value
        }
    }

    class Reference(const: CTopLevelConst) : CExpression<CTopLevelConst>(const.type, const) {
        context(ctx: KTFFICodegenContext)
        override fun codeBlock(): CodeBlock {
            return CodeBlock.of("%M", value.memberName())
        }

        override fun toString(): String {
            return value.name
        }
    }
}

class CConstExpression(val valueInitializer: CodeBlock) : CElement.Impl(valueInitializer.toString())

interface CDeclaration : CElement {
    val type: CType

    open class Impl(name: String, override val type: CType) : CElement.Impl(name), CDeclaration {
        override fun toString(): String {
            return "${type.toSimpleString()} $name"
        }
    }

    interface TopLevel : CDeclaration, CElement.TopLevel {
        context(ctx: KTFFICodegenContext)
        fun memberName(): MemberName
    }
}

open class CConst(name: String, val expression: CExpression<*>) : CDeclaration.Impl(name, expression.type) {
    override fun toString(): String {
        return "${type.toSimpleString()} $name = $expression;"
    }
}

open class CTopLevelConst(name: String, expression: CExpression<*>) : CConst(name, expression), CDeclaration.TopLevel {
    context(ctx: KTFFICodegenContext)
    override fun memberName(): MemberName {
        return MemberName(packageName(), name)
    }
}

//
//interface ITopLevelType : ITopLevelDeclaration {
//    context(ctx: KTFFICodegenContext)
//    fun generate() {
//        val builder = FileSpec.builder(packageName(), name)
//        generateImpl(builder)
//        ctx.writeOutput(builder)
//    }
//}

sealed class CType(name: String) : CElement.Impl(name), CElement.TopLevel {
    context(ctx: KTFFICodegenContext)
    abstract fun nativeType(): TypeName

    context(ctx: KTFFICodegenContext)
    abstract fun ktApiType(): TypeName

    context(ctx: KTFFICodegenContext)
    abstract fun memoryLayout(): CodeBlock

//    context(ctx: KTFFICodegenContext)
//    open fun typeObject(builder: TypeSpec.Builder) {
//        addAnnotationTo(builder)
//        addKdocTo(builder)
//    }

    sealed class ValueType(val baseType: CBasicType<*>) : CType(baseType.name) {
//        context(ctx: KTFFICodegenContext)
//        override fun generateImpl(builder: FileSpec.Builder) {
//            val thisCname = className()
//            val pointerCnameP = KTFFICodegenHelper.pointerCname.parameterizedBy(thisCname)
//            val arrayCnameP = KTFFICodegenHelper.arrayCname.parameterizedBy(thisCname)
//            builder.addFunction(
//                FunSpec.builder("get")
//                    .receiver(arrayCnameP)
//                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
//                    .addParameter("index", LONG)
//                    .returns(thisCname)
//                    .addStatement(
//                        "return %T.fromNative(%T.arrayVarHandle.get(_segment, 0L, index) as %T)",
//                        thisCname,
//                        baseType.nativeTypeName,
//                        baseType.kotlinTypeName
//                    )
//                    .build()
//            )
//            builder.addFunction(
//                FunSpec.builder("set")
//                    .receiver(arrayCnameP)
//                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
//                    .addParameter("index", LONG)
//                    .addParameter("value", thisCname)
//                    .addStatement(
//                        "%T.arrayVarHandle.set(_segment, 0L, index, %T.toNative(value))",
//                        baseType.nativeTypeName,
//                        thisCname,
//                    )
//                    .build()
//            )
//            builder.addFunction(
//                FunSpec.builder("get")
//                    .receiver(pointerCnameP)
//                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
//                    .addParameter("index", LONG)
//                    .returns(thisCname)
//                    .addStatement(
//                        "return %T.fromNative(%T.arrayVarHandle.get(%M, _address, index) as %T)",
//                        thisCname,
//                        baseType.nativeTypeName,
//                        KTFFICodegenHelper.omniSegment,
//                        baseType.kotlinTypeName
//                    )
//                    .build()
//            )
//            builder.addFunction(
//                FunSpec.builder("set")
//                    .receiver(pointerCnameP)
//                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
//                    .addParameter("index", LONG)
//                    .addParameter("value", thisCname)
//                    .addStatement(
//                        "%T.arrayVarHandle.set(%M, _address, index, %T.toNative(value))",
//                        baseType.nativeTypeName,
//                        KTFFICodegenHelper.omniSegment,
//                        thisCname
//                    )
//                    .build()
//            )
//        }
    }

    class BasicType(baseType: CBasicType<*>) : ValueType(baseType) {
//        context(ctx: KTFFICodegenContext)
//        override fun typeObject(builder: TypeSpec.Builder) {
//            super.typeObject(builder)
//            builder.superclass(ClassName(KTFFICodegenHelper.packageName, "NativeTypeImpl"))
//        }

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

        override fun compareTo(other: CElement): Int {
            if (other is BasicType) {
                return this.baseType.index.compareTo(other.baseType.index)
            }
            return super.compareTo(other)
        }

        override fun toString(): String {
            return name
        }
    }

    sealed class CompositeType(name: String) : CType(name)

    abstract class Handle(name: String) : CompositeType(name) {
        context(ctx: KTFFICodegenContext)
        override fun nativeType(): TypeName {
            return LONG
        }

        context(ctx: KTFFICodegenContext)
        override fun ktApiType(): TypeName {
            return this.className()
        }

        context(ctx: KTFFICodegenContext)
        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of("%M", CBasicType.uint64_t.valueLayoutMember)
        }

        override fun toString(): String {
            return "handle $name"
        }
    }

    class TypeDef(name: String, val dstType: CType) : CompositeType(name) {
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

        override fun toString(): String {
            return "typedef ${dstType.name} $name;"
        }

        override fun toSimpleString(): String {
            return name
        }

        override fun compareTo(other: CElement): Int {
            if (other is TypeDef) {
                var v = this.dstType.compareTo(other.dstType)
                if (v == 0) {
                    v = this.name.compareTo(other.name)
                }
                return v
            }
            return super.compareTo(other)
        }
    }

    abstract class EnumBase(override val name: String, val entryType: BasicType) : ValueType(entryType.baseType) {
        val entries: MutableMap<String, Entry> = ConcurrentHashMap()

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

//        context(ctx: KTFFICodegenContext)
//        override fun typeObject(builder: TypeSpec.Builder) {
//            super.typeObject(builder)
//            builder.addSuperinterface(KTFFICodegenHelper.typeCname, CodeBlock.of("%T", entryType.className()))
//        }

        override fun toString(): String {
            return buildString {
                append(name)
                if (entries.isEmpty()) {
                    append(" {};")
                } else {
                    append(" (\n    ")
                    append(entries.values.joinToString(",\n    ") { "${it.name} = ${it.expression}" })
                    append("\n);")
                }
            }
        }

        override fun toSimpleString(): String {
            return name
        }

        inner class Entry(name: String, expression: CExpression<*>) : CTopLevelConst(name, expression) {
            val parent get() = this@EnumBase

            context(ctx: KTFFICodegenContext)
            override fun memberName(): MemberName {
                return parent.className().member(name)
            }
        }
    }

    class Enum(name: String, entryType: BasicType) : EnumBase(name, entryType) {
        override fun toString(): String {
            return "enum ${super.toString()}"
        }

        override fun toSimpleString(): String {
            return "enum ${super.toSimpleString()}"
        }
    }

    class Bitmask(name: String, entryType: BasicType) : EnumBase(name, entryType) {
        override fun toString(): String {
            return "bitmask ${super.toString()}"
        }

        override fun toSimpleString(): String {
            return "bitmask ${super.toSimpleString()}"
        }
    }

    class Function(name: String, val returnType: CType, val parameters: List<Parameter>) : CompositeType(name) {
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

//        context(ctx: KTFFICodegenContext)
//        override fun generateImpl(builder: FileSpec.Builder) {
//            TODO("Not yet implemented")
//        }

        override fun toString(): String {
            return buildString {
                append(returnType.toSimpleString())
                append(' ')
                append(name)
                if (parameters.isEmpty()) {
                    append("();")
                } else {
                    append("(\n    ")
                    append(parameters.joinToString(",\n    ") { it.toSimpleString() })
                    append("\n);")
                }
            }
        }

        override fun toSimpleString(): String {
            return name
        }

        class Parameter(name: String, type: CType) : CDeclaration.Impl(name, type)
    }

    open class Array(open val elementType: CType) : CompositeType("") {
        override val name: String by lazy { compositeName("") }

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

        override fun toString(): String {
            return name
        }

        override fun compositeName(parentStr: String): String {
            return elementType.compositeName("$parentStr[]")
        }

        class Sized(elementType: CType, val length: CExpression<*>) : Array(elementType) {
            override fun compositeName(parentStr: String): String {
                return elementType.compositeName("$parentStr[$length]")
            }
        }
    }

    open class Pointer(elementTypeProvider: () -> CType) : CompositeType("") {
        open val elementType: CType by lazy { elementTypeProvider() }
        override val name: String by lazy { compositeName("") }

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

        override fun toString(): String {
            return name
        }

        override fun compositeName(parentStr: String): String {
            return elementType.compositeName("$parentStr*")
        }
    }

    class FunctionPointer(override val elementType: Function) : Pointer({ elementType }) {
        context(ctx: KTFFICodegenContext)
        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of("%M", KTFFICodegenHelper.pointerLayoutMember)
        }
    }

    sealed class Group(name: String, val members: List<Member>) : CompositeType(name) {
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
                builder.addStatement("%T.layout.withName(%S),", it.type.className(), it.name)
            }
            return builder.build()
        }

        override fun toString(): String {
            return buildString {
                append(name)
                if (members.isEmpty()) {
                    append(" {};")
                } else {
                    append(" {\n    ")
                    append(members.joinToString(",\n    ") { it.toSimpleString() })
                    append("\n};")
                }
            }
        }

        override fun toSimpleString(): String {
            return name
        }

        class Member(name: String, type: CType) : CDeclaration.Impl(name, type)
    }

    class Struct(name: String, members: List<Member>) : Group(name, members) {
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

//        context(ctx: KTFFICodegenContext)
//        override fun generateImpl(builder: FileSpec.Builder) {
//            TODO("Not yet implemented")
//        }

        override fun toString(): String {
            return "struct ${super.toString()}"
        }

        override fun toSimpleString(): String {
            return "struct ${super.toSimpleString()}"
        }
    }

    class Union(name: String, members: List<Member>) : Group(name, members) {
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

//        context(ctx: KTFFICodegenContext)
//        override fun generateImpl(builder: FileSpec.Builder) {
//            TODO("Not yet implemented")
//        }

        override fun toString(): String {
            return "union ${super.toString()}"
        }

        override fun toSimpleString(): String {
            return "union ${super.toSimpleString()}"
        }
    }
}