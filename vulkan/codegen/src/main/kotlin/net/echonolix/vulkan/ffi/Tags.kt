package net.echonolix.vulkan.ffi

import net.echonolix.ktffi.CConst
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.Tag

class BitWidthTag(val width: Int): Tag<BitWidthTag.Key> {
    companion object Key : Tag.Key
}

class LenTag(val len: String): Tag<LenTag.Key> {
    companion object Key : Tag.Key
}

class LineCommentTag(val comment: String): Tag<LineCommentTag.Key> {
    companion object Key : Tag.Key
}

class ElementCommentTag(val comment: String): Tag<ElementCommentTag.Key> {
    companion object Key : Tag.Key
}

class RequiredByTag(val requiredBy: String): Tag<RequiredByTag.Key> {
    companion object Key : Tag.Key
}

class ReturnCodeTag(val successCodes: List<CType.EnumBase.Entry>, val errorCodes: List<CType.EnumBase.Entry>): Tag<ReturnCodeTag.Key> {
    companion object Key : Tag.Key
}