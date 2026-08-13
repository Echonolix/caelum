package net.echonolix.caelum.dxgi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DxgiInterfacesTest {
    @Test
    fun dxgiTenThroughSixteenCatalogIsCompleteAndUnique() {
        assertEquals(46, DxgiInterfaces.ALL.size)
        assertEquals(46, DxgiInterfaces.ALL.map { it.name }.toSet().size)
        assertEquals(46, DxgiInterfaces.ALL.map { it.iid }.toSet().size)
        assertTrue(DxgiInterfaces.ALL.all { it.vtableSize >= 3 })
        assertEquals(32, DxgiInterfaces.IDXGIFactory7.vtableSize)
        assertEquals(41, DxgiInterfaces.IDXGISwapChain4.vtableSize)
        assertEquals(40, DxgiInterfaces.IDXGIInfoQueue.vtableSize)
        assertEquals(7, DxgiInterfaces.IDXGIDebug1.vtableSize)
    }
}
