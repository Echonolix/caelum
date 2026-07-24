plugins {
    id("buildsrc.convention.codegen")
}

dependencies {
    testImplementation(kotlin("test-junit5"))
}

tasks.test {
    useJUnitPlatform()
}
