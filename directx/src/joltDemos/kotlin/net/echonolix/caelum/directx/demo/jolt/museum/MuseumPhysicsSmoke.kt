package net.echonolix.caelum.directx.demo.jolt.museum

import net.echonolix.caelum.jolt.JoltRuntime
import net.echonolix.caelum.jolt.createWorld
import java.nio.file.Files
import java.nio.file.Path

/** Separate physics gate; it never reports DXR acceptance. */
public fun main(): Unit {
    val output=Path.of(System.getProperty("caelum.museum.output", "build/jolt-museum-physics")).toAbsolutePath()
    Files.createDirectories(output)
    val workers=System.getProperty("caelum.museum.workers", "2").toInt()
    val before=JoltRuntime.liveNativeResources()
    JoltRuntime.acquire().use { runtime -> runtime.createWorld(workers=workers).use { world ->
        KineticMuseumScene(runtime,world,MuseumAssets()).use { scene ->
            Files.newBufferedWriter(output.resolve("transforms.csv")).use { csv ->
                csv.write("step,name,id,x,y,z,qx,qy,qz,qw,speed\n")
                scene.writeTransforms(csv::write)
                repeat(720) {
                    scene.step()
                    if (scene.steps % 60 == 0) {
                        println("MUSEUM_PHYSICS=${scene.metrics()}")
                        scene.writeTransforms(csv::write)
                    }
                }
            }
            Files.writeString(output.resolve("metrics.txt"), scene.metrics().toString())
            scene.verify()
        }
    } }
    check(JoltRuntime.liveNativeResources()==before)
    println("CAELUM_JOLT_MUSEUM_PHYSICS_OK")
}
