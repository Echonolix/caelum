import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.CaelumCodegenHelper
import net.echonolix.caelum.addSuppress
import net.echonolix.ktgen.KtgenProcessor
import java.lang.foreign.ValueLayout
import java.lang.invoke.VarHandle
import java.nio.file.Path
import java.util.*
import kotlin.reflect.KClass

class KTFFICoreCodeGenProcessor : KtgenProcessor {
    override fun process(inputs: Set<Path>, outputDir: Path): Set<Path> {
        val validChars = ('a'..'z').toList()
        val random = Random(0)

        val file = FileSpec.builder(CaelumCodegenHelper.basePkgName, "CoreGenerated")
            .indent("    ")
            .addSuppress()

        CBasicType.entries.forEach { basicType ->
            val thisCname = ClassName(CaelumCodegenHelper.basePkgName, basicType.name)
            val arrayCNameP = CaelumCodegenHelper.arrayCname.parameterizedBy(thisCname)
            val valueCNameP = CaelumCodegenHelper.valueCname.parameterizedBy(thisCname)
            val pointerCNameP = CaelumCodegenHelper.pointerCname.parameterizedBy(thisCname)
            val nullableAny = Any::class.asClassName().copy(nullable = true)
            file.addType(
                TypeSpec.objectBuilder(thisCname)
                    .superclass(CaelumCodegenHelper.typeImplCname.parameterizedBy(thisCname))
                    .addSuperclassConstructorParameter("%M", ValueLayout::class.member(basicType.valueLayoutName))
                    .addProperty(
                        PropertySpec.builder("valueVarHandle", VarHandle::class)
                            .addAnnotation(JvmField::class)
                            .initializer("typeDescriptor.layout.varHandle()")
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("arrayVarHandle", VarHandle::class)
                            .addAnnotation(JvmField::class)
                            .initializer("typeDescriptor.layout.arrayElementVarHandle()")
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("fromNativeData")
                            .addAnnotation(JvmStatic::class)
                            .addParameter("value", basicType.nativeDataType)
                            .returns(basicType.ktApiType)
                            .addStatement("return value${basicType.fromNativeData}")
                            .build()
                    )
                    .addFunction(
                        FunSpec.builder("toNativeData")
                            .addAnnotation(JvmStatic::class)
                            .addParameter("value", basicType.ktApiType)
                            .returns(basicType.nativeDataType)
                            .addStatement("return value${basicType.toNativeData}")
                            .build()
                    )
                    .build()
            )

            fun randomName(base: String) = AnnotationSpec.builder(JvmName::class)
                .addMember(
                    "%S",
                    "${thisCname.simpleName}_${base}_${
                        (0..4).map { validChars[random.nextInt(validChars.size)] }.joinToString("")
                    }"
                )
                .build()

            val overloadTypes = listOf(INT, UInt::class.asTypeName(), ULong::class.asTypeName())
            val returnTypeName = basicType.ktApiType.asTypeName()

            fun addGetOverloads(receiver: TypeName) {
                for (pType in overloadTypes) {
                    file.addFunction(
                        FunSpec.builder("get")
                            .addAnnotation(randomName("get"))
                            .receiver(receiver)
                            .addModifiers(KModifier.OPERATOR)
                            .addParameter("index", pType)
                            .returns(returnTypeName)
                            .addStatement("return get(index.toLong())")
                            .build()
                    )
                }
            }

            fun addSetOverloads(receiver: TypeName) {
                for (pType in overloadTypes) {
                    file.addFunction(
                        FunSpec.builder("set")
                            .addAnnotation(randomName("set"))
                            .receiver(receiver)
                            .addModifiers(KModifier.OPERATOR)
                            .addParameter("index", pType)
                            .addParameter("value", returnTypeName)
                            .addStatement("set(index.toLong(), value)")
                            .build()
                    )
                }
            }
            file.addProperty(
                PropertySpec.builder("value", returnTypeName)
                    .receiver(pointerCNameP)
                    .mutable(true)
                    .getter(
                        FunSpec.getterBuilder()
                            .addAnnotation(randomName("getValue"))
                            .addStatement(
                                "return (%T.%N.get(%M, _address) as %T)${basicType.fromNativeData}",
                                thisCname,
                                "valueVarHandle",
                                CaelumCodegenHelper.omniSegment,
                                basicType.nativeDataType.asTypeName()
                            )
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addAnnotation(randomName("setValue"))
                            .addParameter("value", returnTypeName)
                            .addStatement(
                                "%T.%N.set(%M, _address, value${basicType.toNativeData})",
                                thisCname,
                                "valueVarHandle",
                                CaelumCodegenHelper.omniSegment,
                            )
                            .build()
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("get")
                    .addAnnotation(randomName("get"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", LONG)
                    .returns(returnTypeName)
                    .addStatement(
                        "return (%T.%N.get(%M, _address, index) as %T)${basicType.fromNativeData}",
                        thisCname,
                        "arrayVarHandle",
                        CaelumCodegenHelper.omniSegment,
                        basicType.nativeDataType.asTypeName()
                    )
                    .build()
            )
            addGetOverloads(pointerCNameP)

            file.addFunction(
                FunSpec.builder("set")
                    .addAnnotation(randomName("set"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", LONG)
                    .addParameter("value", returnTypeName)
                    .addStatement(
                        "%T.%N.set(%M, _address, index, value${basicType.toNativeData})",
                        thisCname,
                        "arrayVarHandle",
                        CaelumCodegenHelper.omniSegment
                    )
                    .build()
            )
            addSetOverloads(pointerCNameP)

            file.addFunction(
                FunSpec.builder("getValue")
                    .addAnnotation(randomName("getValue"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("thisRef", nullableAny)
                    .addParameter("property", nullableAny)
                    .returns(returnTypeName)
                    .addStatement(
                        "return (%T.%N.get(%M, _address) as %T)${basicType.fromNativeData}",
                        thisCname,
                        "valueVarHandle",
                        CaelumCodegenHelper.omniSegment,
                        basicType.nativeDataType.asTypeName()
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("setValue")
                    .addAnnotation(randomName("setValue"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("thisRef", nullableAny)
                    .addParameter("property", nullableAny)
                    .addParameter("value", returnTypeName)
                    .addStatement(
                        "return %T.%N.set(%M, _address, value)",
                        thisCname,
                        "valueVarHandle",
                        CaelumCodegenHelper.omniSegment
                    )
                    .build()
            )


            file.addProperty(
                PropertySpec.builder("value", basicType.ktApiType)
                    .receiver(valueCNameP)
                    .mutable(true)
                    .getter(
                        FunSpec.getterBuilder()
                            .addAnnotation(randomName("getValue"))
                            .addStatement("return ptr().value")
                            .build()
                    )
                    .setter(
                        FunSpec.setterBuilder()
                            .addAnnotation(randomName("setValue"))
                            .addParameter("value", returnTypeName)
                            .addStatement("ptr().value = value")
                            .build()
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("getValue")
                    .addAnnotation(randomName("getValue"))
                    .receiver(valueCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("thisRef", nullableAny)
                    .addParameter("property", nullableAny)
                    .returns(returnTypeName)
                    .addStatement("return this.value")
                    .build()
            )
            file.addFunction(
                FunSpec.builder("setValue")
                    .addAnnotation(randomName("setValue"))
                    .receiver(valueCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("thisRef", nullableAny)
                    .addParameter("property", nullableAny)
                    .addParameter("value", returnTypeName)
                    .addStatement("this.value = value")
                    .build()
            )

            file.addFunction(
                FunSpec.builder("get")
                    .addAnnotation(randomName("get"))
                    .receiver(arrayCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", LONG)
                    .returns(returnTypeName)
                    .addStatement("return ptr().get(index)")
                    .build()
            )
            addGetOverloads(arrayCNameP)
            file.addFunction(
                FunSpec.builder("set")
                    .addAnnotation(randomName("set"))
                    .receiver(arrayCNameP)
                    .addModifiers(KModifier.OPERATOR)
                    .addParameter("index", LONG)
                    .addParameter("value", returnTypeName)
                    .addStatement("ptr().set(index, value)")
                    .build()
            )
            addSetOverloads(arrayCNameP)
        }
        return setOf(file.build().writeTo(outputDir))
    }

    private enum class CBasicType(
        val ktApiType: KClass<*>,
        val valueLayoutName: String,
        val nativeDataType: KClass<*> = ktApiType,
        val toNativeData: String = "",
        val fromNativeData: String = ""
    ) {
        NativeFloat(
            Float::class,
            "JAVA_FLOAT"
        ),
        NativeDouble(
            Double::class,
            "JAVA_DOUBLE"
        ),
        NativeInt8(
            Byte::class,
            "JAVA_BYTE"
        ),
        NativeUInt8(
            UByte::class,
            "JAVA_BYTE",
            Byte::class,
            ".toByte()",
            ".toUByte()"
        ),
        NativeInt16(
            Short::class,
            "JAVA_SHORT"
        ),
        NativeUInt16(
            UShort::class,
            "JAVA_SHORT",
            Short::class,
            ".toShort()",
            ".toUShort()"
        ),
        NativeInt32(
            Int::class,
            "JAVA_INT"
        ),
        NativeUInt32(
            UInt::class,
            "JAVA_INT",
            Int::class,
            ".toInt()",
            ".toUInt()"
        ),
        NativeInt64(
            Long::class,
            "JAVA_LONG"
        ),
        NativeUInt64(
            ULong::class,
            "JAVA_LONG",
            Long::class,
            ".toLong()",
            ".toULong()"
        ),
    }
}