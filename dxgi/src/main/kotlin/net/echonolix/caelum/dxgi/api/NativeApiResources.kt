package net.echonolix.caelum.dxgi.api

import java.nio.charset.StandardCharsets

/** One catalog entry from a classpath `index.json`. */
public data class NativeApiResource(
    public val api: String,
    public val resourcePath: String,
    public val sha256: String?,
)

/**
 * Loads versioned schema resources without assuming that they live in
 * `META-INF`. Relative schema paths are resolved against the index directory.
 */
public class NativeApiResourceIndex private constructor(
    public val schemaVersion: Int,
    public val indexResourcePath: String,
    public val schemas: Map<String, NativeApiResource>,
    public val statistics: NativeSchemaObject?,
    private val classLoader: ClassLoader,
) {
    public fun resource(api: String): NativeApiResource? = schemas[api]

    public fun requireResource(api: String): NativeApiResource = resource(api)
        ?: throw NoSuchElementException("Native API index /$indexResourcePath has no schema for '$api'")

    public fun load(api: String): NativeApiCatalog =
        NativeApiCatalog.loadResource(requireResource(api).resourcePath, classLoader)

    public fun loadAll(): Map<String, NativeApiCatalog> = schemas.mapValues { (api, _) -> load(api) }

    public companion object {
        public fun loadResource(
            indexResourcePath: String,
            classLoader: ClassLoader = NativeApiCatalog.defaultClassLoader(),
        ): NativeApiResourceIndex {
            val normalized = NativeApiCatalog.normalizeResourcePath(indexResourcePath)
            val text = classLoader.getResourceAsStream(normalized)?.bufferedReader(StandardCharsets.UTF_8)?.use {
                it.readText()
            } ?: throw NativeApiResourceException("Native API index resource not found: /$normalized")
            return try {
                decode(text, normalized, classLoader)
            } catch (error: NativeSchemaException) {
                throw NativeApiResourceException("Invalid native API index resource: /$normalized", error)
            }
        }

        internal fun decode(
            json: String,
            indexResourcePath: String,
            classLoader: ClassLoader,
        ): NativeApiResourceIndex {
            val root = SchemaJsonAccess.parseObject(json)
            val schemaVersion = root.requiredInt("schemaVersion")
            if (schemaVersion != NativeApiCatalog.SUPPORTED_SCHEMA_VERSION) {
                throw NativeSchemaException("Unsupported native API index version $schemaVersion")
            }
            val directory = indexResourcePath.substringBeforeLast('/', "")
            val schemaPaths = root.requiredStringMap("schemas")
            val hashes = root.optionalStringMap("hashes").orEmpty()
            return NativeApiResourceIndex(
                schemaVersion = schemaVersion,
                indexResourcePath = indexResourcePath,
                schemas = schemaPaths.mapValues { (api, path) ->
                    NativeApiResource(api, resolveResourcePath(directory, path), hashes[api] ?: hashes[path])
                },
                statistics = root.optionalPublicObject("statistics"),
                classLoader = classLoader,
            )
        }

        private fun resolveResourcePath(directory: String, path: String): String {
            val normalized = NativeApiCatalog.normalizeResourcePath(path)
            return if ('/' in path || directory.isEmpty()) normalized else "$directory/$normalized"
        }
    }
}
