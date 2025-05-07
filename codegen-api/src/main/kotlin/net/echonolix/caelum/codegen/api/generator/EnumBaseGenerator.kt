package net.echonolix.caelum.codegen.api.generator

import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import net.echonolix.caelum.codegen.api.CExpression
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.CaelumCodegenHelper
import net.echonolix.caelum.codegen.api.EnumEntryFixedName
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.addKdoc
import kotlin.random.Random

public open class EnumBaseGenerator(
    ctx: CodegenContext,
    element: CType.EnumBase
) : Generator<CType.EnumBase>(ctx, element) {
    context(ctx: CodegenContext)
    protected val entries: Collection<CType.EnumBase.Entry> get() = element.entries.values

    context(ctx: CodegenContext)
    protected open fun enumBaseCName(): ClassName {
        return CaelumCodegenHelper.enumCName
    }

    context(ctx: CodegenContext)
    protected open fun buildType(): TypeSpec.Builder {
        val type = TypeSpec.enumBuilder(thisCName)
        type.addKdoc(element)
        type.addSuper(element)
        return type
    }

    context(ctx: CodegenContext)
    protected open fun buildCompanionType(type: TypeSpec.Builder): TypeSpec.Builder {
        val companionType = TypeSpec.companionObjectBuilder()
        entries.asSequence()
            .forEach { entry ->
                val expression = entry.expression
                val fixedName = entry.tags.get<EnumEntryFixedName>().name
                when (expression) {
                    is CExpression.Const -> {
                        type.addEnumConstant(
                            fixedName,
                            TypeSpec.anonymousClassBuilder()
                                .addKdoc(entry)
                                .superclass(thisCName)
                                .addSuperclassConstructorParameter(entry.expression.codeBlock())
                                .build()
                        )
                    }
                    is CExpression.Reference -> {
                        assert(expression.value in entries)
                        companionType.addProperty(
                            PropertySpec.builder(fixedName, thisCName)
                                .initializer("%N", expression.value.tags.get<EnumEntryFixedName>().name)
                                .addKdoc(entry)
                                .build()
                        )
                    }
                    else -> throw IllegalArgumentException("Unsupported expression type: ${expression::class}")
                }
            }

        companionType.addCompanionSuper(element)
        companionType.addFunction(
            FunSpec.builder("fromNativeData")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter(
                    "value",
                    element.baseType.ktApiTypeTypeName
                )
                .returns(thisCName)
                .addCode(
                    CodeBlock.builder()
                        .beginControlFlow("return when (value)")
                        .apply {
                            entries.asSequence()
                                .filter { it.expression is CExpression.Const }
                                .forEach { entry ->
                                    addStatement(
                                        "%L -> %N",
                                        entry.expression.codeBlock(),
                                        entry.tags.get<EnumEntryFixedName>().name
                                    )
                                }
                        }
                        .addStatement("else -> throw IllegalArgumentException(\"Unknown value: \$value\")")
                        .endControlFlow()
                        .build()
                )
                .build()
        )
        companionType.addFunction(
            FunSpec.builder("toNativeData")
                .addModifiers(KModifier.OVERRIDE)
                .addParameter("value", thisCName)
                .returns(element.baseType.ktApiTypeTypeName)
                .addStatement("return value.value")
                .build()
        )
        return companionType
    }

    public final override fun generate(): FileSpec.Builder {
        with(ctx) {
            val type = buildType()
            val companionType = buildCompanionType(type)
            type.addType(companionType.build())
            val file = FileSpec.builder(thisCName)
            file.addType(type.build())
            file.addNativeAccess()
            return file
        }
    }

    context(ctx: CodegenContext)
    protected fun TypeSpec.Builder.addSuper(enumBase: CType.EnumBase): TypeSpec.Builder {
        val baseType = enumBase.baseType
        val superCName = enumBaseCName()
        addSuperinterface(superCName.parameterizedBy(thisCName))
        primaryConstructor(
            FunSpec.constructorBuilder()
                .addParameter("value", baseType.ktApiTypeTypeName)
                .build()
        )
        addProperty(
            PropertySpec.builder("value", baseType.ktApiTypeTypeName)
                .addModifiers(KModifier.OVERRIDE)
                .initializer("value")
                .build()
        )
        addProperty(
            PropertySpec.builder(
                "typeDescriptor",
                CaelumCodegenHelper.NEnum.descriptorCName.parameterizedBy(
                    thisCName,
                    baseType.nNativeDataType.nativeDataType
                )
            ).addModifiers(KModifier.OVERRIDE)
                .getter(
                    FunSpec.getterBuilder()
                        .addStatement("return Companion")
                        .build()
                )
                .build()
        )
        return this
    }

    context(ctx: CodegenContext)
    private fun FileSpec.Builder.addNativeAccess() {
        val random = Random(0)

        val validChars = ('a'..'z').toList()

        fun randomName(base: String): AnnotationSpec {
            val randomChars = (0..4).map { validChars[random.nextInt(validChars.size)] }.joinToString("")
            return AnnotationSpec.builder(JvmName::class)
                .addMember("%S", "${thisCName.simpleName}_${base}_$randomChars")
                .build()
        }
        val overloadTypes = listOf(INT, UInt::class.asTypeName(), ULong::class.asTypeName())

        val returnTypeName = thisCName

        val arrayCNameP = CaelumCodegenHelper.arrayCName.parameterizedBy(thisCName)
        val valueCNameP = CaelumCodegenHelper.valueCName.parameterizedBy(thisCName)
        val pointerCNameP = CaelumCodegenHelper.pointerCName.parameterizedBy(thisCName)
        val nullableAny = Any::class.asClassName().copy(nullable = true)

        fun addGetOverloads(receiver: TypeName) {
            for (pType in overloadTypes) {
                addFunction(
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
                addFunction(
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
        addProperty(
            PropertySpec.builder("value", returnTypeName)
                .receiver(pointerCNameP)
                .mutable(true)
                .getter(
                    FunSpec.getterBuilder()
                        .addAnnotation(randomName("getValue"))
                        .addStatement(
                            "return %T.fromNativeData(%T.pointerGetValue(this))",
                            thisCName,
                            thisCName
                        )
                        .build()
                )
                .setter(
                    FunSpec.setterBuilder()
                        .addAnnotation(randomName("setValue"))
                        .addParameter("value", returnTypeName)
                        .addStatement(
                            "%T.pointerSetValue(this, %T.toNativeData(value))",
                            thisCName,
                            thisCName
                        )
                        .build()
                )
                .build()
        )

        addFunction(
            FunSpec.builder("get")
                .addAnnotation(randomName("get"))
                .receiver(pointerCNameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("index", LONG)
                .returns(returnTypeName)
                .addStatement(
                    "return %T.fromNativeData(%T.pointerGetElement(this, index))",
                    thisCName,
                    thisCName
                )
                .build()
        )
        addGetOverloads(pointerCNameP)

        addFunction(
            FunSpec.builder("set")
                .addAnnotation(randomName("set"))
                .receiver(pointerCNameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("index", LONG)
                .addParameter("value", returnTypeName)
                .addStatement(
                    "%T.pointerSetElement(this, index, %T.toNativeData(value))",
                    thisCName,
                    thisCName
                )
                .build()
        )
        addSetOverloads(pointerCNameP)

        addFunction(
            FunSpec.builder("getValue")
                .addAnnotation(randomName("getValue"))
                .receiver(pointerCNameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("thisRef", nullableAny)
                .addParameter("property", nullableAny)
                .returns(returnTypeName)
                .addStatement(
                    "return this.value",
                    thisCName,
                    thisCName
                )
                .build()
        )
        addFunction(
            FunSpec.builder("setValue")
                .addAnnotation(randomName("setValue"))
                .receiver(pointerCNameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("thisRef", nullableAny)
                .addParameter("property", nullableAny)
                .addParameter("value", returnTypeName)
                .addStatement(
                    "this.value = value",
                    thisCName,
                    thisCName
                )
                .build()
        )

        addProperty(
            PropertySpec.builder("value", thisCName)
                .receiver(valueCNameP)
                .mutable(true)
                .getter(
                    FunSpec.getterBuilder()
                        .addAnnotation(randomName("getValue"))
                        .addStatement(
                            "return %T.fromNativeData(%T.valueGetValue(this))",
                            thisCName,
                            thisCName
                        )
                        .build()
                )
                .setter(
                    FunSpec.setterBuilder()
                        .addAnnotation(randomName("setValue"))
                        .addParameter("value", returnTypeName)
                        .addStatement(
                            "%T.valueSetValue(this, %T.toNativeData(value))",
                            thisCName,
                            thisCName
                        )
                        .build()
                )
                .build()
        )
        addFunction(
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
        addFunction(
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


        addFunction(
            FunSpec.builder("get")
                .addAnnotation(randomName("get"))
                .receiver(arrayCNameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("index", LONG)
                .returns(returnTypeName)
                .addStatement(
                    "return %T.fromNativeData(%T.arrayGetElement(this, index))",
                    thisCName,
                    thisCName
                )
                .build()
        )
        addGetOverloads(arrayCNameP)
        addFunction(
            FunSpec.builder("set")
                .addAnnotation(randomName("set"))
                .receiver(arrayCNameP)
                .addModifiers(KModifier.OPERATOR)
                .addParameter("index", LONG)
                .addParameter("value", returnTypeName)
                .addStatement(
                    "%T.arraySetElement(this, index, %T.toNativeData(value))",
                    thisCName,
                    thisCName
                )
                .build()
        )
        addSetOverloads(arrayCNameP)
    }


    context(ctx: CodegenContext)
    protected fun TypeSpec.Builder.addCompanionSuper(enumBase: CType.EnumBase) {
        val thisCName = enumBase.className()
        addSuperinterface(
            CaelumCodegenHelper.NEnum.typeObjectCName.parameterizedBy(
                thisCName,
                enumBase.baseType.nNativeDataType.nativeDataType
            )
        )
        addSuperinterface(
            enumBase.baseType.nNativeDataType.nNativeDataCName.parameterizedBy(thisCName, thisCName),
            CodeBlock.of("%T.implOf()", enumBase.baseType.nNativeDataType.nNativeDataCName)
        )
    }
}