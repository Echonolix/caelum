package net.echonolix.caelum.vulkan.ctx

import net.echonolix.caelum.codegen.api.CElement
import net.echonolix.caelum.codegen.api.CTopLevelConst
import net.echonolix.caelum.codegen.api.CType
import net.echonolix.caelum.codegen.api.ctx.CodegenOutput
import net.echonolix.caelum.vulkan.VulkanCodegen
import java.nio.file.Path

class VulkanCodegenOutput(outputDir: Path) : CodegenOutput.Base(outputDir, VulkanCodegen.basePkgName) {
    override fun resolvePackageName(element: CElement): String {
        return when (element) {
            is CType.FunctionPointer, is CType.Function -> VulkanCodegen.functionPackageName
            is CType.Enum -> VulkanCodegen.enumPackageName
            is CType.Bitmask -> VulkanCodegen.flagPackageName
            is CType.Struct -> VulkanCodegen.structPackageName
            is CType.Union -> VulkanCodegen.unionPackageName
            is CType.Handle -> VulkanCodegen.handlePackageName
            is CType.EnumBase.Entry -> throw IllegalStateException("Entry should not be resolved")
            is CType.TypeDef -> VulkanCodegen.basePkgName
            is CTopLevelConst -> VulkanCodegen.basePkgName
            else -> throw IllegalArgumentException("Unsupported element: $element")
        }
    }
}
