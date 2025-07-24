package net.echonolix.caelum.vulkan

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

object VulkanCodegen {
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

    val basePkgName = "net.echonolix.caelum.vulkan"
    val enumPackageName = "${basePkgName}.enums"
    val flagPackageName = "${basePkgName}.flags"
    val structPackageName = "${basePkgName}.structs"
    val unionPackageName = "${basePkgName}.unions"
    val handlePackageName = "${basePkgName}.handles"
    val functionPackageName = "${basePkgName}.functions"

    val vkCName = ClassName(basePkgName, "Vk")
    val vkEnumBaseCName = ClassName(basePkgName, "VkEnumBase")
    val vkEnumCName = ClassName(enumPackageName, "VkEnum")
    val vkFlags32CNAME = ClassName(flagPackageName, "VkFlags32")
    val vkFlags64CNAME = ClassName(flagPackageName, "VkFlags64")
    val vkStructCName = ClassName(structPackageName, "VkStruct")
    val vkUnionCName = ClassName(unionPackageName, "VkUnion")
    val vkHandleCName = ClassName(handlePackageName, "VkHandle")
    val vkHandleImplCName = vkHandleCName.nestedClass("Impl")
    val vkFunctionCName = ClassName(functionPackageName, "VkFunction")
    val vkFunctionTypeDescriptorCName = vkFunctionCName.nestedClass("Descriptor")

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
    val vkExceptionCName = ClassName(basePkgName, "VkException")

    val getInstanceFuncMember = MemberName(basePkgName, "getInstanceFunc")
    val getDeviceFuncMember = MemberName(basePkgName, "getDeviceFunc")
    val handleValueMember = MemberName(handlePackageName, "value")
}