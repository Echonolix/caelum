# Windows convex hull execution — 2026-10-28

Scope: the new `cj_shape_convex_hull` export and managed native-array / packed-float
overloads, using official Jolt v5.6.0 commit
`e77f175595e64cb44218cc9d9d56fc365ad0e36a`. No external hull library or generated
source edit was used. Native transport layouts are unchanged.

Environment: Windows x64, JDK 24 (`C:\Program Files\Java\jdk-24`), repository-pinned
Zig native compiler, double positions, 16-bit object layers, baseline CPU,
deterministic FP and exceptions enabled. Source base was
`be4322390d48624ee5bdcad42082ab4ca171a759` plus the accompanying hull changes.

Executed from the repository root:

```powershell
.\gradlew.bat :caelum-jolt:test -PjoltNativeTests `
  -x :codegen-c:compileNativeLinuxX64 -x :codegen-c:compileNativeMacOsAArch64
```

Generation, Kotlin compilation, Windows native compilation, resource packaging,
test compilation and tests executed successfully. Existing parser/Jolt source
downloads and unrelated compiler dependencies were reused. Jolt `processResources`
was executed; native tests loaded the newly compiled host library.

19 tests passed: 7 contracts, 4 native ABI, 5 native world and 3 hull tests, with
zero failures/errors/skips in those suites. The separately tagged stress test
was excluded by the existing default configuration.

After adding the interior/duplicate and oblique-plane counterexamples, a focused
rerun with `--tests '*JoltNativeConvexHullTest'` also passed all three hull tests.
That rerun recompiled the tests and reused the unchanged generated/Kotlin/native
outputs; it did not rebuild the DLL.

Hull evidence:

- Analytic tetrahedron ray hit at y=11.5 and sloped normal y=1/sqrt(3), plus a ray
  outside the tetrahedron but inside its bounding box. Packed source is mutated
  after creation; interior and duplicate points are accepted.
- Off-center local cuboid points x=3..5, y=2..4, z=-1..1, requested count 8 with a
  trailing NaN point outside the count. Native input is mutated after creation;
  the original wrapper and retained wrapper are closed before simulation. Ray
  position is correct, real contacts occur and the body settles after 360 steps
  at shape-origin y=-2 within 0.04 (its COM would be y=1).
- Invalid pointer/count/radius/nonfinite points, coincident/collinear and
  axis-aligned/oblique coplanar inputs fail. Raw failed calls clear a deliberately
  nonnull output sentinel. Resource counts stay unchanged, and successful bodies
  release their own retained shapes when the world closes.

`llvm-readobj --coff-exports` on the host DLL reports exactly the 60 header
exports, including `cj_shape_convex_hull`, with no missing or unexpected `cj_*`
symbols. DLL SHA-256:
`2b952bb121255e02fa0387c307e8790609901238b2b1cd4c9c97c27c3ebc5c1b`.
Header SHA-256:
`63ed4dc44b4ba5f6c20f9c5bec693d526529adf0e77465ec059b47b7624eec7c`.

No Linux/macOS native build/runtime, stress run or portable publication was
performed. Upstream keeps at most 256 hull vertices and accepts an approximation
at that limit; this is a convex collision proxy, with no concave decomposition.
