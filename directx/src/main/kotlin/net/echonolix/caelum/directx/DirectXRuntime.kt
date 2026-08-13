package net.echonolix.caelum.directx

import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandle
import net.echonolix.caelum.dxgi.win32.WindowsLibrary

/** Result of probing one Direct3D generation's process entry points. */
public data class DirectXVersionCapability(
    public val version: DirectXVersion,
    public val libraryAvailable: Boolean,
    public val creationAvailable: Boolean,
    public val extendedCreationAvailable: Boolean,
    public val bindingInterfaceTarget: DirectXInterfaceTarget,
    public val runtimeEntryInterfaceTarget: DirectXInterfaceTarget?,
) {
    /**
     * `true` means that device creation can be attempted. It does not promise
     * that an adapter supports a requested feature level or interface IID.
     */
    public val available: Boolean
        get() = libraryAvailable && creationAvailable
}

/** Optional-runtime capabilities discovered without creating a GPU device. */
public data class DirectXCapabilities(
    public val platformSupported: Boolean,
    public val versions: List<DirectXVersionCapability>,
    public val compiler47Available: Boolean,
    public val bindingTargets: DirectXBindingTargets,
    public val availableEntryPoints: Set<DirectXEntryPoint>,
) {
    init {
        require(versions.map(DirectXVersionCapability::version).toSet() == DirectXVersion.entries.toSet()) {
            "Capabilities must contain exactly one entry for every DirectX version"
        }
    }

    public val availableVersions: List<DirectXVersion>
        get() = versions.filter(DirectXVersionCapability::available).map(DirectXVersionCapability::version)

    public val highestAvailableVersion: DirectXVersion?
        get() = availableVersions.maxByOrNull(DirectXVersion::major)

    public fun forVersion(version: DirectXVersion): DirectXVersionCapability =
        versions.single { it.version == version }

    public fun has(entryPoint: DirectXEntryPoint): Boolean = entryPoint in availableEntryPoints
}

/**
 * Optional process-library and symbol resolver for DirectX.
 *
 * Loading this class never makes a DirectX version mandatory. [probe] opens
 * every DLL independently and every call site resolves its own symbol. This is
 * important for older Windows releases and optional redistributables such as
 * `d3dcompiler_47.dll`.
 */
public class DirectXRuntime private constructor(
    private val libraries: Map<DirectXLibrary, WindowsLibrary>,
    public val platformSupported: Boolean,
) {
    public fun libraryOrNull(library: DirectXLibrary): WindowsLibrary? = libraries[library]

    public fun symbolOrNull(entryPoint: DirectXEntryPoint): MemorySegment? =
        libraries[entryPoint.library]?.findOrNull(entryPoint.name)

    public fun symbolOrNull(library: DirectXLibrary, name: String): MemorySegment? =
        libraries[library]?.findOrNull(name)

    public fun symbol(entryPoint: DirectXEntryPoint): MemorySegment =
        symbolOrNull(entryPoint)
            ?: throw NoSuchElementException(
                "${entryPoint.name} is unavailable from ${entryPoint.library.fileName}",
            )

    public fun downcallOrNull(entryPoint: DirectXEntryPoint): MethodHandle? =
        libraries[entryPoint.library]?.downcallOrNull(entryPoint.name, entryPoint.descriptor)

    public fun downcall(entryPoint: DirectXEntryPoint): MethodHandle =
        downcallOrNull(entryPoint)
            ?: throw NoSuchElementException(
                "${entryPoint.name} is unavailable from ${entryPoint.library.fileName}",
            )

    public fun has(entryPoint: DirectXEntryPoint): Boolean = symbolOrNull(entryPoint) != null

    public fun capabilities(): DirectXCapabilities {
        val availableEntries: Set<DirectXEntryPoint> = DirectXEntryPoints.all.filterTo(linkedSetOf(), ::has)

        fun libraryAvailable(library: DirectXLibrary): Boolean = libraries.containsKey(library)

        val d3d9Ex: Boolean = has(D3D9EntryPoints.direct3DCreate9Ex)
        val d3d10_1: Boolean = has(D3D10EntryPoints.createDevice1)
        val versionCapabilities: List<DirectXVersionCapability> = listOf(
            DirectXVersionCapability(
                version = DirectXVersion.DIRECT3D_9,
                libraryAvailable = libraryAvailable(DirectXLibrary.D3D9),
                creationAvailable = has(D3D9EntryPoints.direct3DCreate9),
                extendedCreationAvailable = d3d9Ex,
                bindingInterfaceTarget = DirectXInterfaceTarget.DIRECT3D9_EX,
                runtimeEntryInterfaceTarget = when {
                    d3d9Ex -> DirectXInterfaceTarget.DIRECT3D9_EX
                    has(D3D9EntryPoints.direct3DCreate9) -> DirectXInterfaceTarget.DIRECT3D9
                    else -> null
                },
            ),
            DirectXVersionCapability(
                version = DirectXVersion.DIRECT3D_10,
                libraryAvailable =
                    libraryAvailable(DirectXLibrary.D3D10) || libraryAvailable(DirectXLibrary.D3D10_1),
                creationAvailable = has(D3D10EntryPoints.createDevice) || d3d10_1,
                extendedCreationAvailable = d3d10_1,
                bindingInterfaceTarget = DirectXInterfaceTarget.DIRECT3D10_1,
                runtimeEntryInterfaceTarget = when {
                    d3d10_1 -> DirectXInterfaceTarget.DIRECT3D10_1
                    has(D3D10EntryPoints.createDevice) -> DirectXInterfaceTarget.DIRECT3D10
                    else -> null
                },
            ),
            DirectXVersionCapability(
                version = DirectXVersion.DIRECT3D_11,
                libraryAvailable = libraryAvailable(DirectXLibrary.D3D11),
                creationAvailable = has(D3D11EntryPoints.createDevice),
                extendedCreationAvailable = false,
                bindingInterfaceTarget = DirectXInterfaceTarget.DIRECT3D11_DEVICE5,
                // Device revisions are discovered with QueryInterface after creation.
                runtimeEntryInterfaceTarget = null,
            ),
            DirectXVersionCapability(
                version = DirectXVersion.DIRECT3D_12,
                libraryAvailable = libraryAvailable(DirectXLibrary.D3D12),
                creationAvailable = has(D3D12EntryPoints.createDevice),
                extendedCreationAvailable = has(D3D12EntryPoints.getInterface),
                bindingInterfaceTarget = DirectXInterfaceTarget.DIRECT3D12_DEVICE15,
                // Device and command-list revisions require QueryInterface.
                runtimeEntryInterfaceTarget = null,
            ),
        )

        return DirectXCapabilities(
            platformSupported = platformSupported,
            versions = versionCapabilities,
            compiler47Available = has(D3DCompiler47EntryPoints.compile),
            bindingTargets = DirectXBindingTargets(),
            availableEntryPoints = availableEntries,
        )
    }

    public companion object {
        /**
         * Probes the current process without making any DirectX DLL mandatory.
         * The current ABI layer supports 64-bit Windows processes.
         */
        @JvmStatic
        public fun probe(): DirectXRuntime {
            if (!WindowsLibrary.isSupportedPlatform) {
                return DirectXRuntime(emptyMap(), false)
            }

            val resolved: Map<DirectXLibrary, WindowsLibrary> = DirectXLibrary.entries
                .mapNotNull { library ->
                    WindowsLibrary.openOrNull(library.fileName)?.let { library to it }
                }
                .toMap()
            return DirectXRuntime(resolved, true)
        }
    }
}

/** Stateless facade matching the other Caelum native API modules. */
public object DirectX {
    @JvmStatic
    public fun probeRuntime(): DirectXRuntime = DirectXRuntime.probe()

    @JvmStatic
    public fun probeCapabilities(): DirectXCapabilities = probeRuntime().capabilities()
}
