package com.example.haritalar.data.network

object LiveSharingRetryPolicy {
    const val MAX_ATTEMPTS = 3

    fun shouldRetryHttp(code: Int): Boolean =
        code == 408 || code == 429 || code in 500..599

    fun backoffMs(attempt: Int): Long = when (attempt) {
        1 -> 500L
        2 -> 1_500L
        else -> 0L
    }
}
