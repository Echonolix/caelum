# caelum-jolt

以 Caelum 的 `NStruct`、`NValue`、`NPointer` 與 `NArray` 提供 Jolt Physics
binding。路徑為官方 Jolt C++ → 自有 C ABI → `codegen-c` → Kotlin 管理層，
沒有 JNI runtime 依賴。

目前為開發版本。Windows x64 原生建置、16 個契約／ABI／world 測試，以及三個
DirectX 物理 demo 已通過；demo 涵蓋單執行緒與兩個原生 worker。壓力測試與
跨平台發佈驗收仍待執行。完整狀態見 [驗收記錄](acceptance/README.md)。

## 範圍與建置

- 固定官方 Jolt v5.6.0、double 世界座標、float 速度／旋轉、16-bit object layer。
- Runtime lease、world、scratch allocator、單執行緒／原生 thread-pool jobs。
- Box、Sphere、Capsule；剛體生命週期、transform、velocity、force／impulse、kinematic movement、activation。
- layer table、closest ray cast、重用輸出陣列的批次 transform。
- contact／activation listener、受限 callback view、deferred command queue。

來源、雜湊與 flags 見 [UPSTREAM.md](UPSTREAM.md)，逐項 API 與所有權見
[SURFACE.md](SURFACE.md)，開發階段見 [roadmap](../docs/caelum-jolt-roadmap.md)。
Constraints、Character、Vehicle、SoftBody 等列為後續 P6。

Gradle project：`:caelum-jolt`。Maven artifact：`net.echonolix:caelum-jolt`。
JDK 預設為 24；執行 JVM 需加 `--enable-native-access=ALL-UNNAMED`。
應用在第一次 raw `cj_*` 呼叫前，必須先 `JoltRuntime.load()` 或 `acquire()`。
bootstrap 不相容時會拒絕使用 native library。

```kotlin
dependencies {
    implementation(platform("net.echonolix:caelum-bom:1.0-SNAPSHOT"))
    implementation("net.echonolix:caelum-jolt")
}
```

日常 jar 建置只包含 host 原生庫；publication 透過 `portableJar` 收集
Windows x86_64、Linux x86_64、macOS arm64 三個 target。缺少任一 target 或其
runtime 驗收時，不應將產物當成 portable release。

## 使用方式

```kotlin
import net.echonolix.caelum.MemoryStack
import net.echonolix.caelum.jolt.*
import net.echonolix.caelum.jolt.enums.JoltMotionType
import net.echonolix.caelum.jolt.structs.*

JoltRuntime.acquire().use { runtime ->
    runtime.createWorld(workers = 0).use { world ->
        runtime.createBox(JoltVector(20f, 0.5f, 20f)).use { floor ->
            MemoryStack {
                val info = JoltBodyCreateInfo.allocate {
                    position.set(0.0, -0.5, 0.0)
                    motion = JoltMotionType.CJ_STATIC
                    layer = JoltObjectLayer.of(0)
                }
                world.bodies.createAndAdd(info, floor, activate = false)
            }
        }
        val body = runtime.createSphere(0.5f).use { sphere ->
            MemoryStack {
                val info = JoltBodyCreateInfo.allocate {
                    position.set(0.0, 2.0, 0.0)
                    motion = JoltMotionType.CJ_DYNAMIC
                    layer = JoltObjectLayer.of(1)
                }
                world.bodies.createAndAdd(info, sphere)
            }
        }
        repeat(300) { world.update(1f / 60f).throwIfFailed() }
        println(world.bodies.transform(body))
    }
}
```

可編譯的完整版本為 [HelloWorld.kt](src/examples/kotlin/net/echonolix/caelum/jolt/examples/HelloWorld.kt)。
有畫面的範例見 [DirectX Jolt demos](../directx/JOLT-DEMOS.md)：堆疊、保齡球、
移動平台／感應區／射線；也提供無桌面操作的 GPU 回讀驗證。
Shape wrapper 關閉後，body 仍持有自己的 shape reference。world 關閉時會清除所有
body。方便的 `createWorld` overload 同時擁有預設 allocator/jobs；若顯式傳入，
它們保持 caller-owned，應晚於使用它們的 world 關閉。

設定 struct 的 `allocate` 會呼叫原生 initializer，包含 padding 與非零預設值。
`malloc` 只配置未初始化空間；不能用它直接建立引擎設定。上游 body 預設 motion
是 Dynamic、layer 是 0；使用預設雙 layer table 時，moving body 要明確指定 layer 1。

熱路徑可呼叫 `readTransform(id, output)` 或
`readTransforms(NArray<NUInt32>, NArray<JoltBodyTransform>)`。後者以單次 native call
寫入 caller-owned buffer，並檢查 count/capacity。便利 `transform`、ray hit 和
callback `snapshot()` 會建立 JVM 值物件。Raw buffer／nested accessor 仍是記憶體
view，必須遵守其 arena／MemoryStack 的期限；管理層無法保護逃逸的任意 raw pointer。

## 生命週期與回呼

所有 owned wrapper 使用 `use`／`close`；重複 close 安全。runtime 必須晚於自己的
resource 關閉。body ID 是 world 內的完整 index+sequence 值，沒有 close，也不包含
world identity。不同 world 的同值 ID 可能指向不同 body；應用須自己維持歸屬。

`world.bodies` 和 `world.query` 是 borrowed view；world 關閉後拒絕呼叫。
`withBodyRead`／`withBodyWrite` 只允許使用傳入的 body view，並在 finally 解鎖。
view 不能跨執行緒或離開 block；需要保留時使用 `snapshot()`。

```kotlin
world.setListeners(contact = object : JoltContactListener {
    override fun contact(event: JoltContactEventView, settings: JoltContactSettingsView) {
        settings.friction = 0.4f
        val snapshot = event.snapshot()
        world.defer {
            // 此處已離開 callback，可用複製的資料更新應用狀態或呼叫 world API。
            println(snapshot.body1.bodyId)
        }
    }
}).use {
    world.update(1f / 60f).throwIfFailed()
}
```

Callback 可能同時在多個 native worker 執行。禁止在 callback 內呼叫 world/resource
API、close、遞迴 update 或操作 body lock；`world.defer` 可以使用。Contact settings
只有當次 callback 可寫，listener 拋出 `Throwable` 時不提交部分修改。
validation 失敗則拒絕接觸。錯誤會在 native 返回後以 `JoltCallbackException`
回到呼叫端，含首個原因、失敗次數和可用的 update flags；已發生的 world 操作不會回滾。

每份 registration 擁有獨立 shared arena 和 listener 強引用。替換、移除或 world
關閉會等待原生回呼結束，再釋放 stub。不要用全域 `freeFunctionStubs()` 管理這些
registration。

每次成功 update 自動執行最多 1024 個 deferred commands。update flags 非零或 callback 失敗時保留佇列，
由呼叫端處理後 `flushDeferred()`；world 關閉會丟棄尚未執行的命令。命令內不能
再次 update／drain 或關閉 world。

## 驗證命令

以下 PowerShell 命令從 repository root 執行。只編譯／執行純契約測試，可避開
本模組的 native build。Windows 本輪使用既有 bundled C parser，排除其他 parser
target 僅為本機迭代；正式發佈不可用這些 exclusions 取代完整建置。

```powershell
.\gradlew.bat :caelum-jolt:compileTestKotlin :caelum-jolt:compileExamplesKotlin :caelum-jolt:test :caelum-jolt:verifyJoltSources `
  -x :caelum-jolt:processResources `
  -x :codegen-c:compileNativeLinuxX64 -x :codegen-c:compileNativeMacOsAArch64
```

下列命令包含原生建置／執行，已準備但本輪未執行：

```powershell
.\gradlew.bat :caelum-jolt:test -PjoltNativeTests
.\gradlew.bat :caelum-jolt:test -PjoltNativeTests -PjoltStressTests
.\gradlew.bat :caelum-jolt:joltHelloWorld -PjoltWorkers=0
.\gradlew.bat :caelum-jolt:joltHelloWorld -PjoltWorkers=4
.\gradlew.bat :caelum-jolt:joltBenchmark -PjoltBodies=1000 -PjoltWorkers=4
```

Benchmark 輸出單筆／批次讀取、update 與 0／1／100% 接觸密度的 CSV，記錄 p50/p95、
吞吐、caller thread JVM allocation、Jolt allocator requests 和 bridge 存活資源。
它是初始 baseline harness，並非已取得的效能結果。Jolt allocation 計數為累積請求量，
不是 live bytes/RSS，也不含 JVM、bridge/system 或 scratch buffer 內的子配置。
跨引擎版本或 CPU flags 不同時，不能把與 jolt-jni 的差異全歸因於 JNI／FFM。
