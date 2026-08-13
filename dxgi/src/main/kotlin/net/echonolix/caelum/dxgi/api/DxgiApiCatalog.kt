package net.echonolix.caelum.dxgi.api

/** Classpath access to the pinned DXGI 1.0-1.6 raw declaration catalog. */
public object DxgiApiCatalog {
    public const val INDEX_RESOURCE: String = "net/echonolix/caelum/dxgi/api/index.json"
    public const val SCHEMA_RESOURCE: String =
        "net/echonolix/caelum/dxgi/api/dxgi-sdk-10.0.22621.0.json"

    public fun load(
        classLoader: ClassLoader = NativeApiCatalog.defaultClassLoader(),
    ): NativeApiCatalog = loadIndexedOrDefault(classLoader)

    public fun loadResource(
        resourcePath: String,
        classLoader: ClassLoader = NativeApiCatalog.defaultClassLoader(),
    ): NativeApiCatalog = NativeApiCatalog.loadResource(resourcePath, classLoader)

    private fun loadIndexedOrDefault(classLoader: ClassLoader): NativeApiCatalog =
        if (classLoader.getResource(INDEX_RESOURCE) != null) {
            NativeApiResourceIndex.loadResource(INDEX_RESOURCE, classLoader).load("dxgi")
        } else {
            NativeApiCatalog.loadResource(SCHEMA_RESOURCE, classLoader)
        }
}
