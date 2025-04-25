@file:OptIn(ExperimentalStdlibApi::class)

package net.echonolix.caelum.vulkan

private val addressHexFormat = HexFormat {
    upperCase = true
    number {
        minLength = 16
        prefix = "0x"
    }
}

internal fun Long.toAddressHexString(): String {
    return this.toHexString(addressHexFormat)
}