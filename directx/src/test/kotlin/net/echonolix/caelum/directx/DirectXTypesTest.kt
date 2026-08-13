package net.echonolix.caelum.directx

import kotlin.test.Test
import kotlin.test.assertEquals

public class DirectXTypesTest {
    @Test
    public fun `RTV descriptor heap type is the SDK raw value two`() {
        // Do not compare against another project constant: the raw SDK value is
        // the regression boundary for the historical native-driver crash.
        assertEquals(2, D3D12DescriptorHeapType.RTV.rawValue)
    }

    @Test
    public fun `feature levels cover 9_1 through 12_2 in preference order`() {
        assertEquals(
            listOf(0xC200, 0xC100, 0xC000, 0xB100, 0xB000, 0xA100, 0xA000, 0x9300, 0x9200, 0x9100),
            D3DFeatureLevel.descending.map(D3DFeatureLevel::rawValue),
        )
    }
}
