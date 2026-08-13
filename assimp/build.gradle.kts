import buildsrc.convention.ElementType

plugins {
    id("buildsrc.convention.codegen-c")
}

codegenC {
    packageName.set("net.echonolix.caelum.assimp")
    functionBaseTypeName.set("net.echonolix.caelum.assimp.functions.AssimpFunction")

    val callbackTypes = setOf(
        "aiLogStreamCallback",
        "aiFileWriteProc",
        "aiFileReadProc",
        "aiFileTellProc",
        "aiFileFlushProc",
        "aiFileSeek",
        "aiFileOpenProc",
        "aiFileCloseProc",
    )

    fun typeName(name: String): String = "Ai${name.removePrefix("ai")}"

    elementMapper = { type, name ->
        when (type) {
            ElementType.STRUCT, ElementType.ENUM -> typeName(name)
            ElementType.TYPEDEF -> if (name in callbackTypes) {
                "AiFunc${name.removePrefix("ai")}"
            } else {
                typeName(name)
            }
            ElementType.FUNCTION -> "AssimpFunc${name.removePrefix("ai")}"
            else -> name
        }
    }
}

dependencies {
    ktgenInput(project.layout.projectDirectory.dir("include").asFileTree)
}

tasks.jar {
    from("NOTICE-assimp.txt")
}

tasks.named<Jar>("sourcesJar") {
    from("NOTICE-assimp.txt")
    from("include")
}
