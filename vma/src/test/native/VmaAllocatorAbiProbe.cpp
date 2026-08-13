#include <cstddef>
#include "vk_mem_alloc.h"

static_assert(sizeof(void*) == 8, "64-bit pointer ABI");
#if VMA_EXTERNAL_MEMORY != 1
#error "Caelum VMA ABI requires VMA_EXTERNAL_MEMORY=1"
#endif
static_assert(sizeof(VmaAllocatorCreateInfo) == 88, "VmaAllocatorCreateInfo size");
static_assert(offsetof(VmaAllocatorCreateInfo, physicalDevice) == 8, "physicalDevice offset");
static_assert(offsetof(VmaAllocatorCreateInfo, instance) == 64, "instance offset");
static_assert(offsetof(VmaAllocatorCreateInfo, vulkanApiVersion) == 72, "vulkanApiVersion offset");
static_assert(offsetof(VmaAllocatorCreateInfo, pTypeExternalMemoryHandleTypes) == 80, "external types offset");
static_assert(sizeof(VmaAllocatorInfo) == 24, "VmaAllocatorInfo size");
static_assert(sizeof(VkMemoryRequirements) == 24, "VkMemoryRequirements size");
static_assert(sizeof(VmaAllocationCreateInfo) == 56, "VmaAllocationCreateInfo size");
static_assert(offsetof(VmaAllocationCreateInfo, pool) == 24, "pool offset");
static_assert(offsetof(VmaAllocationCreateInfo, priority) == 40, "priority offset");
static_assert(offsetof(VmaAllocationCreateInfo, minAlignment) == 48, "minAlignment offset");
static_assert(sizeof(VmaAllocationInfo) == 56, "VmaAllocationInfo size");
static_assert(offsetof(VmaAllocationInfo, deviceMemory) == 8, "deviceMemory offset");
static_assert(offsetof(VmaAllocationInfo, pName) == 48, "pName offset");
static_assert(sizeof(VmaAllocationInfo2) == 72, "VmaAllocationInfo2 size");
static_assert(sizeof(VmaBudget) == 40, "VmaBudget size");
static_assert(sizeof(VmaPoolCreateInfo) == 56, "VmaPoolCreateInfo size");
static_assert(offsetof(VmaPoolCreateInfo, priority) == 32, "pool priority offset");
static_assert(offsetof(VmaPoolCreateInfo, minAllocationAlignment) == 40, "pool alignment offset");
static_assert(offsetof(VmaPoolCreateInfo, pMemoryAllocateNext) == 48, "pool pNext offset");
static_assert(sizeof(VmaDetailedStatistics) == 64, "VmaDetailedStatistics size");
static_assert(sizeof(VmaTotalStatistics) == 3136, "VmaTotalStatistics size");
static_assert(offsetof(VmaTotalStatistics, memoryHeap) == 2048, "total heap stats offset");
static_assert(offsetof(VmaTotalStatistics, total) == 3072, "total aggregate stats offset");
static_assert(sizeof(VmaDefragmentationInfo) == 48, "VmaDefragmentationInfo size");
static_assert(offsetof(VmaDefragmentationInfo, pool) == 8, "defrag pool offset");
static_assert(offsetof(VmaDefragmentationInfo, pfnBreakCallback) == 32, "defrag callback offset");
static_assert(sizeof(VmaDefragmentationMove) == 24, "VmaDefragmentationMove size");
static_assert(offsetof(VmaDefragmentationMove, srcAllocation) == 8, "move source offset");
static_assert(sizeof(VmaDefragmentationPassMoveInfo) == 16, "VmaDefragmentationPassMoveInfo size");
static_assert(offsetof(VmaDefragmentationPassMoveInfo, pMoves) == 8, "pass moves offset");
static_assert(sizeof(VmaDefragmentationStats) == 24, "VmaDefragmentationStats size");
static_assert(sizeof(VmaVulkanFunctions) == 224, "VmaVulkanFunctions size");
static_assert(offsetof(VmaVulkanFunctions, vkGetInstanceProcAddr) == 0, "instance proc offset");
static_assert(offsetof(VmaVulkanFunctions, vkGetDeviceProcAddr) == 8, "device proc offset");
static_assert(offsetof(VmaVulkanFunctions, vkGetPhysicalDeviceProperties2KHR) == 216, "final function offset");

int main() { return 0; }
