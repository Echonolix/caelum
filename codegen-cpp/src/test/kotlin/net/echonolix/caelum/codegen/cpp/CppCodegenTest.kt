package net.echonolix.caelum.codegen.cpp

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.nio.file.Files
import java.net.URLClassLoader
import java.lang.reflect.InvocationTargetException
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler

class CppCodegenTest {
    private val header = checkNotNull(javaClass.getResource("/fixture/sample.hpp")) { "fixture missing" }.toURI().let(java.nio.file.Path::of)
    private val keywordHeader = checkNotNull(javaClass.getResource("/fixture/keyword.hpp")) { "keyword fixture missing" }.toURI().let(java.nio.file.Path::of)
    private val implicitDestructorHeader = checkNotNull(javaClass.getResource("/fixture/implicit_destructor.hpp")) { "implicit destructor fixture missing" }.toURI().let(java.nio.file.Path::of)

    @Test
    fun `root header filtering excludes declarations from cstdint and preserves method qualifiers`() {
        val module = ClangAst.parse(ClangAst.dump(header), header)
        assertEquals(listOf("demo::a::b::echo", "demo::a_b::echo", "demo::add", "demo::add", "demo::add", "demo::clash", "demo::fail", "demo::first::value", "demo::greeting", "demo::second::value"), module.functions.map(CppFunction::qualifiedName))
        assertEquals(listOf("demo::Mode", "demo::a::b::Token", "demo::a_b::Token", "demo::first::State", "demo::second::State"), module.enums.map(CppEnum::qualifiedName))
        assertEquals(listOf("demo::Alpha", "demo::Beta", "demo::Counter"), module.classes.map(CppClass::qualifiedName))
        val counter = module.classes.single { it.name == "Counter" }
        assertTrue(counter.methods.single { it.name == "get" }.isConst)
        assertTrue(counter.methods.single { it.name == "twice" }.isStatic)
        assertFalse(module.functions.any { it.qualifiedName.startsWith("std::") })
        assertTrue(module.diagnostics.isEmpty(), module.diagnostics.toString())
    }

    @Test
    fun `emission is deterministic and generated Kotlin compiles`() {
        val module = ClangAst.parse(ClangAst.dump(header), header)
        val first = Files.createTempDirectory("caelum-cpp-first")
        val second = Files.createTempDirectory("caelum-cpp-second")
        val config = CppCodegenConfig("sample", "demo.generated")
        val a = CppCodegen.emit(module, header, first, config)
        val b = CppCodegen.emit(module, header, second, config)
        assertEquals(a.header.readText(), b.header.readText())
        assertEquals(a.source.readText(), b.source.readText())
        assertEquals(a.kotlin.readText(), b.kotlin.readText())
        assertTrue(a.kotlin.readText().contains("enum class DemoFirstState"))
        assertTrue(a.kotlin.readText().contains("fun add_demo_unsigned_int_unsigned_int"))
        assertTrue(a.kotlin.readText().contains("fun value_demo_first"))
        assertTrue(a.kotlin.readText().contains("fun clash_demo_alpha_int"))
        assertTrue(a.kotlin.readText().contains("fun clash_demo_beta_int"))
        val echoNames = Regex("fun (echo_[A-Za-z0-9_]+)\\(").findAll(a.kotlin.readText()).map { it.groupValues[1] }.toList()
        assertEquals(2, echoNames.distinct().size, echoNames.toString())
        val tokenNames = Regex("enum class (DemoABToken_[A-Fa-f0-9]+)").findAll(a.kotlin.readText()).map { it.groupValues[1] }.toList()
        assertEquals(2, tokenNames.distinct().size, tokenNames.toString())
        val compilerLog = ByteArrayOutputStream()
        val exit = K2JVMCompiler().exec(
            PrintStream(compilerLog),
            "-classpath", System.getProperty("java.class.path"),
            "-d", first.resolve("kotlin-classes").toString(),
            a.kotlin.toString(),
        )
        assertEquals(ExitCode.OK, exit, compilerLog.toString())
    }

    @Test
    fun `generated collision API and const pointer shim compile and lifecycle is enforced`() {
        val output = Files.createTempDirectory("caelum-cpp-collisions")
        val generated = CppCodegen.generate(header, output, CppCodegenConfig("collision_sample", "demo.generated", "CollisionNative"))
        val implementation = checkNotNull(javaClass.getResource("/fixture/sample.cpp")).toURI().let(java.nio.file.Path::of)
        val library = output.resolve(System.mapLibraryName("collision_sample"))
        val native = ProcessBuilder("clang++", "-std=c++17", "-shared", generated.source.toString(), implementation.toString(), "-I", header.parent.toString(), "-o", library.toString())
            .redirectErrorStream(true).start()
        val nativeLog = native.inputStream.bufferedReader().use { it.readText() }
        assertEquals(0, native.waitFor(), nativeLog + "\n" + generated.source.readText())

        val classes = output.resolve("kotlin-classes")
        val compilerLog = ByteArrayOutputStream()
        val exit = K2JVMCompiler().exec(PrintStream(compilerLog), "-classpath", System.getProperty("java.class.path"), "-d", classes.toString(), generated.kotlin.toString())
        assertEquals(ExitCode.OK, exit, compilerLog.toString() + "\n" + generated.kotlin.readText())

        URLClassLoader(arrayOf(classes.toUri().toURL()), javaClass.classLoader).use { loader ->
            val type = loader.loadClass("demo.generated.CollisionNative")
            val singleton = type.getField("INSTANCE").get(null)
            type.getMethod("load", java.nio.file.Path::class.java).invoke(singleton, library)
            val greeting = type.getMethod("greeting").invoke(singleton) as java.lang.foreign.MemorySegment
            assertEquals("hello from const", greeting.reinterpret(32).getString(0))
            type.getMethod("close").invoke(singleton)
            type.getMethod("close").invoke(singleton)
            val failure = kotlin.runCatching { type.getMethod("greeting").invoke(singleton) }.exceptionOrNull()
            assertTrue(failure is InvocationTargetException)
            assertTrue(failure.cause is IllegalStateException)
            assertEquals("native binding is closed", failure.cause?.message)
        }
    }

    @Test
    fun `implicit public destructor produces a delete shim and owned FFM lifecycle`() {
        val output = Files.createTempDirectory("caelum-cpp-implicit-destructor")
        val generated = CppCodegen.generate(implicitDestructorHeader, output, CppCodegenConfig("implicit_destructor", "demo.generated", "ImplicitDestructorNative"))
        val clazz = ClangAst.parse(ClangAst.dump(implicitDestructorHeader), implicitDestructorHeader).classes.single()
        val destructor = checkNotNull(clazz.destructor) { "public implicit destructor was not modeled" }
        assertEquals(CppFunction.Kind.DESTRUCTOR, destructor.kind)
        assertTrue(generated.source.readText().contains("delete static_cast<demo::ImplicitDestructor*>(self);"))

        val implementation = checkNotNull(javaClass.getResource("/fixture/implicit_destructor.cpp")).toURI().let(java.nio.file.Path::of)
        val library = output.resolve(System.mapLibraryName("implicit_destructor"))
        val native = ProcessBuilder("clang++", "-std=c++17", "-shared", generated.source.toString(), implementation.toString(), "-I", implicitDestructorHeader.parent.toString(), "-o", library.toString())
            .redirectErrorStream(true).start()
        val nativeLog = native.inputStream.bufferedReader().use { it.readText() }
        assertEquals(0, native.waitFor(), nativeLog + "\n" + generated.source.readText())
        assertTrue(Files.size(library) > 0)

        val classes = output.resolve("kotlin-classes")
        val compilerLog = ByteArrayOutputStream()
        val exit = K2JVMCompiler().exec(PrintStream(compilerLog), "-classpath", System.getProperty("java.class.path"), "-d", classes.toString(), generated.kotlin.toString())
        assertEquals(ExitCode.OK, exit, compilerLog.toString() + "\n" + generated.kotlin.readText())

        URLClassLoader(arrayOf(classes.toUri().toURL()), javaClass.classLoader).use { loader ->
            val type = loader.loadClass("demo.generated.ImplicitDestructorNative")
            val singleton = type.getField("INSTANCE").get(null)
            type.getMethod("load", java.nio.file.Path::class.java).invoke(singleton, library)
            val instance = type.getMethod("createImplicitDestructor", Int::class.javaPrimitiveType).invoke(singleton, 4)
            val instanceType = loader.loadClass("demo.generated.ImplicitDestructorNative${'$'}ImplicitDestructor")
            assertEquals(7, instanceType.getMethod("add", Int::class.javaPrimitiveType).invoke(instance, 3))
            instanceType.getMethod("close").invoke(instance)
            instanceType.getMethod("close").invoke(instance)
            val failure = kotlin.runCatching { instanceType.getMethod("add", Int::class.javaPrimitiveType).invoke(instance, 1) }.exceptionOrNull()
            assertTrue(failure is InvocationTargetException)
            assertTrue(failure.cause is IllegalStateException)
            assertEquals("ImplicitDestructor is closed", failure.cause?.message)
            type.getMethod("close").invoke(singleton)
        }
    }

    @Test
    fun `kotlin keywords in parameter function and enum entries compile with native shim`() {
        val output = Files.createTempDirectory("caelum-cpp-keywords")
        val generated = CppCodegen.generate(keywordHeader, output, CppCodegenConfig("keyword_fixture", "demo.generated", "KeywordNative"))
        val implementation = checkNotNull(javaClass.getResource("/fixture/keyword.cpp")).toURI().let(java.nio.file.Path::of)
        val library = output.resolve(System.mapLibraryName("keyword_fixture"))
        val native = ProcessBuilder("clang++", "-std=c++17", "-shared", generated.source.toString(), implementation.toString(), "-I", keywordHeader.parent.toString(), "-o", library.toString())
            .redirectErrorStream(true).start()
        val nativeLog = native.inputStream.bufferedReader().use { it.readText() }
        assertEquals(0, native.waitFor(), nativeLog + "\n" + generated.source.readText())
        assertTrue(Files.size(library) > 0)

        val kotlin = generated.kotlin.readText()
        assertTrue(kotlin.contains("fun `object`(`when`: Int): Int"), kotlin)
        assertTrue(kotlin.contains("enum class mode"), kotlin)
        assertTrue(kotlin.contains("`is`(1L)"), kotlin)
        assertTrue(kotlin.contains("`object`(2L)"), kotlin)
        val compilerLog = ByteArrayOutputStream()
        val exit = K2JVMCompiler().exec(PrintStream(compilerLog), "-classpath", System.getProperty("java.class.path"), "-d", output.resolve("kotlin-classes").toString(), generated.kotlin.toString())
        assertEquals(ExitCode.OK, exit, compilerLog.toString() + "\n" + kotlin)
    }

    @Test
    fun `generated shim compiles and exposes caught exception through last error`() {
        val output = Files.createTempDirectory("caelum-cpp-compile")
        val generated = CppCodegen.generate(header, output, CppCodegenConfig("sample", "demo.generated"))
        val implementation = checkNotNull(javaClass.getResource("/fixture/sample.cpp")).toURI().let(java.nio.file.Path::of)
        val library = output.resolve(System.mapLibraryName("sample"))
        val process = ProcessBuilder("clang++", "-std=c++17", "-shared", generated.source.toString(), implementation.toString(), "-I", header.parent.toString(), "-o", library.toString())
            .redirectErrorStream(true).start()
        val log = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(0, process.waitFor(), log)
        assertTrue(Files.size(library) > 0)

        Arena.ofConfined().use { arena ->
            val lookup = SymbolLookup.libraryLookup(library, arena)
            val linker = Linker.nativeLinker()
            val fail = moduleFunction("demo::fail")
            val status = linker.downcallHandle(
                lookup.find(symbolFor(fail)).orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
            ).invokeWithArguments(arena.allocate(ValueLayout.JAVA_INT)) as Int
            assertEquals(1, status)
            val errorAddress = linker.downcallHandle(
                lookup.find("caelum_sample_last_error").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS),
            ).invokeWithArguments() as java.lang.foreign.MemorySegment
            assertEquals("fixture failure", errorAddress.reinterpret(64).getString(0))
        }
    }

    @Test
    fun `ktgen processor writes all four outputs`() {
        val output = Files.createTempDirectory("caelum-cpp-processor")
        val keys = mapOf(
            "codegencpp.moduleName" to "processor_sample",
            "codegencpp.packageName" to "demo.processor",
            "codegencpp.objectName" to "ProcessorNative",
            "codegencpp.clang" to "clang++",
        )
        val old = keys.mapValues { System.getProperty(it.key) }
        try {
            keys.forEach(System::setProperty)
            val files = CppCodegenProcessor().process(setOf(header), output)
            assertEquals(4, files.size)
            assertTrue(files.all(Files::isRegularFile))
            assertTrue(files.single { it.fileName.toString() == "ProcessorNative.kt" }.readText().contains("object ProcessorNative"))
        } finally {
            old.forEach { (key, value) -> if (value == null) System.clearProperty(key) else System.setProperty(key, value) }
        }
    }

    private fun moduleFunction(qualifiedName: String): CppFunction =
        ClangAst.parse(ClangAst.dump(header), header).functions.single { it.qualifiedName == qualifiedName }
}
