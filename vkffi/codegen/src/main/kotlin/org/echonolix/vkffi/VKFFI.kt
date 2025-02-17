package org.echonolix.vkffi

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

    val packageName = "org.echonolix.vulkan"
    val enumPackageName = "$packageName.enums"
    val vkEnumBaseCName = ClassName(enumPackageName, "VkEnumBase")
    val vkEnumsCName = ClassName(enumPackageName, "VkEnums")
    val vkFlags32CNAME = ClassName(enumPackageName, "VkFlags")
    val vkFlags64CNAME = ClassName(enumPackageName, "VkFlags64")

    val structPackage = "$packageName.structs"
    val vkStructCName = ClassName(structPackage, "VkStruct")
    val vkStructArrayCName = vkStructCName.nestedClass("Array")
}