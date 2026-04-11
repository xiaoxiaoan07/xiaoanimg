package util

expect class FilePicker {
    fun pickFile(title: String, extensions: List<String>, onResult: (String?) -> Unit)
    fun pickDirectory(title: String, onResult: (String?) -> Unit)
}

expect fun createFilePicker(): FilePicker
