package net.echonolix.caelum.vulkan.tasks

import net.echonolix.caelum.codegen.api.task.CaelumCodegenTaskBase
import net.echonolix.caelum.vulkan.VulkanCodegenContext

typealias VulkanCodegenTask<R> = CaelumCodegenTaskBase<VulkanCodegenContext, R>