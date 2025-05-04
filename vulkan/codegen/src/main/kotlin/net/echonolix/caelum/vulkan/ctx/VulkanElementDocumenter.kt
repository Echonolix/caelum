package net.echonolix.caelum.vulkan.ctx

import net.echonolix.caelum.codegen.api.CElement
import net.echonolix.caelum.codegen.api.ctx.CodegenContext
import net.echonolix.caelum.codegen.api.ctx.ElementDocumenter
import net.echonolix.caelum.vulkan.AliasedTag
import net.echonolix.caelum.vulkan.RequiredByTag

class VulkanElementDocumenter : ElementDocumenter.Base() {
    context(ctx: CodegenContext)
    override fun buildKdocStr(sb: StringBuilder, element: CElement) {
        super.buildKdocStr(sb, element)
        val since = element.tags.get<RequiredByTag>()?.requiredBy
        val aliasDst = element.tags.get<AliasedTag>()?.dst
        if (aliasDst != null) {
            sb.appendLine("Alias for [${aliasDst.memberName()}]")
        }
        if (since != null) {
            sb.append("@since: ")
            sb.append(since)
        }
    }
}