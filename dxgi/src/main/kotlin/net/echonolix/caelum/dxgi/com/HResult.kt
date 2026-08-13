package net.echonolix.caelum.dxgi.com

/** A 32-bit Windows HRESULT. */
@JvmInline
public value class HResult(public val value: Int) {
    public val succeeded: Boolean get() = value >= 0
    public val failed: Boolean get() = value < 0

    public fun check(operation: String): HResult {
        if (failed) throw ComException(operation, this)
        return this
    }

    override fun toString(): String = "0x%08X".format(value)

    public companion object {
        public val S_OK: HResult = HResult(0x00000000)
        public val S_FALSE: HResult = HResult(0x00000001)
        public val E_NOINTERFACE: HResult = HResult(0x80004002.toInt())
        public val E_POINTER: HResult = HResult(0x80004003.toInt())
        public val E_FAIL: HResult = HResult(0x80004005.toInt())
        public val E_INVALIDARG: HResult = HResult(0x80070057.toInt())
        public val DXGI_ERROR_NOT_FOUND: HResult = HResult(0x887A0002.toInt())
        public val DXGI_ERROR_UNSUPPORTED: HResult = HResult(0x887A0004.toInt())
        public val DXGI_ERROR_DEVICE_REMOVED: HResult = HResult(0x887A0005.toInt())
        public val DXGI_ERROR_DEVICE_HUNG: HResult = HResult(0x887A0006.toInt())
        public val DXGI_ERROR_DEVICE_RESET: HResult = HResult(0x887A0007.toInt())
    }
}

public class ComException(
    public val operation: String,
    public val result: HResult,
) : RuntimeException("$operation failed with HRESULT $result")
