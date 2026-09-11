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

    private data class QueuedUtterance(val text: String, val isPriority: Boolean)
    private val pendingInitQueue = mutableListOf<QueuedUtterance>()

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

            // Flush pending initial utterances if any were requested before onInit completed
            synchronized(pendingInitQueue) {
                for (queued in pendingInitQueue) {
                    executeSpeak(queued.text, queued.isPriority)
                }
                pendingInitQueue.clear()
            }
        } else {
            Log.e("TurkishTtsManager", "TTS initialization failed with status $status")
        }
    }

    /**
     * CRITICAL PRONUNCIATION RULE:
     * In Android TTS, all-caps acronym-like words such as "LANU" or spaced "L A N U"
     * are mistakenly spelled letter-by-letter ("L - A - N - U" or "El - Ay - En - Yu").
     * By normalizing all variations to title-cased "Lanu", Turkish TTS synthesizes
     * it naturally and fluently as a single word: "Lanu".
     */
    fun formatTextForPronunciation(text: String): String {
        return text
            .replace(Regex("\\bL[\\s-]?A[\\s-]?N[\\s-]?U\\b", RegexOption.IGNORE_CASE), "Lanu")
            .replace("LANU", "Lanu")
    }

    fun speak(text: String, isPriority: Boolean = false) {
        if (isMuted) return
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return

        val now = System.currentTimeMillis()
        if (!isPriority && cleanText == lastSpokenText && (now - lastSpokenTime < repeatCooldownMs)) {
            // Suppress duplicate announcement
            return
        }

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

    /**
     * Plays the initial pre-trip safety announcement and route start greeting:
     * 1. Pre-trip safety announcement:
     *    “Lütfen emniyet kemerinizi takınız. Aynalarınızı ve lastiklerinizi kontrol ediniz. LANU güvenli ve iyi yolculuklar dileriz.”
     * 2. Route start announcement:
     *    “LANU, iyi yolculuklar diler. Rotanız başlıyor.”
     */
    fun playNavigationStartSequence() {
        val safetyText = "Lütfen emniyet kemerinizi takınız. Aynalarınızı ve lastiklerinizi kontrol ediniz. LANU güvenli ve iyi yolculuklar dileriz."
        val startText = "LANU, iyi yolculuklar diler. Rotanız başlıyor."

        speak(safetyText, isPriority = true)
        speak(startText, isPriority = false)
    }

    /**
     * Announces arrival when destination is reached:
     * “Vardınız. LANU sağlıklı günler diler.”
     */
    fun announceArrival() {
        speak("Vardınız. LANU sağlıklı günler diler.", isPriority = true)
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

    fun announceLaneGuidance(laneHint: String) {
        speak(laneHint, isPriority = false)
    }

    fun stop() {
        synchronized(pendingInitQueue) {
            pendingInitQueue.clear()
        }
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
        } catch (e: Exception) {
            // Ignore on shutdown
        }
    }
}

