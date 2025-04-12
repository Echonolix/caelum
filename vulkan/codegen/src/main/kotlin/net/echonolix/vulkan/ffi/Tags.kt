package net.echonolix.vulkan.ffi

import net.echonolix.ktffi.Tag

class BitWidthTag(val width: Int): Tag<BitWidthTag.Key> {
    companion object Key : Tag.Key
}

class LenTag(val len: String): Tag<LenTag.Key> {
    companion object Key : Tag.Key
}