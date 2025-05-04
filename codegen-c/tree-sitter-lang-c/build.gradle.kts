plugins {
    id("buildsrc.convention.kotlin-jvm")
}

kotlin {
    explicitApi()
}

dependencies {
    api("io.endoqa:tree-sitter:0.0.11")
    api("io.endoqa:libtree_sitter:0.0.11")
}