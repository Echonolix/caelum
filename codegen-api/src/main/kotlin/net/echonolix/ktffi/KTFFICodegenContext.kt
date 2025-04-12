package net.echonolix.ktffi

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import java.lang.IllegalStateException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

abstract class KTFFICodegenContext(val basePkgName: String, val outputDir: Path) {
    private val allElement0 = ConcurrentHashMap<String, CElement>()
    private val allTypes0 = ConcurrentHashMap<String, CType>()
    private val expressions = ConcurrentHashMap<String, CExpression>()

    val allElement: Map<String, CElement> get() = allElement0
    val allTypes: Map<String, CType> get() = allTypes0
    val allExpressions: Map<String, CExpression> get() = expressions

    abstract fun resolvePackageName(element: CElement): String
    protected abstract fun resolveElementImpl(cElementStr: String): CElement

    fun addToCache(element: CElement) {
        if (element is CType) {
            allTypes0[element.name] = element
        }
        if (element is CExpression) {
            expressions[element.name] = element
        }
        allElement0[element.name] = element
    }

    fun resolveExpression(expressionStr: String): CExpression {
        val trimStr = expressionStr.trim().removeContinuousSpaces()
        return expressions[trimStr] ?: run {
            runCatching {
                resolveElement(trimStr)
            }.mapCatching {
                (it as? CConst)?.let {
                    CExpression.Reference(it)
                } ?: throw IllegalStateException("Not a const: $trimStr")
            }.getOrElse {
                CExpression.Const(CBasicType.int32_t, CodeBlock.of(trimStr))
            }
        }.also(::addToCache)
    }

    fun resolveType(cElementStr: String): CType {
        return resolveElement(cElementStr) as? CType
            ?: throw IllegalArgumentException("Not a type: $cElementStr")
    }

    fun resolveElement(cElementStr: String): CElement {
        try {
            val trimStr = cElementStr
                .trim()
                .removeContinuousSpaces()
            return allTypes[trimStr] ?: run {
                CSyntax.pointerOrArrayRegex.find(trimStr)?.let {
                    return when {
                        it.value.endsWith("*") -> {
                            CType.Pointer {
                                resolveType(trimStr.removeRange(it.range))
                            }
                        }
                        it.value.isEmpty() -> {
                            CType.Array(
                                resolveType(trimStr.removeRange(it.range)),
                            )
                        }
                        else -> {
                            CType.Array.Sized(
                                resolveType(trimStr.removeRange(it.range)),
                                resolveExpression(it.value.removeSurrounding("[", "]"))
                            )
                        }
                    }
                }
                CBasicType.Companion.fromStringOrNull(trimStr)?.let {
                    return it.cType
                }
                resolveElementImpl(trimStr)
            }.also(::addToCache)
        } catch (e: RuntimeException) {
            throw IllegalStateException("Error resolving element: $cElementStr", e)
        }
    }

    fun writeOutput(fileSpec: FileSpec.Builder) {
        fileSpec.addSuppress()
        fileSpec.indent("    ")
        fileSpec.build().writeTo(outputDir)
    }
}