package net.echonolix.vulkan

data class QueueFamilyIndices(val graphicsFamily: Int = -1, val presentFamily: Int = -1) {
    val list = listOf(graphicsFamily, presentFamily)

    val isComplete: Boolean
        get() = graphicsFamily != -1 && presentFamily != -1

    fun unique(): IntArray {
        return list.distinct().toIntArray()
    }

    fun array(): IntArray {
        return list.toIntArray()
    }

    fun withPresentFamily(presentFamily: UInt): QueueFamilyIndices {
        return QueueFamilyIndices(graphicsFamily, presentFamily.toInt())
    }

    fun withGraphicsFamily(graphicsFamily: UInt): QueueFamilyIndices {
        return QueueFamilyIndices(graphicsFamily.toInt(), presentFamily)
    }
}