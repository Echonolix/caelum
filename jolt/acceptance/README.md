# caelum-jolt acceptance

Status on 2026-10-24 (America/Los_Angeles): implementation authoring with limited
checks only. Full native testing was explicitly deferred for this iteration.
No native simulation, worker callback, benchmark, cross-platform release or
independent Maven consumer result is claimed. P0–P5 release acceptance remains
open even though their implementation/test/build sources have been authored.

## Recorded checks

See [authoring-checks.md](authoring-checks.md) for commands and results. The
compiled native test sources are not evidence that those tests passed. The
source-only jar produced while excluding processResources is not a usable
native or publishable artifact.

## Gates prepared for the next validation run

| Gate | Mechanism | Required evidence |
| --- | --- | --- |
| Source input | `verifyJoltSources` | manifest equals pinned Jolt.cmake base list, archive hash verified |
| Header/generation | `JoltContractsTest` | every export, generated function, struct field and oracle entry accounted for |
| Native ABI | `JoltNativeAbiTest` | every layout/offset/constant; dirty initializer/padding/arrays; math and callback probes |
| Single-thread simulation | `JoltNativeWorldTest`, `joltHelloWorld -PjoltWorkers=0` | 300 frames at 1/60s, final y=0.5±0.03, abs(vy)<0.1, owned refs released |
| Bodies/query/lifecycle | native world tests | stale ID, closed view, repeat close, retain, lock scopes, batch capacity, ray hit/miss/layers, creation rollback |
| Workers/callbacks | native world tests with `-PjoltStressTests`, HelloWorld workers=4 | actual non-caller upcalls, throw/defer/unregister/replacement, update/close serialization, no leaks/deadlocks |
| Portable outputs | `:caelum-jolt:compileNative`, `:caelum-jolt:portableJar`, `:caelum-jolt:sourcesJar` | all three declared native resource IDs and notices |
| Export/dependency audit | `tools/verify_artifacts.py` + `llvm-readobj`; macOS `otool -L` | exact cj exports; native dependency report on each platform |
| Consumer | `maven-consumer/` after local publication | independent Maven project loads bundled native and runs floor/ball scenario |
| Performance | `joltBenchmark` | CSV plus hardware/JDK/revision/build/worker/body metadata, repeated independent JVM runs |

Native tests require `-PjoltNativeTests`; stress additionally requires
`-PjoltStressTests`. Default tests exclude both native and stress cases. Kotlin
test compilation may request the main jar and thus native build inputs; exclude
`:caelum-jolt:processResources` only for source-only authoring checks.

## Platform matrix (all runtime cells pending)

| Target/resource ID | Toolchain/link | Native ABI | Simulation/workers | Exports/dependencies | Maven consumer |
| --- | --- | --- | --- | --- | --- |
| `windows-x86_64` | pending | pending | pending | pending | pending |
| `linux-x86_64` | pending | pending | pending | pending | pending |
| `macos-aarch64` | pending | pending | pending | pending | pending |

C++ syntax-only checking on Windows is not a linked Windows library. A successful
cross-compilation is not a runtime pass. Record real execution on each target;
never silently skip unsupported outputs or publish a host-only replacement.

## Publication and consumer procedure

After native validation, run (no source-only task exclusions):

```text
gradlew :caelum-core:publishToMavenLocal :caelum-jolt:publishToMavenLocal :caelum-bom:publishToMavenLocal
python jolt/tools/verify_artifacts.py --jar jolt/build/portable-libs/caelum-jolt-1.0-SNAPSHOT.jar --sources jolt/build/libs/caelum-jolt-1.0-SNAPSHOT-sources.jar
mvn -f jolt/acceptance/maven-consumer/pom.xml package dependency:copy-dependencies
java --enable-native-access=ALL-UNNAMED -cp "jolt/acceptance/maven-consumer/target/classes;jolt/acceptance/maven-consumer/target/dependency/*" acceptance.JoltConsumer
```

On Linux/macOS use `./gradlew` and `:` instead of `;` in the Java classpath.
Use the selected `-Dcaelum.version=...` and matching Kotlin/JDK when overriding
the repository defaults. The consumer has no repository source dependencies.
To collect exports/dependencies, add `--native-dir jolt/build/native
--llvm-readobj /path/to/llvm-readobj` to the artifact checker. Save its stdout
alongside the platform execution logs; assess every dynamic dependency on the
actual target OS.

Record timestamp, OS/CPU/RAM, JDK, native toolchain, Caelum revision, Jolt commit,
DP/layer/CPU/FP flags, body count and worker count in each platform report. Save
JUnit XML, demo marker, binary hashes, export/dependency report and consumer
output. For performance include warmup, measurement counts, multiple JVM runs,
units, allocation-counter scope and any JNI comparison's engine/build mismatch.
