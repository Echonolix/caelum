package net.echonolix.caelum.glfw.functions

import net.echonolix.caelum.*
import net.echonolix.caelum.glfw.structs.GLFWWindow
import net.echonolix.caelum.vulkan.VkException
import net.echonolix.caelum.vulkan.enums.VkResult
import net.echonolix.caelum.vulkan.handles.VkInstance
import net.echonolix.caelum.vulkan.handles.VkInstanceHandle
import net.echonolix.caelum.vulkan.handles.VkSurfaceKHR
import net.echonolix.caelum.vulkan.handles.VkSurfaceKHRHandle
import net.echonolix.caelum.vulkan.handles.value
import net.echonolix.caelum.vulkan.structs.VkAllocationCallbacks
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandle

public val glfwCreateWindowSurface: GLFWFuncCreateWindowSurface =
    GLFWFuncCreateWindowSurface.fromNativeData(APIHelper.findSymbol("glfwCreateWindowSurface"))

public fun glfwCreateWindowSurface(
    instance: VkInstance,
    window: NPointer<GLFWWindow>,
    allocator: NPointer<VkAllocationCallbacks>?,
): Result<VkSurfaceKHR> = MemoryStack {
    val handle114514 = VkSurfaceKHRHandle.malloc()
    when (val result69420 = glfwCreateWindowSurface(instance, window, allocator ?: nullptr(), handle114514.ptr())) {
        VkResult.VK_SUCCESS -> Result.success(VkSurfaceKHR.fromNativeData(instance, handle114514.`value`))
        VkResult.VK_ERROR_OUT_OF_HOST_MEMORY,
        VkResult.VK_ERROR_OUT_OF_DEVICE_MEMORY -> Result.failure(VkException(result69420))
        else -> error("""Unexpected result from vkCreateDevice: $result69420""")
    }
}

public fun interface GLFWFuncCreateWindowSurface : NFunction {
    override val typeDescriptor: NFunction.Descriptor<GLFWFuncCreateWindowSurface>
        get() = TypeDescriptor

    public operator fun invoke(
        @CTypeName("VkInstance") instance: VkInstanceHandle,
        @CTypeName("GLFWwindow*") window: NPointer<GLFWWindow>,
        @CTypeName("const VkAllocationCallbacks*") allocator: NPointer<VkAllocationCallbacks>,
        @CTypeName("VkSurfaceKHR*") surface: NPointer<VkSurfaceKHRHandle>,
    ): VkResult

    public fun invokeNative(
        instance: Long,
        window: Long,
        allocator: Long,
        surface: Long,
    ): Int = VkResult.toNativeData(
        invoke(
            VkInstanceHandle.fromNativeData(instance),
            NPointer.fromNativeData(window),
            NPointer.fromNativeData(allocator),
            NPointer.fromNativeData(surface),
        )
    )

    public companion object TypeDescriptor : NFunction.Descriptor<GLFWFuncCreateWindowSurface>(
        "glfwCreateWindowSurface",
        GLFWFuncCreateWindowSurface::class.java,
        VkResult,
        NPointer,
        NPointer,
        NPointer,
        NPointer
    ) {
        override fun fromNativeData(value: MemorySegment): GLFWFuncCreateWindowSurface = Impl(downcallHandle(value))

        private class Impl(
            funcHandle: MethodHandle,
        ) : NFunction.Impl(funcHandle),
            GLFWFuncCreateWindowSurface {
            override fun invoke(
                instance: VkInstanceHandle,
                window: NPointer<GLFWWindow>,
                allocator: NPointer<VkAllocationCallbacks>,
                surface: NPointer<VkSurfaceKHRHandle>
            ): VkResult = VkResult.fromNativeData(
                invokeNative(
                    VkInstanceHandle.toNativeData(instance),
                    NPointer.toNativeData(window),
                    NPointer.toNativeData(allocator),
                    NPointer.toNativeData(surface)
                )
            )

            override fun invokeNative(
                instance: Long,
                window: Long,
                allocator: Long,
                surface: Long,
            ): Int = funcHandle.invokeExact(
                instance,
                window,
                allocator,
                surface
            ) as Int
        }
    }
}
