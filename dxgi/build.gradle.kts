plugins {
    id("buildsrc.convention.published-module")
}

dependencies {
    api(project(":caelum-core"))
    testImplementation(kotlin("test-junit5"))
}

kotlin {
    explicitApi()
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

dependencies {
    extraJarEntries(rootProject.files("README.MD"))
}
