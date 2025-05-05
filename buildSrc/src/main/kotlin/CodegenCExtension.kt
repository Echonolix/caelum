package buildsrc.convention

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal

abstract class CodegenCExtension {
    abstract val packageName: Property<String>
    var elementMapper: (ElementType, String) -> String? = { _, name -> name }
}