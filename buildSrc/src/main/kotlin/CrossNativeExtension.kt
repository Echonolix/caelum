package buildsrc.convention

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Inputs of the `cross-native` convention. One shared library named [libraryName]
 * is built for every [NativeTarget] from the same sources and flags.
 */
abstract class CrossNativeExtension @Inject constructor(objects: ObjectFactory) {
    /** Library base name; the file is `<name>.dll` or `lib<name>.so`. */
    abstract val libraryName: Property<String>

    abstract val cSources: ConfigurableFileCollection
    abstract val cppSources: ConfigurableFileCollection

    /**
     * ISPC sources. Each one is compiled for [ispcTargets] with runtime dispatch, and
     * its generated `<name>_ispc.h` is placed on the C/C++ include path.
     */
    abstract val ispcSources: ConfigurableFileCollection

    abstract val includeDirs: ConfigurableFileCollection

    /** Preprocessor defines for C and C++ sources. */
    abstract val defines: MapProperty<String, String>

    private val perTargetDefines = NativeTarget.values().associateWith {
        objects.mapProperty(String::class.java, String::class.java)
    }

    /**
     * Extra defines for one [target], merged over [defines]. Use for platform-specific
     * export macros such as `__declspec(dllexport)` switches.
     */
    fun targetDefines(target: NativeTarget): MapProperty<String, String> = perTargetDefines.getValue(target)

    abstract val cFlags: ListProperty<String>
    abstract val cppFlags: ListProperty<String>
    abstract val ispcFlags: ListProperty<String>
    abstract val linkFlags: ListProperty<String>

    abstract val cStandard: Property<String>
    abstract val cppStandard: Property<String>

    /** Optimization flag for C, C++ and ISPC, for example `-O2`. */
    abstract val optimization: Property<String>

    /** ISPC `--target` list; more than one entry enables runtime ISA dispatch. */
    abstract val ispcTargets: ListProperty<String>

    /** Strip symbols that are not needed for dynamic linking. */
    abstract val strip: Property<Boolean>
}
