package buildsrc.convention

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":codegen-api"))
    implementation(project(":caelum-core"))
}