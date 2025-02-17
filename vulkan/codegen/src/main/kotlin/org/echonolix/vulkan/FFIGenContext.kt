package org.echonolix.vulkan

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import org.echonolix.ktffi.CBasicType
import org.echonolix.ktffi.KTFFICodegen
import org.echonolix.vulkan.schema.Element
import org.echonolix.vulkan.schema.PatchedRegistry
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.VarHandle
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

    sealed class StructUnionInfo(val cname: ClassName) {
        val arrayCnameP = KTFFICodegen.arrayCname.parameterizedBy(cname)
        val pointerCnameP = KTFFICodegen.pointerCname.parameterizedBy(cname)
        val valueCnameP = KTFFICodegen.valueCname.parameterizedBy(cname)

        val properties = mutableListOf<PropertySpec>()
        val topLevelProperties = mutableListOf<PropertySpec>()
        val topLevelFunctions = mutableListOf<FunSpec>()
        val memoryLayoutInitializer = CodeBlock.builder().indent()

        init {
            topLevelFunctions.add(
                FunSpec.builder("elementAddress")
                    .receiver(arrayCnameP)
                    .addModifiers(KModifier.INLINE)
                    .addParameter("index", LONG)
                    .returns(LONG)
                    .addStatement(
                        "return %T.arrayByteOffsetHandle.invokeExact(_segment.address(), index) as Long",
                        cname
                    )
                    .build()
            )
            topLevelFunctions.add(
                FunSpec.builder("get")
                    .receiver(arrayCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .returns(pointerCnameP)
                    .addStatement(
                        "return %T(elementAddress(index))",
                        KTFFICodegen.pointerCname
                    )
                    .build()
            )
            topLevelFunctions.add(
                FunSpec.builder("set")
                    .receiver(arrayCnameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .addParameter("value", pointerCnameP)
                    .addStatement(
                        "%M(%M, value._address, _segment, elementAddress(index), %T.arrayLayout.byteSize())",
                        MemorySegment::class.member("copy"),
                        KTFFICodegen.omniSegment,
                        cname
                    )
                    .build()
            )
        }

        abstract fun finish()
    }

    class StructInfo(cname: ClassName) : StructUnionInfo(cname) {
        override fun finish() {
            memoryLayoutInitializer.unindent()
        }
    }

    class UnionInfo(cname: ClassName) : StructUnionInfo(cname) {
        override fun finish() {
            memoryLayoutInitializer.unindent()
        }
    }

    inner class DefaultVisitor(private val registry: PatchedRegistry, private val structUnionInfo: StructUnionInfo) : MemberVisitor  {
        private fun StructUnionInfo.offsetProperty(member: Element.Member) {
            properties += PropertySpec.builder("${member.name}_offset", Long::class)
                .addAnnotation(JvmField::class)
                .initializer("layout.byteOffset(%M(%S))", MemoryLayout.PathElement::class.member("groupElement"), member.name)
                .build()
        }

        private fun common(member: Element.Member) {
            structUnionInfo.offsetProperty(member)
        }

        private fun structOrUnion(member: Element.Member, type: Element.StructUnion, info: StructUnionInfo) {
            structUnionInfo.memoryLayoutInitializer.addStatement(
                "%T.%N.withName(%S),",
                ClassName(info.cname.packageName, member.type),
                "layout",
                member.name
            )
            common(member)
        }

        private fun pointer(member: Element.Member) {
            structUnionInfo.memoryLayoutInitializer.addStatement(
                "%M.withName(%S),",
                ValueLayout::class.member("ADDRESS"),
                member.name
            )
            common(member)
        }

        private fun basicType(member: Element.Member, cBasicType: CBasicType) {
            structUnionInfo.properties.add(
                PropertySpec.builder("${member.name}_valueVarHandle", VarHandle::class.asClassName())
                    .addAnnotation(JvmField::class)
                    .initializer("layout.varHandle(%M(%S))", MemoryLayout.PathElement::class.member("groupElement"), member.name)
                    .build()
            )
            structUnionInfo.properties.add(
                PropertySpec.builder("${member.name}_arrayVarHandle", VarHandle::class.asClassName())
                    .addAnnotation(JvmField::class)
                    .initializer("layout.arrayElementVarHandle(%M(%S))", MemoryLayout.PathElement::class.member("groupElement"), member.name)
                    .build()
            )
            structUnionInfo.topLevelProperties.add(
                PropertySpec.builder(member.name, cBasicType.typeName)
                    .mutable()
                    .receiver(structUnionInfo.valueCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return (%T.%N.get(_segment, 0L) as %T)${cBasicType.fromBase}",
                                structUnionInfo.cname,
                                "${member.name}_valueVarHandle",
                                cBasicType.baseType.asTypeName()
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", cBasicType.typeName)
                            .addStatement(
                                "%T.%N.set(_segment, 0L, value${cBasicType.toBase})",
                                structUnionInfo.cname,
                                "${member.name}_valueVarHandle",
                            )
                            .build()
                    )
                    .build()
            )

            structUnionInfo.topLevelProperties.add(
                PropertySpec.builder(member.name, cBasicType.typeName)
                    .mutable()
                    .receiver(structUnionInfo.pointerCnameP)
                    .getter(
                        FunSpec.getterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addStatement(
                                "return (%T.%N.get(%M, _address) as %T)${cBasicType.fromBase}",
                                structUnionInfo.cname,
                                "${member.name}_valueVarHandle",
                                KTFFICodegen.omniSegment,
                                cBasicType.baseType.asTypeName()
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addModifiers(KModifier.INLINE)
                            .addParameter("value", cBasicType.typeName)
                            .addStatement(
                                "%T.%N.set(%M, _address, value${cBasicType.toBase})",
                                structUnionInfo.cname,
                                "${member.name}_valueVarHandle",
                                KTFFICodegen.omniSegment
                            )
                            .build()
                    )
                    .build()
            )
            structUnionInfo.memoryLayoutInitializer.addStatement(
                "%M.withName(%S),",
                ValueLayout::class.member(cBasicType.valueLayoutName!!),
                member.name
            )
            common(member)
        }

        override fun visitBasicType(index: Int, member: Element.Member, type: Element.BasicType) {
            basicType(member, type.value)
        }

        override fun visitHandleType(index: Int, member: Element.Member, type: Element.HandleType) {
            pointer(member)
        }

        override fun visitEnumType(index: Int, member: Element.Member, type: Element.EnumType) {
            basicType(member, CBasicType.int32_t)
        }

        override fun visitFlagType(
            index: Int,
            member: Element.Member,
            type: Element.FlagType,
            flagBitType: Element.FlagBitType?
        ) {
            basicType(member, registry.flagBitTypes[type.bitType]?.type ?: CBasicType.int32_t)
        }

        override fun visitFuncpointerType(
            index: Int,
            member: Element.Member,
            type: Element.FuncpointerType
        ) {
            pointer(member)
        }

        override fun visitStructType(index: Int, member: Element.Member, type: Element.Struct) {
            val structInfo = getStructInfo(registry, member.type)
            structOrUnion(member, type, structInfo)
        }

        override fun visitUnionType(index: Int, member: Element.Member, type: Element.Union) {
            val unionInfo = getUnionInfo(registry, member.type)
            structOrUnion(member, type, unionInfo)
        }

        override fun visitPointer(index: Int, member: Element.Member, type: Element.Type) {
            pointer(member)
        }

        override fun visitArray(index: Int, member: Element.Member, type: Element.Type) {
            pointer(member)
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
                    visitor.visitFlagType(i, member, flagType, registry.flagBitTypes[flagType.bitType])
                    return@runCatching
                }

                val flagBitType = deTypeDef as? Element.FlagBitType
                if (flagBitType != null) {
                    visitor.visitFlagType(i, member, flagBitType.bitmaskType!!, flagBitType)
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
    }

    private val unionInfoCache = mutableMapOf<String, UnionInfo>()
    fun getUnionInfo(registry: PatchedRegistry, name: String): UnionInfo {
        return unionInfoCache.getOrPut(name) {
            UnionInfo(ClassName(VKFFI.unionPackageName, name)).apply {
                val union = registry.unionTypes[name] ?: error("Struct $name not found")
                visitStruct(registry, union, DefaultVisitor(registry, this))
                finish()
            }
        }
    }

    private val structInfoCache = mutableMapOf<String, StructInfo>()
    fun getStructInfo(registry: PatchedRegistry, name: String): StructInfo {
        return structInfoCache.getOrPut(name) {
            StructInfo(ClassName(VKFFI.structPackageName, name)).apply {
                val struct = registry.structTypes[name] ?: error("Struct $name not found")
                visitStruct(registry, struct, DefaultVisitor(registry, this))
                finish()
            }
        }
    }
}