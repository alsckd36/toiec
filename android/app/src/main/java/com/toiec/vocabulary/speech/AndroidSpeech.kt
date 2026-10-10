package com.toiec.vocabulary.speech

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Application context only; selects an installed English voice that needs no network. */
class AndroidSpeech(context: Context, private val statusChanged: (SpeechStatus) -> Unit) : SpeechPort {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var closed = false
    private var generation = 0
    private var utterance = 0L
    private var currentUtterance: String? = null
    private var status = SpeechStatus.UNAVAILABLE

    fun initialize() {
        if (closed || status == SpeechStatus.LOADING || status == SpeechStatus.READY) return
        stop(); engine?.shutdown(); engine = null
        val attempt = ++generation
        update(SpeechStatus.LOADING)
        try {
            engine = TextToSpeech(context) { result ->
                // Initialization can call back before the constructor assignment; configure on the next main-loop turn.
                handler.post {
                    if (!closed && attempt == generation) configure(result)
                }
            }
        } catch (_: Exception) { update(SpeechStatus.UNAVAILABLE) }
    }
    private fun configure(result: Int) {
        val tts = engine
        if (result != TextToSpeech.SUCCESS || tts == null) { update(SpeechStatus.UNAVAILABLE); return }
        try {
            val voice = tts.voices.orEmpty().filter {
                it.locale.language == Locale.ENGLISH.language && !it.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
            }.sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale == Locale.US }.thenByDescending { it.quality }).firstOrNull()
            if (voice == null || tts.setVoice(voice) != TextToSpeech.SUCCESS) { update(SpeechStatus.UNAVAILABLE); return }
            tts.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = Unit
                @Deprecated("Legacy TTS callback")
                override fun onError(utteranceId: String?) { reportError(utteranceId) }
                override fun onError(utteranceId: String?, errorCode: Int) { reportError(utteranceId) }
            })
            update(SpeechStatus.READY)
        } catch (_: Exception) { update(SpeechStatus.UNAVAILABLE) }
    }
    private fun reportError(id: String?) {
        handler.post { if (!closed && id != null && id == currentUtterance) update(SpeechStatus.UNAVAILABLE) }
    }
    private fun update(value: SpeechStatus) { status = value; if (!closed) statusChanged(value) }
    override fun speak(text: String): Boolean {
        if (closed || status != SpeechStatus.READY) return false
        return try {
            val id = "word-${++utterance}"
            currentUtterance = id
            val accepted = engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) == TextToSpeech.SUCCESS
            if (!accepted) update(SpeechStatus.UNAVAILABLE)
            accepted
        } catch (_: Exception) { update(SpeechStatus.UNAVAILABLE); false }
    }
    override fun stop() {
        currentUtterance = null
        try { engine?.stop() } catch (_: Exception) { }
    }
    override fun shutdown() {
        if (closed) return
        closed = true; generation++
        stop()
        try { engine?.shutdown() } catch (_: Exception) { }
        engine = null
        handler.removeCallbacksAndMessages(null)
    }
}
