# Caelum OpenGL teapot demo

This module includes a small OpenGL 3.3 demo that exercises the generated
Caelum OpenGL bindings and the `caelum-glfw` bindings together. It creates a
core-profile GLFW window, builds a procedural teapot mesh, uploads it through a
VAO/VBO, compiles GLSL lighting shaders, and renders the rotating result.

On Windows, run it with JDK 24 and a GLFW 3 native DLL:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :caelum-opengl:openglTeapotDemo `
    -PglfwDll=C:\absolute\path\to\glfw3.dll
```

Press Escape or close the window to exit. For an automated, hidden smoke run,
set a duration and hide the window:

```powershell
.\gradlew.bat :caelum-opengl:openglTeapotDemo `
    -PglfwDll=C:\absolute\path\to\glfw3.dll `
    -PdemoSeconds=2 `
    -PdemoHidden=true
```

A successful run prints the OpenGL version/renderer, a sampled center pixel,
`GL_ERROR=0`, and `CAELUM_TEAPOT_DEMO_OK`.
