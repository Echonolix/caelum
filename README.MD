# Caelum

Caelum is a Kotlin framework providing a idiomatic way to interact with native libraries and memory.
Built on top of Java Project Panama's foreign function interface (FFI) and foreign memory access (FMA) APIs,
it enables low-overhead access to native libraries and memory. 
It also has a custom-built toolchain that generates caelum bindings from C headers automatically.

## List of modules
| Name               | Description                                           |
|--------------------|-------------------------------------------------------|
| caelum-core        | Core API with basic native memory access              |
| caelum-struct      | A Gradle plugins providing support for custom structs |
| caelum-opengl      | Generated OpenGL bindings and capability loading       |
| caelum-vulkan      | Vulkan API with OOP structure                         |
| caelum-glfw        | Cross platform window library                         |
| caelum-glfw-vulkan | An extension for glfw that provides Vulkan support    |

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
</dependencies>
```

The artifacts contain the Caelum bindings, not platform native libraries such
as `glfw3.dll`; applications still load the matching native library before the
first binding call. See [`samples/maven-consumer`](samples/maven-consumer) for a
minimal Maven consumer.

The group and version can be overridden with `-PcaelumGroup` and
`-PcaelumVersion`. To publish to an authenticated Maven repository, provide
`-PmavenRepositoryUrl`, `-PmavenRepositoryUsername`, and
`-PmavenRepositoryPassword` (or the equivalent uppercase environment
variables), then run `publish`.

## Demos

See the [Caelum OpenGL lit teapot demo](opengl/README.md) for a runnable check
that exercises `caelum-opengl` and `caelum-glfw` together.

## License
This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.
