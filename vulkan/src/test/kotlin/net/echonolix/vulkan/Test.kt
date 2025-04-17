package net.echonolix.vulkan

import net.echonolix.ktffi.*
import net.echonolix.vulkan.Vk.vkCreateInstance
import net.echonolix.vulkan.enums.VkColorSpaceKHR
import net.echonolix.vulkan.enums.VkFormat
import net.echonolix.vulkan.enums.VkPhysicalDeviceType
import net.echonolix.vulkan.enums.VkPresentModeKHR
import net.echonolix.vulkan.enums.VkResult
import net.echonolix.vulkan.enums.VkSharingMode
import net.echonolix.vulkan.enums.get
import net.echonolix.vulkan.flags.VkCompositeAlphaFlagsKHR
import net.echonolix.vulkan.flags.VkDebugUtilsMessageSeverityFlagsEXT
import net.echonolix.vulkan.flags.VkDebugUtilsMessageTypeFlagsEXT
import net.echonolix.vulkan.flags.VkImageUsageFlags
import net.echonolix.vulkan.flags.VkQueueFlags
import net.echonolix.vulkan.handles.VkInstance
import net.echonolix.vulkan.handles.VkPhysicalDevice
import net.echonolix.vulkan.handles.VkQueue
import net.echonolix.vulkan.handles.VkSurfaceKHR
import net.echonolix.vulkan.handles.get
import net.echonolix.vulkan.handles.value
import net.echonolix.vulkan.structs.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions
import org.lwjgl.glfw.GLFWVulkan.nglfwCreateWindowSurface
import org.lwjgl.system.MemoryUtil

fun main() {
    MemoryStack {
        // region Init GLFW
        glfwInit()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
        val width = 800
        val height = 600
        val window = glfwCreateWindow(width, height, "Vulkan", 0, 0)
        // endregion

        val layers = setOf("VK_LAYER_KHRONOS_validation")
        val extensions = buildSet {
            val buffer = glfwGetRequiredInstanceExtensions() ?: return@buildSet
            repeat(buffer.capacity()) { add(MemoryUtil.memASCII(buffer.get(it))) }
        } + setOf(VK_EXT_DEBUG_UTILS_EXTENSION_NAME)
        println(extensions)

        val appInfo = VkApplicationInfo.allocate().apply {
            pApplicationName = "Hello Vulkan".c_str()
            applicationVersion = VkApiVersion(0u, 1u, 0u, 0u).value
            pEngineName = "Echonolix".c_str()
            engineVersion = VkApiVersion(0u, 1u, 0u, 0u).value
            apiVersion = VK_API_VERSION_1_0.value
        }

        fun populateDebugMessengerCreateInfo(debugCreateInfo: NativeValue<VkDebugUtilsMessengerCreateInfoEXT>) {
            debugCreateInfo.messageSeverity = VkDebugUtilsMessageSeverityFlagsEXT.VERBOSE_EXT +
                    VkDebugUtilsMessageSeverityFlagsEXT.WARNING_EXT +
                    VkDebugUtilsMessageSeverityFlagsEXT.ERROR_EXT

            debugCreateInfo.messageType = VkDebugUtilsMessageTypeFlagsEXT.GENERAL_EXT +
                    VkDebugUtilsMessageTypeFlagsEXT.VALIDATION_EXT +
                    VkDebugUtilsMessageTypeFlagsEXT.PERFORMANCE_EXT

            debugCreateInfo.pfnUserCallback { messageSeverity, messageType, pCallbackData, pUserData ->
                if (VkDebugUtilsMessageSeverityFlagsEXT.ERROR_EXT in messageSeverity) {
                    System.err.println("Validation layer: " + pCallbackData.pMessage.string)
                } else {
                    println("Validation layer: " + pCallbackData.pMessage.string)
                }
                VK_FALSE
            }
        }

        val debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.allocate()
        populateDebugMessengerCreateInfo(debugCreateInfo)

        val createInfo = VkInstanceCreateInfo.allocate().apply {
            pApplicationInfo = appInfo.ptr()
            ppEnabledExtensionNames = extensions.c_strs()
            enabledExtensionCount = extensions.size.toUInt()
            ppEnabledLayerNames = layers.c_strs()
            enabledLayerCount = layers.size.toUInt()
            pNext = debugCreateInfo.ptr()
        }

        val instancePtr = VkInstance.malloc()
        val result = vkCreateInstance(createInfo.ptr(), nullptr(), instancePtr.ptr())
        println(result.name)
        val instance = VkInstance.fromNativeData(instancePtr.value)
        val debugUtilsMessenger = instance.createDebugUtilsMessengerEXT(debugCreateInfo.ptr(), null).getOrThrow()

        var physicalDeviceHandle = -1L
        run {
            val physicalDeviceCount = NativeUInt32.calloc()
            instance.enumeratePhysicalDevices(physicalDeviceCount.ptr(), null)
            require(physicalDeviceCount.value != 0u) { "No physical device found." }
            val physicalDevices = VkPhysicalDevice.malloc(physicalDeviceCount.value)
            instance.enumeratePhysicalDevices(physicalDeviceCount.ptr(), physicalDevices.ptr())
            repeat(physicalDeviceCount.value.toInt()) {
                val device = VkPhysicalDevice.fromNativeData(instance, physicalDevices[it])
                val property = VkPhysicalDeviceProperties.allocate()
                device.getPhysicalDeviceProperties(property.ptr())
                if (property.deviceType == VkPhysicalDeviceType.DISCRETE_GPU) physicalDeviceHandle = device.handle
            }
        }
        check(physicalDeviceHandle != -1L) { "No suitable physical device found." }
        val physicalDeviceProperties = VkPhysicalDeviceProperties.allocate()
        val physicalDeviceFeatures = VkPhysicalDeviceFeatures.allocate()
        val physicalDevice = VkPhysicalDevice.fromNativeData(instance, physicalDeviceHandle)
        physicalDevice.getPhysicalDeviceProperties(physicalDeviceProperties.ptr())
        physicalDevice.getPhysicalDeviceFeatures(physicalDeviceFeatures.ptr())

        println("Using physical device ${physicalDeviceProperties.deviceName.string}")

        val surfaceHandle = NativeInt64.malloc()
        require(nglfwCreateWindowSurface(instance.handle, window, 0L, surfaceHandle.ptr()._address) == VkResult.VK_SUCCESS.value)
        val surface = VkSurfaceKHR.fromNativeData(instance, surfaceHandle.value)

        var graphicsQueueFamilyIndex = -1
        var presentQueueFamilyIndex = -1
        run {
            val queueFamilyPropertyCount = NativeUInt32.calloc()
            physicalDevice.getPhysicalDeviceQueueFamilyProperties(queueFamilyPropertyCount.ptr(), null)
            val queueFamilyProperties = VkQueueFamilyProperties.allocate(queueFamilyPropertyCount.value)
            physicalDevice.getPhysicalDeviceQueueFamilyProperties(
                queueFamilyPropertyCount.ptr(),
                queueFamilyProperties.ptr()
            )
            repeat(queueFamilyPropertyCount.value.toInt()) {
                val queueFamilyProperty = queueFamilyProperties[it.toLong()]
                if (queueFamilyProperty.queueFlags.contains(VkQueueFlags.VK_QUEUE_GRAPHICS_BIT)
                    && graphicsQueueFamilyIndex == -1) {
                    graphicsQueueFamilyIndex = it
                } else {
                    val isPresentSupported = NativeUInt32.malloc()
                    physicalDevice.getPhysicalDeviceSurfaceSupportKHR(it.toUInt(), surface, isPresentSupported.ptr())
                    if (isPresentSupported.value == 1u && presentQueueFamilyIndex == -1) {
                        presentQueueFamilyIndex = it
                    }
                }
            }
        }

        println("Graphics Queue: $graphicsQueueFamilyIndex, Present Queue: $presentQueueFamilyIndex")

        val queuePriority = NativeFloat.calloc().apply { value = 1f }
        val queueCreateInfos = VkDeviceQueueCreateInfo.allocate(2)
        queueCreateInfos[0].apply {
            queueFamilyIndex = graphicsQueueFamilyIndex.toUInt()
            queueCount = 1u
            pQueuePriorities = queuePriority.ptr()
        }
        queueCreateInfos[1].apply {
            queueFamilyIndex = presentQueueFamilyIndex.toUInt()
            queueCount = 1u
            pQueuePriorities = queuePriority.ptr()
        }
        val deviceExtensions = setOf(VK_KHR_SWAPCHAIN_EXTENSION_NAME)
        val deviceCreateInfo = VkDeviceCreateInfo.allocate().apply {
            pQueueCreateInfos = queueCreateInfos.ptr()
            queueCreateInfoCount = 2u

            pEnabledFeatures = physicalDeviceFeatures.ptr()

            enabledExtensionCount = deviceExtensions.size.toUInt()
            ppEnabledExtensionNames = deviceExtensions.c_strs()

            enabledLayerCount = layers.size.toUInt()
            ppEnabledLayerNames = layers.c_strs()
        }
        val device = physicalDevice.createDevice(deviceCreateInfo.ptr(), null).getOrThrow()
        val graphicsQueue = VkQueue.malloc()
        val presentQueue = VkQueue.malloc()
        device.getDeviceQueue(graphicsQueueFamilyIndex.toUInt(), 0u, graphicsQueue.ptr())
        device.getDeviceQueue(presentQueueFamilyIndex.toUInt(), 0u, presentQueue.ptr())

        data class SwapchainSupportDetails(
            val capabilities: NativeValue<VkSurfaceCapabilitiesKHR>,
            val formats: List<NativePointer<VkSurfaceFormatKHR>>,
            val presentModes: List<VkPresentModeKHR>
        )
        fun VkPhysicalDevice.querySwapchainSupport(): SwapchainSupportDetails {
            val capabilities = VkSurfaceCapabilitiesKHR.allocate()
            getPhysicalDeviceSurfaceCapabilitiesKHR(surface, capabilities.ptr())

            val formatCount = NativeUInt32.malloc()
            getPhysicalDeviceSurfaceFormatsKHR(surface, formatCount.ptr(), null)
            val formatsBuffer = VkSurfaceFormatKHR.allocate(formatCount.value)
            getPhysicalDeviceSurfaceFormatsKHR(surface, formatCount.ptr(), formatsBuffer.ptr())
            val formats = buildList {
                repeat(formatCount.value.toInt()) {
                    add(formatsBuffer[it.toLong()])
                }
            }

            val presentModeCount = NativeUInt32.malloc()
            getPhysicalDeviceSurfacePresentModesKHR(surface, presentModeCount.ptr(), null)
            val presentModesBuffer = VkPresentModeKHR.malloc(presentModeCount.value)
            getPhysicalDeviceSurfacePresentModesKHR(surface, presentModeCount.ptr(), presentModesBuffer.ptr())
            val presentModes = buildList {
                repeat(presentModeCount.value.toInt()) {
                    add(presentModesBuffer[it.toLong()])
                }
            }

            return SwapchainSupportDetails(capabilities, formats, presentModes)
        }

        fun chooseSwapchainFormat(formats: List<NativePointer<VkSurfaceFormatKHR>>) = formats.find {
            it.format == VkFormat.R8G8B8A8_SRGB && it.colorSpace == VkColorSpaceKHR.SRGB_NONLINEAR_KHR
        }

        fun choosePresentMode(modes: List<VkPresentModeKHR>) = modes.find {
            it == VkPresentModeKHR.MAILBOX_KHR
        } ?: VkPresentModeKHR.FIFO_KHR

        fun chooseSwapchainExtent(capabilities: NativeValue<VkSurfaceCapabilitiesKHR>): NativePointer<VkExtent2D> {
            return if (capabilities.currentExtent.width != UInt.MAX_VALUE) capabilities.currentExtent
            else {
                val widthBuffer = IntArray(1)
                val heightBuffer = IntArray(1)
                glfwGetFramebufferSize(window, widthBuffer, heightBuffer)

                val actualExtent = VkExtent2D.allocate().apply {
                    this.width = widthBuffer[0].toUInt()
                        .coerceIn(capabilities.minImageExtent.width, capabilities.maxImageExtent.width)
                    this.height = heightBuffer[0].toUInt()
                        .coerceIn(capabilities.minImageExtent.height, capabilities.maxImageExtent.height)
                }

                actualExtent.ptr()
            }
        }

        val swapchainSupport = physicalDevice.querySwapchainSupport()
        val surfaceFormat = chooseSwapchainFormat(swapchainSupport.formats)!!
        val presentMode = choosePresentMode(swapchainSupport.presentModes)
        val extent = chooseSwapchainExtent(swapchainSupport.capabilities)

        val imageCount = swapchainSupport.capabilities.minImageCount

        val swapchainCreateInfo = VkSwapchainCreateInfoKHR.allocate().apply {
            this.surface = surface
            minImageCount = imageCount
            imageFormat = surfaceFormat.format
            imageColorSpace = surfaceFormat.colorSpace
            imageExtent = extent
            imageArrayLayers = 1u
            imageUsage = VkImageUsageFlags.COLOR_ATTACHMENT

            val queueFamilyIndices = NativeUInt32.malloc(2)
            queueFamilyIndices[0] = graphicsQueueFamilyIndex.toUInt()
            queueFamilyIndices[1] = presentQueueFamilyIndex.toUInt()
            if (graphicsQueueFamilyIndex != presentQueueFamilyIndex) {
                imageSharingMode = VkSharingMode.CONCURRENT
                queueFamilyIndexCount = 2u
                pQueueFamilyIndices = queueFamilyIndices.ptr()
            } else {
                imageSharingMode = VkSharingMode.EXCLUSIVE
            }

            preTransform = swapchainSupport.capabilities.currentTransform
            compositeAlpha = VkCompositeAlphaFlagsKHR.OPAQUE_KHR
            this.presentMode = presentMode
            clipped = 1u
        }
        val swapchain = device.createSwapchainKHR(swapchainCreateInfo.ptr(), null).getOrThrow()


        device.destroySwapchainKHR(swapchain, null)
        device.destroyDevice(null)
        instance.destroySurfaceKHR(surface, null)
        instance.destroyDebugUtilsMessengerEXT(debugUtilsMessenger, null)
        instance.destroyInstance(null)

        glfwTerminate()
    }
}
