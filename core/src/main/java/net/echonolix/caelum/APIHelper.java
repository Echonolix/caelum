package net.echonolix.caelum;

import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.util.Optional;

public class APIHelper {
    public static final @NotNull MemorySegment _$OMNI_SEGMENT$_ = MemorySegment.ofAddress(0L).reinterpret(Long.MAX_VALUE);
    public static final @NotNull Linker LINKER = Linker.nativeLinker();
    public static final @NotNull SymbolLookup LOADER_LOOKUP = SymbolLookup.loaderLookup();
    public static final @NotNull MemoryLayout POINTER_LAYOUT = ValueLayout.JAVA_LONG;
    public static final @NotNull SymbolLookup SYMBOL_LOOKUP = name -> {
        Optional<MemorySegment> inLoader = LOADER_LOOKUP.find(name);
        if (inLoader.isEmpty()) {
            return LOADER_LOOKUP.find(name);
        } else {
            return inLoader;
        }
    };

    public static @NotNull MemorySegment findSymbol(@NotNull String symbol) {
        return SYMBOL_LOOKUP.find(symbol)
            .orElseThrow(() -> new RuntimeException("unable to find symbol " + symbol));
    }
}

