// Compile with: dxc -T lib_6_3 -HV 2021 -O3 -Qstrip_debug -Qstrip_reflect
//   -Fo JoltRayTracing.dxil JoltRayTracing.hlsl
RaytracingAccelerationStructure Scene : register(t0);
ByteAddressBuffer Vertices : register(t1);
ByteAddressBuffer Indices : register(t2);
ByteAddressBuffer Instances : register(t3);
RWByteAddressBuffer Output : register(u0);

cbuffer RayConstants : register(b0) {
    uint Width, Height, SamplesPerPixel, RowPitch;
    float3 CameraPosition; float TanHalfFovY;
    float3 CameraTarget; uint InstanceCount;
};

// Output header: primary hits, shadow rays, reflection rays, shadow occlusions,
// per-instance primary hits at byte 16, and reflection hits at byte 4096.
// Up to 1020 instance counters fit before byte 4096. Pixels start at byte 8192.
static const uint ImageOffset = 8192;
struct Payload { float3 color; uint kind; };

float hash12(float2 p) {
    float3 q = frac(float3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return frac((q.x + q.y) * q.z);
}

float3 sky(float3 d) {
    return lerp(float3(.035, .045, .075), float3(.35, .48, .68), saturate(d.y * .5 + .5));
}

[shader("raygeneration")]
void RayGen() {
    uint2 pixel = DispatchRaysIndex().xy;
    float3 forward = normalize(CameraTarget - CameraPosition);
    float3 right = normalize(cross(forward, float3(0, 1, 0)));
    float3 up = cross(right, forward);
    float3 sum = 0;
    for (uint sample = 0; sample < SamplesPerPixel; ++sample) {
        float2 jitter = float2(hash12(float2(pixel) + float2(sample * 17, sample * 3)),
                               hash12(float2(pixel.yx) + float2(sample * 11, sample * 23))) - .5;
        float2 uv = (float2(pixel) + .5 + jitter) / float2(Width, Height) * 2 - 1;
        uv.y = -uv.y;
        RayDesc ray;
        ray.Origin = CameraPosition;
        ray.Direction = normalize(forward + right * uv.x * ((float)Width / Height) * TanHalfFovY + up * uv.y * TanHalfFovY);
        ray.TMin = .01; ray.TMax = 1000;
        Payload p; p.color = 0; p.kind = 0;
        TraceRay(Scene, RAY_FLAG_FORCE_OPAQUE, 0xff, 0, 1, 0, ray, p);
        sum += p.color;
    }
    float3 color = sum / SamplesPerPixel;
    color = pow(saturate(color / (1 + color)), 1.0 / 2.2);
    uint4 rgba = uint4(round(color * 255), 255);
    Output.Store(ImageOffset + pixel.y * RowPitch + pixel.x * 4,
                 rgba.x | rgba.y << 8 | rgba.z << 16 | rgba.w << 24);
}

[shader("miss")]
void Miss(inout Payload p) {
    p.color = p.kind == 2 ? 1 : sky(WorldRayDirection());
}

[shader("closesthit")]
void ClosestHit(inout Payload p, in BuiltInTriangleIntersectionAttributes attr) {
    if (p.kind == 2) {
        uint unused;
        Output.InterlockedAdd(12, 1, unused);
        p.color = 0;
        return;
    }
    if (p.kind == 1) {
        uint unused;
        Output.InterlockedAdd(4096, 1, unused);
    }
    uint id = InstanceID();
    // Metadata record: index byte offset, vertex byte offset, RGB, metallic,
    // roughness, object ID. Index values are local to each mesh.
    uint base = id * 32;
    uint indexBase = Instances.Load(base);
    uint vertexBase = Instances.Load(base + 4);
    float3 albedo = asfloat(Instances.Load3(base + 8));
    float metallic = asfloat(Instances.Load(base + 20));
    float roughness = asfloat(Instances.Load(base + 24));
    uint3 ix = Indices.Load3(indexBase + PrimitiveIndex() * 12);
    float3 n0 = asfloat(Vertices.Load3(vertexBase + ix.x * 24 + 12));
    float3 n1 = asfloat(Vertices.Load3(vertexBase + ix.y * 24 + 12));
    float3 n2 = asfloat(Vertices.Load3(vertexBase + ix.z * 24 + 12));
    float3 bary = float3(1 - attr.barycentrics.x - attr.barycentrics.y, attr.barycentrics);
    float3 objectNormal = normalize(n0 * bary.x + n1 * bary.y + n2 * bary.z);
    float3 normal = normalize(mul(objectNormal, (float3x3)WorldToObject3x4()));
    float3 incident = normalize(WorldRayDirection());
    if (dot(normal, incident) > 0) normal = -normal;
    float3 hit = WorldRayOrigin() + incident * RayTCurrent();
    float3 lightDirection = normalize(float3(-.5, 1, -.35));
    float diffuse = saturate(dot(normal, lightDirection));
    float visibility = 1;
    if (p.kind == 0) {
        uint unused;
        Output.InterlockedAdd(0, 1, unused);
        if (id < 1020) Output.InterlockedAdd(16 + id * 4, 1, unused);
        Output.InterlockedAdd(4, 1, unused);
        RayDesc shadow; shadow.Origin = hit + normal * .01; shadow.Direction = lightDirection;
        shadow.TMin = .01; shadow.TMax = 1000;
        Payload s; s.color = 0; s.kind = 2;
        TraceRay(Scene, RAY_FLAG_FORCE_OPAQUE | RAY_FLAG_ACCEPT_FIRST_HIT_AND_END_SEARCH,
                 0xff, 0, 1, 0, shadow, s);
        visibility = s.color.x;
        Output.InterlockedAdd(8, 1, unused);
        RayDesc reflected; reflected.Origin = hit + normal * .01;
        reflected.Direction = reflect(incident, normal); reflected.TMin = .01; reflected.TMax = 1000;
        Payload r; r.color = 0; r.kind = 1;
        TraceRay(Scene, RAY_FLAG_FORCE_OPAQUE, 0xff, 0, 1, 0, reflected, r);
        float reflectivity = lerp(.08, .8, saturate(metallic)) * (1 - .65 * saturate(roughness));
        p.color = albedo * (.12 + 1.5 * diffuse * visibility) + r.color * reflectivity;
    } else {
        p.color = albedo * (.12 + diffuse);
    }
}
