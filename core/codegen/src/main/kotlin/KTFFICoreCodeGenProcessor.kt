import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.ktffi.KTFFICodegenHelper
import net.echonolix.ktgen.KtgenProcessor
import java.lang.foreign.ValueLayout
import java.lang.invoke.VarHandle
import java.nio.file.Path
import java.util.Random
import kotlin.reflect.KClass

class KTFFICoreCodeGenProcessor : KtgenProcessor {
    override fun process(inputs: Set<Path>, outputDir: Path): Set<Path> {
        val validChars = ('a'..'z').toList()
        val random = Random(0)

        val file = FileSpec.builder(KTFFICodegenHelper.packageName, "CoreGenerated")
            .indent("    ")
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class)
                    .addMember("%S", "RemoveRedundantQualifierName")
                    .addMember("%S", "PropertyName")
                    .addMember("%S", "RedundantVisibilityModifier")
                    .addMember("%S", "unused")
                    .addMember("%S", "NOTHING_TO_INLINE")
                    .build()
            )

        CBasicType.entries.forEach {
            val thisCname = ClassName(KTFFICodegenHelper.packageName, it.name)
            val arrayCNameP = KTFFICodegenHelper.arrayCname.parameterizedBy(thisCname)
            val valueCNameP = KTFFICodegenHelper.valueCname.parameterizedBy(thisCname)
            val pointerCNameP = KTFFICodegenHelper.pointerCname.parameterizedBy(thisCname)
            val nullableAny = Any::class.asClassName().copy(nullable = true)
            file.addType(
                TypeSpec.objectBuilder(thisCname)
                    .superclass(KTFFICodegenHelper.typeImplCname.parameterizedBy(thisCname))
                    .addSuperclassConstructorParameter("%M", ValueLayout::class.member(it.valueLayoutName))
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
                    .build()
            )

            fun randomName(base: String) = AnnotationSpec.builder(JvmName::class)
                .addMember("%S",
                    "${thisCname.simpleName}_${base}_${(0..4).map { validChars[random.nextInt(validChars.size)] }.joinToString("")}"
                )
                .build()

            file.addFunction(
                FunSpec.builder("get")
                    .addAnnotation(randomName("get"))
                    .receiver(arrayCNameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .returns(it.ktApiType.asTypeName())
                    .addStatement(
                        "return (%T.%N.get(_segment, 0L, index) as %T)${it.fromNativeData}",
                        thisCname,
                        "arrayVarHandle",
                        it.nativeDataType.asTypeName()
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("set")
                    .addAnnotation(randomName("set"))
                    .receiver(arrayCNameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .addParameter("value", it.ktApiType.asTypeName())
                    .addStatement(
                        "%T.%N.set(_segment, 0L, index, value${it.toNativeData})",
                        thisCname,
                        "arrayVarHandle"
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("getValue")
                    .addAnnotation(randomName("getValue"))
                    .receiver(valueCNameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("thisRef", nullableAny)
                    .addParameter("property", nullableAny)
                    .returns(it.ktApiType.asTypeName())
                    .addStatement(
                        "return (%T.%N.get(_segment, 0L) as %T)${it.fromNativeData}",
                        thisCname,
                        "valueVarHandle",
                        it.nativeDataType.asTypeName()
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("setValue")
                    .addAnnotation(randomName("setValue"))
                    .receiver(valueCNameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("thisRef", nullableAny)
                    .addParameter("property", nullableAny)
                    .addParameter("value", it.ktApiType.asTypeName())
                    .addStatement("%T.%N.set(_segment, 0L, value${it.toNativeData})", thisCname, "valueVarHandle")
                    .build()
            )

            file.addFunction(
                FunSpec.builder("get")
                    .addAnnotation(randomName("get"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .returns(it.ktApiType.asTypeName())
                    .addStatement(
                        "return (%T.%N.get(%M, _address, index) as %T)${it.fromNativeData}",
                        thisCname,
                        "arrayVarHandle",
                        KTFFICodegenHelper.omniSegment,
                        it.nativeDataType.asTypeName()
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("set")
                    .addAnnotation(randomName("set"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .addParameter("value", it.ktApiType.asTypeName())
                    .addStatement(
                        "%T.%N.set(%M, _address, index, value${it.toNativeData})",
                        thisCname,
                        "arrayVarHandle",
                        KTFFICodegenHelper.omniSegment
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("getValue")
                    .addAnnotation(randomName("getValue"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("thisRef", nullableAny)
                    .addParameter("property", nullableAny)
                    .returns(it.ktApiType.asTypeName())
                    .addStatement(
                        "return (%T.%N.get(%M, _address) as %T)${it.fromNativeData}",
                        thisCname,
                        "valueVarHandle",
                        KTFFICodegenHelper.omniSegment,
                        it.nativeDataType.asTypeName()
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("setValue")
                    .addAnnotation(randomName("setValue"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("thisRef", nullableAny)
                    .addParameter("property", nullableAny)
                    .addParameter("value", it.ktApiType.asTypeName())
                    .addStatement("return %T.%N.set(%M, _address, value)", thisCname, "valueVarHandle", KTFFICodegenHelper.omniSegment)
                    .build()
            )
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