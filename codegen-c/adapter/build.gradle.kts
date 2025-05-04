plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":codegen-c:c-ast"))
}