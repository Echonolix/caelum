# Windows MDBX 构建候选验收（2026-10-14）

## 结果与候选

Windows 原生 `:caelum-mdbx:compileNativeWindowsX64` 实际执行成功，退出码 0，17 秒。
旧 pin 为 `01973afcc5483d1aab2572a7a4d1aa567139a4ea`；直接复用已有修复
`5883abfa3d93a4658f3d0b4c0a6b7c45ba461b71`，候选受验代码提交为
`4d076517e64dd12e409ce0ec24500f67503388f1`。没有重新实现修复，没有修改 Fornax pin、任何 main 或用户工作树。

Caelum 当前 main 已包含该修复；Fornax Sandbox 固定旧 pin，因此需要由协调者选择后续 pin 整合。
本候选只包含该现有修复及 Windows 收据，可避免引入 Caelum 新 main 的其他功能。

## 原失败与准确参数

原集成工作树为 Fornax `b8f4b993432ce068f2c68ce3d4f58ad48a758d84`。
Windows 原日志来自集成依赖 Temp 目录的 `sandbox-cpu-excluding-mdbx-crosscompile.log`，完整副本为
[original-windows-failure.log](original-windows-failure.log)，失败命令见其中 387 行。

实际执行为 Windows Zig 0.17.0 `zig.exe cc -target x86_64-windows-gnu -std=c11 -O2`，
源文件是正常的 `build\archives\mdbxSources\libmdbx-a032e40c1c06eaf695a163cfc32d4a01ffb8b26c\mdbx.c`，
并非缺失/错误后缀。源文件 SHA-256 为
`58b770f90b75c533fd66d6d040764f75589565a2bc46034cc2ec2d8d9b257176`。

`CompileCrossNativeTask` 构造 `List<String>` 后直接交给 Windows `ProcessBuilder`；
`-DMDBX_BUILD_FLAGS="Caelum Zig 0.17.0; C11; O2; shared; CPU dispatch disabled"`
同时含空格和字面双引号，不能依靠原直接命令行序列化保留宏值。
修复对 Windows 编译、链接采用每次调用独立 UTF-8 response file，逐参数加双引号并转义反斜杠和字面引号；
宏值、源名、Zig 版本、目标和优化保持原样。实际编译与链接参数见 `c0.rsp`、`c1.rsp`、`link.rsp`。
真正进程调用为 `zig.exe cc @build\tmp\compileNativeWindowsX64\c0.rsp` 等，工作目录是候选的 `mdbx`。

补充 `-###` 参数诊断由同一 GraalVM 25 的 JShell 执行 `ProcessBuilder`，脚本内容保存为
`quote-probe-commands.txt`，输出为 `quote-probe.log`：直接参数重现扩展名错误；response file 进入
Clang 22.1.8，打印完整 C 字符串宏与 `-x c ...mdbx.c`。Zig 的该 dry-run 随后尝试重命名未生成的
临时 object 并返回 1，因此该补充仅证明参数解析差异，不算构建成功。成功依据是实际 native task 日志。

## 工具、执行与静态产物

只使用已安装 GraalVM JDK 25、Gradle 9.8.1、缓存 Zig 0.17.0 与缓存的固定 libmdbx archive；
全程离线，没有安装或更改系统环境。`buildSrc` Kotlin 编译通过；native task 为 executed，关闭本次 build cache。
最终命令通过 PowerShell 字符串数组传入 Gradle：

```text
:caelum-mdbx:compileNativeWindowsX64 --offline --no-daemon --no-configuration-cache
--no-build-cache --max-workers=1 -Dorg.gradle.jvmargs=-Xmx4g
-Pkotlin.compiler.execution.strategy=in-process -PcaelumJavaVersion=25
-Porg.gradle.java.installations.paths=C:/Users/Luna5ama/.jdks/graalvm-jdk-25 --console=plain
```

Gradle 用户缓存复用原集成依赖目录下 `gradle-home`；ktgen 独立 detached 检出固定为
`8debdc35341466ed9178407649e6fdc3a88089c8`。
首轮验收命令把含点的 `-Pkotlin.compiler.execution.strategy=in-process` 写成 PowerShell 裸参数，
被拆为 task 名，在 native task 执行前失败；原日志为 `runner-selection-failure.log`。
改为完整字符串数组后只执行了一次 Windows native 目标，最终日志为 `windows-mdbx-build.log`。

DLL 为 `mdbx/build/native/windows-x86_64/caelum_mdbx.dll`，509952 字节，SHA-256：
`2420cc5e9ad4fa827d1bb504c60bfa86ff1b61121c6009b3b5de0b66f1ed2d21`。
本机已有 `llvm-readobj --file-headers --coff-exports --coff-imports` 确认 AMD64 / PE32+ / DLL，
对 `mdbx/include/mdbx-binding.h` 提取并逐项核对全部 22 个函数导出，缺失 0。
工具版本与完整 PE 检查结果、输入/产物 hash 随本收据保存，DLL 本体保留在上述本地构建路径。

源代码逐字复用已有提交，未额外格式化或重排；`git diff --check 01973af HEAD` 通过。
本收据的日志和 response file 是原始输出，不用 formatter 改写。
Windows checkout 中 `codegen-c/README.MD` 与 `README.md` 的既有大小写碰撞导致 Git 显示前者被修改；
该路径没有加入候选提交，也没有为了清洁状态删除或覆盖文件。

## 验收边界与经验

这是 Windows native DLL 构建和导出的静态验收。没有运行 MDBX 数据库/FFM/JVM 测试，
没有 Linux/WSL、GPU、game 或 G4；不将 Sandbox 既有 14 项、standalone 18 项计入本次测试。
Linux 专项仍留 P8/follow-up。活跃任务及 worktree 检查未发现另一项 MDBX 修复在跑；
已有 SDL3 候选属于其他模块，M5/S12/T14 的工作树没有修改。

复用 Fornax F-L001（区分 executed、缓存、产物与运行时验收）及本仓库 `BINDING-AUTHORING.md`
已有 Windows response-file 经验，无新增 lesson ID。Windows 新证据保存在本收据中。
