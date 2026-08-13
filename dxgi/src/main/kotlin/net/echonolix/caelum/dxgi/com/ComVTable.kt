package net.echonolix.caelum.dxgi.com

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle

/** Bounds-checked COM vtable method loading for the native pointer ABI. */
public object ComVTable {
    private val linker: Linker = Linker.nativeLinker()
    private val pointerSize: Long = ValueLayout.ADDRESS.byteSize()

    public fun functionAddress(
        instance: MemorySegment,
        type: ComInterface<*>,
        slot: Int,
    ): MemorySegment {
        require(instance.address() != 0L) { "Cannot read ${type.name} vtable from a null pointer" }
        require(slot in 0 until type.vtableSize) {
            "${type.name} vtable slot $slot is outside 0..<${type.vtableSize}"
        }
        val instanceMemory = instance.reinterpret(pointerSize)
        val vtable = instanceMemory.get(ValueLayout.ADDRESS, 0L)
        check(vtable.address() != 0L) { "${type.name} has a null vtable pointer" }
        val function = vtable.reinterpret(Math.multiplyExact(type.vtableSize.toLong(), pointerSize))
            .get(ValueLayout.ADDRESS, Math.multiplyExact(slot.toLong(), pointerSize))
        check(function.address() != 0L) { "${type.name} vtable slot $slot is null" }
        return function
    }

    public fun method(
        instance: MemorySegment,
        type: ComInterface<*>,
        slot: Int,
        descriptor: FunctionDescriptor,
    ): MethodHandle = linker.downcallHandle(functionAddress(instance, type, slot), descriptor)
}
