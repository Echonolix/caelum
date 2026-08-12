import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

val caelumGroup = providers.gradleProperty("caelumGroup").getOrElse("net.echonolix")
val caelumVersion = providers.gradleProperty("caelumVersion").getOrElse("1.0-SNAPSHOT")
val repositoryUrl = providers.gradleProperty("mavenRepositoryUrl")
    .orElse(providers.environmentVariable("MAVEN_REPOSITORY_URL"))
val repositoryUsername = providers.gradleProperty("mavenRepositoryUsername")
    .orElse(providers.environmentVariable("MAVEN_REPOSITORY_USERNAME"))
val repositoryPassword = providers.gradleProperty("mavenRepositoryPassword")
    .orElse(providers.environmentVariable("MAVEN_REPOSITORY_PASSWORD"))

val moduleDescriptions = mapOf(
    "caelum" to "Idiomatic Kotlin bindings for native APIs built on the Java Foreign Function and Memory API.",
    "caelum-bom" to "Dependency-management BOM for the Caelum modules.",
    "caelum-core" to "Core native types, memory allocation, and FFM utilities for Caelum.",
    "caelum-opengl" to "Generated desktop OpenGL bindings for Caelum.",
    "caelum-vulkan" to "Generated Vulkan bindings and object model for Caelum.",
    "caelum-glfw" to "Generated GLFW windowing and input bindings for Caelum.",
    "caelum-glfw-vulkan" to "GLFW Vulkan integration bindings for Caelum.",
    "caelum-codegen-api" to "Public API used by Caelum binding code generators.",
    "caelum-struct" to "Gradle plugin for generating Caelum native struct implementations.",
)

allprojects {
    group = caelumGroup
    version = caelumVersion
    description = moduleDescriptions[name]
        ?: "Build-time support module for the Caelum native binding toolchain."
}

subprojects {
    val publishedProject = this
    pluginManager.withPlugin("maven-publish") {
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set(publishedProject.name)
                    description.set(publishedProject.description)
                    url.set("https://github.com/Echonolix/caelum")
                    licenses {
                        license {
                            name.set("Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("echonolix")
                            name.set("Echonolix")
                            url.set("https://github.com/Echonolix")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/Echonolix/caelum.git")
                        developerConnection.set("scm:git:ssh://git@github.com/Echonolix/caelum.git")
                        url.set("https://github.com/Echonolix/caelum")
                    }
                }
            }

            if (repositoryUrl.isPresent) {
                repositories.maven {
                    name = "caelum"
                    url = publishedProject.uri(repositoryUrl.get())
                    if (repositoryUsername.isPresent) {
                        credentials {
                            username = repositoryUsername.get()
                            password = repositoryPassword.orNull
                        }
                    }
                }
            }
        }
    }
}
