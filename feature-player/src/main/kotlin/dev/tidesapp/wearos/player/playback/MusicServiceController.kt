package dev.tidesapp.wearos.player.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MusicServiceController(
    private val scope: CoroutineScope,
) {
    companion object {
        const val INACTIVITY_TIMEOUT_MS = 5L * 60 * 1000 // 5 minutes
    }

    private var inactivityJob: Job? = null
    private var stopCallback: (() -> Unit)? = null
    private var cleanedUp = false

    fun setStopCallback(callback: () -> Unit) {
        stopCallback = callback
    }

    fun startInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = scope.launch {
            delay(INACTIVITY_TIMEOUT_MS)
            stopCallback?.invoke()
        }
    }

    fun cancelInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = null
    }

    fun onTaskRemoved() {
        cancelInactivityTimer()
        if (!cleanedUp) {
            cleanedUp = true
            stopCallback?.invoke()
        }
    }

    fun onDestroy() {
        cancelInactivityTimer()
        cleanedUp = false
    }
}
