package net.echonolix.caelum.vulkan

import net.echonolix.caelum.codegen.api.CDeclaration
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.Tag

class BitWidthTag(val width: Int) : Tag

class CountTag(val v: List<CType.Group.Member>) : Tag
class CountedTag(val len: String) : Tag

class LineCommentTag(val comment: String) : Tag

class RequiredByTag(val requiredBy: String) : Tag

class ResultCodeTag(val successCodes: List<CType.EnumBase.Entry>, val errorCodes: List<CType.EnumBase.Entry>) : Tag

class AliasedTag(val dst: CDeclaration.TopLevel) : Tag

class StructTypeTag(val structType: CType.EnumBase.Entry) : Tag

class VkHandleTag(val parent: CType.Handle?, val objectTypeEnum: CType.EnumBase.Entry, val dispatchable: Boolean) : Tag

object OptionalTag : Tag