package c.lang

sealed interface ASTNumberValue {

    data class Literal(val literal: String) : ASTNumberValue


    data class Unary(val op: UnaryOp, val v: ASTNumberValue) : ASTNumberValue


    enum class UnaryOp(val ktRep: String) {
        Negative("-")
    }

    data class Binary(val op: BinaryOp, val left: ASTNumberValue, val right: ASTNumberValue) : ASTNumberValue

    enum class BinaryOp(val ktRep: String) {
        Or("or"),
        Shl("shl"),
        Sub("-"),
        Add("+")
    }

    data class Paraenthesized(val v: ASTNumberValue) : ASTNumberValue

    data class Ref(val enumName: String) : ASTNumberValue
}