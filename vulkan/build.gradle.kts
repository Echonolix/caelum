import org.jetbrains.kotlin.gradle.utils.extendsFrom

plugins {
    id("buildsrc.convention.published-module")
    id("net.echonolix.ktgen")
}

ktgen {
    defaultCompile.set(false)
}

val baseSrc by sourceSets.creating {
    kotlin.srcDir(ktgen.outputDir.file("baseSrc"))
}

fun SourceSet.setup(parents: List<SourceSet>) {
    kotlin.srcDir(ktgen.outputDir.file(this.name))
    parents.forEach {
        configurations.named(this.apiConfigurationName)
            .extendsFrom(configurations.named(it.apiConfigurationName))
    }
    dependencies {
        parents.forEach {
            apiConfigurationName(it.output)
        }
    }
}

fun SourceSet.setup(parent: SourceSet) {
    setup(listOf(parent))
}

val enumsBase = (0..7).map {
    sourceSets.create("enums$it") {
        setup(baseSrc)
    }
}

val enums by sourceSets.creating {
    setup(enumsBase)
}

val objectHandles by sourceSets.creating {
    setup(enums)
}

val groupsBase = (0..7).map {
    sourceSets.create("groups$it") {
        setup(objectHandles)
    }
}

val groups by sourceSets.creating {
    setup(groupsBase)
}

val objectBase by sourceSets.creating {
    setup(groups)
}

val allGenSourceSets = listOf(baseSrc) + enumsBase + enums +
    groupsBase + groups + objectHandles +
    objectBase

allGenSourceSets.forEach {
    tasks.named(it.getCompileTaskName("kotlin")) {
        dependsOn(tasks.ktgen)
    }
}

sourceSets.main.configure {
    setup(objectBase)
}

sourceSets.test.configure {
    setup(objectBase)
}

dependencies {
    ktgen(project("codegen"))
    baseSrc.apiConfigurationName(project(":caelum-core"))
}

tasks.jar {
    from(allGenSourceSets.map { it.output })
}

tasks.named<Jar>("sourcesJar") {
    from(allGenSourceSets.map { it.allSource })
}