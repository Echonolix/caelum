package net.echonolix.vulkan.ffi

import com.squareup.kotlinpoet.ClassName

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
    val handlePackageName = "${basePkgName}.handles"
    val functionPackageName = "${basePkgName}.functions"

    val vkEnumBaseCname = ClassName(basePkgName, "VkEnumBase")
    val vkEnumCname = ClassName(enumPackageName, "VkEnum")
    val vkFlags32CNAME = ClassName(flagPackageName, "VkFlags32")
    val vkFlags64CNAME = ClassName(flagPackageName, "VkFlags64")
    val vkStructCname = ClassName(structPackageName, "VkStruct")
    val vkUnionCname = ClassName(unionPackageName, "VkUnion")
    val vkHandleCname = ClassName(handlePackageName, "VkHandle")
    val vkFunctionCname = ClassName(functionPackageName, "VkFunction")
    val vkFunctionTypeDescriptorImplCname = vkFunctionCname.nestedClass("TypeDescriptorImpl")

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


    val vkVersionConstRegex = """VK_API_VERSION_(\d+)_(\d+)""".toRegex()
}