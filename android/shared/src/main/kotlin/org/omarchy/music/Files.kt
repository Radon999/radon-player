package org.omarchy.music

import java.io.File

fun writeAtomic(file: File, text: String) {
    file.parentFile?.mkdirs()
    val tmp = File(file.path + ".tmp")
    tmp.writeText(text)
    // Windows won't rename over a file that's there (renameTo just fails, and nothing was ever saved): replace it
    try {
        java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    } catch (_: Exception) {
        java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}

fun readText(file: File): String? = try { file.readText() } catch (_: Exception) { null }
