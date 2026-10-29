# DX12 / DXR Jolt Kinetic Museum

這個 demo 使用硬體 DXR 渲染正在模擬的 Jolt 場景：170 個剛體，其中 155 個動態，
包含 4 個 teapot、4 個 Stanford dragon、32 個骨牌、48 塊磚、48 顆小球及 18 個箱子。
每個渲染幀都從 Jolt 批次讀取姿態、重建 TLAS，再執行 `DispatchRays`；GPU 產生的
完整影像經 copy 送到 DX12 swap chain。陰影與反射會跟隨物理物件移動。

## 啟動與操作

需要 Windows x64、JDK 24，以及支援硬體 DXR 的顯示卡／驅動。
程式選取 high-performance adapter；不支援 DXR 時會直接報錯。
用 `-PdemoGpu=amd` 可指定 AMD，或用 `nvidia`／`intel` 選擇廠商；預設 `auto`
選取最高效能的硬體 adapter。指定廠商時不會改用另一家顯卡。
啟動日誌列出實際選取的名稱及 PCI vendor/device ID，GPU checkpoint 也記錄這兩個 ID。
從倉庫根目錄執行：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :caelum-directx:joltDxrMuseumDemo

# 指定 AMD 硬體光追。
.\gradlew.bat :caelum-directx:joltDxrMuseumDemo -PdemoGpu=amd
```

- **Space**：暫停／繼續物理模擬。
- **R**：重設場景。先關閉原本的 GPU／Jolt 資源，再於同一個視窗重建。
- **Escape** 或關閉視窗：退出。

預設顯示 960 × 640 視窗、每像素 2 個 primary samples、2 個原生物理 worker。
可用 `-PdemoDxrSamples=4` 或 `8` 改善邊緣品質。可見模式以實際時間推進固定
1/60 秒物理步長；每步使用兩個 collision substeps。

```powershell
# 可見視窗，12 秒模擬後自動退出，4 samples。
.\gradlew.bat :caelum-directx:joltDxrMuseumDemo -PdemoFrames=720 -PdemoDxrSamples=4

# 隱藏視窗，按模擬步數驗證，不需桌面操作。
.\gradlew.bat :caelum-directx:joltDxrMuseumSmoke

# 單執行緒版本並保留另一份輸出。
.\gradlew.bat :caelum-directx:joltDxrMuseumSmoke -PdemoWorkers=0 `
  '-PdemoOutput=C:/temp/caelum-museum-workers0'
```

`demoFrames=0` 在可見模式持續至退出，在隱藏模式使用 720 步；其他值至少 720。
`demoHidden` 可覆寫視窗模式，`demoWorkers` 支援 0–64，`demoDxrSamples` 支援 1–32。
所有 JavaExec task 都設定了 `--enable-native-access=ALL-UNNAMED`。

若本機已有 Windows codegen parser，可在命令末尾加：

```text
-x :codegen-c:compileNativeLinuxX64 -x :codegen-c:compileNativeMacOsAArch64
```

這只排除其他平台的 parser；Jolt 的 host 原生庫與 `processResources` 仍為必要依賴。
一般執行使用已提交的 DXIL，不需要本機另行編譯 shader。

## 場景的連鎖互動

1. 第一個骨牌獲得角速度，碰撞依序向後傳播。
2. 最後一個骨牌接觸感應器；callback 複製 ID 並透過 `world.defer` 排入命令。
3. 更新返回後，展台開始向右抽出，同時發射金屬球。
4. 展台上的八個模型失去支撐，依各自的凸包碰撞體落地、轉動並互相接觸。
5. 金屬球撞擊逐塊建立的磚牆；另一區的旋轉撥桿、球群與搖台持續互動。

場地低圍欄外另有隱藏的高碰撞邊界，防止拋起的物件離開地板。移動展台的路徑
避開後方搖台，避免把其他剛體壓進地板。場景不靠定時搬動動態模型來製造倒塌效果；
骨牌、感應器、deferred command、球撞牆及模型落下都有實際物理檢查。

Teapot 沿用本倉庫 `TeapotMesh` 的程序式網格；Stanford dragon 沿用已固定的
`dragon_vrip_res3` PLY、拓樸清理與 normals 重建。完整顯示三角形上傳到 BLAS，
兩種模型分別共用一個 Jolt 凸包。顯示頂點與凸包點雲使用相同的置中／縮放，
TLAS 使用 Jolt 的 shape-origin transform，避免 center-of-mass 偏移造成錯位。

凸包填滿凹陷，並非逐三角形凹模型碰撞；Jolt 至多保留 256 個 hull 頂點，必要時近似。
Stanford 模型的來源及使用條款保留於
[STANFORD_DRAGON_NOTICE.txt](src/test/resources/net/echonolix/caelum/directx/demo/model/STANFORD_DRAGON_NOTICE.txt)。

## 驗證與輸出

```powershell
# 固定 SDK 布局、實例編碼、圖集 offset 與故意損壞資料的拒絕檢查。
.\gradlew.bat :caelum-directx:joltDxrContracts

# 只隔離驗證連鎖物理，不建立 GPU device；不代表 DXR 通過。
.\gradlew.bat :caelum-directx:joltMuseumPhysicsSmoke
```

預設輸出為 `directx/build/jolt-dxr-museum/`，可用 `demoOutput` 覆寫。
第 0、180、360、420、540、720 步保存 GPU PNG、逐 instance／mesh 的 GPU hit counters；
較長的 bounded run 也保存最後一幀。每 60 步保存 `transforms.csv`。
R 重播的輸出放在 `reset-1/`、`reset-2/` 等子目錄。

`summary.json` 只在物理、GPU assertions 和場景資源關閉後寫入 `status=passed`；
提前退出標記 `closed-before-acceptance`，例外標記 `failed`。
`lifecycle.txt` 比較 Jolt native owned-resource 計數；它不是 GPU 記憶體用量統計。
使用不同的輸出目錄保留多次執行；同目錄可能留有先前 checkpoint，應以本次 summary
及命令 exit code 判讀。成功標記為 `CAELUM_JOLT_DXR_MUSEUM_OK`。

驗收要求包括：至少 28 個骨牌倒下、最後骨牌觸發 sensor、20 塊磚位移、8 個模型
全部下降並發生 contact、deferred user data 正確、kinematic 位置誤差小於 .01，
且每一步的射線 hit/miss 正確。每張 checkpoint 都要求 teapot／dragon primary hits、
實際 shadow occlusions 與 reflection hits 非零；首尾至少 2,000 個像素顯著不同。
這些 counter 計算 ray samples，不等於不同像素數。

Renderer 共用 mesh BLAS，每幀完整重建 TLAS；目前沒有 refit、非同步多幀或降噪器。
陰影使用方向光硬陰影，反射為一層，實際最大 ray recursion depth 為 2。
資源上傳、readback 與 allocator 重用均在 fence 完成後進行；frame scratch 使用
短生命週期 arena。DXR 呼叫取自 Caelum API catalog，手寫結構欄位對照固定 SDK metadata。

實測與限制見 [DXR 驗收記錄](../jolt/acceptance/dxr-museum.md)。
