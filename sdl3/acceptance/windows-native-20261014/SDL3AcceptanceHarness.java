import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Path;

public final class SDL3AcceptanceHarness {
    public static void main(String[] args) throws Throwable {
        if (args.length != 2) throw new IllegalArgumentException("expected invalid DLL and valid SDL3.dll paths");
        Class<?> sdl = Class.forName("net.echonolix.caelum.sdl3.SDL");
        Method load = sdl.getMethod("load", Path.class);
        Object sdlTarget = Modifier.isStatic(load.getModifiers()) ? null : sdl.getField("INSTANCE").get(null);
        boolean rejected = false;
        try {
            load.invoke(sdlTarget, Path.of(args[0]).toAbsolutePath().normalize());
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (!(cause instanceof UnsatisfiedLinkError)) throw cause;
            rejected = true;
            System.out.println("SDL_BAD_DLL_REJECTED=" + cause.getClass().getName() + ": " + cause.getMessage());
        }
        if (!rejected) throw new AssertionError("Invalid DLL unexpectedly loaded");
        Method isLoaded = sdl.getMethod("isLoaded");
        Object stateTarget = Modifier.isStatic(isLoaded.getModifiers()) ? null : sdl.getField("INSTANCE").get(null);
        if (Boolean.TRUE.equals(isLoaded.invoke(stateTarget))) {
            throw new AssertionError("Failed DLL load incorrectly marked SDL as loaded");
        }
        System.out.println("SDL_BAD_DLL_LEFT_UNLOADED_OK");

        System.setProperty("sdl3Dll", Path.of(args[1]).toAbsolutePath().normalize().toString());
        Class<?> smoke = Class.forName("net.echonolix.caelum.sdl3.SDL3SmokeKt");
        Method main = smoke.getMethod("main");
        for (int cycle = 1; cycle <= 3; cycle++) {
            System.out.println("SDL3_REPEAT_CYCLE=" + cycle);
            try {
                main.invoke(null);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
        System.out.println("SDL3_REPEATED_NATIVE_ACCEPTANCE_OK cycles=3");
    }
}