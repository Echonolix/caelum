plugins {
    id("buildsrc.convention.codegen")
}

dependencies {
    implementation(project(":codegen-c:adapter"))
}