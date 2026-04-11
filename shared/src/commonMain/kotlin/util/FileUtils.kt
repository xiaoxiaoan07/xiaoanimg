package util

fun formatFileSize(bytes: Long): String {
    val kb = 1024L
    val mb = 1024L * kb
    val gb = 1024L * mb

    return when {
        bytes >= gb -> "%.2f GB".format(bytes.toDouble() / gb)
        bytes >= mb -> "%.2f MB".format(bytes.toDouble() / mb)
        bytes >= kb -> "%.2f KB".format(bytes.toDouble() / kb)
        else -> "$bytes B"
    }
}
