package net.echonolix.caelum

public object CSyntax {
    public val nameRegex: Regex = """[a-zA-Z_][a-zA-Z0-9_]*""".toRegex()
    public val arrayRegex: Regex = """\[\s*(?:${nameRegex}|\d+?|)\s*]""".toRegex()
    public val pointerRegex: Regex = """(?:const\s*)?\*""".toRegex()
    public val pointerOrArrayRegex: Regex = """(?:(?:${arrayRegex.pattern})|(?:${pointerRegex.pattern}))""".toRegex()
    public val typeRegex: Regex =
        """(?:(?:const|struct|union)\s+)*(${nameRegex.pattern}(?:\s*${pointerOrArrayRegex}\s*)*)""".toRegex()
    public val typeDefRegex: Regex = """\s*typedef\s+${typeRegex.pattern}\s+(${nameRegex.pattern})\s*;""".toRegex()
    public val funcPointerHeaderRegex: Regex =
        """\s*typedef\s+${typeRegex.pattern}\s+\(VKAPI_PTR\s*\*\s+(${nameRegex.pattern})\s*\)\((?:void\);)?""".toRegex()
    public val funcPointerParamSplitRegex: Regex = """\s*(?:,|\);)\s*""".toRegex()
    public val funcPointerParameterRegex: Regex = """\s*${typeRegex.pattern}\s+(${nameRegex.pattern})\s*""".toRegex()
    public val intLiteralRegex: Regex = """((0x[\dA-F]+|0b[01]+|\d+)(U?(?:L{1,2})?))""".toRegex(RegexOption.IGNORE_CASE)
}