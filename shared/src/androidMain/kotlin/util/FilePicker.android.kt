package util

actual class FilePicker {
    actual fun pickFile(title: String, extensions: List<String>, onResult: (String?) -> Unit) {
        // Android file picking requires Activity context and ActivityResultContracts
        // This is a placeholder - actual implementation needs Compose integration
        onResult(null)
    }

    actual fun pickDirectory(title: String, onResult: (String?) -> Unit) {
        // Android directory picking requires Activity context
        // This is a placeholder - actual implementation needs Compose integration
        onResult(null)
    }
}

actual fun createFilePicker(): FilePicker = FilePicker()
