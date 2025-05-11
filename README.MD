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
| caelum-vulkan      | Vulkan API with OOP structure                         |
| caelum-glfw        | Cross platform window library                         |
| caelum-glfw-vulkan | An extension for glfw that provides Vulkan support    |

## License
This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.