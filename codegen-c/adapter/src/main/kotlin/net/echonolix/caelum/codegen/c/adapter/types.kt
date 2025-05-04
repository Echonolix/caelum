package net.echonolix.caelum.codegen.c.adapter

import c.lang.ASTNumberValue
import c.lang.CPrimitiveType
import c.lang.CSizeSpecifier

sealed interface CType

/**
 * in most cases it will be some built in macro
 */
object CUnresolved : CType

data class CBasicType(
    val sizeSpecifiers: List<CSizeSpecifier>,
    val basic: CPrimitiveType?,
) : CType

@JvmInline
value class Identifier(val name: String) : CType {
    operator fun component1() = name
}


data class CArrayType(
    val type: CType,
    val size: String?
) : CType

data class CPointer(
    val pointee: CType,
) : CType

data class CFunctionParam(
    val name: Identifier?,
    val type: CType,
)

data class CFunction(
    val params: List<CFunctionParam>,
    val returnType: CType,
) : CType


enum class IdentifierKind {
    struct,
    union,
    enum,
    globals,
}

data class ScopedIdentifier(
    val kind: IdentifierKind,
    val id: Identifier
) : CType


sealed class CGroup(
    open val id: Identifier?,
    open val fields: List<CField>,
) : CType

data class CField(
    val id: Identifier,
    val type: CType,
    val alignas: ULong? = null,
)

data class CStruct(
    override val id: Identifier?,
    override val fields: List<CField>,
) : CGroup(id, fields)

data class CUnion(
    override val id: Identifier?,
    override val fields: List<CField>,
) : CGroup(id, fields)

data class CEnumerator(
    val id: Identifier,
    val value: ASTNumberValue,
)

data class CEnum(
    val id: Identifier?,
    val enumerators: List<CEnumerator>,
) : CType