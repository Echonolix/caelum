# Caelum

Caelum is a Kotlin framework providing a idiomatic way to interact with native libraries and memory.
Built on top of Java Project Panama's foreign function interface (FFI) and foreign memory access (FMA) APIs,
it enables low-overhead access to native libraries and memory. 
It also has a custom-built toolchain that generates caelum bindings from C headers automatically.

Binding maintainers should follow the ABI, provenance, and publication checks
in [`BINDING-AUTHORING.md`](BINDING-AUTHORING.md).

## List of modules
| Name               | Description                                           |
|--------------------|-------------------------------------------------------|
| caelum-core        | Core API with basic native memory access              |
| caelum-struct      | A Gradle plugins providing support for custom structs |
| caelum-opengl      | Generated OpenGL bindings and capability loading       |
| caelum-vulkan      | Vulkan API with OOP structure                         |
| caelum-glfw        | Cross platform window library                         |
| caelum-glfw-vulkan | An extension for glfw that provides Vulkan support    |
| caelum-dxgi        | DXGI 1.0-1.6 bindings and shared Windows COM runtime   |
| caelum-directx     | Direct3D 9 through current Direct3D 12 bindings        |
| caelum-assimp      | Assimp asset import/export C API bindings              |
| caelum-openal      | OpenAL 1.1 audio and device-context API bindings       |

## Maven

Caelum requires JDK 24. Publish the current checkout to Maven Local with:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat publishToMavenLocal
```

Maven projects can import the BOM and then select only the bindings they use:

```xml
<properties>
    <caelum.version>1.0-SNAPSHOT</caelum.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>net.echonolix</groupId>
            <artifactId>caelum-bom</artifactId>
            <version>${caelum.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>net.echonolix</groupId>
        <artifactId>caelum-opengl</artifactId>
    </dependency>
    <dependency>
        <groupId>net.echonolix</groupId>
        <artifactId>caelum-glfw</artifactId>
    </dependency>
    <dependency>
        <groupId>net.echonolix</groupId>
        <artifactId>caelum-assimp</artifactId>
    </dependency>
    <dependency>
        <groupId>net.echonolix</groupId>
        <artifactId>caelum-openal</artifactId>
    </dependency>
</dependencies>
```

The artifacts contain the Caelum bindings, not platform native libraries such
as GLFW, Assimp, or OpenAL; applications still load the matching native library
before the first binding call. See each binding module's README for its ABI and
loading requirements, and [`samples/maven-consumer`](samples/maven-consumer) for
a minimal Maven consumer.

The group and version can be overridden with `-PcaelumGroup` and
`-PcaelumVersion`. To publish to an authenticated Maven repository, provide
`-PmavenRepositoryUrl`, `-PmavenRepositoryUsername`, and
`-PmavenRepositoryPassword` (or the equivalent uppercase environment
variables), then run `publish`.

## Demos

See the [Caelum OpenGL lit teapot demo](opengl/README.md) for a runnable check
that exercises `caelum-opengl` and `caelum-glfw` together.

On Windows, the DirectX Stanford Dragon sample exercises the Direct3D convenience layer and
the generated ABI catalog through a native Direct3D 12 renderer:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :caelum-directx:directxDragonDemo
```

Pass `'-PdemoDxr=true'` to render the dragon through the hardware DXR path.
It builds the BLAS from the cleaned indexed Stanford mesh, dispatches at 960 x 720 with
eight samples per pixel by default, traces a secondary mirror-reflection ray on
each primary hit, and copies the GPU-produced gold image into the swap chain.
Use `'-PdemoDxrSamples=N'` to select 1–32 samples per pixel. Unsupported hardware
is reported as an error rather than silently falling back.

PowerShell users should quote decimal-valued Gradle properties such as
`'-PdemoSeconds=1.0'`; otherwise PowerShell can split the suffix into a separate
Gradle task name. See [the DirectX module guide](directx/README.md) for a hidden
smoke invocation and the exact success markers.

## License
This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
