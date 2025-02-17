includeBuild("../ktgen") {
    dependencySubstitution {
        substitute(module("org.echonolix:ktgen-api")).using(project(":api"))
        substitute(module("org.echonolix:ktgen-runtime")).using(project(":runtime"))
    }
}

pluginManagement {
    includeBuild("../ktgen")
}

rootProject.name = "ktffi"

include(":core:codegen")

listOf(":core").forEach {
    include(it)
    project(it).name = rootProject.name + it.replace(":", "-")
}

include(":vkffi")