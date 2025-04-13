package net.echonolix.vulkan.ffi

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

    val basePkgName = "net.echonolix.vulkan"
    val enumPackageName = "${basePkgName}.enums"
    val flagPackageName = "${basePkgName}.flags"
    val structPackageName = "${basePkgName}.structs"
    val unionPackageName = "${basePkgName}.unions"
    val funcPointerPackageName = "${basePkgName}.funcptrs"
    val handlePackageName = "${basePkgName}.handles"


    val vkEnumBaseCname = ClassName(basePkgName, "VkEnumBase")
    val vkEnumCname = ClassName(enumPackageName, "VkEnum")
    val vkFlags32CNAME = ClassName(flagPackageName, "VkFlags32")
    val vkFlags64CNAME = ClassName(flagPackageName, "VkFlags64")
    val vkStructCname = ClassName(structPackageName, "VkStruct")
    val vkUnionCname = ClassName(unionPackageName, "VkUnion")

    val methodHandlesCname = MethodHandles::class.asClassName()
    val methodTypeCname = MethodType::class.asClassName()

    val typedefBlackList = setOf(
        "ANativeWindow",
            "AHardwareBuffer",
            "CAMetalLayer",
            "MTLDevice_id",
            "MTLCommandQueue_id",
            "MTLBuffer_id",
            "MTLTexture_id",
            "MTLSharedEvent_id",
            "IOSurfaceRef"
    )

    val skippedExtensionPrefix = setOf(
        "VK_KHR_video_"
    )
    val skippedExtension = setOf(
        ""
    )
}