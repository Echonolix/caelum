package net.echonolix.caelum.directx.demo

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

internal class Win32DemoWindow(
    title: String,
    width: Int,
    height: Int,
    hidden: Boolean,
) : AutoCloseable {
    private val arena: Arena
    private var className: MemorySegment = MemorySegment.NULL
    private var message: MemorySegment = MemorySegment.NULL
    private var classAtom: Int = 0
    internal var hwnd: MemorySegment = MemorySegment.NULL
        private set
    private var closed = false

    init {
        arena = Arena.ofConfined()
        try {
            require(width > 0 && height > 0)
            Win32Native.ensureAvailable()
            className = arena.utf16("CaelumDirectXTeapotWindow_${System.nanoTime()}")
            val titleMemory = arena.utf16(title)
            val windowProcedure = Win32Native.windowProcedureStub(arena)
            message = arena.allocate(Win32Layouts.MSG)
            val instance = Win32Native.moduleHandle()
            val windowClass = arena.allocate(Win32Layouts.WNDCLASSEXW)
            Win32Layouts.initializeWindowClass(windowClass, instance, windowProcedure, className)
            classAtom = Win32Native.registerClass(windowClass)
            check(classAtom != 0) { "RegisterClassExW failed with Win32 error ${Win32Native.lastError()}" }

            hwnd = Win32Native.createWindow(
                className = className,
                title = titleMemory,
                instance = instance,
                width = width,
                height = height,
            )
            check(hwnd != MemorySegment.NULL && hwnd.address() != 0L) {
                "CreateWindowExW failed with Win32 error ${Win32Native.lastError()}"
            }
            if (!hidden) {
                Win32Native.showWindow(hwnd)
                Win32Native.updateWindow(hwnd)
            }
        } catch (failure: Throwable) {
            closeAfterConstructionFailure(failure)
        }
    }

    internal fun pollCloseRequested(): Boolean {
        var closeRequested = false
        while (Win32Native.peekMessage(message)) {
            if (message.get(ValueLayout.JAVA_INT, Win32Layouts.MSG_MESSAGE_OFFSET) == WM_QUIT) {
                closeRequested = true
            } else {
                Win32Native.translateMessage(message)
                Win32Native.dispatchMessage(message)
            }
        }
        return closeRequested
    }

    override fun close() {
        if (closed) return
        closed = true
        if (hwnd != MemorySegment.NULL && hwnd.address() != 0L) {
            runCatching { Win32Native.destroyWindow(hwnd) }
            hwnd = MemorySegment.NULL
        }
        if (classAtom != 0 && className != MemorySegment.NULL && className.address() != 0L) {
            runCatching { Win32Native.unregisterClass(className, Win32Native.moduleHandle()) }
            classAtom = 0
        }
        arena.close()
    }

    private fun closeAfterConstructionFailure(failure: Throwable): Nothing {
        runCatching(::close).exceptionOrNull()?.let(failure::addSuppressed)
        throw failure
    }
}

private object Win32Native {
    private val linker = Linker.nativeLinker()
    private val libraryArena = Arena.ofAuto()
    private val user32 = SymbolLookup.libraryLookup("user32.dll", libraryArena)
    private val kernel32 = SymbolLookup.libraryLookup("kernel32.dll", libraryArena)

    private fun downcall(lookup: SymbolLookup, name: String, descriptor: FunctionDescriptor) =
        linker.downcallHandle(lookup.find(name).orElseThrow(), descriptor)

    private val registerClassExW = downcall(user32, "RegisterClassExW", FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS))
    private val createWindowExW = downcall(
        user32,
        "CreateWindowExW",
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        ),
    )
    private val defWindowProcW = downcall(
        user32,
        "DefWindowProcW",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG),
    )
    private val showWindow = downcall(user32, "ShowWindow", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT))
    private val updateWindow = downcall(user32, "UpdateWindow", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
    private val destroyWindow = downcall(user32, "DestroyWindow", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
    private val unregisterClassW = downcall(user32, "UnregisterClassW", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS))
    private val peekMessageW = downcall(user32, "PeekMessageW", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT))
    private val translateMessage = downcall(user32, "TranslateMessage", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS))
    private val dispatchMessageW = downcall(user32, "DispatchMessageW", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS))
    private val postQuitMessage = downcall(user32, "PostQuitMessage", FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT))
    private val getModuleHandleW = downcall(kernel32, "GetModuleHandleW", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS))
    private val getLastError = downcall(kernel32, "GetLastError", FunctionDescriptor.of(ValueLayout.JAVA_INT))

    fun ensureAvailable() {
        registerClassExW
        createWindowExW
    }

    fun windowProcedureStub(arena: Arena): MemorySegment {
        val method = MethodHandles.lookup().findStatic(
            Win32Native::class.java,
            "windowProcedure",
            MethodType.methodType(Long::class.javaPrimitiveType, MemorySegment::class.java, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType, Long::class.javaPrimitiveType),
        )
        return linker.upcallStub(
            method,
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG),
            arena,
        )
    }

    @JvmStatic
    private fun windowProcedure(hwnd: MemorySegment, message: Int, wParam: Long, lParam: Long): Long {
        if (message == WM_DESTROY) {
            postQuitMessage.invokeExact(0)
            return 0L
        }
        return defWindowProcW.invokeExact(hwnd, message, wParam, lParam) as Long
    }

    fun registerClass(windowClass: MemorySegment): Int = (registerClassExW.invokeExact(windowClass) as Short).toInt() and 0xffff

    fun createWindow(className: MemorySegment, title: MemorySegment, instance: MemorySegment, width: Int, height: Int): MemorySegment =
        createWindowExW.invokeExact(
            0,
            className,
            title,
            WS_OVERLAPPEDWINDOW,
            CW_USEDEFAULT,
            CW_USEDEFAULT,
            width,
            height,
            MemorySegment.NULL,
            MemorySegment.NULL,
            instance,
            MemorySegment.NULL,
        ) as MemorySegment

    fun showWindow(hwnd: MemorySegment) {
        showWindow.invokeExact(hwnd, SW_SHOW) as Int
    }

    fun updateWindow(hwnd: MemorySegment) {
        updateWindow.invokeExact(hwnd) as Int
    }

    fun destroyWindow(hwnd: MemorySegment) {
        destroyWindow.invokeExact(hwnd) as Int
    }

    fun unregisterClass(className: MemorySegment, instance: MemorySegment) {
        unregisterClassW.invokeExact(className, instance) as Int
    }

    fun peekMessage(message: MemorySegment): Boolean =
        (peekMessageW.invokeExact(message, MemorySegment.NULL, 0, 0, PM_REMOVE) as Int) != 0

    fun translateMessage(message: MemorySegment) {
        translateMessage.invokeExact(message) as Int
    }

    fun dispatchMessage(message: MemorySegment) {
        dispatchMessageW.invokeExact(message) as Long
    }

    fun moduleHandle(): MemorySegment = getModuleHandleW.invokeExact(MemorySegment.NULL) as MemorySegment

    fun lastError(): Int = getLastError.invokeExact() as Int
}

private object Win32Layouts {
    private val ADDRESS = ValueLayout.ADDRESS
    private val INT = ValueLayout.JAVA_INT

    val WNDCLASSEXW: MemoryLayout = MemoryLayout.structLayout(
        INT,
        INT,
        ADDRESS,
        INT,
        INT,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
        ADDRESS,
    ).withName("WNDCLASSEXW")

    val POINT: MemoryLayout = MemoryLayout.structLayout(INT, INT)
    val MSG: MemoryLayout = MemoryLayout.structLayout(
        ADDRESS,
        INT,
        MemoryLayout.paddingLayout(4),
        ValueLayout.JAVA_LONG,
        ValueLayout.JAVA_LONG,
        INT,
        POINT,
        INT,
        MemoryLayout.paddingLayout(4),
    ).withName("MSG")

    const val MSG_MESSAGE_OFFSET = 8L

    fun initializeWindowClass(
        windowClass: MemorySegment,
        instance: MemorySegment,
        windowProcedure: MemorySegment,
        className: MemorySegment,
    ) {
        windowClass.fill(0)
        windowClass.set(INT, 0L, WNDCLASSEXW.byteSize().toInt())
        windowClass.set(INT, 4L, CS_HREDRAW or CS_VREDRAW)
        windowClass.set(ADDRESS, 8L, windowProcedure)
        windowClass.set(ADDRESS, 24L, instance)
        windowClass.set(ADDRESS, 64L, className)
    }
}

private fun Arena.utf16(value: String): MemorySegment {
    val chars = (value + '\u0000').toCharArray()
    return allocate(chars.size.toLong() * Char.SIZE_BYTES, ValueLayout.JAVA_SHORT.byteAlignment()).also { memory ->
        chars.forEachIndexed { index, char -> memory.setAtIndex(ValueLayout.JAVA_SHORT, index.toLong(), char.code.toShort()) }
    }
}

private const val CS_VREDRAW = 0x0001
private const val CS_HREDRAW = 0x0002
private const val WS_OVERLAPPEDWINDOW = 0x00CF0000
private const val CW_USEDEFAULT = -0x80000000
private const val SW_SHOW = 5
private const val PM_REMOVE = 0x0001
private const val WM_DESTROY = 0x0002
private const val WM_QUIT = 0x0012
