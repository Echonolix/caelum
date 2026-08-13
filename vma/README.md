# Caelum VMA

Low-level Java FFM function bindings for the platform-independent allocator and
**virtual allocation** APIs exported by AMD Vulkan Memory Allocator 3.4.0.
The virtual allocator is useful without a Vulkan device (or even a Vulkan
loader), and the module does not duplicate Vulkan public types owned by
`caelum-vulkan`.

The vendored source is `src/main/headers/vk_mem_alloc.h`. Its provenance and
checksum are recorded next to it in `PROVENANCE.md`. To build the native shared
library, a C++17 compiler and Vulkan headers are required. Set `VULKAN_SDK`, or
pass `-PvulkanIncludeDir=/path/to/Vulkan-Headers/include`. Vulkan-Headers
v1.4.309 is the closest published headers release to the repository registry's
header version 308.

## Supported API

`VmaAllocator` covers allocator lifecycle and all portable exported workflows:

* create/destroy and allocator/device/memory information;
* memory-type selection, allocation (including pages/buffer/image), free and
  allocation information/name/user data;
* map/unmap, flush/invalidate, and copy to/from an allocation; and
* create/destroy buffer and image, including buffer alignment; and
* pools, defragmentation, corruption checks, resource binding and aliasing,
  detailed statistics, and statistics strings.

It exposes `VkResult` and VMA flag bitmasks as `Int`, VMA/Vulkan handles and
callback-table pointers as raw 64-bit `Long` values, and native structs/arrays
as caller-owned `MemorySegment`s so they interoperate with Caelum's Vulkan ABI
without another Vulkan type hierarchy. The binding covers all 73 functions
exported by this portable build, including pools, defragmentation, corruption
checks, binding, aliasing, and statistics strings. It deliberately remains a
low-level function/layout binding: VMA enum/flag constants come from the pinned
header rather than duplicated Kotlin enums, and optional callback records such
as `VmaDeviceMemoryCallbacks` are supplied by raw address. The only omitted
function declarations are the conditional Volk import entry point and the two
Win32 external-memory-handle helpers, none of which this native build exports.

The packaged native binary uses VMA's dynamic Vulkan loading mode. Before
`createAllocator`, allocate `VmaAllocatorExtendedLayouts.vulkanFunctions`, set
its required `vkGetInstanceProcAddr` and `vkGetDeviceProcAddr` addresses through
`VmaVulkanFunctions`, and attach it to `VmaAllocatorCreateInfo`. The table and
any upcall stubs backing those addresses must remain alive for the allocator's
lifetime. `createAllocator` rejects a missing table instead of allowing VMA to
abort on a null dynamic loader.

`VmaVirtual` covers every function in VMA's Virtual allocator chapter:

* `vmaCreateVirtualBlock`, `vmaDestroyVirtualBlock`, `vmaIsVirtualBlockEmpty`
* `vmaGetVirtualAllocationInfo`, `vmaVirtualAllocate`, `vmaVirtualFree`,
  `vmaClearVirtualBlock`
* `vmaSetVirtualAllocationUserData`
* `vmaGetVirtualBlockStatistics`, `vmaCalculateVirtualBlockStatistics`
* `vmaBuildVirtualBlockStatsString`, `vmaFreeVirtualBlockStatsString`

All handles are native addresses (`Long`) and all structures are exposed as
FFM `MemoryLayout`s plus field accessors. Callers own arenas and must keep any
memory referenced by VMA alive for the corresponding operation or block.

Load an explicit file with `VmaAllocator.load(Path)` or `VmaVirtual.load(Path)`.
Both binding surfaces also provide `loadBundled()` for the native binary
packaged in the module JAR. Every path uses a lookup scoped to that library,
never the process-wide loader lookup.

`VmaAllocator.loadBundled()` and `VmaVirtual.loadBundled()` select
`META-INF/caelum/native/<os>-<arch>/<library-name>` for the current host,
extract it below `java.io.tmpdir/caelum/vma/<sha256>/`, and verify SHA-256 both
after extraction and immediately before the library-scoped lookup. The first
extractor publishes the stable digest cache name through an exclusive
`CREATE_NEW` open, so a verified shared cache entry is reused on subsequent
calls. An occupied or corrupt primary is never overwritten; extraction then
uses a uniquely named private `CREATE_NEW` file, copies, flushes, and verifies
it. This reduces
the risk from a writable temporary directory, but cannot make a globally shared
temp directory trustworthy against a same-privilege attacker that can modify
files after verification. A published artifact contains only the binary built
for its host OS and CPU; use an artifact built for the target platform or
provide an explicit library path.

The VMA ABI model here intentionally requires a 64-bit JVM/native address
space. The binding represents native pointers and VMA handles as `Long`, and
fails before library loading on a 32-bit runtime.

## ABI and licensing

The compiled VMA translation unit explicitly sets `VMA_EXTERNAL_MEMORY=1`.
Upstream VMA otherwise derives that macro from `VK_KHR_external_memory` in the
selected Vulkan-Headers version. The Caelum `VmaAllocatorCreateInfo` layout is
therefore fixed to the enabled form, including its final
`pTypeExternalMemoryHandleTypes` pointer field (88 bytes on the supported
64-bit ABI). Do not combine this JAR with a VMA binary compiled with
`VMA_EXTERNAL_MEMORY=0`.

Caelum source is Apache-2.0. The vendored VMA 3.4.0 component remains MIT:
the binary and sources JARs contain the project `LICENSE` plus
`META-INF/licenses/VulkanMemoryAllocator-MIT.txt` and
`META-INF/licenses/VulkanMemoryAllocator-PROVENANCE.md`. The module POM
declares both licenses.
