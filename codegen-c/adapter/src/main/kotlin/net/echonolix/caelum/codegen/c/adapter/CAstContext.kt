package net.echonolix.caelum.codegen.c.adapter

import c.ast.NodeVisitException

class CAstContext(val inputPathStrs: Set<String>) {
    private val typedefs0 = mutableMapOf<String, CType>()
    private val consts0 = mutableMapOf<String, CConst>()
    private val enums0 = mutableMapOf<String, CEnum>()
    private val globalEnums0 = mutableMapOf<String, CEnumerator>()
    private val structs0 = mutableMapOf<String, CStruct>()
    private val unions0 = mutableMapOf<String, CUnion>()
    private val functions0 = mutableMapOf<String, CFunction>()

    val typedefs: Map<String, CType> get() = typedefs0
    val consts: Map<String, CConst> get() = consts0
    val enums: Map<String, CEnum> get() = enums0
    val globalEnums: Map<String, CEnumerator> get() = globalEnums0
    val structs: Map<String, CStruct> get() = structs0
    val unions: Map<String, CUnion> get() = unions0
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
        when (type) {
            is CStruct -> {
                addStruct(name, type)
            }
            is CUnion -> {
                addUnion(name, type)
            }
            else -> typedefs0[name] = type
        }
    }

    fun addEnum(name: String?, enum: CEnum) {
        if (name == null) {
            enum.enumerators.associateByTo(globalEnums0) { it.id.name }
            return
        }
        enums0.compute(name) { _, existing ->
            existing?.copy(enumerators = existing.enumerators + enum.enumerators) ?: enum
        }
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

    fun addFunction(name: String, function: CFunction) {
        functions0[name] = function
    }

    fun addConst(name: String, const: CConst) {
        consts0[name] = const
    }
}