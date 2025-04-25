package net.echonolix.caelum.vulkan

import net.echonolix.caelum.*
import net.echonolix.caelum.vulkan.Vk.vkCreateInstance
import net.echonolix.caelum.vulkan.enums.*
import net.echonolix.caelum.vulkan.flags.*
import net.echonolix.caelum.vulkan.handles.*
import net.echonolix.caelum.vulkan.structs.*
import net.echonolix.caelum.vulkan.unions.VkClearValue
import net.echonolix.caelum.vulkan.unions.color
import net.echonolix.caelum.vulkan.unions.float32
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWVulkan.glfwGetRequiredInstanceExtensions
import org.lwjgl.glfw.GLFWVulkan.nglfwCreateWindowSurface
import org.lwjgl.system.MemoryUtil
import org.lwjgl.util.shaderc.Shaderc

@OptIn(UnsafeAPI::class)
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
        MemoryStack {
            val physicalDeviceCount = NativeUInt32.calloc()
            instance.enumeratePhysicalDevices(physicalDeviceCount.ptr(), null)
            check(physicalDeviceCount.value != 0u) { "No physical device found." }
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
        check(nglfwCreateWindowSurface(instance.handle, window, 0L, surfaceHandle.ptr()._address) == VkResult.VK_SUCCESS.value)
        val surface = VkSurfaceKHR.fromNativeData(instance, surfaceHandle.value)

        var graphicsQueueFamilyIndex = -1
        var presentQueueFamilyIndex = -1
        MemoryStack {
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
        val swapchainExtent = chooseSwapchainExtent(swapchainSupport.capabilities)

        val swapchainCreateInfo = VkSwapchainCreateInfoKHR.allocate().apply {
            this.surface = surface
            minImageCount = swapchainSupport.capabilities.minImageCount
            imageFormat = surfaceFormat.format
            imageColorSpace = surfaceFormat.colorSpace
            imageExtent = swapchainExtent
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

        val swapchainImageCount = NativeUInt32.malloc()
        device.getSwapchainImagesKHR(swapchain, swapchainImageCount.ptr(), null)
        println("Swapchain image count: ${swapchainImageCount.value}")
        val swapchainImages = buildList {
            val buffer = VkImage.malloc(swapchainImageCount.value)
            device.getSwapchainImagesKHR(swapchain, swapchainImageCount.ptr(), buffer.ptr())
            repeat(swapchainImageCount.value.toInt()) {
                add(VkImage.fromNativeData(device, buffer[it]))
            }
        }
        val swapchainImageFormat = surfaceFormat.format

        val swapchainImageViews = buildList {
            repeat(swapchainImageCount.value.toInt()) {
                val createInfo = VkImageViewCreateInfo.allocate().apply {
                    image = swapchainImages[it]
                    viewType = VkImageViewType.`2D`
                    format = swapchainImageFormat
                    components.r = VkComponentSwizzle.IDENTITY
                    components.g = VkComponentSwizzle.IDENTITY
                    components.b = VkComponentSwizzle.IDENTITY
                    components.a = VkComponentSwizzle.IDENTITY
                    subresourceRange.aspectMask = VkImageAspectFlags.COLOR
                    subresourceRange.baseMipLevel = 0u
                    subresourceRange.levelCount = 1u
                    subresourceRange.baseArrayLayer = 0u
                    subresourceRange.layerCount = 1u
                }
                add(device.createImageView(createInfo.ptr(), null).getOrThrow())
            }
        }

        val vertexShaderSourceCode = """
            #version 450

            layout(location = 0) out vec3 fragColor;

            vec2 positions[3] = vec2[](
                vec2(0.0, -0.5),
                vec2(0.5, 0.5),
                vec2(-0.5, 0.5)
            );

            vec3 colors[3] = vec3[](
                vec3(1.0, 0.0, 0.0),
                vec3(0.0, 1.0, 0.0),
                vec3(0.0, 0.0, 1.0)
            );

            void main() {
                gl_Position = vec4(positions[gl_VertexIndex], 0.0, 1.0);
                fragColor = colors[gl_VertexIndex];
            }
        """.trimIndent()
        val fragmentShaderSourceCode = """
            #version 450

            layout(location = 0) in vec3 fragColor;

            layout(location = 0) out vec4 outColor;

            void main() {
                outColor = vec4(fragColor, 1.0);
            }
        """.trimIndent()

        class ShaderCompiler {
            var available = true; private set
            private val compiler = Shaderc.shaderc_compiler_initialize()

            fun compileGlslToSpv(
                source: String,
                kind: Int,
                fileName: String,
                entryPoint: String = "main",
                options: CompilerOptions? = null
            ): CompilationResult {
                checkAvailability()
                org.lwjgl.system.MemoryStack.stackPush().use { stack ->
                    val sourceBuffer = stack.ASCII(source, false) // fuck you
                    val fileNameBuffer = stack.ASCII(fileName)
                    val entryPointBuffer = stack.ASCII(entryPoint)
                    val result = Shaderc.shaderc_compile_into_spv(
                        compiler, sourceBuffer, kind, fileNameBuffer,
                        entryPointBuffer, options?.options ?: 0)
                    return CompilationResult(result)
                }
            }

            private fun checkAvailability() { check(available) }

            fun destroy() {
                Shaderc.shaderc_compiler_release(compiler)
                available = false
            }

            inner class CompilationResult(val handle: Long) {
                val errorMessage = Shaderc.shaderc_result_get_error_message(handle)
                val compilationStatus = Shaderc.shaderc_result_get_compilation_status(handle)
                val numWarnings = Shaderc.shaderc_result_get_num_warnings(handle)
                val numErrors = Shaderc.shaderc_result_get_num_errors(handle)
                val binaryLength = Shaderc.shaderc_result_get_length(handle)
                val binary by lazy { Shaderc.shaderc_result_get_bytes(handle) }
            }
            inner class CompilerOptions {
                var available = true; private set
                val options = Shaderc.shaderc_compile_options_initialize()
                    get() {
                        checkAvailability()
                        return field
                    }

                private fun checkAvailability() { check(available) }

                fun destroy() {
                    Shaderc.shaderc_compile_options_release(options)
                    available = false
                }
            }
        }

        val shaderCompiler = ShaderCompiler()

        val vshCompilationResult = shaderCompiler.compileGlslToSpv(vertexShaderSourceCode, Shaderc.shaderc_vertex_shader, "vert.glsl")
        vshCompilationResult.errorMessage?.let { println(it) }
        val fshCompilationResult = shaderCompiler.compileGlslToSpv(fragmentShaderSourceCode, Shaderc.shaderc_fragment_shader, "frag.glsl")
        fshCompilationResult.errorMessage?.let { println(it) }
        val vshSpv = ByteArray(vshCompilationResult.binary!!.remaining())
            .also { vshCompilationResult.binary!!.get(it) }
        val fshSpv = ByteArray(fshCompilationResult.binary!!.remaining())
            .also { fshCompilationResult.binary!!.get(it) }

        fun VkDevice.createShaderModule(code: ByteArray): VkShaderModule {
            val codeBuffer = NativeInt8.malloc(code.size)
            for (i in code.indices) codeBuffer[i] = code[i]
            val createInfo = VkShaderModuleCreateInfo.allocate().apply {
                codeSize = code.size.toLong()
                pCode = reinterpretCast(codeBuffer.ptr())
            }
            return createShaderModule(createInfo.ptr(), null).getOrThrow()
        }

        val vshModule = device.createShaderModule(vshSpv)
        val fshModule = device.createShaderModule(fshSpv)

        shaderCompiler.destroy()

        val shaderStages = VkPipelineShaderStageCreateInfo.allocate(2)
        shaderStages[0].apply {
            stage = VkShaderStageFlags.VERTEX
            module = vshModule
            pName = "main".c_str()
        }
        shaderStages[1].apply {
            stage = VkShaderStageFlags.FRAGMENT
            module = fshModule
            pName = "main".c_str()
        }

        val vertexInputStateCreateInfo = VkPipelineVertexInputStateCreateInfo.allocate().apply {
            vertexBindingDescriptionCount = 0u
            pVertexBindingDescriptions = nullptr()
            vertexAttributeDescriptionCount = 0u
            pVertexAttributeDescriptions = nullptr()
        }

        val inputAssemblyStateCreateInfo = VkPipelineInputAssemblyStateCreateInfo.allocate().apply {
            topology = VkPrimitiveTopology.TRIANGLE_LIST
            primitiveRestartEnable = 0u
        }

        val viewport = VkViewport.allocate()
        viewport.x = 0.0f
        viewport.y = 0.0f
        viewport.width = swapchainExtent.width.toFloat()
        viewport.height = swapchainExtent.height.toFloat()
        viewport.minDepth = 0.0f
        viewport.maxDepth = 1.0f

        val scissor = VkRect2D.allocate()
        scissor.offset.x = 0
        scissor.offset.y = 0
        scissor.extent = swapchainExtent

        val viewportStateCreateInfo = VkPipelineViewportStateCreateInfo.allocate().apply {
            viewportCount = 1u
            pViewports = viewport.ptr()
            scissorCount = 1u
            pScissors = scissor.ptr()
        }

        val rasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.allocate().apply {
            depthClampEnable = VK_FALSE
            rasterizerDiscardEnable = VK_FALSE
            polygonMode = VkPolygonMode.FILL
            lineWidth = 1f
            cullMode = VkCullModeFlags.NONE
            frontFace = VkFrontFace.CLOCKWISE
            depthBiasEnable = VK_FALSE
            depthBiasConstantFactor = 0.0f // Optional
            depthBiasClamp = 0.0f // Optional
            depthBiasSlopeFactor = 0.0f // Optional
        }

        val multisampleStateCreateInfo = VkPipelineMultisampleStateCreateInfo.allocate().apply {
            sampleShadingEnable = VK_FALSE
            rasterizationSamples = VkSampleCountFlags.`1_BIT`
            minSampleShading = 1.0f // Optional
            pSampleMask = nullptr() // Optional
            alphaToCoverageEnable = VK_FALSE // Optional
            alphaToOneEnable = VK_FALSE // Optional
        }

        val colorBlendAttachmentState = VkPipelineColorBlendAttachmentState.allocate().apply {
            colorWriteMask =
                VkColorComponentFlags.R + VkColorComponentFlags.G + VkColorComponentFlags.B + VkColorComponentFlags.A
            blendEnable = VK_FALSE
            srcColorBlendFactor = VkBlendFactor.ONE // Optional
            dstColorBlendFactor = VkBlendFactor.ZERO // Optional
            colorBlendOp = VkBlendOp.ADD // Optional
            srcAlphaBlendFactor = VkBlendFactor.ONE // Optional
            dstAlphaBlendFactor = VkBlendFactor.ZERO // Optional
            alphaBlendOp = VkBlendOp.ADD // Optional
        }

        val colorBlendState = VkPipelineColorBlendStateCreateInfo.allocate().apply {
            logicOpEnable = VK_FALSE
            logicOp = VkLogicOp.COPY // Optional
            attachmentCount = 1u
            pAttachments = colorBlendAttachmentState.ptr()
            blendConstants[0] = 0.0f // Optional
            blendConstants[1] = 0.0f // Optional
            blendConstants[2] = 0.0f // Optional
            blendConstants[3] = 0.0f // Optional
        }

        val pipelineLayoutCreateInfo = VkPipelineLayoutCreateInfo.allocate().apply {
            setLayoutCount = 0u // Optional
            pSetLayouts = nullptr() // Optional
            pushConstantRangeCount = 0u // Optional
            pPushConstantRanges = nullptr() // Optional
        }

        val pipelineLayout = device.createPipelineLayout(pipelineLayoutCreateInfo.ptr(), null).getOrThrow()

        val colorAttachment = VkAttachmentDescription.allocate().apply {
            format = swapchainImageFormat
            samples = VkSampleCountFlags.`1_BIT`
            loadOp = VkAttachmentLoadOp.CLEAR
            storeOp = VkAttachmentStoreOp.STORE
            stencilLoadOp = VkAttachmentLoadOp.DONT_CARE
            stencilStoreOp = VkAttachmentStoreOp.DONT_CARE
            initialLayout = VkImageLayout.UNDEFINED
            finalLayout = VkImageLayout.PRESENT_SRC_KHR
        }

        val colorAttachmentRef = VkAttachmentReference.allocate().apply {
            attachment = 0u
            layout = VkImageLayout.COLOR_ATTACHMENT_OPTIMAL
        }

        val subpass = VkSubpassDescription.allocate().apply {
            pipelineBindPoint = VkPipelineBindPoint.GRAPHICS
            colorAttachmentCount = 1u
            pColorAttachments = colorAttachmentRef.ptr()
        }

        val subpassDependency = VkSubpassDependency.allocate().apply {
            srcSubpass = VK_SUBPASS_EXTERNAL
            dstSubpass = 0u
            srcStageMask = VkPipelineStageFlags.COLOR_ATTACHMENT_OUTPUT
            srcAccessMask = VkAccessFlags.NONE
            dstStageMask = VkPipelineStageFlags.COLOR_ATTACHMENT_OUTPUT
            dstAccessMask = VkAccessFlags.COLOR_ATTACHMENT_WRITE
        }

        val renderPassCreateInfo = VkRenderPassCreateInfo.allocate().apply {
            attachmentCount = 1u
            pAttachments = colorAttachment.ptr()
            subpassCount = 1u
            pSubpasses = subpass.ptr()
            dependencyCount = 1u
            pDependencies = subpassDependency.ptr()
        }

        val renderPass = device.createRenderPass(renderPassCreateInfo.ptr(), null).getOrThrow()

        val pipelineCreateInfo = VkGraphicsPipelineCreateInfo.allocate().apply {
            stageCount = 2u
            pStages = shaderStages.ptr()
            pVertexInputState = vertexInputStateCreateInfo.ptr()
            pInputAssemblyState = inputAssemblyStateCreateInfo.ptr()
            pViewportState = viewportStateCreateInfo.ptr()
            pRasterizationState = rasterizationStateCreateInfo.ptr()
            pMultisampleState = multisampleStateCreateInfo.ptr()
            pDepthStencilState = nullptr()
            pColorBlendState = colorBlendState.ptr()
            pDynamicState = nullptr()
            layout = pipelineLayout
            this.renderPass = renderPass
            this.subpass = 0u
        }

        val pipeline = device.createGraphicsPipelines(
            VkPipelineCache.fromNativeData(device, 0L), 1u, pipelineCreateInfo.ptr(), null).getOrThrow()

        val swapchainFramebuffers = buildList {
            repeat(swapchainImageViews.size) {
                val attachments = NativeInt64.malloc()
                attachments.value = swapchainImageViews[it].handle
                val framebufferCreateInfo = VkFramebufferCreateInfo.allocate().apply {
                    this.renderPass = renderPass
                    attachmentCount = 1u
                    pAttachments = reinterpretCast(attachments.ptr())
                    this.width = swapchainExtent.width
                    this.height = swapchainExtent.height
                    this.layers = 1u
                }
                add(device.createFramebuffer(framebufferCreateInfo.ptr(), null).getOrThrow())
            }
        }

        val commandPoolCreateInfo = VkCommandPoolCreateInfo.allocate().apply {
            flags = VkCommandPoolCreateFlags.RESET_COMMAND_BUFFER
            queueFamilyIndex = graphicsQueueFamilyIndex.toUInt()
        }

        val commandPool = device.createCommandPool(commandPoolCreateInfo.ptr(), null).getOrThrow()

        val commandBufferAllocateInfo = VkCommandBufferAllocateInfo.allocate().apply {
            this.commandPool = commandPool
            level = VkCommandBufferLevel.PRIMARY
            commandBufferCount = 1u
        }

        val pCommandBuffer = VkCommandBuffer.malloc()
        device.allocateCommandBuffers(commandBufferAllocateInfo.ptr(), pCommandBuffer.ptr())
        val commandBuffer = VkCommandBuffer.fromNativeData(commandPool, pCommandBuffer.value)

        val semaphoreCreateInfo = VkSemaphoreCreateInfo.allocate().apply {}
        val fenceCreateInfo = VkFenceCreateInfo.allocate().apply {
            flags = VkFenceCreateFlags.SIGNALED
        }

        val imageAvailableSemaphore = device.createSemaphore(semaphoreCreateInfo.ptr(), null).getOrThrow()
        val renderFinishedSemaphore = device.createSemaphore(semaphoreCreateInfo.ptr(), null).getOrThrow()
        val inFlightFence = device.createFence(fenceCreateInfo.ptr(), null).getOrThrow()
        val fences = reinterpretCast<VkFence>(NativeInt64.malloc().apply {
            value = inFlightFence.handle
        })

        val clearColor = VkClearValue.malloc().apply {
            color.float32[0] = 0f
            color.float32[1] = 0f
            color.float32[2] = 0f
            color.float32[3] = 1f
        }

        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents()
            MemoryStack {
                device.waitForFences(1u, fences.ptr(), VK_TRUE, ULong.MAX_VALUE)
                device.resetFences(1u, fences.ptr())

                val pImageIndex = NativeUInt32.malloc()
                device.acquireNextImageKHR(
                    swapchain,
                    ULong.MAX_VALUE,
                    imageAvailableSemaphore,
                    VkFence.fromNativeData(device, 0L),
                    pImageIndex.ptr()
                )

                commandBuffer.resetCommandBuffer(VkCommandBufferResetFlags.NONE)

                val imageIndex = pImageIndex.value.toInt()
                val beginInfo = VkCommandBufferBeginInfo.allocate().apply {}
                commandBuffer.beginCommandBuffer(beginInfo.ptr())

                val renderPassInfo = VkRenderPassBeginInfo.allocate().apply {
                    this.renderPass = renderPass
                    this.framebuffer = swapchainFramebuffers[imageIndex]
                    renderArea.offset.apply {
                        x = 0
                        y = 0
                    }
                    renderArea.extent = swapchainExtent
                    clearValueCount = 1u
                    pClearValues = clearColor.ptr()
                }

                commandBuffer.cmdBeginRenderPass(renderPassInfo.ptr(), VkSubpassContents.INLINE)

                commandBuffer.cmdBindPipeline(VkPipelineBindPoint.GRAPHICS, pipeline)
                commandBuffer.cmdDraw(3u, 1u, 0u, 0u)

                commandBuffer.cmdEndRenderPass()

                commandBuffer.endCommandBuffer()

                val waitStages = VkPipelineStageFlags.malloc(1).apply {
                    this[0] = VkPipelineStageFlags.COLOR_ATTACHMENT_OUTPUT
                }
                val waitSemaphores = reinterpretCast<VkSemaphore>(NativeInt64.malloc().apply {
                    value = imageAvailableSemaphore.handle
                })
                val signalSemaphores = reinterpretCast<VkSemaphore>(NativeInt64.malloc().apply {
                    value = renderFinishedSemaphore.handle
                })
                val submitInfo = VkSubmitInfo.allocate().apply {
                    waitSemaphoreCount = 1u
                    pWaitSemaphores = waitSemaphores.ptr()

                    pWaitDstStageMask = waitStages.ptr()

                    commandBufferCount = 1u
                    pCommandBuffers = pCommandBuffer.ptr()

                    signalSemaphoreCount = 1u
                    pSignalSemaphores = signalSemaphores.ptr()
                }
                device.vkQueueSubmit(
                    VkQueue.fromNativeData(device, graphicsQueue.value),
                    1u,
                    submitInfo.ptr(),
                    inFlightFence
                )

                val swapchains = reinterpretCast<VkSwapchainKHR>(NativeInt64.malloc().apply {
                    value = swapchain.handle
                })
                val presentInfo = VkPresentInfoKHR.allocate().apply {
                    waitSemaphoreCount = 1u
                    pWaitSemaphores = signalSemaphores.ptr()

                    swapchainCount = 1u
                    pSwapchains = swapchains.ptr()

                    pImageIndices = pImageIndex.ptr()
                }
                device.vkQueuePresentKHR(VkQueue.fromNativeData(device, presentQueue.value), presentInfo.ptr())
            }
        }
        device.deviceWaitIdle()

        device.destroySemaphore(imageAvailableSemaphore, null)
        device.destroySemaphore(renderFinishedSemaphore, null)
        device.destroyFence(inFlightFence, null)
        device.destroyCommandPool(commandPool, null)
        for (framebuffer in swapchainFramebuffers) {
            device.destroyFramebuffer(framebuffer, null)
        }
        device.destroyPipeline(pipeline, null)
        device.destroyPipelineLayout(pipelineLayout, null)
        device.destroyRenderPass(renderPass, null)
        device.destroyShaderModule(vshModule, null)
        device.destroyShaderModule(fshModule, null)
        for (imageView in swapchainImageViews) {
            device.destroyImageView(imageView, null)
        }
        device.destroySwapchainKHR(swapchain, null)
        device.destroyDevice(null)
        instance.destroySurfaceKHR(surface, null)
        instance.destroyDebugUtilsMessengerEXT(debugUtilsMessenger, null)
        instance.destroyInstance(null)

        glfwTerminate()
    }
}
