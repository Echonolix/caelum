package org.echonolix.vulkan

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName.Companion.member
import org.echonolix.ktffi.CBasicType
import org.echonolix.vulkan.schema.Element
import org.echonolix.vulkan.schema.PatchedRegistry
import java.lang.foreign.ValueLayout
import java.nio.file.Path

class FFIGenContext(
    val packageName: String,
    val whitelistTypes: Set<String>,
) {
    val fileSpecs = mutableListOf<FileSpec.Builder>()

    fun newFile(fileSpec: FileSpec.Builder): FileSpec.Builder {
        fileSpec.addSuppress()
        fileSpec.indent("    ")
        fileSpecs.add(fileSpec)
        return fileSpec
    }

    fun writeOutput(dir: Path) {
        fileSpecs.forEach {
            it.build().writeTo(dir)
        }
    }

    class StructInfo {
        val memoryLayoutInitializer = CodeBlock.builder()
            .indent()
        var size = 0L; private set
        var alignment = 0L; private set
        var endPadding = 0L; private set

        fun updateSize(size: Long) {
            this.size += size
            alignment = maxOf(alignment, size)
        }

        fun finish() {
            if (alignment > 0) {
                val roundedSize = (size + alignment - 1) / alignment * alignment
                endPadding = roundedSize - size
            }
            memoryLayoutInitializer.unindent()
        }
    }

    class UnionInfo {
        val memoryLayoutInitializer = CodeBlock.builder()
            .indent()
        var size = 0L; private set

        fun updateSize(size: Long) {
            this.size = maxOf(this.size, size)
        }

        fun finish() {
            memoryLayoutInitializer.unindent()
        }
    }

    private fun visitStruct(registry: PatchedRegistry, struct: Element.StructUnion, visitor: MemberVisitor) {
        val typeText = struct.javaClass.simpleName.lowercase()
        println("$typeText ${struct.name}")

        for (i in struct.members.indices) {
            val member = struct.members[i]
            runCatching {
                var fixedType = member.type.removePrefix("const ")
                fixedType = fixedType.removePrefix("struct ")
                val withoutStar = fixedType.removeSuffix("*")
                if (withoutStar in whitelistTypes) return@runCatching

                if (withoutStar.length == fixedType.length - 1) {
                    visitor.visitPointer(i, member, registry.allTypes[withoutStar]!!)
                    return@runCatching
                }

                if (fixedType.endsWith("[]")) {
                    val withoutBrackets = fixedType.removeSuffix("[]")
                    visitor.visitArray(i, member, registry.allTypes[withoutBrackets]!!)
                    return@runCatching
                }

                var deTypeDef: Element.Type? = registry.allTypes[fixedType]
                while (deTypeDef is Element.TypeDef) {
                    deTypeDef = deTypeDef.value
                }

                val basicType = deTypeDef as? Element.BasicType
                if (basicType != null) {
                    visitor.visitBasicType(i, member, basicType)
                    return@runCatching
                }

                val handleType = deTypeDef as? Element.HandleType
                if (handleType != null) {
                    visitor.visitHandleType(i, member, handleType)
                    return@runCatching
                }

                val enumType = deTypeDef as? Element.EnumType
                if (enumType != null) {
                    visitor.visitEnumType(i, member, enumType)
                    return@runCatching
                }

                val flagType = deTypeDef as? Element.FlagType
                if (flagType != null) {
                    visitor.visitFlagType(i, member, flagType)
                    return@runCatching
                }

                val flagBitType = deTypeDef as? Element.FlagBitType
                if (flagBitType != null) {
                    visitor.visitFlagType(i, member, flagBitType.bitmaskType!!)
                    return@runCatching
                }

                val funcpointerType = deTypeDef as? Element.FuncpointerType
                if (funcpointerType != null) {
                    visitor.visitFuncpointerType(i, member, funcpointerType)
                    return@runCatching
                }

                val structType = deTypeDef as? Element.Struct
                if (structType != null) {
                    visitor.visitStructType(i, member, structType)
                    return@runCatching
                }

                val unionType = deTypeDef as? Element.Union
                if (unionType != null) {
                    visitor.visitUnionType(i, member, unionType)
                    return@runCatching
                }
                error("Unsupported type: $fixedType($deTypeDef)")
            }.onFailure {
                throw RuntimeException(
                    "Failed to process member <${member.type} ${member.name}> of <$typeText ${struct.name}>",
                    it
                )
            }
        }

        println()
    }

    private val unionInfoCache = mutableMapOf<String, UnionInfo>()
    fun getUnionInfo(registry: PatchedRegistry, name: String): UnionInfo {
        return unionInfoCache.getOrPut(name) {
            UnionInfo().apply {
                val union = registry.unionTypes[name] ?: error("Struct $name not found")

                visitStruct(registry, union, object : MemberVisitor {
                    override fun visitBasicType(index: Int, member: Element.Member, type: Element.BasicType) {
                        val cBasicType = type.value
                        memoryLayoutInitializer.addStatement(
                            "%M.withName(%S),",
                            ValueLayout::class.member(cBasicType.valueLayoutName!!),
                            member.name
                        )
                        updateSize(cBasicType.valueLayout!!.byteSize())
                    }

                    override fun visitHandleType(index: Int, member: Element.Member, type: Element.HandleType) {
                        memoryLayoutInitializer.addStatement(
                            "%M.withName(%S),",
                            ValueLayout::class.member("ADDRESS"),
                            member.name
                        )
                        updateSize(8)
                    }

                    override fun visitEnumType(index: Int, member: Element.Member, type: Element.EnumType) {
                        memoryLayoutInitializer.addStatement(
                            "%M.withName(%S),",
                            ValueLayout::class.member("JAVA_INT"),
                            member.name
                        )
                        updateSize(4)
                    }

                    override fun visitFlagType(index: Int, member: Element.Member, type: Element.FlagType) {
                        val cBasicType = registry.flagBitTypes[type.bitType]?.type ?: CBasicType.int32_t
                        memoryLayoutInitializer.addStatement(
                            "%M.withName(%S),",
                            ValueLayout::class.member(cBasicType.valueLayoutName!!),
                            member.name
                        )
                        updateSize(cBasicType.valueLayout!!.byteSize())
                    }

                    override fun visitFuncpointerType(
                        index: Int,
                        member: Element.Member,
                        type: Element.FuncpointerType
                    ) {
                        memoryLayoutInitializer.addStatement(
                            "%M.withName(%S),",
                            ValueLayout::class.member("ADDRESS"),
                            member.name
                        )
                        updateSize(8)
                    }

                    override fun visitStructType(index: Int, member: Element.Member, type: Element.Struct) {
                        val structInfo = getStructInfo(registry, member.type)
                        memoryLayoutInitializer.addStatement(
                            "%T.%N.withName(%S),",
                            ClassName(VKFFI.structPackageName, member.type),
                            "layout",
                            member.name
                        )
                        updateSize(structInfo.size)
                    }

                    override fun visitUnionType(index: Int, member: Element.Member, type: Element.Union) {
                        val unionInfo = getUnionInfo(registry, member.type)
                        memoryLayoutInitializer.addStatement(
                            "%T.%N.withName(%S),",
                            ClassName(VKFFI.unionPackageName, member.type),
                            "layout",
                            member.name
                        )
                        updateSize(unionInfo.size)
                    }

                    override fun visitPointer(index: Int, member: Element.Member, type: Element.Type) {
                        memoryLayoutInitializer.addStatement(
                            "%M.withName(%S),",
                            ValueLayout::class.member("ADDRESS"),
                            member.name
                        )
                        updateSize(8)
                    }

                    override fun visitArray(index: Int, member: Element.Member, type: Element.Type) {
                        memoryLayoutInitializer.addStatement(
                            "%M.withName(%S),",
                            ValueLayout::class.member("ADDRESS"),
                            member.name
                        )
                        updateSize(8)
                    }

                })

                finish()
            }
        }
    }

    private val structInfoCache = mutableMapOf<String, StructInfo>()
    fun getStructInfo(registry: PatchedRegistry, name: String): StructInfo {
        return structInfoCache.getOrPut(name) {
            StructInfo().apply {
                val struct = registry.structTypes[name] ?: error("Struct $name not found")

                visitStruct(registry, struct, object : MemberVisitor {
                    override fun visitBasicType(index: Int, member: Element.Member, type: Element.BasicType) {
                        val cBasicType = type.value
                        memoryLayoutInitializer.addStatement(
                            "%M.withName(%S),",
                            ValueLayout::class.member(cBasicType.valueLayoutName!!),
                            member.name
                        )
                        updateSize(cBasicType.valueLayout!!.byteSize())
                    }

                    override fun visitHandleType(index: Int, member: Element.Member, type: Element.HandleType) {
                        memoryLayoutInitializer.addStatement(
                            "%M.withName(%S),",
                            ValueLayout::class.member("ADDRESS"),
                            member.name
                        )
                        updateSize(8)
                    }

                    override fun visitEnumType(index: Int, member: Element.Member, type: Element.EnumType) {
                        memoryLayoutInitializer.addStatement(
                            "%M.withName(%S),",
                            ValueLayout::class.member("JAVA_INT"),
                            member.name
                        )
                        updateSize(4)
                    }

                    override fun visitFlagType(index: Int, member: Element.Member, type: Element.FlagType) {
                        val cBasicType = registry.flagBitTypes[type.bitType]?.type ?: CBasicType.int32_t
                        memoryLayoutInitializer.addStatement(
                            "%M.withName(%S),",
                            ValueLayout::class.member(cBasicType.valueLayoutName!!),
                            member.name
                        )
                        updateSize(cBasicType.valueLayout!!.byteSize())
                    }

                    override fun visitFuncpointerType(
                        index: Int,
                        member: Element.Member,
                        type: Element.FuncpointerType
                    ) {
                        memoryLayoutInitializer.addStatement(
                            "%M.withName(%S),",
                            ValueLayout::class.member("ADDRESS"),
                            member.name
                        )
                        updateSize(8)
                    }

                    override fun visitStructType(index: Int, member: Element.Member, type: Element.Struct) {
                        val structInfo = getStructInfo(registry, member.type)
                        memoryLayoutInitializer.addStatement(
                            "%T.%N.withName(%S),",
                            ClassName(VKFFI.structPackageName, member.type),
                            "layout",
                            member.name
                        )
                        updateSize(structInfo.size)
                    }

                    override fun visitUnionType(index: Int, member: Element.Member, type: Element.Union) {
                        val unionInfo = getUnionInfo(registry, member.type)
                        memoryLayoutInitializer.addStatement(
                            "%T.%N.withName(%S),",
                            ClassName(VKFFI.unionPackageName, member.type),
                            "layout",
                            member.name
                        )
                        updateSize(unionInfo.size)
                    }

                    override fun visitPointer(index: Int, member: Element.Member, type: Element.Type) {
                        memoryLayoutInitializer.addStatement(
                            "%M.withName(%S),",
                            ValueLayout::class.member("ADDRESS"),
                            member.name
                        )
                        updateSize(8)
                    }

                    override fun visitArray(index: Int, member: Element.Member, type: Element.Type) {
                        memoryLayoutInitializer.addStatement(
                            "%M.withName(%S),",
                            ValueLayout::class.member("ADDRESS"),
                            member.name
                        )
                        updateSize(8)
                    }

                })

                finish()
            }
        }
    }
}