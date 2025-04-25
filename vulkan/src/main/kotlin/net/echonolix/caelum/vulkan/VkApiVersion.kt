package net.echonolix.caelum.vulkan

@JvmInline
public value class VkApiVersion(public val value: UInt) {
    public constructor(
        variant: UInt,
        major: UInt,
        minor: UInt,
        patch: UInt
    ) : this((variant shl 29) or (major shl 22) or (minor shl 12) or patch)

    public val variant: UInt
        get() = value shr 29

    public val major: UInt
        get() = (value shr 22) and 0x7FU

    public val minor: UInt
        get() = (value shr 12) and 0x3FFU

    public val patch: UInt
        get() = value and 0xFFFU
}

public val VK_API_VERSION_1_0: VkApiVersion
    get() = VkApiVersion(0u, 1u, 0u, 0u)

public val VK_API_VERSION_1_1: VkApiVersion
    get() = VkApiVersion(0u, 1u, 1u, 0u)

public val VK_API_VERSION_1_2: VkApiVersion
    get() = VkApiVersion(0u, 1u, 2u, 0u)

public val VK_API_VERSION_1_3: VkApiVersion
    get() = VkApiVersion(0u, 1u, 3u, 0u)

public val VK_API_VERSION_1_4: VkApiVersion
    get() = VkApiVersion(0u, 1u, 4u, 0u)