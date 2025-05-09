package net.echonolix.caelum.codegen.api.ctx

import net.echonolix.caelum.codegen.api.CBasicType
import net.echonolix.caelum.codegen.api.CElement
import net.echonolix.caelum.codegen.api.CExpression
import net.echonolix.caelum.codegen.api.CSyntax
import net.echonolix.caelum.codegen.api.CTopLevelConst
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.removeContinuousSpaces
import java.util.stream.Stream

public interface ElementResolver {
    public val allElements: Map<String, CElement>
    public fun resolveElement(input: String): CElement

    public abstract class Base : ElementResolver {
        public override val allElements: Map<String, CElement>
            get() = allElements0

        protected val allElements0: MutableMap<String, CElement> = mutableMapOf()

        public override fun resolveElement(input: String): CElement {
            try {
                val trimStr = input
                    .trim()
                    .removeContinuousSpaces()
                val cached = allElements0[trimStr]
                if (cached != null) return cached

                run {
                    CSyntax.pointerOrArrayRegex.find(trimStr)?.let {
                        return when {
                            it.value.endsWith("*") -> {
                                CType.Pointer {
                                    resolveTypedElement<CType>(trimStr.removeRange(it.range))
                                }
                            }
                            it.value.isEmpty() -> {
                                CType.Array(
                                    resolveTypedElement<CType>(trimStr.removeRange(it.range)),
                                )
                            }
                            else -> {
                                CType.Array.Sized(
                                    resolveTypedElement<CType>(trimStr.removeRange(it.range)),
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
                throw IllegalStateException("Error resolving element: $input", e)
            }
        }

        protected fun addToCache(name: String, element: CElement) {
            allElements0.putIfAbsent(name, element)
        }

        protected fun resolveExpression(expressionStr: String): CExpression<*> {
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

        protected abstract fun resolveElementImpl(input: String): CElement
    }
}

@Suppress("UNCHECKED_CAST")
public inline fun <reified T> ElementResolver.filterTypeStream(): Stream<Pair<String, T>> {
    return allElements.entries.parallelStream()
        .filter { it.value is T }
        .map { it.key to (it.value as T) }
}

@Suppress("UNCHECKED_CAST")
public inline fun <reified T> ElementResolver.filterType(): List<Pair<String, T>> {
    return filterTypeStream<T>().toList()
}

public inline fun <reified T> ElementResolver.resolveTypedElement(cElementStr: String): T {
    return resolveElement(cElementStr) as? T
        ?: throw IllegalArgumentException("Not a ${T::class.simpleName}: $cElementStr")
}