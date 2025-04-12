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

class KtffiCoreCodeGenProcessor : KtgenProcessor {
    override fun process(inputs: List<Path>, outputDir: Path) {
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
            val cname = ClassName(KTFFICodegenHelper.packageName, it.name)
            val arrayCNameP = KTFFICodegenHelper.arrayCname.parameterizedBy(cname)
            val valueCNameP = KTFFICodegenHelper.valueCname.parameterizedBy(cname)
            val pointerCNameP = KTFFICodegenHelper.pointerCname.parameterizedBy(cname)
            val nullableAny = Any::class.asClassName().copy(nullable = true)
            file.addType(
                TypeSpec.objectBuilder(cname)
                    .superclass(KTFFICodegenHelper.typeImplCname.parameterizedBy(cname))
                    .addSuperclassConstructorParameter("%M", ValueLayout::class.member(it.valueLayoutName))
                    .addProperty(
                        PropertySpec.builder("valueVarHandle", VarHandle::class)
                            .addAnnotation(JvmField::class)
                            .initializer("descriptor.layout.varHandle()")
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder("arrayVarHandle", VarHandle::class)
                            .addAnnotation(JvmField::class)
                            .initializer("descriptor.layout.arrayElementVarHandle()")
                            .build()
                    )
                    .build()
            )

            fun randomName(base: String) = AnnotationSpec.builder(JvmName::class)
                .addMember("%S",
                    "${cname.simpleName}_${base}_${(0..5).map { validChars[random.nextInt(validChars.size)] }.joinToString("")}"
                )
                .build()

            file.addFunction(
                FunSpec.builder("get")
                    .addAnnotation(randomName("get"))
                    .receiver(arrayCNameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .returns(it.kotlinType.asTypeName())
                    .addStatement(
                        "return (%T.%N.get(_segment, 0L, index) as %T)${it.fromBase}",
                        cname,
                        "arrayVarHandle",
                        it.baseType.asTypeName()
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("set")
                    .addAnnotation(randomName("set"))
                    .receiver(arrayCNameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .addParameter("value", it.kotlinType.asTypeName())
                    .addStatement(
                        "%T.%N.set(_segment, 0L, index, value${it.toBase})",
                        cname,
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
                    .returns(it.kotlinType.asTypeName())
                    .addStatement(
                        "return (%T.%N.get(_segment, 0L) as %T)${it.fromBase}",
                        cname,
                        "valueVarHandle",
                        it.baseType.asTypeName()
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
                    .addParameter("value", it.kotlinType.asTypeName())
                    .addStatement("%T.%N.set(_segment, 0L, value${it.toBase})", cname, "valueVarHandle")
                    .build()
            )

            file.addFunction(
                FunSpec.builder("get")
                    .addAnnotation(randomName("get"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .returns(it.kotlinType.asTypeName())
                    .addStatement(
                        "return (%T.%N.get(%M, _address, index) as %T)${it.fromBase}",
                        cname,
                        "arrayVarHandle",
                        KTFFICodegenHelper.omniSegment,
                        it.baseType.asTypeName()
                    )
                    .build()
            )
            file.addFunction(
                FunSpec.builder("set")
                    .addAnnotation(randomName("set"))
                    .receiver(pointerCNameP)
                    .addModifiers(KModifier.OPERATOR, KModifier.INLINE)
                    .addParameter("index", LONG)
                    .addParameter("value", it.kotlinType.asTypeName())
                    .addStatement(
                        "%T.%N.set(%M, _address, index, value${it.toBase})",
                        cname,
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
                    .returns(it.kotlinType.asTypeName())
                    .addStatement(
                        "return (%T.%N.get(%M, _address) as %T)${it.fromBase}",
                        cname,
                        "valueVarHandle",
                        KTFFICodegenHelper.omniSegment,
                        it.baseType.asTypeName()
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
                    .addParameter("value", it.kotlinType.asTypeName())
                    .addStatement("return %T.%N.set(%M, _address, value)", cname, "valueVarHandle", KTFFICodegenHelper.omniSegment)
                    .build()
            )
        }

        file.build().writeTo(outputDir)
    }

    private enum class CBasicType(
        val kotlinType: KClass<*>,
        val literalSuffix: String,
        val valueLayout: ValueLayout,
        val valueLayoutType: KClass<*>,
        val valueLayoutName: String,
        val baseType: KClass<*> = kotlinType,
        val toBase: String = "",
        val fromBase: String = ""
    ) {
        float(
            Float::class,
            "F",
            ValueLayout.JAVA_FLOAT,
            ValueLayout.OfFloat::class,
            "JAVA_FLOAT"
        ),
        double(
            Double::class,
            "",
            ValueLayout.JAVA_DOUBLE,
            ValueLayout.OfDouble::class,
            "JAVA_DOUBLE"
        ),
        int8_t(
            Byte::class,
            "",
            ValueLayout.JAVA_BYTE,
            ValueLayout.OfByte::class,
            "JAVA_BYTE"
        ),
        uint8_t(
            UByte::class,
            "U",
            ValueLayout.JAVA_BYTE,
            ValueLayout.OfByte::class,
            "JAVA_BYTE",
            Byte::class,
            ".toByte()",
            ".toUByte()"
        ),
        int16_t(
            Short::class,
            "",
            ValueLayout.JAVA_SHORT,
            ValueLayout.OfShort::class,
            "JAVA_SHORT"
        ),
        uint16_t(
            UShort::class,
            "U",
            ValueLayout.JAVA_SHORT,
            ValueLayout.OfShort::class,
            "JAVA_SHORT",
            Short::class,
            ".toShort()",
            ".toUShort()"
        ),
        int32_t(
            Int::class,
            "",
            ValueLayout.JAVA_INT,
            ValueLayout.OfInt::class,
            "JAVA_INT"
        ),
        uint32_t(
            UInt::class,
            "U",
            ValueLayout.JAVA_INT,
            ValueLayout.OfInt::class,
            "JAVA_INT",
            Int::class,
            ".toInt()",
            ".toUInt()"
        ),
        int64_t(
            Long::class,
            "L",
            ValueLayout.JAVA_LONG,
            ValueLayout.OfLong::class,
            "JAVA_LONG"
        ),
        uint64_t(
            ULong::class,
            "UL",
            ValueLayout.JAVA_LONG,
            ValueLayout.OfLong::class,
            "JAVA_LONG",
            Long::class,
            ".toLong()",
            ".toULong()"
        ),
    }
}