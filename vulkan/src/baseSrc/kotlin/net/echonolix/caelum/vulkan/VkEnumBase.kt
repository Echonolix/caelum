package net.echonolix.caelum.vulkan

import net.echonolix.caelum.NEnum

interface VkEnumBase<T : VkEnumBase<T, N>, N : Any> : NEnum<T, N>