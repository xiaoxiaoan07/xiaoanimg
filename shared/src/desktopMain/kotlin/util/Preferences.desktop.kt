package util

import java.io.File
import java.util.Properties

private val configFile = File(System.getProperty("user.home"), ".payload_extract_gui/config.properties")
private val properties = Properties()

private fun initializeConfig() {
    configFile.parentFile?.mkdirs()
    if (configFile.exists()) {
        properties.load(configFile.inputStream())
    }
}

actual fun prefSet(key: String, value: String) {
    if (properties.isEmpty) initializeConfig()
    properties.setProperty(key, value)
    configFile.outputStream().use { properties.store(it, "Payload Extract GUI Configuration") }
}

actual fun prefGet(key: String): String? {
    if (properties.isEmpty) initializeConfig()
    return properties.getProperty(key)
}
