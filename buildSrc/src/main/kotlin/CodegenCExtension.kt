package buildsrc.convention

import org.gradle.api.provider.Property

abstract class CodegenCExtension {
    abstract val packageName: Property<String>
}