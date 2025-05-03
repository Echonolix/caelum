package net.echonolix.caelum.codegen.c.adapter

import c.ast.NodeVisitException

class ElementContext {
    private val typedefs0 = mutableMapOf<String, CType>()
    val typedefs: Map<String, CType> get() = typedefs0
    private val structs0 = mutableMapOf<String, CStruct>()
    val structs: Map<String, CStruct> get() = structs0
    private val unions0 = mutableMapOf<String, CUnion>()
    val unions: Map<String, CUnion> get() = unions0
    private val enums0 = mutableMapOf<String, CEnum>()
    val enums: Map<String, CEnum> get() = enums0
    private val functions0 = mutableMapOf<String, CFunction>()
    val functions: Map<String, CFunction> get() = functions0

    fun parse(source: String) {
        val visitor = AdapterASTVisitor(this)
        try {
            c.ast.parse(source, visitor)
        } catch (e: NodeVisitException) {
            System.err.println("Node content:")
            System.err.println(
                source.toByteArray(Charsets.UTF_8)
                    .sliceArray(e.node.range.startByte.toInt()..<e.node.range.endByte.toInt())
                    .toString(Charsets.UTF_8)
            )
            throw IllegalStateException(
                "Error at line ${e.node.range.startPoint.row}(${visitor.lineMarker.posOf(e.node)})",
                e
            )
        }
    }

    fun addTypedef(name: String, type: CType) {
        typedefs0[name] = type
    }

    fun addStruct(name: String, struct: CStruct) {
        structs0.compute(name) { _, existing ->
            existing?.copy(fields = existing.fields + struct.fields) ?: struct
        }
    }

    fun addUnion(name: String, union: CUnion) {
        unions0.compute(name) { _, existing ->
            existing?.copy(fields = existing.fields + union.fields) ?: union
        }
    }

    fun addEnum(name: String, enum: CEnum) {
        enums0.compute(name) { _, existing ->
            existing?.copy(enumerators = existing.enumerators + enum.enumerators) ?: enum
        }
    }

    fun addFunction(name: String, function: CFunction) {
        functions0[name] = function
    }
}