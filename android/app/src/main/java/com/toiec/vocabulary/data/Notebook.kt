package com.toiec.vocabulary.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface Notebook {
    fun ids(): Set<String>
    fun update(id: String, add: Boolean)
}

/** Private app file, durable before an answer is accepted. Atomic replacement preserves old data on failure. */
class FileNotebook(private val file: File) : Notebook {
    @Synchronized override fun ids(): Set<String> =
        if (file.exists()) file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.toSet() else emptySet()

    @Synchronized override fun update(id: String, add: Boolean) {
        require(id.matches(Regex("[a-f0-9]{24}")))
        val old = ids()
        val updated = if (add) old + id else old - id
        if (updated == old) return
        check(file.parentFile!!.let { it.exists() || it.mkdirs() }) { "오답 저장 폴더를 만들 수 없습니다." }
        val temporary = File.createTempFile("notebook-", ".tmp", file.parentFile)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(updated.sorted().joinToString("\n", postfix = if (updated.isEmpty()) "" else "\n").toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }
}
