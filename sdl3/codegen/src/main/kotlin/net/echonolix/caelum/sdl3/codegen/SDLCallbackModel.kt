package net.echonolix.caelum.sdl3.codegen

internal data class SDLCallbackRegistry(
    val callbacks: List<SDLCallback>,
    val unsupported: List<SDLUnsupportedCallback>,
    val erasures: List<SDLCallbackPointeeErasure>,
) {
    val supportedNames: Set<String> = callbacks.mapTo(linkedSetOf(), SDLCallback::name)
    val unsupportedNames: Set<String> = unsupported.mapTo(linkedSetOf(), SDLUnsupportedCallback::name)
}

internal data class SDLCallbackPointeeErasure(
    val callbackName: String,
    val parameterName: String,
    val nativeType: String,
    val exposedType: String,
    val reason: String,
)

internal data class SDLCallback(
    val name: String,
    val returnType: SDLType,
    val parameters: List<SDLParameter>,
    val declaration: String,
)

internal data class SDLUnsupportedCallback(
    val name: String,
    val reason: String,
)
