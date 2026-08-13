plugins {
    `java-platform`
    `maven-publish`
}

dependencies {
    constraints {
        api(project(":caelum-core"))
        api(project(":caelum-opengl"))
        api(project(":caelum-vulkan"))
        api(project(":caelum-glfw"))
        api(project(":caelum-glfw-vulkan"))
        api(project(":caelum-dxgi"))
        api(project(":caelum-directx"))
        api(project(":caelum-assimp"))
        api(project(":caelum-openal"))
        api(project(":caelum-codegen-api"))
        api(project(":caelum-struct"))
    }
}

publishing {
    publications {
        create<MavenPublication>("caelum-bom") {
            from(components["javaPlatform"])
        }
    }
}
