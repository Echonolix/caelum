
package net.echonolix.vulkan.ffi

import net.echonolix.ktffi.CType

open class VkHandle(name: String, val parent: VkHandle?, val objectTypeEnum: CType.EnumBase.Entry) : CType.Handle(name) {
    override fun toString(): String {
        return "VkHandle $name(parent=${parent?.name}, objectTypeEnum=${objectTypeEnum.name})"
    }
    override fun toSimpleString(): String {
        return "VkHandle $name"
    }
}
class VkDispatchableHandle(name: String, parent: VkHandle?, objectTypeEnum: EnumBase.Entry) : VkHandle(name, parent, objectTypeEnum) {
    override fun toString(): String {
        return "VkDispatchableHandle $name(parent=${parent?.name}, objectTypeEnum=${objectTypeEnum.name})"
    }
    override fun toSimpleString(): String {
        return "VkDispatchableHandle $name"
    }
}