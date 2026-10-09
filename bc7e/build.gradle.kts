import buildsrc.convention.ElementType

plugins {
    id("buildsrc.convention.codegen-c")
    id("buildsrc.convention.cross-native")
}

// src/main/ispc/bc7e.ispc is vendored unmodified from
// https://github.com/richgel999/bc7enc_rdo at b9438627eef73a1157e84201b6fa6eb2ffd6d9f0
// (Apache 2.0, Binomial LLC). See README.md in this module.

codegenC {
    packageName.set("net.echonolix.caelum.bc7e")
    elementMapper = { type, name ->
        when (type) {
            ElementType.CONST -> null
            ElementType.FUNCTION -> "Bc7eFunc${name.removePrefix("bc7e_")}"
            else -> name
        }
    }
}

dependencies {
    ktgenInput(files("include/bc7e.h"))
    testImplementation(kotlin("test-junit5"))
}

crossNative {
    libraryName.set("caelum_bc7e")
    ispcSources.from("src/main/ispc/bc7e.ispc")
    // Compile-time layout check against the ISPC-generated header; emits no code.
    cSources.from("src/main/c/bc7e_abi_check.c")
    // Flags recommended by the bc7e.ispc header.
    ispcFlags.addAll("--opt=fast-math", "--opt=disable-assertions")
}

tasks.jar {
    from("licenses")
}

tasks.named<Jar>("sourcesJar") {
    from("licenses")
    from("include")
    from("src/main/ispc")
    from("src/main/c")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
