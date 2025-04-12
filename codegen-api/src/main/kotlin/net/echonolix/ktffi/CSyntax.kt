package net.echonolix.ktffi

object CSyntax {
    val nameRegex = """[a-zA-Z_][a-zA-Z0-9_]*""".toRegex()
    val arrayRegex = """\[\s*(?:${nameRegex}|\d+?|)\s*]""".toRegex()
    val pointerRegex = """(?:const\s*)?\*""".toRegex()
    val pointerOrArrayRegex = """(?:(?:${arrayRegex.pattern})|(?:${pointerRegex.pattern}))""".toRegex()
    val typeRegex = """(?:(?:const|struct|union)\s+)*(${nameRegex.pattern}(?:\s*${pointerOrArrayRegex}\s*)*)""".toRegex()
    val typeDefRegex = """\s*typedef\s+${typeRegex.pattern}\s+(${nameRegex.pattern})\s*;""".toRegex()
    val funcPointerHeaderRegex =
        """\s*typedef\s+${typeRegex.pattern}\s+\(VKAPI_PTR\s*\*\s+(${nameRegex.pattern})\s*\)\((?:void\);)?""".toRegex()
    val funcPointerParamSplitRegex = """\s*(?:,|\);)\s*""".toRegex()
    val funcPointerParameterRegex =
        """\s*${typeRegex.pattern}\s+(${nameRegex.pattern})\s*""".toRegex()
}