package util

import android.os.Environment
import java.io.File

actual fun defaultOutputDir(): String {
    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return File(downloads, "payload_extract_output").absolutePath
}
