package com.itrepos.aiotv.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val LOG_FILE = "crash_log.txt"
    private const val MAX_SIZE = 200_000L  // 200 KB cap

    fun install(context: Context) {
        val appContext = context.applicationContext
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val file = File(appContext.filesDir, LOG_FILE)
                // Rotate if over the cap
                if (file.exists() && file.length() > MAX_SIZE) file.delete()
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                file.appendText("[$ts] Thread: ${thread.name}\n${throwable.stackTraceToString()}\n\n")
            } catch (_: Exception) {}
            default?.uncaughtException(thread, throwable)
        }
    }

    fun read(context: Context): String {
        val file = File(context.filesDir, LOG_FILE)
        return if (file.exists()) file.readText() else "No crashes recorded."
    }

    fun clear(context: Context) {
        File(context.filesDir, LOG_FILE).delete()
    }
}
