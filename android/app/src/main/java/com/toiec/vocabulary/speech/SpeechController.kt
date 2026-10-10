package com.toiec.vocabulary.speech

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

enum class SpeechStatus { LOADING, READY, UNAVAILABLE }
interface SpeechPort {
    fun speak(text: String): Boolean
    fun stop()
    fun shutdown()
}

/** A question key includes the session, position and entry ID, never grading/recomposition state. */
class SpeechController(private val port: SpeechPort) {
    var automatic = true; private set
    private var foreground = false
    private var status = SpeechStatus.LOADING
    private var key: String? = null
    private var text: String? = null
    private var pending = false
    private var closed = false

    fun setAutomatic(enabled: Boolean) {
        automatic = enabled
        if (!enabled) pending = false
        // Turning ON is intentionally never a trigger for the current question.
    }
    fun showQuestion(questionKey: String?, word: String?) {
        if (closed || questionKey == key) return
        port.stop()
        key = questionKey; text = word; pending = questionKey != null && !word.isNullOrBlank() && automatic && status != SpeechStatus.UNAVAILABLE
        playPending()
    }
    fun setForeground(active: Boolean) {
        if (closed) return
        foreground = active
        if (!active) { pending = false; port.stop() } else playPending()
    }
    fun setStatus(value: SpeechStatus) {
        if (closed) return
        status = value
        if (value == SpeechStatus.UNAVAILABLE) pending = false
        playPending()
    }
    private fun playPending() {
        if (!closed && pending && foreground && status == SpeechStatus.READY) {
            pending = false // Consume before speaking, including engine errors/reentrant callbacks.
            text?.let(port::speak)
        }
    }
    fun replay(): Boolean {
        if (closed || !foreground || status != SpeechStatus.READY || key == null || text.isNullOrBlank()) return false
        pending = false
        port.stop()
        return port.speak(checkNotNull(text))
    }
    fun stop() { pending = false; port.stop() }
    fun close() {
        if (closed) return
        closed = true; pending = false; key = null; text = null
        port.stop(); port.shutdown()
    }
}

/** Kept separate from notebook and daily progress; JVM tests use the same durable file implementation. */
class SoundSettings(private val file: File) {
    fun read(): Boolean = if (!file.exists()) true else when (file.readText().trim()) {
        "ON" -> true; "OFF" -> false; else -> error("Invalid sound setting")
    }
    fun write(enabled: Boolean) {
        check(file.parentFile!!.let { it.exists() || it.mkdirs() })
        val temp = File.createTempFile("sound-", ".tmp", file.parentFile)
        try {
            FileOutputStream(temp).use { it.write((if (enabled) "ON\n" else "OFF\n").toByteArray()); it.fd.sync() }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.delete() }
    }
}
