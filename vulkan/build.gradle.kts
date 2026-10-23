import org.jetbrains.kotlin.gradle.utils.extendsFrom

plugins {
    id("buildsrc.convention.published-module")
    id("net.echonolix.ktgen")
}

ktgen {
    defaultCompile.set(false)
}

val baseSrc = sourceSets.create("baseSrc") {
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

val enums = sourceSets.create("enums") {
    setup(enumsBase)
}

val objectHandles = sourceSets.create("objectHandles") {
    setup(enums)
}

val groupsBase = (0..7).map {
    sourceSets.create("groups$it") {
        setup(objectHandles)
    }
}

val groups = sourceSets.create("groups") {
    setup(groupsBase)
}

val objectBase = sourceSets.create("objectBase") {
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
    kotlin.srcDir(ktgen.outputDir.file(name))
    configurations.named(implementationConfigurationName)
        .extendsFrom(configurations.named(objectBase.apiConfigurationName))
    dependencies {
        testImplementation(objectBase.output)
    }
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
