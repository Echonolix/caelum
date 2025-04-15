package net.echonolix.vulkan.ffi

import net.echonolix.ktffi.CDeclaration
import net.echonolix.ktffi.CType
import net.echonolix.ktffi.Tag

class BitWidthTag(val width: Int) : Tag

class LenTag(val len: String) : Tag

class LineCommentTag(val comment: String) : Tag

class ElementCommentTag(val comment: String) : Tag

class RequiredByTag(val requiredBy: String) : Tag

class ReturnCodeTag(val successCodes: List<CType.EnumBase.Entry>, val errorCodes: List<CType.EnumBase.Entry>) : Tag

class EnumEntryFixedName(val name: String) : Tag

class AliasedTag(val dst: CDeclaration.TopLevel) : Tag

class StructTypeTag(val structType: CType.EnumBase.Entry) : Tag