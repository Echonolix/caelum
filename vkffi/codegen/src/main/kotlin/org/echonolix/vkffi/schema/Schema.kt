package org.echonolix.vkffi.schema

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import nl.adaptivity.xmlutil.util.CompactFragment
import org.echonolix.vkffi.CBasicType

@JvmInline
@Serializable
@XmlSerialName("name")
value class XMLName(val value: String)

@JvmInline
@Serializable
@XmlSerialName("comment")
value class XMLComment(val value: String)

@JvmInline
@Serializable
@XmlSerialName("type")
value class XMLType(val value: String)

@Serializable
data class Registry(
    @XmlElement val comment: String,
    val platforms: Platforms,
    val tags: Tags,
    val types: Types,
    val enums: List<Enums>,
    val commands: List<Commands>,
    val features: List<Feature>,
    val extensions: Extensions,
    val formats: Formats,
) {

    @Serializable
    @XmlSerialName("platforms")
    data class Platforms(
        @XmlValue val platforms: List<Platform>,
        val comment: String,
    ) {
        @Serializable
        data class Platform(
            val name: String,
            val protect: String,
            val comment: String
        )
    }

    @Serializable
    @XmlSerialName("tags")
    data class Tags(
        @XmlValue val tags: List<Tag>,
        val comment: String,
    ) {
        @Serializable
        data class Tag(
            val name: String,
            val author: String,
            val contact: String
        )
    }

    @Serializable
    @XmlSerialName("types")
    data class Types(
        val comment: String,
        val types: List<Type>,
        @XmlSerialName("comment") val comments: List<String>,
    ) {
        @Serializable
        @XmlSerialName("type")
        data class Type(
            val name: String?,
            @XmlElement(false) val api: API? = null,
            val requires: String? = null,
            @XmlElement(false) val category: Category? = null,
            val deprecated: Boolean = false,
            val comment: String? = null,
            val alias: String? = null,
            val bitvalues: String? = null,
            val objtypeenum: String? = null,
            val parent: String? = null,
            val returnedonly: Boolean? = null,
            val structextends: String? = null,
            val allowduplicate: Boolean? = null,
            @XmlValue
            val inner: List<CompactFragment> = emptyList()

        ) {
            fun fix(): Type {
                val name = name ?: inner.asSequence()
                    .mapNotNull { runCatching { XML.decodeFromString<XMLName>(it.contentString) }.getOrNull() }
                    .first().value
                val comment = inner.asSequence()
                    .mapNotNull { runCatching { XML.decodeFromString<XMLComment>(it.contentString) }.getOrNull() }
                    .firstOrNull()?.value
                return this.copy(name = name, comment = comment)
            }

            enum class Category {
                include,
                define,
                basetype,
                bitmask,
                handle,
                enum,
                funcpointer,
                struct,
                union,
            }
        }
    }

    @Serializable
    @XmlSerialName("enums")
    data class Enums(
        val name: String,
        @XmlElement(false) val type: Type,
        val bitwidth: Int?,
        val comment: String?,
        val enums: List<Enum>,
        @XmlValue
        val other: List<CompactFragment> = emptyList()
    ) {
        @Serializable
        @XmlSerialName("enum")
        data class Enum(
            val name: String,
            @XmlElement(false) val api: API?,
            @XmlElement(false) val type: CBasicType?,
            val value: String?,
            val protect: String?,
            val extends: String?,
            val extnumber: String?,
            val offset: String?,
            val bitpos: Int?,
            val alias: String?,
            val dir: String?,
            val deprecated: String?,
            val comment: String?
        )

        enum class Type {
            constants,
            enum,
            bitmask
        }
    }

    @Serializable
    @XmlSerialName("commands")
    data class Commands(
        val name: String?,
        val comment: String?,
        val commands: List<Command>,
    ) {
        @Serializable
        @XmlSerialName("command")
        data class Command(
            val name: String?,
            val alias: String?,
            @XmlElement(false) val api: API? = null,
            val successcodes: String?,
            val errorcodes: String?,
            val proto: Proto?,
            val params: List<Param>,
            val implicitexternsyncparams: ImplicitExternSyncParams?,
            @XmlElement(false) val queues: String?,
            @XmlElement(false) val renderpass: RenderPass? = null,
            @XmlElement(false) val cmdbufferlevel: String?,
            @XmlElement(false) val tasks: String?,
            @XmlElement(false) val videocoding: VideoCoding? = null,
            val comment: String?,
        ) {
            @Serializable
            @XmlSerialName("proto")
            data class Proto(
                @XmlElement val type: String,
                @XmlElement val name: String
            )

            @Serializable
            @XmlSerialName("implicitexternsyncparams")
            data class ImplicitExternSyncParams(
                @XmlValue
                val param: Param
            )

            @Serializable
            @XmlSerialName("param")
            data class Param(
                @XmlElement(false) val api: API? = null,
                val optional: Boolean = false,
                val externsync: Boolean = false,
                val len: String? = null,
                val noautovalidity: Boolean = false,
                val stride: String? = null,
                val objecttype: String? = null,
                val altlen: String? = null,
                val validstructs: String? = null,
                @XmlValue
                val inner: List<CompactFragment> = emptyList()
            )
        }
    }

    @Serializable
    @XmlSerialName("feature")
    data class Feature(
        val api: String,
        val name: String,
        val number: String,
        val comment: String,
        val depends: String?,
        val require: List<Require>,
        val remove: List<Remove>,
    ) {
        @Serializable
        @XmlSerialName("require")
        data class Require(
            @XmlElement(false) val api: API?,
            val depends: String?,
            val comment: String?,
            val types: List<Type>,
            val enums: List<Enums.Enum>,
            val commands: List<Command>,
            val features: List<Feature>,
            val comments: List<XMLComment>,
        )

        @Serializable
        @XmlSerialName("remove")
        data class Remove(
            val reasonlink: String?,
            val comment: String?,
            val types: List<Type>,
            val enums: List<Enums.Enum>,
            val commands: List<Command>,
            val features: List<Feature>,
            val comments: List<XMLComment>,
        )

        @Serializable
        @XmlSerialName("type")
        data class Type(
            val name: String,
            val comment: String?
        )

        @Serializable
        @XmlSerialName("command")
        data class Command(
            val name: String,
            val comment: String?
        )

        @Serializable
        @XmlSerialName("feature")
        data class Feature(
            val name: String,
            val struct: String,
        )
    }

    @Serializable
    @XmlSerialName("extensions")
    data class Extensions(
        val comment: String,
        val extensions: List<Extension>,
    ) {
        @Serializable
        @XmlSerialName("extension")
        data class Extension(
            val name: String,
            val number: Int,
            @XmlElement(false) val type: Type?,
            @XmlElement(false) val platform: Platform?,
            @XmlElement(false) val specialuse: String?,
            val sortorder: Int?,
            val promotedto: String?,
            val depends: String?,
            val deprecatedby: String?,
            val obsoletedby: String?,
            val author: String?,
            val contact: String?,
            val supported: String,
            val ratified: String?,
            val nofeatures: Boolean = false,
            val provisional: Boolean = false,
            val require: List<Feature.Require>,
            val remove: List<Feature.Remove>,
            val comment: String?,
        ) {
            enum class Type {
                instance,
                device,
            }

            enum class Platform {
                xlib,
                xcb,
                wayland,
                android,
                win32,
                ggp,
                vi,
                xlib_xrandr,
                ios,
                macos,
                provisional,
                fuchsia,
                metal,
                directfb,
                sci,
                screen
            }
        }
    }

    @Serializable
    @XmlSerialName("formats")
    data class Formats(
        val formats: List<Format>
    ) {
        @Serializable
        @XmlSerialName("format")
        data class Format(
            val name: String,
            val `class`: String,
            val blockSize: Int,
            val texelsPerBlock: Int,
            val packed: Int?,
            @XmlElement(false) val blockExtent: String?,
            val compressed: String?,
            val components: List<Component>,
            val spirvimageformat: SpirvImageFormat?,
            val planes: List<Plane>,
            val chroma: String?,
        ) {
            @Serializable
            @XmlSerialName("component")
            data class Component(
                val name: String,
                val bits: String,
                @XmlElement(false) val numericFormat: NumericFormat,
                val planeIndex: Int?,
            ) {
                enum class NumericFormat {
                    UNORM,
                    SNORM,
                    USCALED,
                    SSCALED,
                    UINT,
                    SINT,
                    SRGB,
                    SFLOAT,
                    UFLOAT,
                    SFIXED5
                }
            }

            @Serializable
            @XmlSerialName("spirvimageformat")
            data class SpirvImageFormat(
                val name: String
            )

            @Serializable
            @XmlSerialName("plane")
            data class Plane(
                val index: Int,
                val widthDivisor: Int,
                val heightDivisor: Int,
                val compatible: String,
            )
        }
    }
}

enum class API {
    vulkan,
    vulkansc,
}

enum class RenderPass {
    both,
    inside,
    outside,
}

enum class Queue {
    sparse_binding,
    graphics,
    compute,
}

enum class VideoCoding {
    both,
    inside,
    outside,
}

enum class Tasks {
    action,
    state,
    synchronization
}