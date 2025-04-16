package net.echonolix.vulkan

//class VKPhysicalDevice(delegate: VkPhysicalDevice) : VkPhysicalDevice by delegate {
//    constructor(instance: VkInstance) : this(MemoryStack {
//        fun findQueueFamilies(device: VkPhysicalDevice): QueueFamilyIndices {
//            var indices = QueueFamilyIndices()
//
//            MemoryStack {
//                val queueFamilyCount = NativeUInt32.malloc()
//                instance.getPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount.ptr(), null)
//                val queueFamilies = VkQueueFamilyProperties.malloc(queueFamilyCount.value)
//                instance.getPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount.ptr(), queueFamilies.ptr())
//                val presentSupport = VkBool32.calloc()
//
//                var i = 0u
//                while (i < queueFamilyCount.value || !indices.isComplete) {
//                    if (VkQueueFlags.GRAPHICS in queueFamilies[i].queueFlags) {
//                        indices = indices.withGraphicsFamily(i)
//                    }
//
//                    instance.getPhysicalDeviceSurfaceSupportKHR(device, i, vk.surface, presentSupport.ptr())
//
//                    if (presentSupport.value == VK_TRUE) {
//                        indices = indices.withPresentFamily(i)
//                    }
//                    i++
//                }
//
//                return indices
//            }
//        }
//
//        fun querySwapChainSupport(device: VkPhysicalDevice, stack: MemoryStack): SwapChainSupportDetails {
//            MemoryStack {
//                val capabilities = VkSurfaceCapabilitiesKHR.allocate()
//                vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, vk.surface, capabilities)
//
//                val count = stack.ints(0)
//
//                vkGetPhysicalDeviceSurfaceFormatsKHR(device, vk.surface, count, null)
//                check(count.get(0) != 0) { "Failed to find surface formats" }
//                val formats = VkSurfaceFormatKHR.malloc(count.get(0), stack)
//                vkGetPhysicalDeviceSurfaceFormatsKHR(device, vk.surface, count, formats)
//
//                vkGetPhysicalDeviceSurfacePresentModesKHR(device, vk.surface, count, null)
//                check(count.get(0) != 0) { "Failed to find present modes" }
//                val presentModes = stack.mallocInt(count.get(0))
//                vkGetPhysicalDeviceSurfacePresentModesKHR(device, vk.surface, count, presentModes)
//
//                return SwapChainSupportDetails(capabilities, formats, presentModes)
//            }
//        }
//
//        fun querySwapChainSupport(stack: MemoryStack): SwapChainSupportDetails {
//            return querySwapChainSupport(handle, stack)
//        }
//
//            fun isDeviceSuitable(device: VkPhysicalDevice): Boolean {
//                val indices = findQueueFamilies(device)
//
//                fun checkDeviceExtensionSupport(device: VkPhysicalDevice?): Boolean {
//                    MemoryStack {
//                        val extensionCount = this.ints(0)
//                        vkEnumerateDeviceExtensionProperties(device!!, null as String?, extensionCount, null)
//
//                        val availableExtensions = VkExtensionProperties.malloc(extensionCount.get(0), this)
//
//                        vkEnumerateDeviceExtensionProperties(device, null as String?, extensionCount, availableExtensions)
//                        return availableExtensions
//                            .map { it.extensionNameString() }
//                            .toSet()
//                            .containsAll(DEVICE_EXTENSIONS)
//                    }
//                }
//
//                val extensionsSupported = checkDeviceExtensionSupport(device)
//                var swapChainAdequate = false
//
//                if (extensionsSupported) {
//                    val swapChainSupport = querySwapChainSupport(device, this)
//                    swapChainAdequate = swapChainSupport.formats.hasRemaining()
//                        && swapChainSupport.presentModes.hasRemaining()
//                }
//
//                return indices.isComplete && extensionsSupported && swapChainAdequate
//            }
//
//            val deviceCount = this.ints(0)
//            vkEnumeratePhysicalDevices(vkInstance, deviceCount, null)
//
//            if (deviceCount.get(0) == 0) {
//                throw RuntimeException("Failed to find GPUs with Vulkan support")
//            }
//
//            val ppPhysicalDevices = this.mallocPointer(deviceCount.get(0))
//            vkEnumeratePhysicalDevices(vkInstance, deviceCount, ppPhysicalDevices)
//
//            for (i in 0..<ppPhysicalDevices.capacity()) {
//                val device = VkPhysicalDevice(ppPhysicalDevices.get(i), vkInstance)
//
//                if (isDeviceSuitable(device)) {
//                    return@MemoryStack device
//                }
//            }
//
//            throw RuntimeException("Failed to find a suitable GPU")
//        })
//}