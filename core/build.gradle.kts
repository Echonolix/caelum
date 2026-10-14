plugins {
    id("buildsrc.convention.published-module")
    id("net.echonolix.ktgen")
}

dependencies {
    ktgen(project("codegen"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
    enableAssertions = true
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

kotlin {
    explicitApi()
}

dependencies {
    extraJarEntries(rootProject.files("README.MD"))
}
