package net.echonolix.caelum

import com.squareup.kotlinpoet.FileSpec
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.stream.Stream

public abstract class CaelumCodegenContext(public val basePkgName: String, public val outputDir: Path) {
    private val allElements0 = ConcurrentHashMap<String, CElement>()
    public val allElements: Map<String, CElement>
        get() = allElements0

    private val outputFiles0 = ConcurrentLinkedQueue<Path>()
    public val outputFiles: Set<Path>
        get() = outputFiles0.toSet()

    @Suppress("UNCHECKED_CAST")
    public inline fun <reified T> filterTypeStream(): Stream<Pair<String, T>> {
        return allElements.entries.parallelStream()
            .filter { it.value is T }
            .map { it.key to (it.value as T) }
    }

    @Suppress("UNCHECKED_CAST")
    public inline fun <reified T> filterType(): List<Pair<String, T>> {
        return filterTypeStream<T>().toList()
    }

    public abstract fun resolvePackageName(element: CElement): String
    protected abstract fun resolveElementImpl(cElementStr: String): CElement

    public fun addToCache(name: String, element: CElement) {
        allElements0.putIfAbsent(name, element)
    }

    public fun resolveExpression(expressionStr: String): CExpression<*> {
        val trimStr = expressionStr.trim().removeContinuousSpaces()
        return runCatching {
            resolveElement(trimStr)
        }.mapCatching { element ->
            (element as? CExpression<*>)?.let {
                return@mapCatching it
            }
            (element as? CTopLevelConst)?.let {
                return@mapCatching CExpression.Reference(it)
            }
            throw IllegalStateException("Not a const: $trimStr")
        }.getOrElse {
            val quoteRemoved = trimStr.removeSurrounding("\"")
            if (quoteRemoved.length == trimStr.length) {
                CExpression.Const(CBasicType.int32_t, CBasicType.int32_t.codeBlock(trimStr))
            } else {
                CExpression.StringLiteral(quoteRemoved)
            }
        }
    }

    public fun resolveType(cElementStr: String): CType {
        return resolveElement(cElementStr) as? CType
            ?: throw IllegalArgumentException("Not a type: $cElementStr")
    }

    public fun resolveElement(cElementStr: String): CElement {
        try {
            val trimStr = cElementStr
                .trim()
                .removeContinuousSpaces()
            val cached = allElements0[trimStr]
            if (cached != null) return cached

            run {
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

            return allElements0[trimStr]!!
        } catch (e: RuntimeException) {
            throw IllegalStateException("Error resolving element: $cElementStr", e)
        }
    }

    public fun writeOutput(path: Path, fileSpec: FileSpec.Builder) {
        fileSpec.addSuppress()
        fileSpec.indent("    ")
        outputFiles0.add(fileSpec.build().writeTo(outputDir.resolve(path)))
    }

    public fun writeOutput(fileSpec: FileSpec.Builder) {
        fileSpec.addSuppress()
        fileSpec.indent("    ")
        outputFiles0.add(fileSpec.build().writeTo(outputDir))
    }
}