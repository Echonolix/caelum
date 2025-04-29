import org.jetbrains.kotlin.gradle.utils.extendsFrom

plugins {
    id("net.echonolix.ktgen")
}

val lwjglVersion = "3.3.6"
val lwjglNatives = "natives-windows"

ktgen {
    defaultCompile.set(false)
}

val baseSrc by sourceSets.creating {
    kotlin.srcDir(ktgen.outputDir.file("baseSrc"))
}

fun SourceSet.setup(parents: List<SourceSet>) {
    kotlin.srcDir(ktgen.outputDir.file(this.name))
    parents.forEach {
        configurations.named(this.implementationConfigurationName)
            .extendsFrom(configurations.named(it.implementationConfigurationName))
    }
    dependencies {
        parents.forEach {
            implementationConfigurationName(it.output)
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

val functionsBase = (0..7).map {
    sourceSets.create("functions$it") {
        setup(groups)
    }
}

val functions by sourceSets.creating {
    setup(functionsBase)
}

val objectBase by sourceSets.creating {
    setup(functions)
}

dependencies {
    ktgen(project("codegen"))

    baseSrc.implementationConfigurationName(kotlin("reflect"))
    baseSrc.implementationConfigurationName(project(":caelum-core"))

    testImplementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    testImplementation("org.lwjgl", "lwjgl", lwjglVersion)
    testImplementation("org.lwjgl", "lwjgl-glfw", lwjglVersion)
    testImplementation("org.lwjgl", "lwjgl-shaderc", lwjglVersion)
    testRuntimeOnly("org.lwjgl", "lwjgl", classifier = lwjglNatives)
    testRuntimeOnly("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)
    testRuntimeOnly("org.lwjgl", "lwjgl-shaderc", classifier = lwjglNatives)
}

afterEvaluate {
    tasks.named(baseSrc.getCompileTaskName("kotlin")).configure {
        dependsOn(tasks.ktgen)
    }
}

val myTask by tasks.registering {
    group = "build"
    dependsOn(objectBase.output)
}

kotlin {
    explicitApi()
}