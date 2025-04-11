package net.echonolix.ktffi

import com.squareup.kotlinpoet.FileSpec
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

abstract class KTFFICodegenContext(val basePkgName: String, val outputDir: Path) {
    private val allElement0 = ConcurrentHashMap<String, CElement>()
    private val allTypes0 = ConcurrentHashMap<String, CType>()

    val allElement: Map<String, CElement> get() = allElement0
    val allTypes: Map<String, CType> get() = allTypes0

    abstract fun resolvePackageName(element: CElement): String
    protected abstract fun resolveTypeImpl(cTypeStr: String): CType

    fun addToCache(element: CElement) {
        if (element is CType) {
            allTypes0[element.name] = element
        }
        allElement0[element.name] = element
    }

    fun resolveType(cTypeStr: String): CType {
        val trimStr = cTypeStr.trim()
        return allTypes[trimStr] ?: run {
            if (trimStr.last() == '*') {
                return CType.Pointer(resolveType(trimStr.dropLast(1)))
            }
            CBasicType.Companion.fromStringOrNull(trimStr)?.let {
                return it.cType
            }
            resolveTypeImpl(trimStr)
        }.also(::addToCache)
    }

    fun writeOutput(fileSpec: FileSpec.Builder) {
        fileSpec.addSuppress()
        fileSpec.indent("    ")
        fileSpec.build().writeTo(outputDir)
    }
}