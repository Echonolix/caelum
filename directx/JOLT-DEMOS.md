# Jolt × Caelum DirectX demos

三個可獨立啟動的剛體場景，以 `caelum-jolt` 模擬，透過 `caelum-directx`／
`caelum-dxgi` 的 API catalog 建立 Direct3D 11 renderer。需要 Windows x64、
JDK 24、D3D feature level 11_0 與 `d3dcompiler_47.dll`。

| Gradle task | 場景 | 涵蓋的 Jolt API |
| --- | --- | --- |
| `:caelum-directx:joltStackDemo` | 三列箱子、四顆球、四個膠囊自由落下 | Box/Sphere/Capsule、重力、摩擦、反彈、sleep、批次 transform |
| `:caelum-directx:joltBowlingDemo` | 球撞擊十個膠囊球瓶，球道含側欄與後擋板 | 線／角速度、旋轉、動態碰撞、contact listener |
| `:caelum-directx:joltKinematicDemo` | 平台載物移動，探測球穿越感應區 | `moveKinematic`、sensor 進出、callback/defer、closest ray hit/miss |

平台場景中，藍色為平台、黃色方塊為乘載物、橘色球為探測物；感應區外框收到
首次 entry 後由綠轉黃。黃色垂直線表示查詢射線，橘色小球標記實際命中點。
sensor 沒有碰撞阻擋作用；探測物設為無重力，以恆速穿越。

## 啟動

在倉庫根目錄執行，每次選一個場景：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :caelum-directx:joltStackDemo
.\gradlew.bat :caelum-directx:joltBowlingDemo
.\gradlew.bat :caelum-directx:joltKinematicDemo
```

預設顯示視窗，以固定 1/60 秒步長模擬並按實際時間推進；關閉視窗即可退出。
鏡頭固定，重新執行 task 可重播場景。第 360 步會檢查場景結果，視窗繼續顯示。
若要六秒後自動退出，加 `-PdemoFrames=360`。

第一次啟動會下載已固定雜湊的 Jolt sources 與原生工具鏈，編譯 host Jolt 庫。
JavaExec task 已設定 `--enable-native-access=ALL-UNNAMED`。

## 無桌面操作驗證

以下 task 建立隱藏 Win32 視窗，逐一跑完三個場景；不需要 computer use、
截桌面或模擬鍵盤滑鼠。隱藏模式按模擬步數完成，不依賴 wall clock 時限。

```powershell
.\gradlew.bat :caelum-directx:joltDemosSmoke

# 原生 thread pool；獨立輸出目錄保留兩組結果。
.\gradlew.bat :caelum-directx:joltDemosSmoke -PdemoWorkers=2 `
  '-PdemoOutput=C:/temp/caelum-jolt-workers2'

# 也可單獨驗證一個場景。
.\gradlew.bat :caelum-directx:joltBowlingDemo -PdemoHidden=true -PdemoFrames=360
```

若本機只需 Windows parser、已有可用的 parser resource，可在上述命令加：

```text
-x :codegen-c:compileNativeLinuxX64 -x :codegen-c:compileNativeMacOsAArch64
```

這只排除其他平台的 codegen parser；執行 demo 需要 `caelum-jolt:processResources`
與 host Jolt library，不能把它們排除。

| 參數 | 預設／意義 |
| --- | --- |
| `demoHidden` | 個別 task 預設 `false`；smoke 固定 `true` |
| `demoFrames` | 個別 task 預設 `0`，可見模式持續至關閉；隱藏模式的 `0` 換算為 360。其他值至少 360 |
| `demoWorkers` | 預設 `0`，使用 calling-thread jobs；可設 1–64 個原生 worker |
| `demoOutput` | 預設 `directx/build/jolt-demos`；各場景分別存入 `stack/`、`bowling/`、`kinematic/` |

輸出目錄內包含：

- `frame-0000.png`、`frame-0060.png`、`frame-0180.png`、`frame-0360.png`：
  1100 × 760 的 GPU backbuffer 原始回讀；較長的 bounded run 也保留最後一步。
- `transforms.csv`：初始與每 30 步的 body ID、位置、四元數、速度與 active 狀態。
- `summary.json`：場景數據、Jolt commit、driver 類型、worker 數、callback 執行緒數、
  與初始 GPU 畫面的像素差。`status=passed` 只在場景、GPU 和資源關閉檢查均通過後寫入。
- `lifecycle.txt`：world 關閉前後的 native owned-resource 基線檢查。

同一輸出目錄會更新本次的 checkpoint 檔案；其他舊 checkpoint 可能仍存在。
以本次 `summary.json`、`lifecycle.txt` 與命令 exit code 判讀，不以 PNG 的存在判定成功。
若需要保留不同次執行，使用不同 `demoOutput`。

成功標記為 `CAELUM_JOLT_STACK_DEMO_OK`、`CAELUM_JOLT_BOWLING_DEMO_OK`、
`CAELUM_JOLT_KINEMATIC_DEMO_OK`；smoke 全部完成後印出 `CAELUM_JOLT_DIRECTX_SMOKE_OK`。
可見模式在第 360 步以前關閉，只標記 `DEMO_CLOSED`，不宣稱已驗收。

## 驗證內容

每一步檢查 transform 有限值、四元數長度、batch body ID 與 update error flags。
堆疊須有至少 20 個物件下降、最後速度小於 0.3，且物體留在合理高度。
保齡球須有至少 5 個球瓶傾倒並位移，球確實抵達球瓶位置。
平台誤差須小於 0.005、乘載物移動超過 1 米且仍在平台上；sensor 必須進入並離開、
不阻擋探測物，deferred command 必須寫入 user data。每一步的兩條射線須分別
命中平台與 miss。

每張 checkpoint 至少有 5,000 個非背景像素；驗收畫面與初始畫面至少有 500 個
顯著不同的像素，另檢查 D3D device health。這些是 smoke assertions，搭配 PNG
檢查外觀；並非跨 GPU 的逐像素 golden-image 測試。

最新實際結果見 [Windows 驗收記錄](../jolt/acceptance/windows-directx-demos.md)。

## 程式結構

程式位於 `src/joltDemos/kotlin/net/echonolix/caelum/directx/demo/jolt/`：

- `JoltDirectXDemo.kt`：固定步長、視窗生命週期、checkpoint 與 CLI 驗收。
- `PhysicsScenes.kt`：三個場景、`NArray` 重用輸出、callback/defer 與物理 assertions。
- `PhysicsMeshes.kt`：Box/Sphere/Capsule 幾何，以及 Jolt quaternion/position model matrix。
- `JoltSceneRenderer.kt`：schema 驅動的 D3D11/DXGI/compiler 呼叫、depth buffer、
  indexed drawing、staging texture PNG 回讀。尺寸烘焙進 mesh，model 保持 rigid transform。

`joltDemos` 是獨立 source set，僅重用既有 demo 的 Win32 視窗、數學與 mesh 型別。
它不會把 Jolt 加入 DirectX 的公開 API、發佈 jar 或普通 test runtime。
所有 callback 只取複製的 ID／更新 thread-safe counters；world 操作透過 `defer`
在 update 完成後執行。Shape wrapper 建立 body 後立即關閉，body 保留原生 reference。

D3D11 優先使用硬體；失敗時明確列印 `DRIVER=WARP`。本輪實測為硬體路徑。
沒有 DXR、軟陰影或即時編輯器功能；這些範例用於檢查剛體 binding 與 renderer 整合。
