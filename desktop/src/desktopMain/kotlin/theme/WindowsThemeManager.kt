package theme

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import kotlin.time.Duration.Companion.milliseconds

object WindowsThemeManager {
    private const val REGISTRY_KEY_PATH =
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
    private const val REGISTRY_VALUE_NAME = "AppsUseLightTheme"
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20

    /**
     * FFM downcall handle for dwmapi!DwmSetWindowAttribute(HWND, DWORD, LPCVOID, DWORD).
     * Resolved lazily; null when dwmapi / the symbol can't be found. Replaces the former
     * Rust JNI setWindowDarkTitleBar export (FFM is a stable API on JDK 22+).
     */
    private val dwmSetWindowAttribute: MethodHandle? by lazy {
        try {
            val sym = SymbolLookup.libraryLookup("dwmapi", Arena.global())
                .find("DwmSetWindowAttribute").orElseThrow()
            Linker.nativeLinker().downcallHandle(
                sym,
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ),
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Reads HKCU\...\Personalize\AppsUseLightTheme via reg.exe.
     * dark == (value == 0); value missing / any error => light (false).
     * Mirrors the ProcessBuilder approach used by MacOSThemeManager / LinuxThemeManager.
     */
    fun isWindowsDarkTheme(): Boolean {
        return try {
            val process = ProcessBuilder(
                "reg", "query", REGISTRY_KEY_PATH, "/v", REGISTRY_VALUE_NAME
            ).redirectErrorStream(true).start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()

            // Line of interest: "    AppsUseLightTheme    REG_DWORD    0x1"
            val line = output.lineSequence()
                .firstOrNull { it.contains(REGISTRY_VALUE_NAME) }
                ?: return false // value absent -> Windows defaults to light

            // Take the hex token (locale-independent; the name/REG_DWORD words may localize).
            val hexToken = line.trim().split(Regex("\\s+"))
                .firstOrNull { it.startsWith("0x", ignoreCase = true) }
                ?: return false

            hexToken.substring(2).toLongOrNull(16) == 0L // 0 == light off == dark on
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Applies the immersive dark/light title bar via dwmapi!DwmSetWindowAttribute through the
     * Java FFM downcall handle. The native HWND is resolved without JNA, see [getHwnd].
     * invokeWithArguments (not the signature-polymorphic MethodHandle.invoke) keeps this
     * ProGuard-safe. No-op when dwmapi or the HWND can't be resolved.
     */
    fun setWindowsTitleBarTheme(window: java.awt.Window, isDark: Boolean) {
        try {
            val handle = dwmSetWindowAttribute ?: return
            val hwnd = getHwnd(window)
            if (hwnd == 0L) return
            Arena.ofConfined().use { arena ->
                val pv = arena.allocate(ValueLayout.JAVA_INT)
                pv.set(ValueLayout.JAVA_INT, 0L, if (isDark) 1 else 0)
                handle.invokeWithArguments(
                    MemorySegment.ofAddress(hwnd),
                    DWMWA_USE_IMMERSIVE_DARK_MODE,
                    pv,
                    4,
                )
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Resolves the native Win32 HWND of an AWT window without JNA, via the JDK-internal
     * sun.awt.AWTAccessor -> WComponentPeer.getHWnd() (public long). Requires
     * --add-opens java.desktop/sun.awt and java.desktop/sun.awt.windows (set in build.gradle.kts).
     * Returns 0 when unavailable (window not yet realized / access denied), so theming is skipped.
     */
    private fun getHwnd(window: java.awt.Window): Long {
        return try {
            val componentAccessor = Class.forName("sun.awt.AWTAccessor")
                .getMethod("getComponentAccessor")
                .apply { isAccessible = true }
                .invoke(null)
            val peer = Class.forName("sun.awt.AWTAccessor\$ComponentAccessor")
                .getMethod("getPeer", java.awt.Component::class.java)
                .apply { isAccessible = true }
                .invoke(componentAccessor, window) ?: return 0L
            peer.javaClass.getMethod("getHWnd")
                .apply { isAccessible = true }
                .invoke(peer) as Long
        } catch (_: Throwable) {
            0L
        }
    }

    /**
     * Polls [isWindowsDarkTheme] and fires [onThemeChanged] only when the value flips.
     * Cancellable via the surrounding coroutine. Replaces the former JNA
     * RegNotifyChangeKeyValue listener, matching the Linux/Mac polling pattern.
     */
    suspend fun listenWindowsThemeChanges(onThemeChanged: (isDark: Boolean) -> Unit) {
        var lastValue = isWindowsDarkTheme()
        while (currentCoroutineContext().isActive) {
            val currentValue = isWindowsDarkTheme()
            if (currentValue != lastValue) {
                lastValue = currentValue
                onThemeChanged(currentValue)
            }
            delay(1500.milliseconds)
        }
    }
}
