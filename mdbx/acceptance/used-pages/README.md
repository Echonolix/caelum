# Used-pages Linux CPU acceptance — 2026-10-13

Source base: `421e98c2c3a4930dd3948dbaa3f33e6dadb31de8`.
Implementation/test source: `e4189492ee54a7f1df5f6497906bbbcae90914cd`.
GraalVM 25.0.4+7.1; Gradle 9.8.1; Gradle user home `/workspace/.setup/gradle`;
fixed adjacent dependency worktrees `/workspace/l1-deps`.

Executed from `/workspace/fornax-l1`, reusing Fornax's existing source-composite CPU script:

```sh
bash engine/persistence/acceptance/l1-used-pages/run.sh \
  :engine-persistence:test --tests '*PersistenceQuotaTest' \
  :caelum:caelum-mdbx:test --tests '*MdbxUsageTest'
```

Runner supplies `--build-cache`, `-x :engine-terrain:compileSlang`,
`-PcaelumJavaVersion=25`, `--no-configuration-cache`, `--max-workers=1`,
`-Pcodegenc.clang=/workspace/.setup/clang19/extracted/usr/bin/clang-19`,
and exact candidate/source pins. **MdbxUsageTest: 1 test, 0 failures/errors/skips.**
The command overall failed in Fornax's separate growth fixture because it generated
invalid RGB values; Caelum's native statistics test completed successfully. The
corrected Fornax full persistence suite subsequently passed 56 tests.

The native test runs on workspace filesystem temporary databases, with a
preallocated 16 MiB file and 32 MiB mapping limit. It verifies used pages differ
from file extent, grow after a 1 MiB value, remain snapshot-stable while a platform
writer deletes, preserve the old reader's bytes, reopen, and reject finished
transaction access. No user worlds were opened/deleted. Both platform binaries
were built; exported symbols are attached. Windows runtime remains untested.

The cold environment restored official Debian clang 19.1.7-3+b1 packages into the
workspace using the exact SHA-256 values already recorded in `mdbx/ACCEPTANCE.md`.
This run verified those three hashes and actual `clang-19 --version`; it did not
repeat the earlier signed-index verification. No source/production pins or shared
checkout were reset. Native source archive/hash/build flags remain unchanged.

`txn_space_used` is exactly `pgno2bytes(env, txn->geo.first_unallocated)` in the
fixed official `mdbx.c:11802`: a used-page high-water extent, not live table payload.
The bridge does not scan reader locks, subtract free pages, estimate file size,
change geometry, or mutate a transaction. Deletion does not guarantee immediate
quota recovery. No Windows execution, 64 GiB stress, crash/power-loss injection,
or native performance claim is made.
