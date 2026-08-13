package net.echonolix.caelum.directx

/** Native `D3D_FEATURE_LEVEL` value. */
@JvmInline
public value class D3DFeatureLevel(public val rawValue: Int) : Comparable<D3DFeatureLevel> {
    override fun compareTo(other: D3DFeatureLevel): Int = rawValue.compareTo(other.rawValue)

    override fun toString(): String = when (this) {
        LEVEL_9_1 -> "9_1"
        LEVEL_9_2 -> "9_2"
        LEVEL_9_3 -> "9_3"
        LEVEL_10_0 -> "10_0"
        LEVEL_10_1 -> "10_1"
        LEVEL_11_0 -> "11_0"
        LEVEL_11_1 -> "11_1"
        LEVEL_12_0 -> "12_0"
        LEVEL_12_1 -> "12_1"
        LEVEL_12_2 -> "12_2"
        else -> "D3D_FEATURE_LEVEL(0x${rawValue.toUInt().toString(16)})"
    }

    public companion object {
        public val LEVEL_9_1: D3DFeatureLevel = D3DFeatureLevel(0x9100)
        public val LEVEL_9_2: D3DFeatureLevel = D3DFeatureLevel(0x9200)
        public val LEVEL_9_3: D3DFeatureLevel = D3DFeatureLevel(0x9300)
        public val LEVEL_10_0: D3DFeatureLevel = D3DFeatureLevel(0xA000)
        public val LEVEL_10_1: D3DFeatureLevel = D3DFeatureLevel(0xA100)
        public val LEVEL_11_0: D3DFeatureLevel = D3DFeatureLevel(0xB000)
        public val LEVEL_11_1: D3DFeatureLevel = D3DFeatureLevel(0xB100)
        public val LEVEL_12_0: D3DFeatureLevel = D3DFeatureLevel(0xC000)
        public val LEVEL_12_1: D3DFeatureLevel = D3DFeatureLevel(0xC100)
        public val LEVEL_12_2: D3DFeatureLevel = D3DFeatureLevel(0xC200)

        /** All public desktop feature levels, in preference order. */
        public val descending: List<D3DFeatureLevel> = listOf(
            LEVEL_12_2,
            LEVEL_12_1,
            LEVEL_12_0,
            LEVEL_11_1,
            LEVEL_11_0,
            LEVEL_10_1,
            LEVEL_10_0,
            LEVEL_9_3,
            LEVEL_9_2,
            LEVEL_9_1,
        )
    }
}

/** Native `D3D_DRIVER_TYPE` shared by D3D10 and D3D11. */
public enum class D3DDriverType(public val rawValue: Int) {
    UNKNOWN(0),
    HARDWARE(1),
    REFERENCE(2),
    NULL_DRIVER(3),
    SOFTWARE(4),
    WARP(5),
}

/** Native `D3D10_FEATURE_LEVEL1`. */
public enum class D3D10FeatureLevel(public val rawValue: Int) {
    LEVEL_10_0(0xA000),
    LEVEL_10_1(0xA100),
}

/**
 * Native `D3D12_DESCRIPTOR_HEAP_TYPE`.
 *
 * Values are explicit SDK values; callers must never use enum ordinals for a
 * native call. In particular, RTV is `2` (not the CBV/SRV/UAV value `0`).
 */
public enum class D3D12DescriptorHeapType(public val rawValue: Int) {
    CBV_SRV_UAV(0),
    SAMPLER(1),
    RTV(2),
    DSV(3),
}

/** Type marker used by descriptor heaps and handles. */
public sealed interface D3D12DescriptorKind

public sealed interface D3D12CbvSrvUav : D3D12DescriptorKind

public sealed interface D3D12Sampler : D3D12DescriptorKind

public sealed interface D3D12Rtv : D3D12DescriptorKind

public sealed interface D3D12Dsv : D3D12DescriptorKind

/** A CPU descriptor handle whose heap kind is carried by [K]. */
@JvmInline
public value class D3D12CpuDescriptorHandle<K : D3D12DescriptorKind> internal constructor(
    public val address: ULong,
)

/** A GPU descriptor handle whose heap kind is carried by [K]. */
@JvmInline
public value class D3D12GpuDescriptorHandle<K : D3D12DescriptorKind> internal constructor(
    public val address: ULong,
)
