package net.echonolix.caelum.dxgi.win32

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

public class WindowsLibraryTest {
    @Test
    public fun `only the audited Windows x64 process ABI is accepted`() {
        assertTrue(WindowsLibrary.isWindowsX64("Windows 11", "amd64", 8))
        assertTrue(WindowsLibrary.isWindowsX64("windows server", "x86_64", 8))

        assertFalse(WindowsLibrary.isWindowsX64("Windows 11", "aarch64", 8))
        assertFalse(WindowsLibrary.isWindowsX64("Windows 11", "x86", 4))
        assertFalse(WindowsLibrary.isWindowsX64("Linux", "x86_64", 8))
    }
}
