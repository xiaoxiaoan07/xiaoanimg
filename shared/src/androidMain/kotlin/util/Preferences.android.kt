package util

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences

// For Android, we need an application context. This is a simple static holder.
// In production, initialize via Application.onCreate() or ContentProvider.
@SuppressLint("StaticFieldLeak")
object AndroidAppContext {
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    fun get(): Context? = context
}

private val sharedPreferences: SharedPreferences? by lazy {
    AndroidAppContext.get()?.getSharedPreferences("PayloadExtractGUI", Context.MODE_PRIVATE)
}

actual fun prefSet(key: String, value: String) {
    sharedPreferences?.edit()?.putString(key, value)?.apply()
}

actual fun prefGet(key: String): String? {
    return sharedPreferences?.getString(key, null)
}
