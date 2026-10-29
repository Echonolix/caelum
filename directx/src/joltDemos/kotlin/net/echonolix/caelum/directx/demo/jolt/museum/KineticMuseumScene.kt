package net.echonolix.caelum.directx.demo.jolt.museum

import net.echonolix.caelum.*
import net.echonolix.caelum.directx.demo.Vec3
import net.echonolix.caelum.directx.demo.jolt.*
import net.echonolix.caelum.directx.demo.jolt.dxr.DxrSceneInstance
import net.echonolix.caelum.jolt.*
import net.echonolix.caelum.jolt.enums.JoltContactKind
import net.echonolix.caelum.jolt.enums.JoltMotionType
import net.echonolix.caelum.jolt.structs.*
import java.lang.foreign.Arena
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A contact-driven machine: domino chain -> sensor -> retracting display table + wrecking ball. */
internal class KineticMuseumScene(private val runtime: JoltRuntime, val world: JoltPhysicsSystem, val assets: MuseumAssets) : AutoCloseable {
    val camera = Vec3(21f, 18f, 28f)
    val target = Vec3(0f, 1.5f, -.5f)
    private val arena = Arena.ofConfined()
    private val allocator = WrappedAllocateScope.Impl(arena)
    private val bodies = ArrayList<Body>()
    private val shapes = HashMap<Int, JoltShape>()
    private val contacts = AtomicInteger()
    private val sensorEntries = AtomicInteger()
    private val triggered = AtomicBoolean()
    private val callbackThreads = ConcurrentHashMap.newKeySet<Long>()
    private val modelContacts = ConcurrentHashMap.newKeySet<UInt>()
    private val modelIds = HashSet<UInt>()
    private val callerThread = Thread.currentThread().threadId()
    private val dominoes = ArrayList<Body>()
    private val bricks = ArrayList<Body>()
    private val models = ArrayList<Body>()
    private val ids: NArray<NUInt32>
    private val transforms: NArray<JoltBodyTransform>
    private val table: Body
    private val rotor: Body
    private val rocker: Body
    private val cannon: Body
    private val sensor: Body
    val instances: List<DxrSceneInstance>
    private var released = false
    private var releaseStep = -1
    private var rayHits = 0
    private var rayMisses = 0
    private var maxKinematicError = 0.0
    private var closed = false
    var steps: Int = 0
        private set

    init {
        try {
            box("floor", 12f, .5f, 11f, 0.0, -.5, 0.0, Vec3(.1f, .13f, .18f), JoltMotionType.CJ_STATIC, metallic=.15f)
            for (x in listOf(-12.0, 12.0)) box("side_wall", .18f, .65f, 11f, x, .65, 0.0, TEAL, JoltMotionType.CJ_STATIC)
            for (z in listOf(-11.0, 11.0)) box("end_wall", 12f, .65f, .18f, 0.0, .65, z, TEAL, JoltMotionType.CJ_STATIC)
            // Tall invisible safety walls contain pieces tossed above the visible rail by the machinery.
            for (x in listOf(-12.0, 12.0)) box("safety_side", .15f, 5f, 11f, x, 5.0, 0.0, TEAL, JoltMotionType.CJ_STATIC, visible=false)
            for (z in listOf(-11.0, 11.0)) box("safety_end", 12f, 5f, .15f, 0.0, 5.0, z, TEAL, JoltMotionType.CJ_STATIC, visible=false)
            // Tall thin dominoes make a long, visibly propagating chain down the left side.
            repeat(32) { i ->
                dominoes += box("domino_$i", .44f, .85f, .12f, -6.4, .86, 7.5 - i * .52,
                    if (i % 2 == 0) GOLD else ORANGE)
            }
            sensor = box("chain_sensor", .65f, .35f, .32f, -6.4, .35, -9.5, TEAL, JoltMotionType.CJ_STATIC, visible=false) { isSensor = 1u }
            // A visible plinth under the sensor makes its location readable without drawing a solid volume.
            box("sensor_marker", .65f, .025f, .32f, -6.4, .025, -9.5, GOLD, JoltMotionType.CJ_STATIC)
            table = box("retracting_table", 6.2f, .18f, 2.7f, 3.0, 3.2, -3.5, BLUE, JoltMotionType.CJ_KINEMATIC) { friction=0f }
            for (row in 0..1) for (column in 0..3) {
                val model = if ((row + column) % 2 == 0) assets.teapot else assets.dragon
                val type = if (model == assets.teapot) "teapot" else "dragon"
                val body = body("${type}_${models.size}", model, -1.5 + column * 3.0,
                    3.42 - assets.bottom(model), -4.8 + row * 2.8,
                    if (type == "teapot") Vec3(.95f, .56f, .12f) else Vec3(.08f, .7f, .55f), metallic=.8f)
                models.add(body)
                modelIds.add(body.id.bits)
            }
            // Masonry wall in the ball's path; staggered courses remain individual dynamic bodies.
            for (row in 0..5) for (column in 0..7) bricks += box("brick_${row}_$column", .37f, .24f, .25f,
                2.2 + column * .78 + (row % 2) * .12, .25 + row * .49, 3.2,
                if (row % 2 == 0) Vec3(.65f, .19f, .08f) else Vec3(.9f, .39f, .11f))
            cannon = body("wrecking_ball", assets.primitive(Geometry.Sphere(.85f)), 5.1, .86, 8.5,
                Vec3(.35f, .42f, .5f), metallic=.95f) { friction = .15f; linearDamping = .01f; restitution = .15f }
            box("rotor_base", .55f, .42f, .55f, -1.8, .42, 5.0, Vec3(.22f, .26f, .34f), JoltMotionType.CJ_STATIC)
            rotor = box("motor_rotor", 2.25f, .22f, .25f, -1.8, 1.03, 5.0, ORANGE, JoltMotionType.CJ_KINEMATIC, metallic=.6f)
            for (level in 0..3) for (x in 0..3) for (z in 0..2) body("ball_${level}_${x}_$z",
                assets.primitive(Geometry.Sphere(.28f)), -3.6 + x * 1.1, 2.0 + level * .7, 3.7 + z * 1.15,
                listOf(TEAL, BLUE, GOLD, ORANGE)[x], metallic=.3f) { restitution=.35f; friction=.35f }
            rocker = box("rocker", 2.0f, .18f, 1.15f, -1.1, 1.15, -8.0, TEAL, JoltMotionType.CJ_KINEMATIC)
            for (level in 0..2) for (x in 0..2) for (z in 0..1) box("crate_${level}_${x}_$z", .3f, .3f, .3f,
                -2.25 + x * .9, 1.65 + level * .65, -8.45 + z * .9, GOLD)
            world.optimizeBroadPhase()
            ids = allocator.malloc(NUInt32, bodies.size.toLong())
            transforms = JoltBodyTransform.allocate(allocator, bodies.size.toLong())
            bodies.forEachIndexed { i, body -> ids[i.toLong()] = body.id.bits }
            instances = bodies.mapNotNull { it.instance }
            world.setListeners(object : JoltContactListener {
                override fun contact(event: JoltContactEventView, settings: JoltContactSettingsView) {
                    contacts.incrementAndGet()
                    callbackThreads.add(Thread.currentThread().threadId())
                    val a = event.body1.bodyId.bits; val b = event.body2.bodyId.bits
                    if (a in modelIds) modelContacts.add(a)
                    if (b in modelIds) modelContacts.add(b)
                    val last = dominoes.last().id.bits
                    if (event.kind == JoltContactKind.CJ_CONTACT_ADDED &&
                        ((a == sensor.id.bits && b == last) || (b == sensor.id.bits && a == last))) {
                        sensorEntries.incrementAndGet()
                        if (triggered.compareAndSet(false, true)) world.defer {
                            released = true
                            releaseStep = steps + 1
                            this.bodies.setVelocity(cannon.id, JoltVector(0f, 0f, -16f), JoltVector(-18f, 0f, 0f))
                            this.bodies.setUserData(cannon.id, 0xcafeuL)
                        }
                    }
                }
            })
            sync()
        } catch (failure: Throwable) {
            arena.close()
            throw failure
        } finally {
            shapes.values.forEach(JoltShape::close)
            shapes.clear()
        }
    }

    private fun box(label: String, hx: Float, hy: Float, hz: Float, x: Double, y: Double, z: Double,
                    color: Vec3, motion: JoltMotionType = JoltMotionType.CJ_DYNAMIC,
                    visible: Boolean = true, metallic: Float = .1f, configure: NValue<JoltBodyCreateInfo>.() -> Unit = {}): Body =
        body(label, assets.primitive(Geometry.Box(hx, hy, hz)), x, y, z, color, motion, visible, metallic, configure)

    private fun body(label: String, mesh: Int, x: Double, y: Double, z: Double, color: Vec3,
                     motion: JoltMotionType = JoltMotionType.CJ_DYNAMIC, visible: Boolean = true,
                     metallic: Float = .1f, configure: NValue<JoltBodyCreateInfo>.() -> Unit = {}): Body {
        val shape = shapes.getOrPut(mesh) {
            when (val geometry = assets.primitiveShape(mesh)) {
                is Geometry.Box -> runtime.createBox(JoltVector(geometry.x, geometry.y, geometry.z), minOf(.04f, geometry.x * .4f, geometry.y * .4f, geometry.z * .4f))
                is Geometry.Sphere -> runtime.createSphere(geometry.radius)
                is Geometry.Capsule -> runtime.createCapsule(geometry.halfHeight, geometry.radius)
                null -> runtime.createConvexHull(assets.points(mesh), .025f)
            }
        }
        val id = MemoryStack {
            val info = JoltBodyCreateInfo.allocate {
                position.set(x, y, z); this.motion = motion
                layer = JoltObjectLayer.of(if (motion == JoltMotionType.CJ_STATIC) 0 else 1)
                friction=.7f
                configure()
            }
            world.bodies.createAndAdd(info, shape, motion != JoltMotionType.CJ_STATIC)
        }
        val instance = if (visible) DxrSceneInstance(mesh, modelMatrix(JoltPosition(x,y,z), JoltRotation()), color,
            metallic, if (metallic > .5f) .18f else .45f, bodies.size + 1) else null
        return Body(label, id, motion == JoltMotionType.CJ_DYNAMIC, x, y, z, instance).also(bodies::add)
    }

    fun step() {
        if (steps == 0) world.bodies.setVelocity(dominoes.first().id, JoltVector(0f,0f,0f), JoltVector(-3f,0f,0f))
        val time = (steps + 1) * STEP
        val rotorRotation = JoltRotation(0f, sin(time * .65f), 0f, cos(time * .65f))
        world.bodies.moveKinematic(rotor.id, JoltPosition(-1.8,1.03,5.0), rotorRotation, STEP)
        val rockerAngle = .18f * sin(time * .8f)
        world.bodies.moveKinematic(rocker.id, JoltPosition(-1.1,1.15,-8.0), JoltRotation(0f,0f,sin(rockerAngle/2),cos(rockerAngle/2)), STEP)
        // Pull the shelf to the right, clear of the rocker at the back of the arena.
        val tableX = if (released) 3.0 + minOf(22.0, (steps + 1 - releaseStep) * STEP * 14.0) else 3.0
        world.bodies.moveKinematic(table.id, JoltPosition(tableX,3.2,-3.5), JoltRotation(), STEP)
        world.update(STEP, collisionSteps=2).throwIfFailed()
        steps++
        sync()
        maxKinematicError = maxOf(maxKinematicError, abs(table.x-tableX))
        if (world.query.castRay(JoltPosition(-10.0,10.0,0.0), JoltVector(0f,-20f,0f)) != null) rayHits++
        if (world.query.castRay(JoltPosition(25.0,10.0,25.0), JoltVector(0f,5f,0f)) == null) rayMisses++
    }

    private fun sync() {
        world.bodies.readTransforms(ids, transforms)
        bodies.forEachIndexed { i, body ->
            val t = transforms[i.toLong()]
            check(t.bodyId == body.id.bits)
            body.x=t.position.x; body.y=t.position.y; body.z=t.position.z
            body.qx=t.rotation.x; body.qy=t.rotation.y; body.qz=t.rotation.z; body.qw=t.rotation.w
            body.speed = sqrt((t.linearVelocity.x*t.linearVelocity.x + t.linearVelocity.y*t.linearVelocity.y + t.linearVelocity.z*t.linearVelocity.z).toDouble())
            check(body.x.isFinite() && body.y.isFinite() && body.z.isFinite() && body.speed.isFinite()) { "Non-finite body ${body.name} at step $steps" }
            check(abs(body.qx*body.qx+body.qy*body.qy+body.qz*body.qz+body.qw*body.qw-1) < .005f)
            check(!body.dynamic || body.y > -3.0) { "Body escaped through floor: ${body.name}, position=${body.x},${body.y},${body.z}" }
            body.instance?.transform?.let { m ->
                val x=body.qx; val y=body.qy; val z=body.qz; val w=body.qw
                m[0]=1-2*(y*y+z*z); m[1]=2*(x*y+z*w); m[2]=2*(x*z-y*w)
                m[4]=2*(x*y-z*w); m[5]=1-2*(x*x+z*z); m[6]=2*(y*z+x*w)
                m[8]=2*(x*z+y*w); m[9]=2*(y*z-x*w); m[10]=1-2*(x*x+y*y)
                m[12]=body.x.toFloat(); m[13]=body.y.toFloat(); m[14]=body.z.toFloat()
            }
        }
    }

    fun metrics(): Map<String, Number> = linkedMapOf(
        "steps" to steps, "bodies" to bodies.size, "dynamicBodies" to bodies.count { it.dynamic },
        "instances" to instances.size, "meshes" to assets.meshes.size,
        "contacts" to contacts.get(), "nativeCallbackThreads" to callbackThreads.count { it != callerThread },
        "sensorEntries" to sensorEntries.get(), "releaseStep" to releaseStep,
        "fallenDominoes" to dominoes.count { abs(1-2*(it.qx*it.qx+it.qz*it.qz)) < .6f },
        "displacedBricks" to bricks.count { it.distanceFromStart() > .35 },
        "models" to models.size, "teapots" to models.count { it.name.startsWith("teapot") }, "dragons" to models.count { it.name.startsWith("dragon") },
        "descendedModels" to models.count { it.startY-it.y > 1.5 }, "contactedModels" to modelContacts.size,
        "cannonTravel" to cannon.startZ-cannon.z, "cannonUserData" to world.bodies.transform(cannon.id).userData.toLong(),
        "maxKinematicError" to maxKinematicError, "rayHits" to rayHits, "rayMisses" to rayMisses,
    )

    fun verify() {
        check(steps >= 720) { "Museum needs at least 720 physics steps" }
        val m=metrics()
        check(contacts.get()>1000) { "Insufficient contacts: $m" }
        check(released && releaseStep>0 && releaseStep<600 && sensorEntries.get()>0) { "Domino/sensor chain did not release table: $m" }
        check(m.getValue("fallenDominoes").toInt() >= 28) { "Domino cascade incomplete: $m" }
        check(m.getValue("displacedBricks").toInt() >= 20 && m.getValue("cannonTravel").toDouble()>5) { "Event-driven wall impact failed: $m" }
        check(m.getValue("descendedModels").toInt()==8 && modelContacts.size==8) { "Teapot/dragon convex body motion failed: $m" }
        check(m.getValue("cannonUserData").toLong()==0xcafeL) { "Deferred command did not execute: $m" }
        check(maxKinematicError<.01 && rayHits==steps && rayMisses==steps) { "Kinematic/query validation failed: $m" }
    }

    fun writeTransforms(append: (String) -> Unit) {
        for (b in bodies) append("$steps,${b.name},${b.id.bits},${b.x},${b.y},${b.z},${b.qx},${b.qy},${b.qz},${b.qw},${b.speed}\n")
    }
    override fun close() { if (!closed) { closed=true; try { world.clearListeners() } finally { arena.close() } } }

    private class Body(val name: String, val id: JoltBodyId, val dynamic: Boolean,
                       val startX: Double, val startY: Double, val startZ: Double, val instance: DxrSceneInstance?) {
        var x=startX; var y=startY; var z=startZ
        var qx=0f; var qy=0f; var qz=0f; var qw=1f
        var speed=0.0
        fun distanceFromStart(): Double = sqrt((x-startX)*(x-startX)+(y-startY)*(y-startY)+(z-startZ)*(z-startZ))
    }
}
