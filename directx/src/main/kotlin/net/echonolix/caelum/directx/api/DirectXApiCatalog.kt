package net.echonolix.caelum.directx.api

import net.echonolix.caelum.directx.DirectXVersion
import net.echonolix.caelum.dxgi.api.NativeApiCatalog
import net.echonolix.caelum.dxgi.api.NativeApiResourceIndex
import net.echonolix.caelum.dxgi.api.CompositeNativeApiCatalog
import net.echonolix.caelum.dxgi.api.DxgiApiCatalog
import java.lang.foreign.FunctionDescriptor

/** Classpath facade over the versioned Direct3D 9, 10, 11, and 12 schemas. */
public object DirectXApiCatalog {
    public const val INDEX_RESOURCE: String = "net/echonolix/caelum/directx/api/index.json"

    public const val D3D9_SCHEMA_RESOURCE: String =
        "net/echonolix/caelum/directx/api/d3d9-sdk-10.0.22621.0.json"
    public const val D3D10_SCHEMA_RESOURCE: String =
        "net/echonolix/caelum/directx/api/d3d10-sdk-10.0.22621.0.json"
    public const val D3D11_SCHEMA_RESOURCE: String =
        "net/echonolix/caelum/directx/api/d3d11-sdk-10.0.22621.0.json"
    public const val D3D12_SCHEMA_RESOURCE: String =
        "net/echonolix/caelum/directx/api/d3d12-headers-1.619.5.json"

    public fun load(
        version: DirectXVersion,
        classLoader: ClassLoader = defaultClassLoader(),
    ): NativeApiCatalog {
        val api = apiName(version)
        return if (classLoader.getResource(INDEX_RESOURCE) != null) {
            NativeApiResourceIndex.loadResource(INDEX_RESOURCE, classLoader).load(api)
        } else {
            NativeApiCatalog.loadResource(defaultResource(version), classLoader)
        }
    }

    public fun loadAll(
        classLoader: ClassLoader = defaultClassLoader(),
    ): Map<DirectXVersion, NativeApiCatalog> = DirectXVersion.entries.associateWith { load(it, classLoader) }

    /** Loads a version with the shared D3D and DXGI type catalogs required by its signatures. */
    public fun profile(
        version: DirectXVersion,
        classLoader: ClassLoader = defaultClassLoader(),
    ): CompositeNativeApiCatalog {
        val index = NativeApiResourceIndex.loadResource(INDEX_RESOURCE, classLoader)
        val common = index.load("d3dcommon-compiler")
        val dependencies = when (version) {
            DirectXVersion.DIRECT3D_9 -> emptyList()
            DirectXVersion.DIRECT3D_10 -> listOf(common, DxgiApiCatalog.load(classLoader))
            DirectXVersion.DIRECT3D_11 -> listOf(
                common,
                DxgiApiCatalog.load(classLoader),
                index.load("d3d12"),
            )
            DirectXVersion.DIRECT3D_12 -> listOf(common, DxgiApiCatalog.load(classLoader))
        }
        return CompositeNativeApiCatalog(load(version, classLoader), dependencies)
    }

    public fun functionDescriptor(
        version: DirectXVersion,
        functionName: String,
        classLoader: ClassLoader = defaultClassLoader(),
    ): FunctionDescriptor = profile(version, classLoader).functionDescriptor(functionName)

    public fun comMethodDescriptor(
        version: DirectXVersion,
        interfaceName: String,
        methodName: String,
        classLoader: ClassLoader = defaultClassLoader(),
    ): FunctionDescriptor = profile(version, classLoader).comMethodDescriptor(interfaceName, methodName)

    public fun loadResource(
        resourcePath: String,
        classLoader: ClassLoader = defaultClassLoader(),
    ): NativeApiCatalog = NativeApiCatalog.loadResource(resourcePath, classLoader)

    public fun defaultResource(version: DirectXVersion): String = when (version) {
        DirectXVersion.DIRECT3D_9 -> D3D9_SCHEMA_RESOURCE
        DirectXVersion.DIRECT3D_10 -> D3D10_SCHEMA_RESOURCE
        DirectXVersion.DIRECT3D_11 -> D3D11_SCHEMA_RESOURCE
        DirectXVersion.DIRECT3D_12 -> D3D12_SCHEMA_RESOURCE
    }

    public fun apiName(version: DirectXVersion): String = when (version) {
        DirectXVersion.DIRECT3D_9 -> "d3d9"
        DirectXVersion.DIRECT3D_10 -> "d3d10"
        DirectXVersion.DIRECT3D_11 -> "d3d11"
        DirectXVersion.DIRECT3D_12 -> "d3d12"
    }

    private fun defaultClassLoader(): ClassLoader =
        Thread.currentThread().contextClassLoader ?: DirectXApiCatalog::class.java.classLoader
}
