package net.echonolix.vulkan

import net.echonolix.ktffi.*
import net.echonolix.vulkan.Vk.vkCreateInstance
import net.echonolix.vulkan.enums.VkPhysicalDeviceType
import net.echonolix.vulkan.flags.VkDebugUtilsMessageSeverityFlagsEXT
import net.echonolix.vulkan.flags.VkDebugUtilsMessageTypeFlagsEXT
import net.echonolix.vulkan.flags.VkQueueFlags
import net.echonolix.vulkan.handles.VkInstance
import net.echonolix.vulkan.handles.VkPhysicalDevice
import net.echonolix.vulkan.handles.get
import net.echonolix.vulkan.handles.value
import net.echonolix.vulkan.structs.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions
import org.lwjgl.system.MemoryUtil

private fun populateDebugMessengerCreateInfo(debugCreateInfo: NativeValue<VkDebugUtilsMessengerCreateInfoEXT>) {
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

fun main() {
    MemoryStack {
        // region Init GLFW
        glfwInit()
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API)
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE)
        val width = 800
        val height = 600
        glfwCreateWindow(width, height, "Vulkan", 0, 0)
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

        var graphicsQueueIndex = -1
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
                    && graphicsQueueIndex == -1
                ) {
                    graphicsQueueIndex = it
                }
            }
        }

        val graphicsQueuePriority = NativeFloat.calloc().apply { value = 1f }
        val graphicsQueueCreateInfo = VkDeviceQueueCreateInfo.allocate().apply {
            queueFamilyIndex = graphicsQueueIndex.toUInt()
            queueCount = 1u
            pQueuePriorities = graphicsQueuePriority.ptr()
        }
        val deviceCreateInfo = VkDeviceCreateInfo.allocate().apply {
            pQueueCreateInfos = graphicsQueueCreateInfo.ptr()
            queueCreateInfoCount = 1u
            pEnabledFeatures = physicalDeviceFeatures.ptr()
            // TODO: Add extensions
            enabledExtensionCount = 0u

            enabledLayerCount = layers.size.toUInt()
            ppEnabledLayerNames = layers.c_strs()
        }
        physicalDevice.createDevice(deviceCreateInfo.ptr(), null).getOrThrow()
//        val queue = physicalDevice.

//        device.destroyDevice(null)
        instance.destroyDebugUtilsMessengerEXT(debugUtilsMessenger, null)
        instance.destroyInstance(null)

        glfwTerminate()
    }
}