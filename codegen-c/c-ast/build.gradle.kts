plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":codegen-c:tree-sitter-lang-c"))
}