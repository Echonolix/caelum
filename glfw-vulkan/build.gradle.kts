plugins {
    id("buildsrc.convention.published-module")
}

dependencies {
    implementation(project(":caelum-core"))
    implementation(project(":caelum-vulkan"))
    api(project(":caelum-glfw"))
}

kotlin {
    explicitApi()
}