RaytracingAccelerationStructure Scene : register(t0);
ByteAddressBuffer Vertices : register(t1);
ByteAddressBuffer Indices : register(t2);
RWByteAddressBuffer Output : register(u0);

// Regenerate the checked-in DXIL with the pinned Windows SDK compiler:
// dxc -T lib_6_3 -HV 2021 -O3 -Qstrip_debug -Qstrip_reflect
//     -Fo MinimalRayTracing.dxil MinimalRayTracing.hlsl

cbuffer RayConstants : register(b0) {
    uint Width;
    uint Height;
    uint SamplesPerPixel;
    uint RowPitch;
    float3 CameraPosition;
    float TanHalfFovY;
    uint4 Reserved0;
    uint4 Reserved1;
};

static const uint HIT_MARKER = 0x48595421;
static const uint IMAGE_OFFSET = 512;

struct Payload {
    float3 color;
    uint depth;
};

float hash12(float2 p) {
    float3 p3 = frac(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return frac((p3.x + p3.y) * p3.z);
}

float3 studioSky(float3 d) {
    d = normalize(d);
    float3 low = float3(0.018, 0.025, 0.045);
    float3 high = float3(0.28, 0.38, 0.62);
    float3 sky = lerp(low, high, saturate(d.y * 0.5 + 0.5));
    float keyStrip = smoothstep(0.965, 0.995, abs(d.x)) * smoothstep(-0.20, 0.28, d.y);
    float rimStrip = smoothstep(0.91, 0.985, dot(d, normalize(float3(-0.65, 0.45, -0.62))));
    float floorGlow = pow(saturate(-d.y), 5.0);
    return sky + keyStrip * float3(4.5, 3.9, 2.8) + rimStrip * float3(2.2, 2.8, 4.0)
        + floorGlow * float3(0.42, 0.16, 0.035);
}

[shader("raygeneration")]
void RayGen() {
    uint2 pixel = DispatchRaysIndex().xy;
    float3 target = float3(0.0, 0.0, 0.0);
    float3 forward = normalize(target - CameraPosition);
    float3 right = normalize(cross(forward, float3(0.0, 1.0, 0.0)));
    float3 up = cross(right, forward);
    float aspect = (float)Width / (float)Height;
    float3 accumulated = 0.0;

    [loop]
    for (uint sample = 0; sample < SamplesPerPixel; ++sample) {
        float2 jitter = float2(
            hash12(float2(pixel) + float2(sample * 17.0, sample * 3.0)),
            hash12(float2(pixel.yx) + float2(sample * 11.0, sample * 23.0))) - 0.5;
        float2 uv = ((float2(pixel) + 0.5 + jitter) / float2(Width, Height)) * 2.0 - 1.0;
        uv.y = -uv.y;
        float3 direction = normalize(forward + right * uv.x * aspect * TanHalfFovY + up * uv.y * TanHalfFovY);

        RayDesc ray;
        ray.Origin = CameraPosition;
        ray.Direction = direction;
        ray.TMin = 0.01;
        ray.TMax = 100.0;
        Payload payload;
        payload.color = 0.0;
        payload.depth = 0;
        TraceRay(Scene, RAY_FLAG_FORCE_OPAQUE, 0xff, 0, 1, 0, ray, payload);
        accumulated += payload.color;
    }

    float3 color = accumulated / max(1, SamplesPerPixel);
    color = color / (1.0 + color);
    color = pow(saturate(color), 1.0 / 2.2);
    uint4 rgba = uint4(round(color * 255.0), 255);
    uint packed = rgba.x | (rgba.y << 8) | (rgba.z << 16) | (rgba.w << 24);
    Output.Store(IMAGE_OFFSET + pixel.y * RowPitch + pixel.x * 4, packed);
}

[shader("miss")]
void Miss(inout Payload payload) {
    payload.color = studioSky(WorldRayDirection());
}

[shader("closesthit")]
void ClosestHit(inout Payload payload, in BuiltInTriangleIntersectionAttributes attributes) {
    uint primitive = PrimitiveIndex();
    uint3 vertexIndex = Indices.Load3(primitive * 12);
    float3 n0 = asfloat(Vertices.Load3(vertexIndex.x * 24 + 12));
    float3 n1 = asfloat(Vertices.Load3(vertexIndex.y * 24 + 12));
    float3 n2 = asfloat(Vertices.Load3(vertexIndex.z * 24 + 12));
    float3 bary = float3(1.0 - attributes.barycentrics.x - attributes.barycentrics.y,
                          attributes.barycentrics.x, attributes.barycentrics.y);
    float3 normal = normalize(n0 * bary.x + n1 * bary.y + n2 * bary.z);
    float3 incident = normalize(WorldRayDirection());
    if (dot(normal, incident) > 0.0) normal = -normal;

    Output.Store(0, HIT_MARKER);
    float3 goldF0 = float3(1.000, 0.620, 0.120);
    float facing = saturate(dot(-incident, normal));
    float3 fresnel = goldF0 + (1.0 - goldF0) * pow(1.0 - facing, 5.0);

    if (payload.depth == 0) {
        RayDesc reflection;
        reflection.Origin = WorldRayOrigin() + incident * RayTCurrent() + normal * 0.012;
        reflection.Direction = normalize(reflect(incident, normal));
        reflection.TMin = 0.01;
        reflection.TMax = 100.0;
        Payload reflected;
        reflected.color = 0.0;
        reflected.depth = 1;
        TraceRay(Scene, RAY_FLAG_FORCE_OPAQUE, 0xff, 0, 1, 0, reflection, reflected);
        float3 warmBase = float3(0.14, 0.045, 0.004) * (0.25 + 0.75 * facing);
        payload.color = reflected.color * fresnel + warmBase;
    } else {
        payload.color = studioSky(reflect(incident, normal)) * fresnel;
    }
}
