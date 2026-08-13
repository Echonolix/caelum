package net.echonolix.caelum.codegen.cpp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

public class ClangAstException(message: String) : IllegalArgumentException(message)

public object ClangAst {
    private val json: Json = Json { ignoreUnknownKeys = true }

    public fun dump(
        header: Path,
        clangExecutable: String = "clang++",
        compilerArguments: List<String> = emptyList(),
    ): String {
        require(Files.isRegularFile(header)) { "C++ header does not exist: $header" }
        val normalizedHeader = header.toAbsolutePath().normalize()
        val command = listOf(
            clangExecutable, "-x", "c++", "-std=c++17", "-fsyntax-only",
            "-Xclang", "-ast-dump=json",
        ) + compilerArguments + normalizedHeader.toString()
        val process = ProcessBuilder(command).redirectErrorStream(false).start()
        var stdout = ""
        var stderr = ""
        val stdoutReader = kotlin.concurrent.thread(name = "caelum-codegen-cpp-stdout") {
            stdout = process.inputStream.bufferedReader().use { it.readText() }
        }
        val stderrReader = kotlin.concurrent.thread(name = "caelum-codegen-cpp-stderr") {
            stderr = process.errorStream.bufferedReader().use { it.readText() }
        }
        val exit = process.waitFor()
        stdoutReader.join()
        stderrReader.join()
        if (exit != 0) throw ClangAstException("clang++ AST dump failed (exit $exit):\n$stderr")
        if (stdout.isBlank()) throw ClangAstException("clang++ produced an empty AST dump")
        return stdout
    }

    public fun parse(astJson: String, rootHeader: Path? = null): CppModule {
        val root = json.parseToJsonElement(astJson).jsonObject
        return Parser(rootHeader?.toAbsolutePath()?.normalize()).parse(root)
    }

    private class Parser(private val rootHeader: Path?) {
        private val functions = mutableListOf<CppFunction>()
        private val enums = mutableListOf<CppEnum>()
        private val classes = mutableListOf<CppClass>()
        private val diagnostics = mutableListOf<CppDiagnostic>()
        private val enumNames = mutableSetOf<String>()

        fun parse(root: JsonObject): CppModule {
            collectEnumNames(root, emptyList())
            visit(root, emptyList())
            return CppModule(
                functions.sortedBy(::signature),
                enums.distinctBy { it.qualifiedName }.sortedBy { it.qualifiedName },
                classes.distinctBy { it.qualifiedName }.sortedBy { it.qualifiedName },
                diagnostics.distinct().sortedWith(compareBy(CppDiagnostic::declaration, CppDiagnostic::reason)),
            )
        }

        private fun collectEnumNames(node: JsonObject, namespace: List<String>) {
            val kind = node.string("kind")
            if (kind in rootBoundaryKinds && !isFromRootHeader(node)) return
            val next = if (kind == "NamespaceDecl" && node.string("name").isNotBlank()) namespace + node.string("name") else namespace
            if (kind == "EnumDecl" && node.string("name").isNotBlank()) enumNames += (next + node.string("name")).joinToString("::")
            node.children().forEach { collectEnumNames(it, next) }
        }

        private fun visit(node: JsonObject, namespace: List<String>) {
            if (node.string("kind") in rootBoundaryKinds && !isFromRootHeader(node)) return
            when (node.string("kind")) {
                "NamespaceDecl" -> {
                    val name = node.string("name")
                    if (name.isNotBlank()) node.children().forEach { visit(it, namespace + name) }
                }
                "FunctionDecl" -> parseCallable(node, namespace, null, CppFunction.Kind.FUNCTION)?.let(functions::add)
                "EnumDecl" -> parseEnum(node, namespace)?.let(enums::add)
                "CXXRecordDecl" -> parseClass(node, namespace)?.let(classes::add)
                else -> node.children().forEach { visit(it, namespace) }
            }
        }

        private fun parseEnum(node: JsonObject, namespace: List<String>): CppEnum? {
            val name = node.string("name")
            if (name.isBlank() || node.boolean("isImplicit")) return null
            val scoped = node.string("scopedEnumTag")
            if (scoped.isBlank()) {
                diagnostics += CppDiagnostic((namespace + name).joinToString("::"), "only scoped enum class/struct declarations are supported")
                return null
            }
            var nextValue = 0L
            val values = node.children().filter { it.string("kind") == "EnumConstantDecl" }.map { constant ->
                val explicit = constant.descendants().firstNotNullOfOrNull { it.stringOrNull("value")?.toLongOrNull() }
                val value = explicit ?: nextValue
                nextValue = value + 1
                constant.string("name") to value
            }
            return CppEnum(namespace, name, values)
        }

        private fun parseClass(node: JsonObject, namespace: List<String>): CppClass? {
            val name = node.string("name")
            if (name.isBlank() || node.boolean("isImplicit") || !node.boolean("completeDefinition")) return null
            if (node.string("tagUsed") !in setOf("class", "struct")) return null
            var access = if (node.string("tagUsed") == "struct") "public" else "private"
            val ctors = mutableListOf<CppFunction>()
            val methods = mutableListOf<CppFunction>()
            var destructor: CppFunction? = null
            node.children().forEach { child ->
                if (child.string("kind") == "AccessSpecDecl") {
                    access = child.string("access")
                    return@forEach
                }
                if (access != "public") return@forEach
                when (child.string("kind")) {
                    "CXXConstructorDecl" -> if (!child.boolean("isImplicit")) {
                        parseCallable(child, namespace, name, CppFunction.Kind.CONSTRUCTOR)?.let(ctors::add)
                    }
                    "CXXDestructorDecl" -> if (!child.boolean("isImplicit") && !child.isDeleted()) {
                        destructor = parseCallable(child, namespace, name, CppFunction.Kind.DESTRUCTOR)
                    }
                    "CXXMethodDecl" -> if (!child.boolean("isImplicit")) {
                        parseCallable(child, namespace, name, CppFunction.Kind.METHOD)?.let(methods::add)
                    }
                }
            }
            if (destructor == null && hasUsableImplicitDestructor(node)) {
                destructor = CppFunction(
                    namespace = namespace,
                    owner = name,
                    name = "~$name",
                    returnType = CppType.Void,
                    parameters = emptyList(),
                    kind = CppFunction.Kind.DESTRUCTOR,
                )
            }
            if (ctors.isEmpty() && methods.isEmpty() && destructor == null) return null
            if (destructor == null) diagnostics += CppDiagnostic((namespace + name).joinToString("::"), "opaque class has no public destructor; generated instances cannot be owned")
            return CppClass(namespace, name, ctors.sortedBy(::signature), destructor, methods.sortedBy(::signature))
        }

        /**
         * Clang does not materialize an unused implicit destructor as a child declaration.
         * `definitionData.dtor.needsImplicit` is its semantic record that the compiler can
         * synthesize it; an implicit destructor is public. Do not synthesize when Clang instead
         * requires overload resolution (for example a deleted/inaccessible member destructor).
         */
        private fun hasUsableImplicitDestructor(node: JsonObject): Boolean =
            node.obj("definitionData")?.obj("dtor")?.boolean("needsImplicit") == true

        private fun parseCallable(node: JsonObject, namespace: List<String>, owner: String?, kind: CppFunction.Kind): CppFunction? {
            val name = node.string("name")
            if (name.isBlank() || node.boolean("isImplicit")) return null
            val display = (namespace + listOfNotNull(owner, name)).joinToString("::")
            val functionType = node.obj("type")?.string("qualType").orEmpty()
            val returnSpelling = when (kind) {
                CppFunction.Kind.CONSTRUCTOR, CppFunction.Kind.DESTRUCTOR -> "void"
                else -> functionType.substringBefore('(').trim()
            }
            val returnType = parseType(returnSpelling, namespace)
            if (returnType == null) {
                diagnostics += CppDiagnostic(display, "unsupported return type '$returnSpelling'")
                return null
            }
            val parameters = mutableListOf<CppParameter>()
            for ((index, parameter) in node.children().filter { it.string("kind") == "ParmVarDecl" }.withIndex()) {
                val spelling = parameter.obj("type")?.string("qualType").orEmpty()
                val type = parseType(spelling, namespace)
                if (type == null) {
                    diagnostics += CppDiagnostic(display, "unsupported parameter type '$spelling'")
                    return null
                }
                parameters += CppParameter(parameter.string("name").ifBlank { "arg$index" }, type)
            }
            val isConst = kind == CppFunction.Kind.METHOD && functionType.substringAfterLast(')').trim().split(' ').contains("const")
            return CppFunction(
                namespace = namespace,
                owner = owner,
                name = name,
                returnType = returnType,
                parameters = parameters,
                isStatic = node.string("storageClass") == "static",
                isConst = isConst,
                kind = kind,
            )
        }

        /**
         * Clang omits `loc.file` for declarations in the main input file. Declarations
         * originating in an include carry either an explicit different file or an
         * `includedFrom` chain. Filtering at namespace/record boundaries also keeps
         * their children from leaking into the public module.
         */
        private fun isFromRootHeader(node: JsonObject): Boolean {
            val locations = listOfNotNull(node.obj("loc"), node.obj("range")?.obj("begin"))
            val files = locations.mapNotNull { it.stringOrNull("file") }.map { Path.of(it).toAbsolutePath().normalize() }
            if (rootHeader != null && files.any { it == rootHeader }) return true
            if (locations.any { it["includedFrom"] is JsonObject }) return false
            if (rootHeader != null && files.isNotEmpty()) return false
            return files.isEmpty()
        }

        private fun parseType(raw: String, namespace: List<String>): CppType? {
            val pointerIsConst = raw.substringBeforeLast('*', "").contains(Regex("\\bconst\\b"))
            val noConst = raw.replace(Regex("\\b(const|volatile)\\b"), "").replace(Regex("\\s+"), " ").trim()
            if ('&' in noConst || '[' in noConst || '(' in noConst) return null
            if (noConst.endsWith("*")) return CppType.Pointer(noConst.removeSuffix("*").trim(), pointerIsConst)
            if (noConst == "void") return CppType.Void
            val primitive = when (noConst) {
                "bool" -> CppPrimitive.BOOL
                "char" -> CppPrimitive.CHAR
                "signed char" -> CppPrimitive.INT8
                "unsigned char" -> CppPrimitive.UINT8
                "short", "short int", "signed short", "signed short int" -> CppPrimitive.INT16
                "unsigned short", "unsigned short int" -> CppPrimitive.UINT16
                "int", "signed", "signed int" -> CppPrimitive.INT32
                "unsigned", "unsigned int" -> CppPrimitive.UINT32
                "long long", "long long int", "signed long long", "signed long long int" -> CppPrimitive.INT64
                "unsigned long long", "unsigned long long int" -> CppPrimitive.UINT64
                "float" -> CppPrimitive.FLOAT
                "double" -> CppPrimitive.DOUBLE
                else -> null
            }
            if (primitive != null) return CppType.Primitive(primitive)
            val candidates = if ("::" in noConst) listOf(noConst) else (namespace.indices.reversed().map { (namespace.take(it + 1) + noConst).joinToString("::") } + noConst)
            return candidates.firstOrNull(enumNames::contains)?.let(CppType::Enum)
        }

        private fun signature(function: CppFunction): String = function.qualifiedName + function.parameters.joinToString(",", "(", ")") { it.type.toString() }

        private companion object {
            // Children of a root namespace/record usually omit loc.file entirely,
            // so source filtering belongs at boundaries that own their children.
            // Top-level functions/enums/records are still checked individually.
            val rootBoundaryKinds = setOf("NamespaceDecl", "FunctionDecl", "EnumDecl", "CXXRecordDecl")
        }
    }
}

private fun JsonObject.string(key: String): String = stringOrNull(key).orEmpty()
private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.content
private fun JsonObject.boolean(key: String): Boolean = stringOrNull(key) == "true"
private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.isDeleted(): Boolean = string("explicitlyDefaulted") == "deleted" || boolean("isDeleted")
private fun JsonObject.children(): List<JsonObject> = (this["inner"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
private fun JsonObject.descendants(): Sequence<JsonObject> = sequence {
    yield(this@descendants)
    children().forEach { yieldAll(it.descendants()) }
}
