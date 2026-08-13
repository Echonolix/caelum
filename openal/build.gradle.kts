import buildsrc.convention.ElementType

plugins {
    id("buildsrc.convention.codegen-c")
}

codegenC {
    packageName.set("net.echonolix.caelum.openal")
    functionBaseTypeName.set("net.echonolix.caelum.openal.functions.OpenALFunction")

    // Keep dynamic-loader callback types distinct from global C exports.
    // Global functions retain their original al*/alc* symbol names.
    val alFunctions = listOf(
        "Enable", "Disable", "IsEnabled", "DopplerFactor", "DopplerVelocity", "DistanceModel",
        "GetString", "GetBooleanv", "GetIntegerv", "GetFloatv", "GetDoublev", "GetBoolean",
        "GetInteger", "GetFloat", "GetDouble", "GetError", "IsExtensionPresent", "GetProcAddress",
        "GetEnumValue", "Listenerf", "Listener3f", "Listenerfv", "Listeneri", "Listener3i",
        "Listeneriv", "GetListenerf", "GetListener3f", "GetListenerfv", "GetListeneri",
        "GetListener3i", "GetListeneriv", "GenSources", "DeleteSources", "IsSource", "Sourcef",
        "Source3f", "Sourcefv", "Sourcei", "Source3i", "Sourceiv", "GetSourcef", "GetSource3f",
        "GetSourcefv", "GetSourcei", "GetSource3i", "GetSourceiv", "SourcePlay", "SourceStop",
        "SourceRewind", "SourcePause", "SourcePlayv", "SourceStopv", "SourceRewindv", "SourcePausev",
        "SourceQueueBuffers", "SourceUnqueueBuffers", "GenBuffers", "DeleteBuffers", "IsBuffer",
        "BufferData", "Bufferf", "Buffer3f", "Bufferfv", "Bufferi", "Buffer3i", "Bufferiv",
        "GetBufferf", "GetBuffer3f", "GetBufferfv", "GetBufferi", "GetBuffer3i", "GetBufferiv",
        "SpeedOfSound",
    )

    val alcFunctions = listOf(
        "CreateContext", "MakeContextCurrent", "ProcessContext", "SuspendContext", "DestroyContext",
        "GetCurrentContext", "GetContextsDevice", "OpenDevice", "CloseDevice", "GetError",
        "IsExtensionPresent", "GetProcAddress", "GetEnumValue", "GetString", "GetIntegerv",
        "CaptureOpenDevice", "CaptureCloseDevice", "CaptureStart", "CaptureStop", "CaptureSamples",
    )
    val typeDefRename = alFunctions.associate { functionName ->
        "LPAL${functionName.uppercase()}" to "ALFuncPtr$functionName"
    } + alcFunctions.associate { functionName ->
        "LPALC${functionName.uppercase()}" to "ALCFuncPtr$functionName"
    }

    val structRename = mapOf(
        "ALCdevice" to "ALCDevice",
        "ALCcontext" to "ALCContext",
    )

    elementMapper = block@ { type, name ->
        when (type) {
            ElementType.TYPEDEF -> typeDefRename[name] ?: name
            ElementType.FUNCTION -> when {
                name.startsWith("alc") -> "ALCFunc${name.removePrefix("alc")}"
                name.startsWith("al") -> "ALFunc${name.removePrefix("al")}"
                else -> name
            }
            ElementType.STRUCT -> structRename[name]
            else -> name
        }
    }
}

dependencies {
    ktgenInput(project.layout.projectDirectory.dir("codegen").asFileTree)
}

tasks.jar {
    from("NOTICE-openal.txt")
}

tasks.named<Jar>("sourcesJar") {
    from("NOTICE-openal.txt")
    from("include")
    from("codegen")
}
