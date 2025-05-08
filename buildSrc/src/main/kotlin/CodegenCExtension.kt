package buildsrc.convention

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

abstract class CodegenCExtension {
    abstract val packageName: Property<String>
    abstract val excludedIncludes: ListProperty<String>
    abstract val preprocessDefines: MapProperty<String, String>
    var elementMapper: (ElementType, String) -> String? = { _, name -> name }
}