package com.example.haritalar.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Local Android TTS adapter. It has no network dependency and is safe to disable when muted. */
class AndroidNavigationVoice(
    context: Context,
    private val locale: Locale = Locale("tr", "TR")
) : TextToSpeech.OnInitListener {
    private val textToSpeech = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS && textToSpeech.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE
    }

    fun speak(text: String, muted: Boolean = false) {
        if (!ready || muted || text.isBlank()) return
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lanu-navigation")
    }

    fun shutdown() {
        textToSpeech.stop()
        textToSpeech.shutdown()
        ready = false
    }
}
