package net.echonolix.vulkan

import net.echonolix.ktffi.*
import net.echonolix.vulkan.flags.VkDebugUtilsMessageSeverityFlagsEXT
import net.echonolix.vulkan.flags.VkDebugUtilsMessageTypeFlagsEXT
import net.echonolix.vulkan.functions.VkFuncPtrDebugUtilsMessengerCallbackEXT
import net.echonolix.vulkan.handles.VkInstance
import net.echonolix.vulkan.structs.*

private fun populateDebugMessengerCreateInfo(debugCreateInfo: NativeValue<VkDebugUtilsMessengerCreateInfoEXT>) {
    debugCreateInfo.messageSeverity = VkDebugUtilsMessageSeverityFlagsEXT.VERBOSE_EXT +
        VkDebugUtilsMessageSeverityFlagsEXT.WARNING_EXT +
        VkDebugUtilsMessageSeverityFlagsEXT.ERROR_EXT

    debugCreateInfo.messageType = VkDebugUtilsMessageTypeFlagsEXT.GENERAL_EXT +
        VkDebugUtilsMessageTypeFlagsEXT.VALIDATION_EXT +
        VkDebugUtilsMessageTypeFlagsEXT.PERFORMANCE_EXT

    debugCreateInfo.pfnUserCallback =
        VkFuncPtrDebugUtilsMessengerCallbackEXT.toNativeData { messageSeverity, messageType, pCallbackData, pUserData ->
            System.err.println("Validation layer: " + pCallbackData.pMessage.string)
            VK_FALSE
        }
}

fun main() {
    MemoryStack {
        val VALIDATION_LAYERS = setOf("VK_LAYER_KHRONOS_validation")

        val appInfo = VkApplicationInfo.allocate()
        appInfo.pApplicationName = "Hello Vulkan".c_str()
        appInfo.applicationVersion = VkApiVersion(0u, 1u, 0u, 0u).value
        appInfo.pEngineName = "Echonolix".c_str()
        appInfo.engineVersion = VkApiVersion(0u, 1u, 0u, 0u).value
        appInfo.apiVersion = VK_API_VERSION_1_0.value

        val createInfo = VkInstanceCreateInfo.allocate()
        createInfo.pApplicationInfo = appInfo.ptr()

        createInfo.ppEnabledLayerNames = VALIDATION_LAYERS.c_strs()
        val debugCreateInfo = VkDebugUtilsMessengerCreateInfoEXT.allocate()
        populateDebugMessengerCreateInfo(debugCreateInfo)
        createInfo.pNext = debugCreateInfo.ptr()

        val instancePtr = VkInstance.mallocArr(1)
        VkGlobalCommands.vkCreateInstance(createInfo.ptr(), nullptr(), instancePtr.ptr())
    }
}