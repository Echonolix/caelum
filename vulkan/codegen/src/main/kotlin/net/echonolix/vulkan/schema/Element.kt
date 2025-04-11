package net.echonolix.vulkan.schema

import com.squareup.kotlinpoet.CodeBlock
import net.echonolix.ktffi.CBasicType

sealed class Element(val name: String) {
    var requiredBy: String? = null
    var docs: String? = null

    override fun toString(): String {
        return "${javaClass.simpleName}($name)"
    }

    sealed class Type(name: String) : Element(name)
    sealed class CEnum(name: String, val type: CBasicType) : Type(name) {
        val entries = mutableMapOf<String, Entry>()
        val aliases = mutableMapOf<String, Alias>()

        class Entry(name: String, val fixedName: String, val valueCode: CodeBlock, val valueNum: Number) : Element(name)
        class Alias(name: String, val value: String) : Element(name)
    }

    class Constant(name: String, val type: CBasicType, val value: CodeBlock) : Element(name)
    class Define(name: String, val value: String) : Element(name)

    class OpaqueType(name: String) : Type(name)
    class BasicType(name: String, val value: CBasicType) : Type(name)
    class BaseType(name: String, val value: TypeDef) : Type(name)
    class TypeDef(name: String, val value: Type) : Type(name)

    class FlagType(name: String, val bitType: String?) : Type(name) {
        val unused get() = bitType == null
    }

    class FlagBitType(name: String, type: CBasicType, val bitmaskType: FlagType?) : CEnum(name, type)
    class EnumType(name: String) : CEnum(name, CBasicType.int32_t)
    class HandleType(name: String, val objectEnum: String, val parent: String?) : Type(name)

    class FuncpointerType(name: String, val returnType: String, val params: Map<String, String>) : Type(name)

    class Member(name: String, val type: String, val length: String?, val bits: Int, val xml: XMLMember) :
        Element(name) {
        override fun toString(): String {
            return "$type $name"
        }
    }

    sealed class Group(name: String, val members: List<Member>) : Type(name)
    class Struct(name: String, members: List<Member>, val extends: List<String>) : Group(name, members)
    class Union(name: String, members: List<Member>) : Group(name, members)
}