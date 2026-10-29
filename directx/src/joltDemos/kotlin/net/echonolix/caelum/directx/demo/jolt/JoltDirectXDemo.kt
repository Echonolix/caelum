package net.echonolix.caelum.directx.demo.jolt

import net.echonolix.caelum.directx.demo.Win32DemoWindow
import net.echonolix.caelum.jolt.JoltRuntime
import net.echonolix.caelum.jolt.createWorld
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.locks.LockSupport

/** Interactive demos and a CLI-only, deterministic-step Windows graphics/physics acceptance run. */
public fun main(): Unit {
    check(System.getProperty("os.name").startsWith("Windows", true)) { "Jolt DirectX demos require Windows" }
    val selected = System.getProperty("caelum.jolt.demo.scene", "stack")
    val names = if (selected == "all") listOf("stack", "bowling", "kinematic") else listOf(selected)
    require(names.all { it in listOf("stack", "bowling", "kinematic") }) { "Unknown scene: $selected" }
    val hidden = System.getProperty("caelum.jolt.demo.hidden", "false").toBooleanStrict()
    val requestedFrames = System.getProperty("caelum.jolt.demo.frames", "0").toInt()
    require(requestedFrames == 0 || requestedFrames >= 360) { "demoFrames must be 0 (interactive) or at least 360 for acceptance" }
    val frames = if (hidden && requestedFrames == 0) 360 else requestedFrames
    require(selected != "all" || frames > 0) { "The all-scenes launcher needs a bounded run" }
    val workers = System.getProperty("caelum.jolt.demo.workers", "0").toInt()
    require(workers in 0..64)
    val root = Path.of(System.getProperty("caelum.jolt.demo.output", "build/jolt-demos")).toAbsolutePath()
    val baseline = JoltRuntime.liveNativeResources()
    println("JOLT_COMMIT=${JoltRuntime.JOLT_COMMIT} WORKERS=$workers FIXED_STEP=$STEP")
    if (!hidden) println("Close the window to exit. Physics runs at 60 fixed steps/second; acceptance is checked after 6 seconds.")
    JoltRuntime.acquire().use { runtime ->
        // Reuse the HWND across smoke scenes so WM_QUIT from a destroyed window cannot stop the next scene.
        Win32DemoWindow("Caelum Jolt / Direct3D 11 - $selected", 1100, 760, hidden).use { window ->
            for (name in names) {
                val output = root.resolve(name)
                Files.createDirectories(output)
                val summaryPath = output.resolve("summary.json")
                Files.writeString(summaryPath, "{\"scene\":\"$name\",\"status\":\"running\"}\n")
                Files.writeString(output.resolve("lifecycle.txt"), "status=running\n")
                var accepted = false
                var acceptedSummary: String? = null
                runtime.createWorld(workers = workers).use { world ->
                    PhysicsScene(name, runtime, world).use { scene ->
                        JoltSceneRenderer(window.hwnd, 1100, 760, scene.objects.map { it.geometry }, scene.camera, scene.target).use { renderer ->
                            Files.newBufferedWriter(output.resolve("transforms.csv")).use { csv ->
                                csv.write("step,name,bodyId,x,y,z,qx,qy,qz,qw,speed,active\n")
                                scene.writeTransforms(csv::write)
                                renderer.render(scene.objects)
                                val initial = renderer.capture(output.resolve("frame-0000.png"))
                                renderer.present(hidden)
                                var changedPixels = 0
                                var previousTime = System.nanoTime()
                                var accumulated = 0.0
                                while (frames == 0 || scene.steps < frames) {
                                    if (window.pollCloseRequested()) {
                                        check(frames == 0) { "Window closed before all $frames fixed steps completed" }
                                        break
                                    }
                                    if (!hidden) {
                                        val now = System.nanoTime()
                                        accumulated += minOf((now - previousTime) / 1e9, .25)
                                        previousTime = now
                                        if (accumulated < STEP) { LockSupport.parkNanos(1_000_000); continue }
                                        accumulated -= STEP
                                    }
                                    scene.step()
                                    if (scene.steps % 30 == 0) scene.writeTransforms(csv::write)
                                    renderer.render(scene.objects)
                                    if (scene.steps in listOf(60, 180, 360) || scene.steps == frames) {
                                        val frame = renderer.capture(output.resolve("frame-${scene.steps.toString().padStart(4, '0')}.png"))
                                        changedPixels = initial.changedPixels(frame)
                                    }
                                    renderer.present(hidden)
                                    if (scene.steps == 360 || (frames > 0 && scene.steps == frames)) {
                                        fun summary(status: String): String {
                                            val fields = scene.metrics().map { (key, value) -> "  \"$key\": $value" }
                                            return buildString {
                                                append("{\n  \"scene\": \"$name\",\n  \"status\": \"$status\",\n")
                                                append("  \"driver\": \"${renderer.driver}\",\n  \"workers\": $workers,\n")
                                                append("  \"joltCommit\": \"${JoltRuntime.JOLT_COMMIT}\",\n")
                                                append("  \"changedPixels\": $changedPixels,\n")
                                                append(fields.joinToString(",\n")); append("\n}\n")
                                            }
                                        }
                                        Files.writeString(summaryPath, summary("failed"))
                                        scene.verify()
                                        check(changedPixels > 500) { "Physics moved but GPU image did not: $changedPixels changed pixels" }
                                        renderer.checkHealth()
                                        acceptedSummary = summary("passed")
                                        accepted = true
                                        println("SCENE=$name METRICS=${scene.metrics()} CHANGED_PIXELS=$changedPixels")
                                    }
                                }
                                renderer.checkHealth()
                                if (frames > 0) check(accepted) { "Scene never reached its acceptance checkpoint" }
                            }
                        }
                    }
                }
                val after = JoltRuntime.liveNativeResources()
                check(after == baseline) { "Native resource leak after $name: $baseline -> $after" }
                Files.writeString(output.resolve("lifecycle.txt"), "liveNativeResourcesBefore=$baseline\nliveNativeResourcesAfter=$after\n")
                Files.writeString(summaryPath, acceptedSummary ?: "{\"scene\":\"$name\",\"status\":\"closed-before-acceptance\"}\n")
                println(if (accepted) "CAELUM_JOLT_${name.uppercase()}_DEMO_OK" else "CAELUM_JOLT_${name.uppercase()}_DEMO_CLOSED")
            }
        }
    }
    check(JoltRuntime.liveNativeResources() == baseline)
    if (selected == "all") println("CAELUM_JOLT_DIRECTX_SMOKE_OK")
}
