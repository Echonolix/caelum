package net.echonolix.caelum.directx.demo.jolt.museum

import net.echonolix.caelum.directx.demo.Win32DemoWindow
import net.echonolix.caelum.directx.demo.jolt.FrameCapture
import net.echonolix.caelum.directx.demo.jolt.STEP
import net.echonolix.caelum.directx.demo.jolt.dxr.JoltDxrRenderer
import net.echonolix.caelum.jolt.JoltRuntime
import net.echonolix.caelum.jolt.createWorld
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.locks.LockSupport

/** Full DX12/DXR rendering of the current Jolt state, never a frozen startup ray-traced image. */
public fun main(): Unit {
    check(System.getProperty("os.name").startsWith("Windows",true)) { "DXR museum requires Windows x64" }
    val hidden=System.getProperty("caelum.museum.hidden","false").toBooleanStrict()
    val requestedFrames=System.getProperty("caelum.museum.frames","0").toInt()
    require(requestedFrames==0 || requestedFrames>=720) { "Museum acceptance requires at least 720 fixed steps" }
    val frames=if(hidden && requestedFrames==0) 720 else requestedFrames
    val workers=System.getProperty("caelum.museum.workers","2").toInt()
    val samples=System.getProperty("caelum.museum.samples","2").toInt()
    require(workers in 0..64 && samples in 1..32)
    val output=Path.of(System.getProperty("caelum.museum.output","build/jolt-dxr-museum")).toAbsolutePath()
    Files.createDirectories(output)
    val summary=output.resolve("summary.json")
    Files.writeString(summary,"{\"status\":\"running\"}\n")
    Files.writeString(output.resolve("lifecycle.txt"),"status=running\n")
    val baseline=JoltRuntime.liveNativeResources()
    val assets=MuseumAssets()
    var quit=false
    var run=0
    var currentOutput=output
    println("BACKEND=DX12_DXR HARDWARE_REQUIRED=true JOLT_COMMIT=${JoltRuntime.JOLT_COMMIT} WORKERS=$workers")
    println("CONTROLS: Space=pause, R=reset, Escape=exit. Domino chain triggers after about 6 seconds.")
    try {
      JoltRuntime.acquire().use { runtime ->
        Win32DemoWindow("Caelum DX12 / DXR - Jolt Kinetic Museum | Space pause | R reset | Esc exit",960,640,hidden).use { window ->
            do {
                var reset=false
                var accepted=false
                var passedReport: String?=null
                val runOutput=if(run==0) output else output.resolve("reset-$run").also(Files::createDirectories)
                currentOutput=runOutput
                Files.writeString(runOutput.resolve("summary.json"),"{\"status\":\"running\"}\n")
                Files.writeString(runOutput.resolve("lifecycle.txt"),"status=running\n")
                runtime.createWorld(workers=workers).use { world ->
                    KineticMuseumScene(runtime,world,assets).use { scene ->
                        JoltDxrRenderer(window.hwnd,960,640,assets.meshes,scene.instances,scene.camera,scene.target,samples).use { renderer ->
                            Files.newBufferedWriter(runOutput.resolve("transforms.csv")).use { csv ->
                                csv.write("step,name,id,x,y,z,qx,qy,qz,qw,speed\n")
                                scene.writeTransforms(csv::write)
                                var initial: FrameCapture?=null
                                var changedPixels=0
                                fun checkpoint() {
                                    val capture=renderer.capture(runOutput.resolve("frame-${scene.steps.toString().padStart(4,'0')}.png"))
                                    val reference=initial
                                    if(reference==null) initial=capture else changedPixels=reference.changedPixels(capture)
                                    val evidence=renderer.evidence()
                                    check(evidence.getValue("primaryHits").toLong()>1000)
                                    check(evidence.getValue("shadowOcclusions").toLong()>10) { "No actual shadow occlusions: $evidence" }
                                    check(evidence.getValue("reflectionHits").toLong()>10) { "No reflected geometry hits: $evidence" }
                                    check(evidence.getValue("mesh${assets.teapot}PrimaryHits").toLong()>10) { "Teapot absent from GPU primary rays: $evidence" }
                                    check(evidence.getValue("mesh${assets.dragon}PrimaryHits").toLong()>10) { "Stanford Dragon absent from GPU primary rays: $evidence" }
                                    Files.writeString(runOutput.resolve("gpu-${scene.steps.toString().padStart(4,'0')}.json"),json("captured",evidence))
                                    println("DXR_CHECKPOINT step=${scene.steps} changedPixels=$changedPixels teapotHits=${evidence["mesh${assets.teapot}PrimaryHits"]} dragonHits=${evidence["mesh${assets.dragon}PrimaryHits"]} shadowOcclusions=${evidence["shadowOcclusions"]} reflectionHits=${evidence["reflectionHits"]}")
                                }
                                renderer.render(scene.instances,hidden)
                                checkpoint()
                                var paused=false
                                var accumulated=0.0
                                var previous=System.nanoTime()
                                while(frames==0 || scene.steps<frames) {
                                    if(window.pollCloseRequested() || window.consumeKeyPress(0x1b)) { quit=true; break }
                                    if(window.consumeKeyPress(0x52)) { reset=true; break }
                                    if(window.consumeKeyPress(0x20)) paused=!paused
                                    val now=System.nanoTime()
                                    if(!hidden) {
                                        accumulated+=minOf((now-previous)/1e9,.25)
                                        previous=now
                                        if(paused) { accumulated=0.0; LockSupport.parkNanos(4_000_000); continue }
                                        if(accumulated<STEP) { LockSupport.parkNanos(1_000_000); continue }
                                        accumulated-=STEP
                                    }
                                    scene.step()
                                    renderer.render(scene.instances,hidden)
                                    if(scene.steps % 60==0) scene.writeTransforms(csv::write)
                                    if(scene.steps in listOf(180,360,420,540,720) || scene.steps==frames) checkpoint()
                                    if(scene.steps==720 || (frames>0 && scene.steps==frames)) {
                                        val metrics=scene.metrics()+renderer.evidence()+mapOf("changedPixels" to changedPixels,"workers" to workers,"samplesPerPixel" to samples)
                                        Files.writeString(runOutput.resolve("summary.json"),json("failed",metrics))
                                        scene.verify()
                                        check(changedPixels>2000) { "Physics moved without a corresponding GPU frame change: $changedPixels" }
                                        check(renderer.evidence().getValue("tlasBuilds").toLong()==scene.steps+1L)
                                        renderer.checkHealth()
                                        passedReport=json("passed",metrics)
                                        accepted=true
                                        println("MUSEUM_ACCEPTANCE=${scene.metrics()}")
                                    }
                                }
                                check(!hidden || accepted) { "Hidden museum closed/reset before its acceptance checkpoint" }
                                renderer.checkHealth()
                            }
                        }
                    }
                }
                val after=JoltRuntime.liveNativeResources()
                check(after==baseline) { "Native resources leaked: $baseline -> $after" }
                Files.writeString(runOutput.resolve("lifecycle.txt"),"liveNativeResourcesBefore=$baseline\nliveNativeResourcesAfter=$after\n")
                Files.writeString(runOutput.resolve("summary.json"),passedReport ?: "{\"status\":\"closed-before-acceptance\"}\n")
                if(accepted) println("CAELUM_JOLT_DXR_MUSEUM_OK")
                run++
            } while(reset && !quit)
        }
    }
    check(JoltRuntime.liveNativeResources()==baseline)
    } catch(failure: Throwable) {
        val message=(failure.message ?: failure.javaClass.simpleName)
            .replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n")
        runCatching { Files.writeString(currentOutput.resolve("summary.json"),"{\"status\":\"failed\",\"error\":\"$message\"}\n") }
            .exceptionOrNull()?.let(failure::addSuppressed)
        throw failure
    }
}

private fun json(status: String, values: Map<String,Number>): String = buildString {
    append("{\n  \"status\":\"$status\",\n  \"joltCommit\":\"${JoltRuntime.JOLT_COMMIT}\",\n")
    append(values.entries.joinToString(",\n") { "  \"${it.key}\":${it.value}" }); append("\n}\n")
}
