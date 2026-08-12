plugins {
    id("buildsrc.convention.published-module")
}

dependencies {
    api(project(":caelum-core"))
    api(project(":caelum-vulkan"))
    api(project(":caelum-glfw"))
}

kotlin {
    explicitApi()
}
