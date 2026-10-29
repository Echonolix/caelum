# Jolt DXR shader

`JoltRayTracing.dxil` is compiled from the adjacent HLSL using the Windows
Vulkan SDK 1.4.313.0 `dxc.exe`:

```powershell
dxc -T lib_6_3 -HV 2021 -O3 -Qstrip_debug -Qstrip_reflect `
  -Fo JoltRayTracing.dxil JoltRayTracing.hlsl
```

SHA-256: `BF2AD2F65318948D3A867E7BA729D6CB77E83367517EE52ADEAFAD708CDBC4AC`.
The shader expects a cleared 8192-byte output header before each dispatch.
Its per-instance records use atlas byte offsets and are indexed by the TLAS
instance ID. The host must copy the resulting image to the swap chain and
read back the header after the dispatch completes.

The first four words count primary hits, shadow rays, reflection rays and
shadow occlusions. Per-instance primary-hit counters start at byte 16;
reflection hits are counted at byte 4096. Counts are ray samples, not pixels.
