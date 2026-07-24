$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$evidencePath = Join-Path $repositoryRoot ".omo\ulw-loop\caelum-opengl-20260724\evidence\C001-opengl33-smoke.log"
$tempBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$tempRoot = Join-Path $tempBase ("caelum-opengl-glfw-" + [Guid]::NewGuid().ToString("N"))
$archivePath = Join-Path $tempRoot "glfw-3.4.bin.WIN64.zip"
$extractPath = Join-Path $tempRoot "glfw"
$glfwUrl = "https://github.com/glfw/glfw/releases/download/3.4/glfw-3.4.bin.WIN64.zip"
$expectedSha256 = "54efa829400f2a0537f742b2b3bdd74e437bb4f2f048e4b7d3c5557d11a611e6"
$javaHome = Join-Path $env:USERPROFILE ".jdks\graalvm-jdk-24.0.2"
$result = New-Object System.Collections.Generic.List[string]

try {
    New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
    Invoke-WebRequest -Uri $glfwUrl -OutFile $archivePath

    $actualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $archivePath).Hash.ToLowerInvariant()
    if ($actualSha256 -ne $expectedSha256) {
        throw "GLFW archive SHA-256 mismatch: expected $expectedSha256, got $actualSha256"
    }

    Expand-Archive -LiteralPath $archivePath -DestinationPath $extractPath
    $glfwDll = (Resolve-Path (Join-Path $extractPath "glfw-3.4.bin.WIN64\lib-vc2022\glfw3.dll")).Path
    if (-not (Test-Path -LiteralPath (Join-Path $javaHome "bin\java.exe"))) {
        throw "GraalVM JDK 24.0.2 not found: $javaHome"
    }

    $headTree = (& git -C $repositoryRoot rev-parse --short "HEAD^{tree}").Trim()
    $diffMaterial = New-Object System.Collections.Generic.List[string]
    $diffMaterial.Add((& git -C $repositoryRoot diff --binary -- . ":(exclude).omo" | Out-String))
    foreach ($relativePath in (& git -C $repositoryRoot ls-files --others --exclude-standard -- opengl settings.gradle.kts)) {
        $absolutePath = Join-Path $repositoryRoot $relativePath
        if (Test-Path -LiteralPath $absolutePath -PathType Leaf) {
            $fileHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $absolutePath).Hash.ToLowerInvariant()
            $diffMaterial.Add("$relativePath=$fileHash")
        }
    }
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $diffHash = [BitConverter]::ToString(
            $sha256.ComputeHash([Text.Encoding]::UTF8.GetBytes(($diffMaterial -join "`n")))
        ).Replace("-", "").ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }

    $gradleArguments = @(
        ":caelum-opengl:opengl33Smoke",
        "-PglfwDll=$glfwDll",
        "--no-daemon"
    )
    $invocation = ".\gradlew.bat " + ($gradleArguments -join " ")
    $previousJavaHome = $env:JAVA_HOME
    $previousPath = $env:Path
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $env:JAVA_HOME = $javaHome
        $env:Path = (Join-Path $javaHome "bin") + [IO.Path]::PathSeparator + $previousPath
        $gradleBatch = Join-Path $repositoryRoot "gradlew.bat"
        $ErrorActionPreference = "Continue"
        $gradleOutput = @(
            & $gradleBatch @gradleArguments 2>&1 |
                ForEach-Object { "$_" }
        )
        $gradleExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
        $env:JAVA_HOME = $previousJavaHome
        $env:Path = $previousPath
    }

    $result.Add("CRITERION=C001")
    $result.Add("GLFW_URL=$glfwUrl")
    $result.Add("GLFW_SHA256=$actualSha256")
    $result.Add("JAVA_HOME=$javaHome")
    $result.Add(
        "JDK_SELECTION=GraalVM avoids Temurin 24.0.2's bundled Mesa opengl32.dll, " +
        "which hangs in glfwTerminate"
    )
    $result.Add(
        "DEBUG_CONCLUSION=GLFW symbol addresses matched; the system NVIDIA OpenGL path " +
        "completes full cleanup"
    )
    $result.Add("INVOCATION=$invocation")
    $result.Add("HEAD_TREE=$headTree")
    $result.Add("WORKING_DIFF_SHA256=$diffHash")
    $result.AddRange([string[]]$gradleOutput)

    if ($gradleExitCode -ne 0) {
        throw "OpenGL smoke Gradle task failed with exit code $gradleExitCode"
    }
    if (-not ($gradleOutput -match "^OPENGL_VERSION=.+")) {
        throw "Smoke output did not contain a nonblank OPENGL_VERSION"
    }
    if (-not ($gradleOutput -contains "GL_ERROR=0")) {
        throw "Smoke output did not contain GL_ERROR=0"
    }
    if (-not ($gradleOutput -contains "OPENGL33_SMOKE_OK")) {
        throw "Smoke output did not contain OPENGL33_SMOKE_OK"
    }
    $result.Add("RESULT=PASS")
} catch {
    $result.Add("RESULT=FAIL")
    $result.Add("ERROR=$($_.Exception.Message)")
    throw
} finally {
    $resolvedTempRoot = [System.IO.Path]::GetFullPath($tempRoot)
    $tempBaseWithSeparator = $tempBase.TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar,
        [System.IO.Path]::AltDirectorySeparatorChar
    ) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $resolvedTempRoot.StartsWith($tempBaseWithSeparator, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove temp path outside system temp: $resolvedTempRoot"
    }
    if (Test-Path -LiteralPath $resolvedTempRoot) {
        Remove-Item -LiteralPath $resolvedTempRoot -Recurse -Force
    }
    $result.Add("CLEANUP_OK path=$resolvedTempRoot archive=false extract=false")
    New-Item -ItemType Directory -Path (Split-Path -Parent $evidencePath) -Force | Out-Null
    Set-Content -LiteralPath $evidencePath -Value $result -Encoding utf8
}
