package buildsrc.convention

plugins {
    id("buildsrc.convention.published-module")
    id("net.echonolix.ktgen")
}

val codegenCExtension = extensions.create("codegenC", CodegenCExtension::class.java)

dependencies {
    ktgen(project(":codegen-c"))
    implementation(kotlin("reflect"))
    api(project(":caelum-core"))
}

tasks.ktgen {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("codegenc.packageName", codegenCExtension.packageName.get())
    systemProperty("codegenc.excludeConsts", codegenCExtension.excludedConsts.get().joinToString(","))
}