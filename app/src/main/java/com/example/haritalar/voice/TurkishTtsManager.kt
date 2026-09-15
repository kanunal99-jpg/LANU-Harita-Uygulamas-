package com.example.haritalar.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.haritalar.navigation.NavigationVoicePolicy
import java.util.Locale

class TurkishTtsManager(context: Context) : TextToSpeech.OnInitListener, NavigationVoice {
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var isInitialized = false
    var isMuted: Boolean = false

    private var lastSpokenText: String = ""
    private var lastSpokenTime: Long = 0L
    private val repeatCooldownMs: Long = 12_000L

    private data class QueuedUtterance(val text: String, val isPriority: Boolean)
    private val pendingInitQueue = mutableListOf<QueuedUtterance>()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locale = Locale("tr", "TR")
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setSpeechRate(1.0f)
            tts?.setPitch(1.0f)
            isInitialized = true
            Log.d("TurkishTtsManager", "TTS initialized successfully")
            synchronized(pendingInitQueue) {
                for (queued in pendingInitQueue) executeSpeak(queued.text, queued.isPriority)
                pendingInitQueue.clear()
            }
        } else {
            Log.e("TurkishTtsManager", "TTS initialization failed with status $status")
        }
    }

    fun formatTextForPronunciation(text: String): String {
        return text
            .replace(Regex("\\bL[\\s-]?A[\\s-]?N[\\s-]?U\\b", RegexOption.IGNORE_CASE), "Lanu")
            .replace("LANU", "Lanu")
    }

    override fun speak(text: String, isPriority: Boolean) {
        if (isMuted) return
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        val now = System.currentTimeMillis()
        if (!isPriority && cleanText == lastSpokenText && now - lastSpokenTime < repeatCooldownMs) return

        lastSpokenText = cleanText
        lastSpokenTime = now

        if (!isInitialized) {
            synchronized(pendingInitQueue) {
                if (isPriority) pendingInitQueue.clear()
                pendingInitQueue.add(QueuedUtterance(cleanText, isPriority))
            }
            return
        }
        executeSpeak(cleanText, isPriority)
    }

    private fun executeSpeak(text: String, isPriority: Boolean) {
        val speechReadyText = formatTextForPronunciation(text)
        val queueMode = if (isPriority) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        try {
            tts?.speak(speechReadyText, queueMode, null, "NAV_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e("TurkishTtsManager", "Error speaking text: ${e.message}")
        }
    }

    override fun playNavigationStartSequence() {
        speak(
            "${NavigationVoicePolicy.SEAT_BELT_MESSAGE} ${NavigationVoicePolicy.START_MESSAGE}",
            isPriority = true
        )
    }

    override fun announceArrival() {
        speak(NavigationVoicePolicy.ARRIVAL_MESSAGE, isPriority = true)
    }

    fun speakDistanceInstruction(distanceMeters: Double, instruction: String) {
        val formatted = when {
            distanceMeters > 1000 -> {
                val km = String.format(Locale.US, "%.1f", distanceMeters / 1000.0)
                "$km kilometre sonra $instruction"
            }
            distanceMeters > 80 -> "${(Math.round(distanceMeters / 50.0) * 50).toInt()} metre sonra $instruction"
            distanceMeters > 20 -> "Şimdi $instruction"
            else -> instruction
        }
        speak(formatted)
    }

    override fun announceReroute() {
        speak("Rotanızdan çıktınız. Yeni rota hesaplanıyor.", isPriority = true)
    }

    fun announceLaneGuidance(laneHint: String) {
        speak(laneHint)
    }

    override fun stop() {
        synchronized(pendingInitQueue) { pendingInitQueue.clear() }
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.e("TurkishTtsManager", "Error stopping TTS: ${e.message}")
        }
    }

    fun shutdown() {
        try {
            stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
        }
    }
}
