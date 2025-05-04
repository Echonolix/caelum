package net.echonolix.caelum

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import java.util.concurrent.ConcurrentHashMap

public interface Tag

public class TagStorage {
    private val backingMap = mutableMapOf<Class<out Tag>, Tag>()

    @Suppress("UNCHECKED_CAST")
    public fun <T : Tag> get(clazz: Class<T>): T? {
        return backingMap[clazz] as? T
    }

    public fun <T : Tag> set(clazz: Class<T>, value: T) {
        backingMap[clazz] = value
    }

    public inline fun <reified T : Tag> get(): T? = get(T::class.java)
    public inline fun <reified T : Tag> set(value: T): Unit = set(T::class.java, value)
    public inline fun <reified T : Tag> has(): Boolean = get<T>() != null
}

public class TypeNameRename(public val name: String): Tag

public interface CElement : Comparable<CElement> {
    public val name: String
    public val tags: TagStorage

    public fun toSimpleString(): String {
        return toString()
    }

    public fun compositeName(parentStr: String): String {
        return "$name$parentStr"
    }

    public sealed class Impl(override val name: String) : CElement {
        override val tags: TagStorage = TagStorage()

        override fun compareTo(other: CElement): Int {
            return this.javaClass.simpleName.compareTo(other.javaClass.simpleName)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Impl

            return name == other.name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }
    }

    public interface TopLevel : CElement {
        context(ctx: CaelumCodegenContext)
        public fun packageName(): String {
            return ctx.resolvePackageName(this)
        }

        context(ctx: CaelumCodegenContext)
        public fun typeName(): TypeName {
            return ClassName(packageName(), tags.get<TypeNameRename>()?.name ?: name)
        }

        context(ctx: CaelumCodegenContext)
        public fun className(): ClassName {
            return typeName() as? ClassName
                ?: throw UnsupportedOperationException("${javaClass.simpleName} doesn't have a class name")
        }
    }
}

public sealed class CExpression<T : Any>(public val type: CType, public val value: T) :
    CElement.Impl(value.toString()) {
    context(ctx: CaelumCodegenContext)
    public abstract fun codeBlock(): CodeBlock

    public class Const(type: CBasicType<*>, value: CodeBlock) : CExpression<CodeBlock>(type.cType, value) {
        context(ctx: CaelumCodegenContext)
        override fun codeBlock(): CodeBlock {
            return value
        }

        override fun toString(): String {
            return value.toString()
        }
    }

    public class StringLiteral(value: String) : CExpression<String>(CType.Pointer(CBasicType.char::cType), value) {
        context(ctx: CaelumCodegenContext)
        override fun codeBlock(): CodeBlock {
            return CodeBlock.of("%S", value)
        }

        override fun toString(): String {
            return value
        }
    }

    public class Reference(const: CTopLevelConst) : CExpression<CTopLevelConst>(const.type, const) {
        context(ctx: CaelumCodegenContext)
        override fun codeBlock(): CodeBlock {
            return CodeBlock.of("%M", value.memberName())
        }

        override fun toString(): String {
            return value.name
        }
    }
}

public class CConstExpression(valueInitializer: CodeBlock) : CElement.Impl(valueInitializer.toString())

public interface CDeclaration : CElement {
    public val type: CType

    public open class Impl(name: String, override val type: CType) : CElement.Impl(name), CDeclaration {
        override fun toString(): String {
            return "${type.toSimpleString()} $name"
        }
    }

    public interface TopLevel : CDeclaration, CElement.TopLevel {
        context(ctx: CaelumCodegenContext)
        public fun memberName(): MemberName
    }
}

public open class CConst(name: String, public val expression: CExpression<*>) :
    CDeclaration.Impl(name, expression.type) {
    override fun toString(): String {
        return "${type.toSimpleString()} $name = $expression;"
    }
}

public open class CTopLevelConst(name: String, expression: CExpression<*>) : CConst(name, expression),
    CDeclaration.TopLevel {
    context(ctx: CaelumCodegenContext)
    override fun memberName(): MemberName {
        return MemberName(packageName(), name)
    }
}

public sealed class CType(name: String) : CElement.Impl(name), CElement.TopLevel {
    context(ctx: CaelumCodegenContext)
    public abstract fun nativeType(): TypeName

    context(ctx: CaelumCodegenContext)
    public abstract fun ktApiType(): TypeName

    context(ctx: CaelumCodegenContext)
    public open fun typeDescriptorTypeName(): TypeName? {
        return typeName()
    }

    context(ctx: CaelumCodegenContext)
    public abstract fun memoryLayoutDeep(): CodeBlock

    context(ctx: CaelumCodegenContext)
    public open fun memoryLayout(): CodeBlock {
        return memoryLayoutDeep()
    }

    public sealed class ValueType(public val baseType: CBasicType<*>) : CType(baseType.cTypeNameStr)

    public class BasicType(baseType: CBasicType<*>) : ValueType(baseType) {
        context(ctx: CaelumCodegenContext)
        override fun nativeType(): TypeName {
            return baseType.nativeDataTypeName
        }

        context(ctx: CaelumCodegenContext)
        override fun ktApiType(): TypeName {
            return baseType.ktApiTypeTypeName
        }

        context(ctx: CaelumCodegenContext)
        override fun packageName(): String {
            return CaelumCodegenHelper.basePkgName
        }

        context(ctx: CaelumCodegenContext)
        override fun typeName(): ClassName {
            return baseType.caelumCoreTypeName as ClassName
        }

        context(ctx: CaelumCodegenContext)
        public override fun typeDescriptorTypeName(): TypeName? {
            if (baseType === CBasicType.void) return null
            return typeName()
        }

        context(ctx: CaelumCodegenContext)
        override fun memoryLayoutDeep(): CodeBlock {
            if (baseType === CBasicType.void) {
                return CodeBlock.of("%M", baseType.valueLayoutMember)
            }
            return CodeBlock.of("%T.layout", baseType.caelumCoreTypeName)
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

    public sealed class CompositeType(name: String) : CType(name)

    public class Handle(name: String) : CompositeType(name) {
        context(ctx: CaelumCodegenContext)
        override fun nativeType(): TypeName {
            return LONG
        }

        context(ctx: CaelumCodegenContext)
        override fun ktApiType(): TypeName {
            return this.typeName()
        }

        context(ctx: CaelumCodegenContext)
        override fun memoryLayoutDeep(): CodeBlock {
            return CodeBlock.of("%T.layout", typeName())
        }

        override fun toString(): String {
            return "handle $name"
        }
    }

    public class TypeDef(name: String, public val dstType: CType) : CompositeType(name) {
        context(ctx: CaelumCodegenContext)
        override fun nativeType(): TypeName {
            return dstType.nativeType()
        }

        context(ctx: CaelumCodegenContext)
        override fun ktApiType(): TypeName {
            return dstType.ktApiType()
        }

        context(ctx: CaelumCodegenContext)
        public override fun typeDescriptorTypeName(): TypeName? {
            return if (dstType is FunctionPointer) {
                CaelumCodegenHelper.pointerCname
            } else {
                typeName()
            }
        }

        context(ctx: CaelumCodegenContext)
        override fun memoryLayoutDeep(): CodeBlock {
            return CodeBlock.of("%T.layout", typeName())
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

    public abstract class EnumBase(override val name: String, public val entryType: BasicType) :
        ValueType(entryType.baseType) {
        public val entries: MutableMap<String, Entry> = mutableMapOf()

        context(ctx: CaelumCodegenContext)
        override fun nativeType(): TypeName {
            return entryType.nativeType()
        }

        context(ctx: CaelumCodegenContext)
        override fun ktApiType(): TypeName {
            return typeName()
        }

        context(ctx: CaelumCodegenContext)
        override fun typeName(): TypeName {
            return ClassName(packageName(), name)
        }

        context(ctx: CaelumCodegenContext)
        override fun memoryLayoutDeep(): CodeBlock {
            return entryType.memoryLayout()
        }

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

        public inner class Entry(name: String, expression: CExpression<*>) : CTopLevelConst(name, expression) {
            public val parent: EnumBase get() = this@EnumBase

            context(ctx: CaelumCodegenContext)
            override fun memberName(): MemberName {
                return parent.className().member(name)
            }
        }
    }

    public class Enum(name: String, entryType: BasicType) : EnumBase(name, entryType) {
        override fun toString(): String {
            return "enum ${super.toString()}"
        }

        override fun toSimpleString(): String {
            return "enum ${super.toSimpleString()}"
        }
    }

    public class Bitmask(name: String, entryType: BasicType) : EnumBase(name, entryType) {
        override fun toString(): String {
            return "bitmask ${super.toString()}"
        }

        override fun toSimpleString(): String {
            return "bitmask ${super.toSimpleString()}"
        }
    }

    public class Function(name: String, public val returnType: CType, public val parameters: List<Parameter>) :
        CompositeType(name) {
        context(ctx: CaelumCodegenContext)
        override fun nativeType(): TypeName {
            throw UnsupportedOperationException()
        }

        context(ctx: CaelumCodegenContext)
        override fun ktApiType(): TypeName {
            return typeName()
        }

        context(ctx: CaelumCodegenContext)
        override fun memoryLayoutDeep(): CodeBlock {
            throw UnsupportedOperationException()
        }

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

        public class Parameter(name: String, type: CType) : CDeclaration.Impl(name, type)
    }

    public open class Array(public open val elementType: CType) : CompositeType("") {
        override val name: String by lazy { compositeName("") }

        context(ctx: CaelumCodegenContext)
        override fun nativeType(): TypeName {
            return LONG
        }

        context(ctx: CaelumCodegenContext)
        public override fun typeDescriptorTypeName(): TypeName? {
            return CaelumCodegenHelper.pointerCname
        }

        context(ctx: CaelumCodegenContext)
        override fun ktApiType(): TypeName {
            val eType = elementType.deepResolve()
            return if (eType is BasicType) {
                CaelumCodegenHelper.pointerCname.parameterizedBy(eType.baseType.caelumCoreTypeName)
            } else {
                CaelumCodegenHelper.pointerCname.parameterizedBy(eType.ktApiType())
            }
        }

        context(ctx: CaelumCodegenContext)
        override fun packageName(): String {
            throw UnsupportedOperationException("Array isn't a top level type")
        }

        context(ctx: CaelumCodegenContext)
        override fun typeName(): TypeName {
            throw UnsupportedOperationException("Array isn't a top level type")
        }

        context(ctx: CaelumCodegenContext)
        override fun memoryLayoutDeep(): CodeBlock {
            throw UnsupportedOperationException("Flexible array is currently not supported")
//            return CodeBlock.of(
//                "%M(%L)",
//                CaelumCodegenHelper.addressLayoutMember,
//                elementType.memoryLayout()
//            )
        }

        override fun toString(): String {
            return name
        }

        override fun compositeName(parentStr: String): String {
            return elementType.compositeName("$parentStr[]")
        }

        public class Sized(elementType: CType, public val length: CExpression<*>) : Array(elementType) {
            override fun compositeName(parentStr: String): String {
                return elementType.compositeName("$parentStr[$length]")
            }

            context(ctx: CaelumCodegenContext)
            override fun memoryLayoutDeep(): CodeBlock {
                return CodeBlock.of(
                    "%M(%L.toLong(), %L)",
                    CaelumCodegenHelper.sequenceLayout,
                    length.codeBlock(),
                    elementType.memoryLayout()
                )
            }
        }
    }

    public open class Pointer(elementTypeProvider: () -> CType) : CompositeType("") {
        public open val elementType: CType by lazy { elementTypeProvider() }
        override val name: String by lazy { compositeName("") }

        context(ctx: CaelumCodegenContext)
        override fun nativeType(): TypeName {
            return LONG
        }

        context(ctx: CaelumCodegenContext)
        public override fun typeDescriptorTypeName(): TypeName? {
            return CaelumCodegenHelper.pointerCname
        }

        context(ctx: CaelumCodegenContext)
        override fun ktApiType(): TypeName {
            val eType = elementType.deepResolve()
            return when (eType) {
                is BasicType -> {
                    CaelumCodegenHelper.pointerCname.parameterizedBy(eType.baseType.caelumCoreTypeName)
                }
                is Handle -> {
                    CaelumCodegenHelper.pointerCname.parameterizedBy(WildcardTypeName.producerOf(elementType.ktApiType()))
                }
                else -> {
                    CaelumCodegenHelper.pointerCname.parameterizedBy(elementType.ktApiType())
                }
            }
        }

        context(ctx: CaelumCodegenContext)
        override fun packageName(): String {
            return CaelumCodegenHelper.basePkgName
        }

        context(ctx: CaelumCodegenContext)
        override fun typeName(): TypeName {
            return CBasicType.size_t.caelumCoreTypeName
        }

        context(ctx: CaelumCodegenContext)
        override fun memoryLayoutDeep(): CodeBlock {
            return CodeBlock.of("%M", CaelumCodegenHelper.pointerLayoutMember)
        }

        override fun toString(): String {
            return name
        }

        override fun compositeName(parentStr: String): String {
            return elementType.compositeName("$parentStr*")
        }
    }

    public class FunctionPointer(override val elementType: Function) : Pointer({ elementType }) {
        context(ctx: CaelumCodegenContext)
        override fun packageName(): String {
            return ctx.resolvePackageName(this)
        }

        context(ctx: CaelumCodegenContext)
        override fun typeName(): TypeName {
            return CaelumCodegenHelper.pointerCname.parameterizedBy(elementType.typeName())
        }
    }

    public sealed class Group(name: String, public val members: List<Member>) : CompositeType(name) {
        context(ctx: CaelumCodegenContext)
        override fun nativeType(): TypeName {
            throw UnsupportedOperationException()
        }

        context(ctx: CaelumCodegenContext)
        override fun ktApiType(): TypeName {
            return typeName()
        }

        context(ctx: CaelumCodegenContext)
        override fun memoryLayoutDeep(): CodeBlock {
            val builder = CodeBlock.builder()
            members.forEach {
                builder.addStatement("%L.withName(%S),", it.type.memoryLayout(), it.name)
            }
            return builder.build()
        }

        context(ctx: CaelumCodegenContext)
        override fun memoryLayout(): CodeBlock {
            return CodeBlock.of("%T.layout", typeName())
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

        public class Member(name: String, type: CType) : CDeclaration.Impl(name, type)
    }

    public open class Struct(name: String, members: List<Member>) : Group(name, members) {
        override fun toString(): String {
            return "struct ${super.toString()}"
        }

        override fun toSimpleString(): String {
            return "struct ${super.toSimpleString()}"
        }
    }

    public open class Union(name: String, members: List<Member>) : Group(name, members) {
        override fun toString(): String {
            return "union ${super.toString()}"
        }

        override fun toSimpleString(): String {
            return "union ${super.toSimpleString()}"
        }
    }
}

public tailrec fun CType.deepReferenceResolve(): CType {
    return when (this) {
        is CType.Pointer -> this.elementType.deepReferenceResolve()
        is CType.Array -> this.elementType.deepReferenceResolve()
        is CType.TypeDef -> this.dstType.deepReferenceResolve()
        else -> this
    }
}

public tailrec fun CType.deepResolve(): CType {
    if (this !is CType.TypeDef) return this
    return this.dstType.deepResolve()
}