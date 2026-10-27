package net.echonolix.caelum.jolt

/** Immutable native table-filter input. Tables are copied when a world is created. */
public class JoltLayers private constructor(
    internal val mapping: ByteArray,
    internal val collisions: ByteArray,
    public val broadPhaseLayerCount: Int,
) {
    public val objectLayerCount: Int get() = mapping.size

    public fun collides(first: JoltObjectLayer, second: JoltObjectLayer): Boolean {
        val a = first.bits.toInt(); val b = second.bits.toInt()
        require(a < objectLayerCount && b < objectLayerCount)
        return collisions[a * objectLayerCount + b] != 0.toByte()
    }

    public companion object {
        /** Layer 0 is non-moving; layer 1 collides with both, layer 0 only with 1. */
        public fun movingAndStatic(): JoltLayers = of(intArrayOf(0, 1), 2) { a, b -> a == 1 || b == 1 }

        public fun of(
            objectToBroadPhase: IntArray,
            broadPhaseLayerCount: Int,
            shouldCollide: (Int, Int) -> Boolean,
        ): JoltLayers {
            require(objectToBroadPhase.size in 1..4096) { "table filters support 1..4096 object layers" }
            require(broadPhaseLayerCount in 1..256)
            require(objectToBroadPhase.all { it in 0 until broadPhaseLayerCount })
            val count = objectToBroadPhase.size
            val matrix = ByteArray(Math.multiplyExact(count, count))
            for (a in 0 until count) for (b in 0..a) {
                val ab = shouldCollide(a, b)
                require(ab == shouldCollide(b, a)) { "Collision table must be symmetric at $a/$b" }
                val value: Byte = if (ab) 1 else 0
                matrix[a * count + b] = value; matrix[b * count + a] = value
            }
            return JoltLayers(ByteArray(count) { objectToBroadPhase[it].toByte() }, matrix, broadPhaseLayerCount)
        }
    }
}
