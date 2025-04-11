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

    private fun resolveType0(cTypeStr: String): CType {
        if (cTypeStr.last() == '*') {
            return CType.Pointer(resolveType(cTypeStr.dropLast(1)))
        }
        CBasicType.Companion.fromStringOrNull(cTypeStr)?.let {
            return it.cType
        }
        return resolveTypeImpl(cTypeStr)
    }

    fun resolveType(cTypeStr: String): CType {
        return resolveType0(cTypeStr.trim()).also(::addToCache)
    }

    fun writeOutput(fileSpec: FileSpec.Builder) {
        fileSpec.addSuppress()
        fileSpec.indent("    ")
        fileSpec.build().writeTo(outputDir)
    }
}