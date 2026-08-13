package net.echonolix.caelum.dxgi

import kotlin.test.Test
import kotlin.test.assertEquals

class DxgiFactorySmokeTest {
    @Test
    fun latestInstalledFactoryCanBeQueried() {
        if (!Dxgi.isAvailable) return

        Dxgi.createFactory2().use { factory2 ->
            factory2.queryInterface(DxgiInterfaces.IDXGIFactory7).use { factory7 ->
                assertEquals(DxgiInterfaces.IDXGIFactory7, factory7.type)
            }
        }
    }
}
