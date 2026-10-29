package org.omarchy.music

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What went wrong, kept so it can be fixed: a crash's details (the app closed) go to a file before
 *  the process ends, and errors the app caught and lived through are noted too. Settings shows the
 *  last ones with a Copy button. */
object Crashes {
    private lateinit var dir: File
    /** The report shown in Settings ("" when nothing went wrong). */
    var last by mutableStateOf("")
        private set

    fun init(c: Context) {
        if (::dir.isInitialized) return
        dir = c.filesDir
        last = runCatching { File(dir, "problems.txt").readText() }.getOrDefault("")
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write("The app closed (${t.name})", e) }
            before?.uncaughtException(t, e)
        }
    }

    /** An error the app caught (it kept going). */
    fun caught(e: Throwable, what: String = "An error the app lived through") {
        android.util.Log.w("radon-player", what, e)
        runCatching { write(what, e) }
    }

    fun note(what: String) = caught(Exception(what), what)

    @Synchronized
    private fun write(what: String, e: Throwable) {
        if (!::dir.isInitialized) return
        val f = File(dir, "problems.txt")
        val stamp = SimpleDateFormat("d MMM yyyy, HH:mm:ss", Locale.US).format(Date())
        val entry = "$stamp · ${runCatching { Updater.version }.getOrDefault("?")} · $what\n${e.stackTraceToString().take(4000)}\n"
        // The newest first, and only the last few.
        val old = runCatching { f.readText() }.getOrDefault("")
        val text = (entry + "\n" + old).take(16000)
        f.writeText(text)
        last = text
    }

    fun clear() {
        if (::dir.isInitialized) File(dir, "problems.txt").delete()
        last = ""
    }
}
