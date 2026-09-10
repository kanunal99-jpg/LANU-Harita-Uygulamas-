package com.example.haritalar.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TurkishTtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    var isMuted: Boolean = false

    private var lastSpokenText: String = ""
    private var lastSpokenTime: Long = 0L
    private val repeatCooldownMs: Long = 12_000L // 12 seconds minimum between identical phrases

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale("tr", "TR")
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to default locale if Turkish voice pack not installed
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setSpeechRate(1.0f)
            tts?.setPitch(1.0f)
            isInitialized = true
            Log.d("TurkishTtsManager", "TTS initialized successfully")
        } else {
            Log.e("TurkishTtsManager", "TTS initialization failed with status $status")
        }
    }

    fun speak(text: String, isPriority: Boolean = false) {
        if (isMuted || !isInitialized) return
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        val now = System.currentTimeMillis()
        if (!isPriority && cleanText == lastSpokenText && (now - lastSpokenTime < repeatCooldownMs)) {
            // Suppress duplicate announcement
            return
        }

        lastSpokenText = cleanText
        lastSpokenTime = now

        val queueMode = if (isPriority) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(cleanText, queueMode, null, "NAV_${System.currentTimeMillis()}")
    }

    fun speakDistanceInstruction(distanceMeters: Double, instruction: String) {
        val formatted = when {
            distanceMeters > 1000 -> {
                val km = String.format(Locale.US, "%.1f", distanceMeters / 1000.0)
                "$km kilometre sonra $instruction"
            }
            distanceMeters > 80 -> {
                val rounded = (Math.round(distanceMeters / 50.0) * 50).toInt()
                "$rounded metre sonra $instruction"
            }
            distanceMeters > 20 -> {
                "Şimdi $instruction"
            }
            else -> instruction
        }
        speak(formatted)
    }

    fun announceReroute() {
        speak("Rotanızdan çıktınız. Yeni rota hesaplanıyor.", isPriority = true)
    }

    fun announceArrival() {
        speak("Hedefinize ulaştınız. Haritalar iyi günler diler.", isPriority = true)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            // Ignore on shutdown
        }
    }
}
