package net.echonolix.caelum.directx.demo.jolt

import net.echonolix.caelum.*
import net.echonolix.caelum.directx.demo.Vec3
import net.echonolix.caelum.jolt.*
import net.echonolix.caelum.jolt.enums.JoltContactKind
import net.echonolix.caelum.jolt.enums.JoltMotionType
import net.echonolix.caelum.jolt.structs.*
import java.lang.foreign.Arena
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

internal const val STEP = 1f / 60f
internal val TEAL = Vec3(.04f, .65f, .48f)
internal val ORANGE = Vec3(.95f, .28f, .06f)
internal val BLUE = Vec3(.08f, .33f, .9f)
internal val GOLD = Vec3(.95f, .65f, .08f)

internal class VisualBody(
    val name: String,
    val geometry: Geometry,
    val id: JoltBodyId?,
    var position: JoltPosition,
    var color: Vec3,
    val grid: Boolean = false,
    var visible: Boolean = true,
) {
    val start: JoltPosition = position
    var rotation = JoltRotation()
    var speed = 0.0
    var active = false
    var dynamic = false
}

/** One world per scene. Physics IDs and reusable native arrays stay within that world's lifetime. */
internal class PhysicsScene(val name: String, private val runtime: JoltRuntime, val world: JoltPhysicsSystem) : AutoCloseable {
    val objects = ArrayList<VisualBody>()
    val camera = when (name) {
        "stack" -> Vec3(13f, 11f, 18f)
        "bowling" -> Vec3(14f, 13f, 21f)
        "kinematic" -> Vec3(13f, 11f, 17f)
        else -> error("Unknown scene: $name")
    }
    val target = if (name == "stack") Vec3(0f, 2.5f, 0f) else Vec3(0f, 1f, 1f)
    private val arena = Arena.ofConfined()
    private val scope = WrappedAllocateScope.Impl(arena)
    private val ids: NArray<NUInt32>
    private val transforms: NArray<JoltBodyTransform>
    private val physical: List<VisualBody>
    private val contacts = AtomicInteger()
    private val callerThread = Thread.currentThread().threadId()
    private val callbackThreads = ConcurrentHashMap.newKeySet<Long>()
    private val sensorEnters = AtomicInteger()
    private val sensorExits = AtomicInteger()
    private var sensorId: JoltBodyId? = null
    private lateinit var platform: VisualBody
    private lateinit var rider: VisualBody
    private lateinit var probe: VisualBody
    private lateinit var rayMarker: VisualBody
    private lateinit var rayBeam: VisualBody
    private val sensorEdges = ArrayList<VisualBody>()
    private val pins = ArrayList<VisualBody>()
    private var platformError = 0.0
    private var riderTravel = 0.0
    private var rayHits = 0
    private var rayMisses = 0
    private var rayPlatformHits = 0
    private var expectedPlatformX = 0.0
    private var started = false
    private var closed = false
    var steps = 0
        private set

    init {
        try {
            when (name) {
                "stack" -> stack()
                "bowling" -> bowling()
                "kinematic" -> kinematic()
            }
            physical = objects.filter { it.id != null }
            ids = scope.malloc(NUInt32, physical.size.toLong())
            transforms = JoltBodyTransform.allocate(scope, physical.size.toLong())
            physical.forEachIndexed { i, body -> ids[i.toLong()] = body.id!!.bits }
            world.setListeners(object : JoltContactListener {
                override fun contact(event: JoltContactEventView, settings: JoltContactSettingsView) {
                    contacts.incrementAndGet()
                    callbackThreads.add(Thread.currentThread().threadId())
                    if (sensorId != null && event.kind == JoltContactKind.CJ_CONTACT_ADDED) {
                        val a = event.body1.bodyId
                        val b = event.body2.bodyId
                        if (a == sensorId || b == sensorId) {
                            sensorEnters.incrementAndGet()
                            val other = if (a == sensorId) b else a
                            // Copy the ID; never capture the borrowed event in a deferred command.
                            world.defer { bodies.setUserData(other, 1uL) }
                        }
                    }
                }
                override fun removed(body1: JoltBodyId, subShape1: JoltSubShapeId, body2: JoltBodyId, subShape2: JoltSubShapeId) {
                    if (body1 == sensorId || body2 == sensorId) sensorExits.incrementAndGet()
                }
            })
            world.optimizeBroadPhase()
            sync()
        } catch (failure: Throwable) {
            arena.close()
            throw failure
        }
    }

    private fun body(
        label: String, geometry: Geometry, x: Double, y: Double, z: Double, color: Vec3,
        motion: JoltMotionType = JoltMotionType.CJ_DYNAMIC, grid: Boolean = false,
        configure: NValue<JoltBodyCreateInfo>.() -> Unit = {},
    ): VisualBody {
        val shape = when (geometry) {
            is Geometry.Box -> runtime.createBox(JoltVector(geometry.x, geometry.y, geometry.z))
            is Geometry.Sphere -> runtime.createSphere(geometry.radius)
            is Geometry.Capsule -> runtime.createCapsule(geometry.halfHeight, geometry.radius)
        }
        val id = shape.use { MemoryStack {
            val settings = JoltBodyCreateInfo.allocate {
                position.set(x, y, z)
                this.motion = motion
                layer = JoltObjectLayer.of(if (motion == JoltMotionType.CJ_STATIC) 0 else 1)
                friction = .75f
                configure()
            }
            world.bodies.createAndAdd(settings, it, motion != JoltMotionType.CJ_STATIC)
        } }
        return VisualBody(label, geometry, id, JoltPosition(x, y, z), color, grid).also(objects::add)
    }

    private fun decoration(label: String, geometry: Geometry, x: Double, y: Double, z: Double, color: Vec3): VisualBody =
        VisualBody(label, geometry, null, JoltPosition(x, y, z), color).also(objects::add)

    private fun floor(x: Float = 9f, z: Float = 9f) = body("floor", Geometry.Box(x, .5f, z), 0.0, -.5, 0.0,
        Vec3(.08f, .115f, .17f), JoltMotionType.CJ_STATIC, grid = true)

    private fun stack() {
        floor()
        // Three five-box columns, plus falling spheres and capsules on the same floor.
        for (column in -1..1) for (row in 0..4) {
            body("box_${column}_$row", Geometry.Box(.48f, .48f, .48f), column * 1.25,
                1.1 + row * 1.12, -1.2, listOf(TEAL, BLUE, ORANGE)[column + 1])
        }
        for (i in 0..3) {
            body("sphere_$i", Geometry.Sphere(.5f), -3.6 + i * 2.4, 6.5 + i * .55, 1.6, GOLD) { restitution = .25f }
            body("capsule_$i", Geometry.Capsule(.5f, .3f), -3.6 + i * 2.4, 3.0 + i * .4, 3.6, TEAL) {
                rotation.set(0f, 0f, sin(.2f * i), kotlin.math.cos(.2f * i))
            }
        }
    }

    private fun bowling() {
        floor(7f, 12f)
        // Side rails are colliders. Lane stripes are only visual markers.
        for (x in listOf(-3.8, 3.8)) {
            body("rail_$x", Geometry.Box(.12f, .3f, 11f), x, .3, 0.0, TEAL, JoltMotionType.CJ_STATIC)
            decoration("stripe_$x", Geometry.Box(.025f, .008f, 10f), x * .86, .01, 0.0, GOLD)
        }
        body("backstop", Geometry.Box(3.8f, .8f, .15f), 0.0, .8, -11.5, BLUE, JoltMotionType.CJ_STATIC)
        for (row in 0..3) for (column in 0..row) {
            pins += body("pin_${pins.size + 1}", Geometry.Capsule(.62f, .27f), (column - row * .5) * 1.0,
                .9, -row * .95, Vec3(.88f, .9f, .82f)) { friction = .35f }
        }
        probe = body("bowling_ball", Geometry.Sphere(.8f), .22, .82, 8.5, ORANGE) {
            friction = .25f; restitution = .12f; linearDamping = .02f
        }
    }

    private fun kinematic() {
        floor(9f, 8f)
        platform = body("platform", Geometry.Box(2f, .25f, 1.4f), 0.0, 1.0, -1.0, BLUE,
            JoltMotionType.CJ_KINEMATIC) { friction = 1f }
        rider = body("rider", Geometry.Box(.45f, .45f, .45f), 0.0, 1.72, -1.0, GOLD) { friction = 1f }
        val sensor = body("sensor", Geometry.Box(1.1f, 1f, 1f), 0.0, 1.1, 4.0, TEAL, JoltMotionType.CJ_STATIC) { isSensor = 1u }
        sensor.visible = false
        sensorId = sensor.id
        // Twelve thin bars show the bounds while keeping the sensor volume transparent.
        for (y in listOf(.1, 2.1)) for (z in listOf(3.0, 5.0))
            sensorEdges += decoration("sensor_x", Geometry.Box(1.1f, .025f, .025f), 0.0, y, z, TEAL)
        for (x in listOf(-1.1, 1.1)) for (z in listOf(3.0, 5.0))
            sensorEdges += decoration("sensor_y", Geometry.Box(.025f, 1f, .025f), x, 1.1, z, TEAL)
        for (x in listOf(-1.1, 1.1)) for (y in listOf(.1, 2.1))
            sensorEdges += decoration("sensor_z", Geometry.Box(.025f, .025f, 1f), x, y, 4.0, TEAL)
        probe = body("sensor_probe", Geometry.Sphere(.35f), -5.0, 1.1, 4.0, ORANGE) {
            gravityFactor = 0f; linearDamping = 0f; allowSleeping = 0u
        }
        rayMarker = decoration("ray_hit", Geometry.Sphere(.13f), 0.0, 0.0, 0.0, ORANGE).also { it.visible = false }
        rayBeam = decoration("query_ray", Geometry.Box(.018f, 3.5f, .018f), 0.0, 4.5, -1.8, GOLD)
    }

    fun step() {
        if (!started) {
            if (name == "bowling") world.bodies.setVelocity(probe.id!!, JoltVector(0f, 0f, -11f), JoltVector(-11f / .8f, 0f, 0f))
            if (name == "kinematic") world.bodies.setVelocity(probe.id!!, JoltVector(2f, 0f, 0f))
            started = true
        }
        if (name == "kinematic") {
            expectedPlatformX = 2.2 * sin((steps + 1) * STEP.toDouble() * .7)
            world.bodies.moveKinematic(platform.id!!, JoltPosition(expectedPlatformX, 1.0, -1.0), JoltRotation(), STEP)
        }
        world.update(STEP).throwIfFailed()
        steps++
        sync()
        if (name == "kinematic") {
            platformError = maxOf(platformError, abs(platform.position.x - expectedPlatformX))
            riderTravel = maxOf(riderTravel, abs(rider.position.x))
            sensorEdges.forEach { it.color = if (sensorEnters.get() > 0) GOLD else TEAL }
            // The ray moves with the platform, offset from the rider, and highlights the returned hit point.
            val hit = world.query.castRay(JoltPosition(platform.position.x, 8.0, -1.8), JoltVector(0f, -10f, 0f))
            rayMarker.visible = hit != null
            if (hit != null) {
                rayHits++
                if (hit.bodyId == platform.id) rayPlatformHits++
                rayMarker.position = hit.position
            }
            rayBeam.position = JoltPosition(platform.position.x, 4.5, -1.8)
            if (world.query.castRay(JoltPosition(30.0, 8.0, 30.0), JoltVector(0f, 5f, 0f)) == null) rayMisses++
        }
    }

    private fun sync() {
        world.bodies.readTransforms(ids, transforms)
        physical.forEachIndexed { i, body ->
            val t = transforms[i.toLong()]
            check(t.bodyId == body.id!!.bits) { "Batch transform ID mismatch" }
            body.position = JoltPosition(t.position.x, t.position.y, t.position.z)
            body.rotation = JoltRotation(t.rotation.x, t.rotation.y, t.rotation.z, t.rotation.w)
            body.active = t.isActive != 0.toUByte()
            body.dynamic = t.motionType == JoltMotionType.CJ_DYNAMIC.value
            body.speed = sqrt((t.linearVelocity.x * t.linearVelocity.x + t.linearVelocity.y * t.linearVelocity.y +
                t.linearVelocity.z * t.linearVelocity.z).toDouble())
            val p = body.position
            val q = body.rotation
            check(listOf(p.x, p.y, p.z, q.x.toDouble(), q.y.toDouble(), q.z.toDouble(), q.w.toDouble(), body.speed).all { it.isFinite() }) {
                "Non-finite transform for ${body.name} at step $steps"
            }
            check(abs(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w - 1f) < .005f) { "Invalid quaternion: ${body.name}" }
        }
    }

    fun metrics(): Map<String, Number> {
        val dynamic = physical.filter { it.dynamic }
        return linkedMapOf<String, Number>(
            "steps" to steps, "simulatedSeconds" to steps * STEP.toDouble(), "bodies" to physical.size,
            "contacts" to contacts.get(), "activeDynamicBodies" to dynamic.count { it.active },
            "nativeCallbackThreads" to callbackThreads.count { it != callerThread },
            "maxSpeed" to (dynamic.maxOfOrNull { it.speed } ?: 0.0),
        ).apply {
            when (name) {
                "stack" -> {
                    put("descendedBodies", dynamic.count { it.start.y - it.position.y > .25 })
                    put("minBodyY", dynamic.minOf { it.position.y })
                    put("maxBodyY", dynamic.maxOf { it.position.y })
                }
                "bowling" -> {
                    put("fallenPins", pins.count { abs(1 - 2 * (it.rotation.x * it.rotation.x + it.rotation.z * it.rotation.z)) < .65 })
                    put("displacedPins", pins.count { distanceXZ(it.start, it.position) > .5 })
                    put("ballTravel", probe.start.z - probe.position.z)
                }
                "kinematic" -> {
                    put("maxPlatformError", platformError); put("riderTravel", riderTravel)
                    put("riderY", rider.position.y); put("sensorEnters", sensorEnters.get()); put("sensorExits", sensorExits.get())
                    put("probeX", probe.position.x); put("probeY", probe.position.y)
                    put("probeUserData", world.bodies.transform(probe.id!!).userData.toLong())
                    put("rayHits", rayHits); put("rayMisses", rayMisses); put("rayPlatformHits", rayPlatformHits)
                }
            }
        }
    }

    /** Acceptance checks apply to runs of at least six simulated seconds. */
    fun verify() {
        require(steps >= 360) { "Physics acceptance requires at least 360 fixed steps" }
        val m = metrics()
        check(contacts.get() > 0) { "No contact callbacks: $m" }
        when (name) {
            "stack" -> {
                check(m.getValue("descendedBodies").toInt() >= 20) { "Bodies did not fall: $m" }
                check(m.getValue("minBodyY").toDouble() > .2 && m.getValue("maxBodyY").toDouble() < 5.1) { "Floor/stack bounds: $m" }
                check(m.getValue("maxSpeed").toDouble() < .3) { "Stack has not settled: $m" }
            }
            "bowling" -> {
                check(m.getValue("fallenPins").toInt() >= 5 && m.getValue("displacedPins").toInt() >= 5) { "Insufficient pin impacts: $m" }
                check(m.getValue("ballTravel").toDouble() > 8.0) { "Ball did not reach the pins: $m" }
            }
            "kinematic" -> {
                check(platformError < .005 && riderTravel > 1.0 && rider.position.y in 1.6..1.8) { "Platform/rider motion: $m" }
                check(sensorEnters.get() > 0 && sensorExits.get() > 0) { "Missing sensor entry/exit: $m" }
                check(abs(probe.position.x - (-5.0 + steps * STEP * 2)) < .02 && abs(probe.position.y - 1.1) < .01) { "Sensor blocked the probe: $m" }
                check(m.getValue("probeUserData").toLong() == 1L) { "Deferred callback command did not execute: $m" }
                check(rayHits == steps && rayMisses == steps && rayPlatformHits == steps) { "Ray query mismatch: $m" }
            }
        }
    }

    fun writeTransforms(append: (String) -> Unit) {
        for (body in physical) {
            val p = body.position; val q = body.rotation
            append("$steps,${body.name},${body.id!!.bits},${p.x},${p.y},${p.z},${q.x},${q.y},${q.z},${q.w},${body.speed},${body.active}\n")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try { world.clearListeners() } finally { arena.close() }
    }
}

private fun distanceXZ(a: JoltPosition, b: JoltPosition): Double = sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z))
