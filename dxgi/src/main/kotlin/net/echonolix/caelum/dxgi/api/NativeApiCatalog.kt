package net.echonolix.caelum.dxgi.api

import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Immutable, queryable view of one pinned native-header schema.
 *
 * A catalog indexes every declaration by its native spelling. Duplicate names
 * are rejected while loading; silently picking one would make ABI calls depend
 * on JSON ordering.
 */
public class NativeApiCatalog internal constructor(
    public val schemaVersion: Int,
    public val api: String,
    public val namespace: String?,
    public val target: NativeSchemaValue?,
    public val sourceSet: NativeSchemaObject?,
    public val reviewedExclusions: List<NativeReviewedExclusion>,
    public val declarations: NativeApiDeclarations,
    public val statistics: NativeSchemaObject?,
) {
    public val interfaces: Map<String, NativeInterfaceDeclaration> =
        declarations.interfaces.uniqueIndex("interfaces") { it.name }
    public val enums: Map<String, NativeEnumDeclaration> =
        declarations.enums.uniqueIndex("enums") { it.name }
    public val records: Map<String, NativeRecordDeclaration> =
        declarations.records.uniqueIndex("records") { it.name }
    public val typedefs: Map<String, NativeTypedefDeclaration> =
        declarations.typedefs.uniqueIndex("typedefs") { it.name }
    public val functions: Map<String, NativeFunctionDeclaration> =
        declarations.functions.uniqueIndex("functions") { it.name }
    public val constants: Map<String, NativeConstantDeclaration> =
        declarations.constants.uniqueIndex("constants") { it.name }

    public fun interfaceDeclaration(name: String): NativeInterfaceDeclaration? = interfaces[name]

    public fun enumDeclaration(name: String): NativeEnumDeclaration? = enums[name]

    public fun record(name: String): NativeRecordDeclaration? = records[name]

    public fun typedef(name: String): NativeTypedefDeclaration? = typedefs[name]

    public fun function(name: String): NativeFunctionDeclaration? = functions[name]

    public fun constant(name: String): NativeConstantDeclaration? = constants[name]

    public fun requireInterface(name: String): NativeInterfaceDeclaration = interfaces[name]
        ?: throw NoSuchElementException("$api schema has no interface named $name")

    public fun requireRecord(name: String): NativeRecordDeclaration = records[name]
        ?: throw NoSuchElementException("$api schema has no record named $name")

    public fun requireFunction(name: String): NativeFunctionDeclaration = functions[name]
        ?: throw NoSuchElementException("$api schema has no function named $name")

    public fun nativeTypes(dependencies: List<NativeApiCatalog> = emptyList()): NativeTypeParser =
        NativeTypeParser(this, dependencies)

    public companion object {
        public const val SUPPORTED_SCHEMA_VERSION: Int = 1

        public fun parse(json: String): NativeApiCatalog =
            NativeCatalogDecoder.decode(json)

        public fun read(input: InputStream): NativeApiCatalog = input.bufferedReader(StandardCharsets.UTF_8).use {
            parse(it.readText())
        }

        public fun loadResource(
            resourcePath: String,
            classLoader: ClassLoader = defaultClassLoader(),
        ): NativeApiCatalog {
            val normalized = normalizeResourcePath(resourcePath)
            val input = classLoader.getResourceAsStream(normalized)
                ?: throw NativeApiResourceException("Native API schema resource not found: /$normalized")
            return try {
                input.use(::read)
            } catch (error: NativeSchemaException) {
                throw NativeApiResourceException("Invalid native API schema resource: /$normalized", error)
            }
        }

        internal fun defaultClassLoader(): ClassLoader =
            Thread.currentThread().contextClassLoader ?: NativeApiCatalog::class.java.classLoader

        internal fun normalizeResourcePath(path: String): String = path.trim().removePrefix("/").also {
            require(it.isNotEmpty()) { "Resource path must not be blank" }
        }
    }
}

private fun <T> List<T>.uniqueIndex(section: String, key: (T) -> String): Map<String, T> {
    val result = LinkedHashMap<String, T>(size)
    for (item in this) {
        val name = key(item)
        if (result.put(name, item) != null) {
            throw NativeSchemaException("declarations.$section contains duplicate name '$name'")
        }
    }
    return result.toMap()
}
