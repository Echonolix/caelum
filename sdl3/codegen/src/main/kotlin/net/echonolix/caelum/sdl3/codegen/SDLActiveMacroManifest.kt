package net.echonolix.caelum.sdl3.codegen

internal data class SDLActiveMacroDefinition(
    val name: String,
    val expression: String,
)

internal object SDLActiveMacroManifest {
    const val RESOURCE_PATH: String =
        "/net/echonolix/caelum/sdl3/codegen/sdl3-3.4.14-windows-x64-active-macros.tsv"

    val definitions: List<SDLActiveMacroDefinition> by lazy {
        val stream = requireNotNull(SDLActiveMacroManifest::class.java.getResourceAsStream(RESOURCE_PATH)) {
            "Missing pinned SDL active macro manifest: $RESOURCE_PATH"
        }
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filterNot { it.isBlank() || it.startsWith('#') }
                .map { line ->
                    val separator = line.indexOf('\t')
                    require(separator >= 0) { "Invalid SDL active macro manifest row: $line" }
                    SDLActiveMacroDefinition(line.substring(0, separator), line.substring(separator + 1))
                }
                .toList()
        }.also(::validate)
    }

    val publicCandidates: List<SDLActiveMacroDefinition> by lazy {
        definitions.filter { definition ->
            definition.expression.isNotEmpty() && definition.name !in NON_CONSTANT_INFRASTRUCTURE_MACROS
        }
    }

    private fun validate(definitions: List<SDLActiveMacroDefinition>) {
        require(definitions.size == 1_148) {
            "Expected 1,148 pinned SDL object-like macros, found ${definitions.size}"
        }
        require(definitions.map(SDLActiveMacroDefinition::name).toSet().size == definitions.size) {
            "Pinned SDL active macro manifest contains duplicate names"
        }
        require(definitions.count { it.name.startsWith("SDL_") } == 890) {
            "Expected 890 active SDL_* object-like macros"
        }
        require(definitions.count { it.name.startsWith("SDLK_") } == 258) {
            "Expected 258 active SDLK_* object-like macros"
        }
        require(definitions.count { it.expression.isEmpty() } == 63) {
            "Expected 63 empty active SDL object-like macros"
        }
        require(definitions.all { PUBLIC_NAME.matches(it.name) }) {
            "Pinned SDL active macro manifest contains a non-public name"
        }
    }

    // These active definitions shape C declarations or redirect C calls; they are not values.
    private val NON_CONSTANT_INFRASTRUCTURE_MACROS = setOf(
        "SDL_ANALYZER_NORETURN",
        "SDL_ASSERT_FILE",
        "SDL_BeginThreadFunction",
        "SDL_DEPRECATED",
        "SDL_DISABLE_OLD_NAMES",
        "SDL_EndThreadFunction",
        "SDL_FALLTHROUGH",
        "SDL_FILE",
        "SDL_FORCE_INLINE",
        "SDL_FUNCTION",
        "SDL_INLINE",
        "SDL_LINE",
        "SDL_NODISCARD",
        "SDL_NORETURN",
        "SDL_NO_THREAD_SAFETY_ANALYSIS",
        "SDL_PRINTF_FORMAT_STRING",
        "SDL_RESTRICT",
        "SDL_SCANF_FORMAT_STRING",
        "SDL_SCOPED_CAPABILITY",
        "SDL_WINAPI_FAMILY_PHONE",
        "SDL_memcpy",
        "SDL_memmove",
        "SDL_memset",
    )

    private val PUBLIC_NAME = Regex("""SDL(?:K|_[A-Za-z0-9])[A-Za-z0-9_]*""")
}
