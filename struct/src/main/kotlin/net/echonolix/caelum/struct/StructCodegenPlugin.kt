package net.echonolix.caelum.struct

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer

class StructCodegenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("net.echonolix.ktgen")

        val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer
        val main = sourceSets.getByName("main")
        val structs = sourceSets.create("structs")

        project.dependencies.add(main.implementationConfigurationName, "net.echonolix:caelum-core:${version}")

        project.dependencies.add("ktgen", "net.echonolix:caelum-struct:$version")
        project.dependencies.add("ktgen", structs.output)
        project.dependencies.add("ktgenInput", structs.output)

        project.configurations.getByName(structs.compileClasspathConfigurationName)
            .extendsFrom(project.configurations.getByName(main.compileClasspathConfigurationName))
        val structRuntimeClasspath = project.configurations.getByName(structs.runtimeClasspathConfigurationName)
        structRuntimeClasspath
            .extendsFrom(project.configurations.getByName(main.runtimeClasspathConfigurationName))

        val ktgen = project.configurations.getByName("ktgen")
        ktgen.extendsFrom(structRuntimeClasspath)

        project.afterEvaluate {
            project.tasks.findByName("sourceJar")?.dependsOn("ktgen")
        }
    }

    companion object {
        val version: String = StructCodegenPlugin::class.java.`package`.implementationVersion
    }
}