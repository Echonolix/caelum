package net.echonolix.ktffi

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import java.lang.IllegalStateException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

abstract class KTFFICodegenContext(val basePkgName: String, val outputDir: Path) {
    private val allElements0 = ConcurrentHashMap<String, CElement>()
    val allElements: Map<String, CElement>
        get() = allElements0

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> filterType(): List<Pair<String, T>> {
        return allElements.entries.parallelStream()
            .filter { it.value is T }
            .map { it.key to (it.value as T) }
            .toList()
    }

    abstract fun resolvePackageName(element: CElement): String
    protected abstract fun resolveElementImpl(cElementStr: String): CElement

    fun addToCache(name: String, element: CElement) {
        allElements0[name] = element
    }

    fun resolveExpression(expressionStr: String): CExpression<*> {
        val trimStr = expressionStr.trim().removeContinuousSpaces()
        return runCatching {
            resolveElement(trimStr)
        }.mapCatching { element ->
            (element as? CExpression<*>)?.let {
                return@mapCatching it
            }
            (element as? CConst)?.let {
                return@mapCatching CExpression.Reference(it)
            }
            throw IllegalStateException("Not a const: $trimStr")
        }.getOrElse {
            val quoteRemoved = trimStr.removeSurrounding("\"")
            if (quoteRemoved.length == trimStr.length) {
                CExpression.Const(CBasicType.int32_t, CodeBlock.of(trimStr))
            } else {
                CExpression.StringLiteral( trimStr)
            }
        }
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
            return allElements0[trimStr] ?: run {
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
            }.also {
                addToCache(trimStr, it)
            }
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