package util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.lang.ref.WeakReference

actual class FilePicker(private val activity: Activity) {
    private var fileCallback: ((String?) -> Unit)? = null
    private var dirCallback: ((String?) -> Unit)? = null
    private var openedFd: ParcelFileDescriptor? = null

    var filePickerLauncher: ActivityResultLauncher<Intent>? = null
    var dirPickerLauncher: ActivityResultLauncher<Uri?>? = null

    fun onFileResult(uri: Uri?) {
        val path = uri?.let { resolvePathFromUri(it) }
        fileCallback?.invoke(path)
        fileCallback = null
    }

    fun onDirResult(uri: Uri?) {
        val path = uri?.let { resolveTreePathFromUri(it) }
        dirCallback?.invoke(path)
        dirCallback = null
    }

    actual fun pickFile(title: String, extensions: List<String>, onResult: (String?) -> Unit) {
        fileCallback = onResult
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, title)
        }
        filePickerLauncher?.launch(intent) ?: onResult(null)
    }

    actual fun pickDirectory(title: String, onResult: (String?) -> Unit) {
        dirCallback = onResult
        dirPickerLauncher?.launch(null) ?: onResult(null)
    }

    fun closeOpenedFd() {
        openedFd?.close()
        openedFd = null
    }

    private fun resolvePathFromUri(uri: Uri): String {
        closeOpenedFd()
        val pfd = activity.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open file descriptor for $uri")
        openedFd = pfd
        return "/proc/self/fd/${pfd.fd}"
    }

    private fun resolveTreePathFromUri(uri: Uri): String {
        val docUri = DocumentsContract.buildDocumentUriUsingTree(
            uri, DocumentsContract.getTreeDocumentId(uri)
        )
        val path = docUri.path
        if (path != null && path.contains(":")) {
            val split = path.substringAfter(":")
            if (path.contains("primary")) {
                return "/storage/emulated/0/$split"
            }
        }
        return activity.getExternalFilesDir(null)?.absolutePath ?: activity.cacheDir.absolutePath
    }
}

private var filePickerRef: WeakReference<FilePicker>? = null

fun initFilePicker(activity: ComponentActivity) {
    val picker = FilePicker(activity)

    picker.filePickerLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        picker.onFileResult(result.data?.data)
    }

    picker.dirPickerLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        picker.onDirResult(uri)
    }

    filePickerRef = WeakReference(picker)
}

actual fun createFilePicker(): FilePicker {
    return filePickerRef?.get() ?: error("FilePicker not initialized. Call initFilePicker() in Activity.onCreate()")
}
