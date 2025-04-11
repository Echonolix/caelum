package org.echonolix.vulkan.schema

import org.echonolix.vulkan.ffi.tryParseXML

class NewPatchedRegistry(registry: Registry) {
    val raw = registry
    val registryFeatures =
        registry.features.asSequence().filter { it.api.isEmpty() || it.api.split(',').contains("vulkan") }.toList()
    val registryExtensions = registry.extensions.extensions
    val registryEnums = registry.enums
    val registryTypes = registry.types.types.associate { type ->
        val name = type.name ?: type.inner.firstNotNullOf {
            it.tryParseXML<XMLName>()?.value
        }
        name to type.copy(name = name)
    }
    private val registryTypesWithTypeDef =
        registryTypes.values.filter { it.inner.getOrNull(0)?.contentString?.startsWith("typedef") == true }
    val externalTypeNames =
        registryTypes.values.asSequence().filter { it.requires?.endsWith(".h") == true }.map { it.name!! }.toSet()
}