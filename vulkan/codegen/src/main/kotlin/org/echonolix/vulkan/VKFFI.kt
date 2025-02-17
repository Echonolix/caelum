package org.echonolix.vulkan

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

object VKFFI {
    const val VK_EXT_ENUM_BASE = 1000000000
    const val VK_EXT_ENUM_BLOCKSIZE = 1000

    val VENDOR_TAGS = setOf(
        "IMG",
        "AMD",
        "AMDX",
        "ARM",
        "FSL",
        "BRCM",
        "NXP",
        "NV",
        "NVX",
        "VIV",
        "VSI",
        "KDAB",
        "ANDROID",
        "CHROMIUM",
        "FUCHSIA",
        "GGP",
        "GOOGLE",
        "QCOM",
        "LUNARG",
        "NZXT",
        "SAMSUNG",
        "SEC",
        "TIZEN",
        "RENDERDOC",
        "NN",
        "MVK",
        "KHR",
        "KHX",
        "EXT",
        "MESA",
        "INTEL",
        "HUAWEI",
        "VALVE",
        "QNX",
        "JUICE",
        "FB",
        "RASTERGRID",
        "MSFT",
        "SHADY",
        "FREDEMMOTT"
    )

    val ignoredVendor = setOf(
        "ANDROID", "HUAWEI"
    )

    val packageName = "org.echonolix.vulkan"
    val enumPackageName = "$packageName.enums"
    val vkEnumBaseCname = ClassName(enumPackageName, "VkEnumBase")
    val vkEnumsCname = ClassName(enumPackageName, "VkEnums")
    val vkFlags32CNAME = ClassName(enumPackageName, "VkFlags")
    val vkFlags64CNAME = ClassName(enumPackageName, "VkFlags64")

    val structPackageName = "$packageName.structs"
    val vkStructCname = ClassName(structPackageName, "VkStruct")

    val unionPackageName = "$packageName.unions"
    val vkUnionCname = ClassName(unionPackageName, "VkUnion")

    val methodHandlesCname = MethodHandles::class.asClassName()
    val methodTypeCname = MethodType::class.asClassName()

    val skippedExtension = setOf(
        "VK_KHR_win32_surface",
            "VK_KHR_xlib_surface",
            "VK_KHR_xcb_surface",
            "VK_FUCHSIA_imagepipe_surface",
            "VK_GGP_stream_descriptor_surface",
            "VK_NV_external_memory_sci_buf",
            "VK_KHR_external_memory_win32",
            "VK_FUCHSIA_external_memory",
            "VK_KHR_external_semaphore_win32",
            "VK_FUCHSIA_external_semaphore",
            "VK_KHR_external_fence_win32",
            "VK_NV_external_sci_sync2",
            "VK_GGP_frame_token",
            "VK_EXT_full_screen_exclusive",
            "VK_KHR_video_decode_h264",
            "VK_KHR_video_decode_h265",
            "VK_KHR_video_decode_av1",
            "VK_KHR_video_encode_h264",
            "VK_KHR_video_encode_h265",
            "VK_KHR_video_encode_av1",
            "VK_FUCHSIA_buffer_collection",
            "VK_EXT_metal_objects",
        "VK_KHR_video_encode_quantization_map",
        "VK_NV_external_memory_win32",
        "VK_NV_external_sci_sync"
    )
}