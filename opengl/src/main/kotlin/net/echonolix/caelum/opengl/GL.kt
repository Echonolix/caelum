package net.echonolix.caelum.opengl

public fun interface GLFunctionProvider {
    public fun getFunctionAddress(functionName: String): Long
}

public object GL {
    private val capabilities: ThreadLocal<GLCapabilities> = ThreadLocal()

    @JvmStatic
    public fun createCapabilities(provider: GLFunctionProvider): GLCapabilities =
        createGLCapabilities(provider).also(capabilities::set)

    @JvmStatic
    public fun setCapabilities(capabilities: GLCapabilities?) {
        if (capabilities == null) {
            this.capabilities.remove()
        } else {
            this.capabilities.set(capabilities)
        }
    }

    @JvmStatic
    public fun getCapabilities(): GLCapabilities =
        capabilities.get()
            ?: throw IllegalStateException(
                "Thread '${Thread.currentThread().name}' has no current OpenGL capabilities",
            )
}
