package dev.tidesapp.wearos.player.playback

import javax.inject.Inject
import javax.inject.Singleton

fun interface StreamingPrivilegeSource {
    suspend fun acquire(): Result<Unit>
}

@Singleton
class StreamingPrivilegesManager @Inject constructor(
    private val source: StreamingPrivilegeSource,
) {
    private var revocationListener: (() -> Unit)? = null

    suspend fun acquireStreamingPrivileges(): Result<Unit> {
        return source.acquire()
    }

    fun setRevocationListener(listener: () -> Unit) {
        revocationListener = listener
    }

    fun onPrivilegesRevoked() {
        revocationListener?.invoke()
    }
}
