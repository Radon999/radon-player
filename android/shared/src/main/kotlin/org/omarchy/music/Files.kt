package org.omarchy.music

import java.io.File

fun writeAtomic(file: File, text: String) {
    file.parentFile?.mkdirs()
    val tmp = File(file.path + ".tmp")
    tmp.writeText(text)
    tmp.renameTo(file)
}

fun readText(file: File): String? = try { file.readText() } catch (_: Exception) { null }
