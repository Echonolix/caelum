package buildsrc.convention

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/** Configuration passed to the C++ ktgen processor. */
abstract class CodegenCppExtension {
    /** Kotlin package emitted by the generated binding. */
    abstract val packageName: Property<String>

    /** Stable module stem used for generated shim names. */
    abstract val moduleName: Property<String>

    /** Name of the generated Kotlin native-library object. */
    abstract val kotlinObjectName: Property<String>

    /** C++ Clang executable used for the semantic AST. */
    abstract val clangExecutable: Property<String>

    /**
     * Header search paths shared by semantic parsing and, when the
     * native-library convention is also applied, the native compiler.
     *
     * Put all public-header include paths here rather than configuring the two
     * tools separately. This makes Clang parse the same declarations that the
     * generated shim is compiled against.
     */
    abstract val includeDirs: ConfigurableFileCollection

    /**
     * Public ABI macros shared by semantic parsing and the native compiler.
     *
     * Use [implementationDefines] on `nativeLibrary` only for implementation
     * selection macros (for example `STB_IMAGE_IMPLEMENTATION`) that must not
     * affect the public declarations parsed by Clang.
     */
    abstract val apiDefines: MapProperty<String, String>

    /** Arguments supplied to Clang before the root header path. */
    abstract val compilerArguments: ListProperty<String>

    init {
        packageName.convention("")
        clangExecutable.convention("clang++")
        apiDefines.convention(emptyMap())
        compilerArguments.convention(emptyList())
    }
}
