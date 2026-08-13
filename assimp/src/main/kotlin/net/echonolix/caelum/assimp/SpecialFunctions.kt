package net.echonolix.caelum.assimp

import net.echonolix.caelum.APIHelper
import net.echonolix.caelum.NChar
import net.echonolix.caelum.NFloat
import net.echonolix.caelum.NInt
import net.echonolix.caelum.NPointer
import net.echonolix.caelum.NUInt32
import net.echonolix.caelum.NValue
import net.echonolix.caelum.nullptr
import net.echonolix.caelum.assimp.functions.aiGetMaterialFloatArray
import net.echonolix.caelum.assimp.functions.aiGetMaterialIntegerArray
import net.echonolix.caelum.assimp.structs.AiLogStream
import net.echonolix.caelum.assimp.structs.AiMaterial
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.SegmentAllocator

private val aiGetPredefinedLogStreamHandle by lazy {
    APIHelper.downcallHandleOf(
        "aiGetPredefinedLogStream",
        FunctionDescriptor.of(AiLogStream.layout, NInt.layout, NPointer.layout),
    ) ?: error("Unable to create aiGetPredefinedLogStream downcall handle")
}

/**
 * Calls Assimp's aggregate-returning log-stream factory.
 *
 * The returned value is allocated by [allocator] and remains valid for the
 * allocator's lifetime. This function is handwritten because Java FFM inserts
 * a [SegmentAllocator] parameter for C functions that return a struct by value.
 */
public fun aiGetPredefinedLogStream(
    allocator: SegmentAllocator,
    streams: AiDefaultLogStream,
    file: NPointer<NChar> = nullptr(),
): NValue<AiLogStream> {
    val result = aiGetPredefinedLogStreamHandle.invokeExact(
        allocator,
        streams,
        NPointer.toNativeData(file),
    ) as java.lang.foreign.MemorySegment
    return NValue(result.address())
}

/** Kotlin equivalent of Assimp's non-exported `aiGetMaterialFloat` inline helper. */
public fun aiGetMaterialFloat(
    material: NPointer<AiMaterial>,
    key: NPointer<NChar>,
    type: UInt,
    index: UInt,
    out: NPointer<NFloat>,
): AiReturn = aiGetMaterialFloatArray(material, key, type, index, out, nullptr())

/** Kotlin equivalent of Assimp's non-exported `aiGetMaterialInteger` inline helper. */
public fun aiGetMaterialInteger(
    material: NPointer<AiMaterial>,
    key: NPointer<NChar>,
    type: UInt,
    index: UInt,
    out: NPointer<NInt>,
): AiReturn = aiGetMaterialIntegerArray(material, key, type, index, out, nullptr<NUInt32>())
