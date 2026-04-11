package util

import java.io.File

actual fun defaultOutputDir(): String {
    val home = System.getProperty("user.home") ?: "."
    return File(home, "payload_extract_output").absolutePath
}
