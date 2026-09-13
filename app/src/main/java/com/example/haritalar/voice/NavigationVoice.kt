package com.example.haritalar.voice

/**
 * Voice boundary used by navigation logic.
 *
 * Keeping navigation independent from Android TextToSpeech makes the navigation engine
 * deterministic and testable while TurkishTtsManager remains the production adapter.
 */
interface NavigationVoice {
    fun speak(text: String, isPriority: Boolean = false)
    fun playNavigationStartSequence()
    fun announceArrival()
    fun announceReroute()
    fun stop()
}
