package net.echonolix.caelum.glfw.functions

import net.echonolix.caelum.APIHelper
import net.echonolix.caelum.CTypeName
import net.echonolix.caelum.NativeFunction
import net.echonolix.caelum.NativePointer
import net.echonolix.caelum.glfw.structs.GLFWWindow
import net.echonolix.caelum.vulkan.enums.VkResult
import net.echonolix.caelum.vulkan.handles.VkInstanceHandle
import net.echonolix.caelum.vulkan.handles.VkSurfaceKHR
import net.echonolix.caelum.vulkan.structs.VkAllocationCallbacks
import java.lang.foreign.MemorySegment
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import kotlin.reflect.jvm.javaMethod

public val glfwCreateWindowSurface: GLFWFuncCreateWindowSurface =
    GLFWFuncCreateWindowSurface.fromNativeData(APIHelper.findSymbol("glfwCreateWindowSurface"))

public fun interface GLFWFuncCreateWindowSurface : NativeFunction {
    override val typeDescriptor: NativeFunction.TypeDescriptorImpl<GLFWFuncCreateWindowSurface>
        get() = TypeDescriptor

    public operator fun invoke(
        @CTypeName("VkInstance") instance: VkInstanceHandle,
        @CTypeName("GLFWwindow*") window: NativePointer<GLFWWindow>,
        @CTypeName("const VkAllocationCallbacks*") allocator: NativePointer<VkAllocationCallbacks>,
        @CTypeName("VkSurfaceKHR*") surface: NativePointer<VkSurfaceKHR>,
    ): Int

    public fun invokeNative(
        instance: Long,
        window: Long,
        allocator: Long,
        surface: Long,
    ): Int = invoke(
        VkInstanceHandle.fromNativeData(instance),
        NativePointer.fromNativeData(window),
        NativePointer.fromNativeData(allocator),
        NativePointer.fromNativeData(surface),
    )

    public companion object TypeDescriptor : NativeFunction.TypeDescriptorImpl<GLFWFuncCreateWindowSurface>(
        "glfwCreateWindowSurface",
        MethodHandles.lookup().unreflect(GLFWFuncCreateWindowSurface::invokeNative.javaMethod),
        VkResult,
        NativePointer,
        NativePointer,
        NativePointer,
        NativePointer
    ) {
        override fun fromNativeData(value: MemorySegment): GLFWFuncCreateWindowSurface = Impl(downcallHandle(value))

        private class Impl(
            funcHandle: MethodHandle,
        ) : NativeFunction.Impl(funcHandle),
            GLFWFuncCreateWindowSurface {
            override fun invoke(
                instance: VkInstanceHandle,
                window: NativePointer<GLFWWindow>,
                allocator: NativePointer<VkAllocationCallbacks>,
                surface: NativePointer<VkSurfaceKHR>
            ): Int = invokeNative(
                VkInstanceHandle.toNativeData(instance),
                NativePointer.toNativeData(window),
                NativePointer.toNativeData(allocator),
                NativePointer.toNativeData(surface)
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
