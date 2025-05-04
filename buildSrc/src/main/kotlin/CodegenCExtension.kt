package buildsrc.convention

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class CodegenCExtension {
    abstract val packageName: Property<String>
    abstract val excludedConsts: ListProperty<String>
}