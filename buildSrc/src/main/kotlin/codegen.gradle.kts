package buildsrc.convention

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":caelum-codegen-api"))
    implementation(project(":caelum-core"))
}