package net.echonolix.caelum

@OptIn(UnsafeAPI::class, ExperimentalStdlibApi::class)
fun main() {
    MemoryStack {
        NFloat.valueOf(1.0f)
    }
}