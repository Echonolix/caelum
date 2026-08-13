package net.echonolix.caelum.dxgi.com

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.StructLayout
import java.lang.foreign.ValueLayout
import java.nio.ByteOrder
import java.util.UUID

/**
 * A Windows GUID value.
 *
 * Text uses the canonical RFC 4122 field order. Native memory uses the Windows
 * `GUID` layout: the first three fields are little-endian integers followed by
 * eight bytes in text order.
 */
public class Guid private constructor(
    public val mostSignificantBits: Long,
    public val leastSignificantBits: Long,
) {
    public fun write(destination: MemorySegment, offset: Long = 0L) {
        requireRange(destination, offset)
        destination.set(DATA1, offset + DATA1_OFFSET, (mostSignificantBits ushr 32).toInt())
        destination.set(DATA2, offset + DATA2_OFFSET, (mostSignificantBits ushr 16).toShort())
        destination.set(DATA3, offset + DATA3_OFFSET, mostSignificantBits.toShort())
        for (index in 0 until DATA4_LENGTH) {
            val shift = (DATA4_LENGTH - 1 - index) * Byte.SIZE_BITS
            destination.set(ValueLayout.JAVA_BYTE, offset + DATA4_OFFSET + index, (leastSignificantBits ushr shift).toByte())
        }
    }

    public fun allocate(arena: Arena): MemorySegment = arena.allocate(LAYOUT).also(::write)

    override fun equals(other: Any?): Boolean =
        other is Guid &&
            mostSignificantBits == other.mostSignificantBits &&
            leastSignificantBits == other.leastSignificantBits

    override fun hashCode(): Int =
        (mostSignificantBits xor (mostSignificantBits ushr 32) xor
            leastSignificantBits xor (leastSignificantBits ushr 32)).toInt()

    override fun toString(): String = UUID(mostSignificantBits, leastSignificantBits).toString()

    public companion object {
        private val CANONICAL_PATTERN: Regex =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        private val DATA1: ValueLayout.OfInt =
            ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN).withName("Data1")
        private val DATA2: ValueLayout.OfShort =
            ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN).withName("Data2")
        private val DATA3: ValueLayout.OfShort =
            ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN).withName("Data3")

        public val LAYOUT: StructLayout = java.lang.foreign.MemoryLayout.structLayout(
            DATA1,
            DATA2,
            DATA3,
            java.lang.foreign.MemoryLayout.sequenceLayout(8L, ValueLayout.JAVA_BYTE).withName("Data4"),
        ).withName("GUID")

        public const val BYTE_SIZE: Long = 16L

        private const val DATA1_OFFSET: Long = 0L
        private const val DATA2_OFFSET: Long = 4L
        private const val DATA3_OFFSET: Long = 6L
        private const val DATA4_OFFSET: Long = 8L
        private const val DATA4_LENGTH: Int = 8

        init {
            check(LAYOUT.byteSize() == BYTE_SIZE) { "Unexpected native GUID size: ${LAYOUT.byteSize()}" }
            check(LAYOUT.byteAlignment() == Integer.BYTES.toLong()) {
                "Unexpected native GUID alignment: ${LAYOUT.byteAlignment()}"
            }
        }

        /** Parses only the canonical 8-4-4-4-12 GUID representation. */
        public fun parse(text: String): Guid {
            require(CANONICAL_PATTERN.matches(text)) {
                "GUID must use the canonical 8-4-4-4-12 hexadecimal form: $text"
            }
            val uuid = UUID.fromString(text)
            return Guid(uuid.mostSignificantBits, uuid.leastSignificantBits)
        }

        public fun fromBits(mostSignificantBits: Long, leastSignificantBits: Long): Guid =
            Guid(mostSignificantBits, leastSignificantBits)

        public fun read(source: MemorySegment, offset: Long = 0L): Guid {
            requireRange(source, offset)
            val data1 = source.get(DATA1, offset + DATA1_OFFSET).toLong() and 0xffff_ffffL
            val data2 = source.get(DATA2, offset + DATA2_OFFSET).toLong() and 0xffffL
            val data3 = source.get(DATA3, offset + DATA3_OFFSET).toLong() and 0xffffL
            val mostSignificantBits = (data1 shl 32) or (data2 shl 16) or data3
            var leastSignificantBits = 0L
            for (index in 0 until DATA4_LENGTH) {
                leastSignificantBits = (leastSignificantBits shl Byte.SIZE_BITS) or
                    (source.get(ValueLayout.JAVA_BYTE, offset + DATA4_OFFSET + index).toLong() and 0xffL)
            }
            return Guid(mostSignificantBits, leastSignificantBits)
        }

        private fun requireRange(segment: MemorySegment, offset: Long) {
            require(offset >= 0L) { "GUID offset must not be negative: $offset" }
            require(offset <= segment.byteSize() - BYTE_SIZE) {
                "GUID needs $BYTE_SIZE bytes at offset $offset, segment size is ${segment.byteSize()}"
            }
        }
    }
}
