package net.echonolix.caelum.vulkan.schema

import net.echonolix.caelum.vulkan.VulkanCodegen
import net.echonolix.caelum.vulkan.tryParseXML

class FilteredRegistry(registry: Registry) {
    val raw = registry
    val registryFeatures = registry.features.asSequence()
        .filter { it.api.isEmpty() || it.api.split(',').contains("vulkan") }
        .toList()
    val registryExtensions = registry.extensions.extensions.asSequence()
        .filter { it.platform == null }
        .filter { it.supported != "disabled" }
        .filter { extension -> VulkanCodegen.skippedExtensionPrefix.none { extension.name.startsWith(it) } }
        .toList()
    val registryTypes = registry.types.types.associate { type ->
        val name = type.name ?: type.inner.firstNotNullOf {
            it.tryParseXML<XMLName>()?.value
        }
        name to type.copy(name = name)
    }
    val typeDefTypes = registryTypes.values.asSequence()
        .filter { it.category != Registry.Types.Type.Category.funcpointer }
        .filter { it.category != Registry.Types.Type.Category.bitmask }
        .filter { it.name !in VulkanCodegen.typedefBlackList }
        .filter { it.inner.getOrNull(0)?.contentString?.startsWith("typedef") == true }
        .associateBy { it.name!! }

    val enums = registry.enums.asSequence()
        .filter { it.type != Registry.Enums.Type.constants }
        .associateBy { it.name }

    val enumTypes = registryTypes.asSequence()
        .filter { it.value.category == Registry.Types.Type.Category.enum }
        .associate { it.toPair() }

    val enumsValueTypeName = enums.values.asSequence()
        .filter { it.type == Registry.Enums.Type.enum }
        .flatMap { enumType ->
            enumType.enums.map {
                it.name to enumType
            }
        }
        .toMap()

    val bitmaskTypes = registryTypes.asSequence()
        .filter { it.value.category == Registry.Types.Type.Category.bitmask }
        .associate { it.toPair() }

    val funcPointerTypes = registryTypes.values.asSequence()
        .filter { it.category == Registry.Types.Type.Category.funcpointer }
        .associateBy { it.name!! }

    val structTypes = registryTypes.values.asSequence()
        .filter { it.category == Registry.Types.Type.Category.struct }
        .associateBy { it.name!! }

    val unionTypes = registryTypes.values.asSequence()
        .filter { it.category == Registry.Types.Type.Category.union }
        .associateBy { it.name!! }

    val handleTypes = registryTypes.values.asSequence()
        .filter { it.category == Registry.Types.Type.Category.handle }
        .associateBy { it.name!! }

    val constants = registry.enums.asSequence()
        .filter { it.type == Registry.Enums.Type.constants }
        .flatMap { it.enums }
        .associateBy { it.name }

    val commands = registry.commands.asSequence()
        .flatMap { it.commands }
        .filter { it.api == null || it.api == API.vulkan }
        .associateBy { it.proto?.name ?: it.name }

    val extEnums = (
        registryFeatures.asSequence()
            .flatMap { it.require }
            .flatMap { it.enums } +
            registryExtensions.flatMap { extension ->
                extension.require.asSequence()
                    .flatMap { it.enums }
                    .map { it.copy(extnumber = it.extnumber ?: extension.number.toString()) }
            }
        )
        .filter { it.api == null || it.api == API.vulkan }
        .sortedWith(compareBy {
            it.alias != null || it.value != null
        })
        .associateBy { it.name }

    val typesRequiredBy = (registryFeatures.asSequence().flatMap { feature ->
        feature.require.asSequence()
            .flatMap { it.types }
            .map { it.name to feature.name }
    } + registryExtensions.flatMap { extension ->
        extension.require.asSequence()
            .flatMap { it.types }
            .map { it.name to extension.name }
    })
        .toMap()

    val stuffRequiredBy: Map<String, String>

    init {
        val featureEnums = registryFeatures.asSequence().flatMap { feature ->
            feature.require.asSequence()
                .flatMap { it.enums }
                .map { it to feature.name }
        }
        val extensionEnums = registryExtensions.flatMap { extension ->
            extension.require.asSequence()
                .flatMap { it.enums }
                .map { it.copy(extnumber = it.extnumber ?: extension.number.toString()) }
                .map { it to extension.name }
        }
        val enums = (featureEnums + extensionEnums)
            .filter { (it, _) -> it.api == null || it.api == API.vulkan }
            .map { it.first.name to it.second }

        val featureTypesNCommands = registryFeatures.asSequence().flatMap { feature ->
            feature.require.asSequence()
                .flatMap { require ->
                    require.types.asSequence().map { it.name } + require.commands.asSequence().map { it.name }
                }
                .map { it to feature.name }
        }

        val extensionTypesNCommands = registryExtensions.flatMap { extension ->
            extension.require.asSequence()
                .flatMap { require ->
                    require.types.asSequence().map { it.name } + require.commands.asSequence().map { it.name }
                }
                .map { it to extension.name }
        }

        stuffRequiredBy = (enums + featureTypesNCommands + extensionTypesNCommands).toMap()
    }

    val enumValueOrders = (registry.enums.asSequence()
        .flatMap { it.enums }
        .map { it.name }
        + extEnums.values.asSequence()
        .map { it.name })
        .withIndex()
        .associate { (index, name) ->
            name to index
        }

//    val externalTypeNames =
//        registryTypes.values.asSequence().filter { it.requires?.endsWith(".h") == true }.map { it.name!! }.toSet()
}