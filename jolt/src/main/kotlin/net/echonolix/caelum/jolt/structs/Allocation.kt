@file:Suppress("NOTHING_TO_INLINE")

package net.echonolix.caelum.jolt.structs

import net.echonolix.caelum.*
import net.echonolix.caelum.jolt.*
import net.echonolix.caelum.jolt.enums.JoltMotionType
import net.echonolix.caelum.jolt.functions.cj_body_create_info_init
import net.echonolix.caelum.jolt.functions.cj_world_config_init

/** Initializes every byte and the upstream defaults; does not rely on allocator.calloc. */
public fun JoltBodyCreateInfo.allocate(allocator: AllocateScope, block: NValue<JoltBodyCreateInfo>.() -> Unit = {}): NValue<JoltBodyCreateInfo> {
    JoltRuntime.load()
    return allocator.malloc(this).also { checkJolt("initialize body settings", cj_body_create_info_init(it.ptr())) }.apply(block)
}
context(allocator: AllocateScope)
public fun JoltBodyCreateInfo.allocate(block: NValue<JoltBodyCreateInfo>.() -> Unit = {}): NValue<JoltBodyCreateInfo> = allocate(allocator, block)

public fun JoltBodyCreateInfo.allocate(allocator: AllocateScope, count: Long): NArray<JoltBodyCreateInfo> {
    require(count >= 0); Math.multiplyExact(count, layout.byteSize())
    JoltRuntime.load()
    return allocator.malloc(this, count).also { array ->
        for (index in 0 until count) checkJolt("initialize body settings array", cj_body_create_info_init(array[index]))
    }
}
context(allocator: AllocateScope)
public fun JoltBodyCreateInfo.allocate(count: Long): NArray<JoltBodyCreateInfo> = allocate(allocator, count)

public fun JoltPhysicsSystemConfig.allocate(allocator: AllocateScope, block: NValue<JoltPhysicsSystemConfig>.() -> Unit = {}): NValue<JoltPhysicsSystemConfig> {
    JoltRuntime.load()
    return allocator.malloc(this).also { checkJolt("initialize world config", cj_world_config_init(it.ptr())) }.apply(block)
}
context(allocator: AllocateScope)
public fun JoltPhysicsSystemConfig.allocate(block: NValue<JoltPhysicsSystemConfig>.() -> Unit = {}): NValue<JoltPhysicsSystemConfig> = allocate(allocator, block)

@JvmName("setVec3")
public fun NPointer<JoltVec3>.set(x: Float, y: Float, z: Float) { this.x = x; this.y = y; this.z = z }
@JvmName("setVec3Value")
public fun NValue<JoltVec3>.set(x: Float, y: Float, z: Float): Unit = ptr().set(x, y, z)
@JvmName("setRVec3")
public fun NPointer<JoltRVec3>.set(x: Double, y: Double, z: Double) { this.x = x; this.y = y; this.z = z }
@JvmName("setRVec3Value")
public fun NValue<JoltRVec3>.set(x: Double, y: Double, z: Double): Unit = ptr().set(x, y, z)
@JvmName("setQuat")
public fun NPointer<JoltQuat>.set(x: Float, y: Float, z: Float, w: Float) { this.x = x; this.y = y; this.z = z; this.w = w }
@JvmName("setQuatValue")
public fun NValue<JoltQuat>.set(x: Float, y: Float, z: Float, w: Float): Unit = ptr().set(x, y, z, w)

public fun JoltVec3.valueOf(allocator: AllocateScope, x: Float, y: Float, z: Float): NValue<JoltVec3> = allocator.malloc(this).apply { set(x, y, z) }
context(allocator: AllocateScope)
public fun JoltVec3.valueOf(x: Float, y: Float, z: Float): NValue<JoltVec3> = valueOf(allocator, x, y, z)
public fun JoltRVec3.valueOf(allocator: AllocateScope, x: Double, y: Double, z: Double): NValue<JoltRVec3> = allocator.malloc(this).apply { set(x, y, z) }
context(allocator: AllocateScope)
public fun JoltRVec3.valueOf(x: Double, y: Double, z: Double): NValue<JoltRVec3> = valueOf(allocator, x, y, z)
public fun JoltQuat.valueOf(allocator: AllocateScope, x: Float = 0f, y: Float = 0f, z: Float = 0f, w: Float = 1f): NValue<JoltQuat> = allocator.malloc(this).apply { set(x, y, z, w) }
context(allocator: AllocateScope)
public fun JoltQuat.valueOf(x: Float = 0f, y: Float = 0f, z: Float = 0f, w: Float = 1f): NValue<JoltQuat> = valueOf(allocator, x, y, z, w)

public var NValue<JoltBodyCreateInfo>.motion: JoltMotionType
    get() = JoltMotionType.fromNativeData(motionType)
    set(value) { motionType = value.value }
public var NValue<JoltBodyCreateInfo>.layer: JoltObjectLayer
    get() = JoltObjectLayer(objectLayer)
    set(value) { objectLayer = value.bits }

/** Safe count multiplication for caller-owned batch output. Values are written by native calls. */
public fun JoltBodyTransform.allocate(allocator: AllocateScope, count: Long): NArray<JoltBodyTransform> {
    require(count >= 0); Math.multiplyExact(count, layout.byteSize())
    return allocator.malloc(this, count)
}
context(allocator: AllocateScope)
public fun JoltBodyTransform.allocate(count: Long): NArray<JoltBodyTransform> = allocate(allocator, count)
