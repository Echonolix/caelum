package net.echonolix.caelum.dxgi.com

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HResultTest {
    @Test
    fun signBitDefinesSuccessAndFailure() {
        assertTrue(HResult.S_OK.succeeded)
        assertTrue(HResult.S_FALSE.succeeded)
        assertFalse(HResult.S_FALSE.failed)
        assertTrue(HResult.E_FAIL.failed)
        assertEquals("0x80004005", HResult.E_FAIL.toString())
    }

    @Test
    fun checkPreservesSuccessAndReportsTheOperation() {
        assertEquals(HResult.S_FALSE, HResult.S_FALSE.check("probe"))
        val exception = assertFailsWith<ComException> { HResult.E_NOINTERFACE.check("QueryInterface") }
        assertEquals("QueryInterface", exception.operation)
        assertEquals(HResult.E_NOINTERFACE, exception.result)
    }
}
